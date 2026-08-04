package mchorse.blockbuster.commands;

import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Command {@code /on_head} — port of legacy {@code commands.CommandOnHead}
 * (roadmap P125). The roadmap's cosmetic title "/on-head" notwithstanding, the
 * legacy {@code getName()} is the underscore form {@code "on_head"} — a
 * wire/registry contract we preserve.
 *
 * <p>Copies the sender's main-hand stack into the {@link EquipmentSlot#HEAD}
 * slot ({@code stack.copy()}), leaving the original in hand — a costume tool,
 * not an equip-move. Only acts when the main hand is non-empty.</p>
 *
 * <p>1.20.4 mappings: legacy {@code player.getHeldItemMainhand()} →
 * {@link ServerPlayerEntity#getMainHandStack()}; legacy
 * {@code player.setItemStackToSlot(EntityEquipmentSlot.HEAD, ...)} →
 * {@link ServerPlayerEntity#equipStack(EquipmentSlot, ItemStack)}.</p>
 */
public class CommandOnHead extends BBCommandBase
{
    @Override
    public String getName()
    {
        return "on_head";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.on_head.help";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}on_head{r}";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        ServerPlayerEntity player = getCommandSenderAsPlayer(sender);
        ItemStack stack = player.getMainHandStack();

        if (!stack.isEmpty())
        {
            player.equipStack(EquipmentSlot.HEAD, stack.copy());
        }
    }
}
