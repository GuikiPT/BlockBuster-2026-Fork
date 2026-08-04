package mchorse.aperture.client;

import mchorse.aperture.Aperture;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.text.Text;

/**
 * Client command {@code /load_chunks} body (S15 P186) — port of legacy
 * {@code mchorse.aperture.commands.CommandLoadChunks}, which force-compiled
 * every dummy {@code RenderChunk} in the view frustum to avoid chunk pop-in
 * before filming.
 *
 * <p>Open question #6 (S15) resolution: rather than reflecting into 1.12
 * {@code ChunkRenderDispatcher}/{@code ViewFrustum} internals (or cascading
 * access wideners into {@code BuiltChunkStorage}), the port schedules a full
 * terrain rebuild through the public {@link WorldRenderer#reload()}. This marks
 * every render-distance chunk for recompilation — the same observable end
 * state (no un-built chunks before filming). <b>Documented non-parity</b>: the
 * legacy variant compiled the dummy chunks <i>synchronously</i> in the command
 * tick; this schedules the rebuilds (they complete over the following frames).
 * For a film capture the operator runs it a moment before recording, so the
 * end state is identical.</p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/commands/CommandLoadChunks.java
 */
public final class LoadChunks
{
    private LoadChunks()
    {}

    public static void run(FabricClientCommandSource source)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.worldRenderer == null || mc.world == null)
        {
            return;
        }

        try
        {
            mc.worldRenderer.reload();
            source.sendFeedback(Text.translatable("aperture.info.commands.load_chunks"));
        }
        catch (Exception e)
        {
            Aperture.LOGGER.error("/load_chunks failed to schedule a terrain rebuild", e);
            source.sendError(Text.translatable("aperture.error.commands.load_chunks"));
        }
    }
}
