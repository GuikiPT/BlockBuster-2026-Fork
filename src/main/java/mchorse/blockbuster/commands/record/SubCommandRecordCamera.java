package mchorse.blockbuster.commands.record;

import mchorse.aperture.camera.CameraAPI;
import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.CameraUtils;
import mchorse.aperture.camera.data.Position;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;

/**
 * Command /record camera
 *
 * This command is responsible for generating a player recording based on
 * an Aperture camera profile. (Legacy registered it only when Aperture was
 * installed; Aperture is bundled in the port, so it is always available —
 * parity note.)
 */
public class SubCommandRecordCamera extends SubCommandRecordBase
{
    @Override
    public String getName()
    {
        return "camera";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.record.camera";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}record {8}camera{r} {7}<filename> <camera_profile> [x] [y] [z]{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 2;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        String filename = args[0];

        try
        {
            float x = args.length >= 3 ? (float) parseDouble(args[2]) : 0;
            float y = args.length >= 4 ? (float) parseDouble(args[3]) : 0;
            float z = args.length >= 5 ? (float) parseDouble(args[4]) : 0;

            Record record = this.generateProfile(filename, args[1], x, y, z);

            CommonProxy.manager.records.put(filename, record);
            RecordUtils.saveRecord(record);

            Blockbuster.l10n.success(sender, "record.camera", filename, args[1]);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Blockbuster.l10n.error(sender, "record.couldnt_save", filename);
        }
    }

    private Record generateProfile(String filename, String profile, float x, float y, float z) throws Exception
    {
        Record record = new Record(filename);
        CameraProfile camera = CameraUtils.readProfile(profile);
        Position prev = new Position();
        Position position = new Position();

        int c = (int) camera.getDuration();

        for (int i = 0; i <= c; i++)
        {
            Frame frame = new Frame();

            if (i == c)
            {
                camera.applyProfile(c - 1, 0.999F, position);
            }
            else
            {
                camera.applyProfile(i, 0, position);
            }

            if (i == 0)
            {
                prev.copy(position);
            }

            frame.x = position.point.x + x;
            frame.y = position.point.y + y;
            frame.z = position.point.z + z;
            frame.yaw = position.angle.yaw;
            frame.yawHead = position.angle.yaw;
            frame.bodyYaw = position.angle.yaw;
            frame.pitch = position.angle.pitch;
            frame.roll = position.angle.roll;

            frame.motionX = position.point.x - prev.point.x;
            frame.motionY = position.point.y - prev.point.y;
            frame.motionZ = position.point.z - prev.point.z;

            frame.hasBodyYaw = true;

            record.frames.add(frame);
            record.actions.add(null);

            prev.copy(position);
        }

        return record;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ServerCommandSource sender, String[] args)
    {
        if (args.length == 2)
        {
            return getListOfStringsMatchingLastWord(args, CameraAPI.getServerProfiles());
        }

        return super.getTabCompletions(server, sender, args);
    }
}
