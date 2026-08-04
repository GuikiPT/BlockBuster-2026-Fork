package mchorse.mclib.commands.utils;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Localization utils — port of McLib 2.4.3's
 * {@code mchorse.mclib.commands.utils.L10n} (roadmap P22 slice; package kept
 * for diff-ability against {@code .tools/legacy-src}).
 *
 * This class provides shortcuts for sending messages to players. Legacy took
 * {@code ICommandSender}; command sources get their own overloads when the
 * Brigadier commands land (S10+), players are all the recording engine needs.
 */
public class L10n
{
    public static String ERROR_MARKER = "§4(§cX§4)§r ";
    public static String SUCCESS_MARKER = "§2(§aV§2)§r ";
    public static String INFO_MARKER = "§9(§bi§9)§r ";

    private final String id;

    public L10n(String id)
    {
        this.id = id;
    }

    /**
     * Send a translated message to player
     */
    public void send(PlayerEntity sender, String key, Object... objects)
    {
        sender.sendMessage(Text.translatable(key, objects));
    }

    /**
     * Send error message to the sender
     */
    public void error(PlayerEntity sender, String key, Object... objects)
    {
        this.sendWithMarker(sender, ERROR_MARKER, this.id + ".error." + key, objects);
    }

    /**
     * Get error message
     */
    public Text error(String key, Object... objects)
    {
        return this.messageWithMarker(ERROR_MARKER, this.id + ".error." + key, objects);
    }

    /**
     * Send success message to the sender
     */
    public void success(PlayerEntity sender, String key, Object... objects)
    {
        this.sendWithMarker(sender, SUCCESS_MARKER, this.id + ".success." + key, objects);
    }

    /**
     * Get success message
     */
    public Text success(String key, Object... objects)
    {
        return this.messageWithMarker(SUCCESS_MARKER, this.id + ".success." + key, objects);
    }

    /**
     * Send informing message to the sender
     */
    public void info(PlayerEntity sender, String key, Object... objects)
    {
        this.sendWithMarker(sender, INFO_MARKER, this.id + ".info." + key, objects);
    }

    /**
     * Get informing message
     */
    public Text info(String key, Object... objects)
    {
        return this.messageWithMarker(INFO_MARKER, this.id + ".info." + key, objects);
    }

    /* Command-source overloads (the Brigadier commands of S10+; legacy took
     * ICommandSender everywhere — these mirror that surface) */

    public void send(ServerCommandSource sender, String key, Object... objects)
    {
        Text message = Text.translatable(key, objects);

        sender.sendFeedback(() -> message, false);
    }

    public void error(ServerCommandSource sender, String key, Object... objects)
    {
        this.sendWithMarker(sender, ERROR_MARKER, this.id + ".error." + key, objects);
    }

    public void success(ServerCommandSource sender, String key, Object... objects)
    {
        this.sendWithMarker(sender, SUCCESS_MARKER, this.id + ".success." + key, objects);
    }

    public void info(ServerCommandSource sender, String key, Object... objects)
    {
        this.sendWithMarker(sender, INFO_MARKER, this.id + ".info." + key, objects);
    }

    public void sendWithMarker(ServerCommandSource sender, String marker, String key, Object... objects)
    {
        Text message = this.messageWithMarker(marker, key, objects);

        sender.sendFeedback(() -> message, false);
    }

    /**
     * Send a message with given marker
     */
    public void sendWithMarker(PlayerEntity sender, String marker, String key, Object... objects)
    {
        sender.sendMessage(this.messageWithMarker(marker, key, objects));
    }

    public Text messageWithMarker(String marker, String key, Object... objects)
    {
        MutableText message = Text.literal(marker);
        MutableText string = Text.translatable(key, objects);

        string.formatted(Formatting.GRAY);

        return message.append(string);
    }
}
