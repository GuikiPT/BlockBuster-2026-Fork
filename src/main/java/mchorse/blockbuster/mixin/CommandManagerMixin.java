package mchorse.blockbuster.mixin;

import com.mojang.brigadier.ParseResults;

import mchorse.blockbuster.recording.capturing.ActionHandler;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Legacy {@code CommandEvent} replacement — every command execution with a
 * player source lands here (chat-typed and signed alike; a single seam so
 * nothing double-records). The string arrives slash-less, matching legacy's
 * name+args reconstruction.
 */
@Mixin(CommandManager.class)
public abstract class CommandManagerMixin
{
    @Inject(method = "execute", at = @At("HEAD"))
    private void blockbuster$onCommand(ParseResults<ServerCommandSource> parseResults, String command, CallbackInfo info)
    {
        ActionHandler.onPlayerCommand(parseResults.getContext().getSource(), command);
    }
}
