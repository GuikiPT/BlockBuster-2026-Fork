package mchorse.blockbuster.network.server.recording.actions;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.actions.PacketActionsChange;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * OP-gated DELETE/ADD/EDIT mutate-then-save handler (roadmap P117).
 *
 * <p>Loads the record via {@code manager.get}; {@code fromTick >= 0} guards the
 * whole mutation (<b>negative fromTick packets are accepted but do nothing</b>).
 * DELETE applies the mask; ADD with an index requires exactly-one-action
 * packets ({@code containsOneAction}); EDIT with an index replaces one action;
 * then {@code saveRecord(record, false, false)} — <b>no {@code .dat~N} backup
 * and no client unload</b> (deliberate: action edits are frequent).</p>
 *
 * <p>The static client helpers <b>mutate the local record first, then send</b>
 * (optimistic mirroring). They touch only common APIs (Record + the
 * dispatcher's client→server send), so they live with the handler for
 * diff-parity; S12's editor calls them 1:1.</p>
 */
public class ServerHandlerActionsChange extends ServerMessageHandler<PacketActionsChange>
{
    @Override
    public void run(ServerPlayerEntity player, PacketActionsChange message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        Record record = null;

        try
        {
            record = CommonProxy.manager.get(message.getFilename());
        }
        catch (Exception e)
        {}

        if (record == null)
        {
            return;
        }

        if (applyChange(record, message))
        {
            try
            {
                RecordUtils.saveRecord(record, false, false);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Apply a DELETE/ADD/EDIT mutation to the record (the exact server-side
     * effect, minus the OP gate and save). {@code fromTick < 0} is accepted
     * but does nothing. Returns whether the mutation branch ran (i.e.
     * {@code fromTick >= 0}) — which is also when the legacy handler saved,
     * even if the specific case (e.g. indexed ADD without exactly one action)
     * changed nothing. Extracted so the mutation is headless-testable without
     * an OP player.
     */
    public static boolean applyChange(Record record, PacketActionsChange message)
    {
        if (message.getFromTick() < 0)
        {
            return false;
        }

        switch (message.getStatus())
        {
            case DELETE:
                record.removeActionsMask(message.getFromTick(), message.getMask());

                break;
            case ADD:
                if (message.getIndex() != -1)
                {
                    if (message.containsOneAction())
                    {
                        record.addActionCollection(message.getFromTick(), message.getIndex(), message.getActions());
                    }
                }
                else
                {
                    record.addActionCollection(message.getFromTick(), message.getActions());
                }

                break;
            case EDIT:
                if (message.getIndex() != -1)
                {
                    if (message.containsOneAction())
                    {
                        record.replaceAction(message.getFromTick(), message.getIndex(), message.getActions().get(0).get(0));
                    }
                }

                break;
        }

        return true;
    }

    /**
     * Send a deletion package to the server (optimistic: mutate locally first).
     */
    public static void deleteActions(Record record, int from, List<List<Boolean>> mask)
    {
        record.removeActionsMask(from, mask);
        Dispatcher.sendToServer(new PacketActionsChange(record.filename, from, mask));
    }

    /**
     * Send a package to the server to add the given actions at the given tick.
     */
    public static void addActions(List<List<Action>> actions, Record record, int tick)
    {
        record.addActionCollection(tick, actions);
        Dispatcher.sendToServer(new PacketActionsChange(record.filename, tick, actions, PacketActionsChange.Type.ADD));
    }

    public static void addActions(List<List<Action>> actions, Record record, int tick, int index)
    {
        if (index == -1)
        {
            addActions(actions, record, tick);
        }
        else
        {
            record.addActionCollection(tick, index, actions);
            Dispatcher.sendToServer(new PacketActionsChange(record.filename, tick, index, actions, PacketActionsChange.Type.ADD));
        }
    }

    /**
     * Send a package to the server to add an action at a specific index.
     */
    public static void addAction(Action action, Record record, int tick, int index)
    {
        record.addAction(tick, index, action);
        Dispatcher.sendToServer(new PacketActionsChange(record.filename, tick, index, action, PacketActionsChange.Type.ADD));
    }

    public static void editAction(Action action, Record record, int tick, int index)
    {
        record.replaceAction(tick, index, action);
        Dispatcher.sendToServer(new PacketActionsChange(record.filename, tick, index, action, PacketActionsChange.Type.EDIT));
    }
}
