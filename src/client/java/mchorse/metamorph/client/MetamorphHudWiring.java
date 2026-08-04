package mchorse.metamorph.client;

import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.client.gui.overlays.GuiHud;
import mchorse.metamorph.client.gui.overlays.GuiOverlay;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Installs Metamorph's two HUD surfaces (roadmap P225, S22) — the acquired-morph
 * toast and the squid-air replacement bubble bar.
 *
 * <p>Legacy kept both widgets on {@code ClientProxy} ({@code morphOverlay} and
 * {@code hud}) and handed them to {@code RenderingHandler}, which subscribed
 * {@code RenderGameOverlayEvent.Post(ALL)} for the toast and
 * {@code RenderGameOverlayEvent.Pre(AIR)} for the air-bar takeover. The port had
 * {@link GuiOverlay}, {@link GuiHud} and {@link ClientMorphFlow} all landed and
 * <b>all orphaned</b>: nothing constructed them, the acquire handler carried a
 * {@code SEAM(S7)} comment where the toast push belonged, and there was no HUD
 * render hook at all. This class is the missing {@code ClientProxy} +
 * {@code RenderingHandler} half.</p>
 *
 * <p><b>Toast.</b> {@link #renderOverlay} is legacy {@code GuiOverlay.render}
 * verbatim, expressed through the pure geometry functions {@link GuiOverlay}
 * already exposed. Note the timer is decremented <b>per rendered frame</b>, not
 * per client tick: legacy decremented inside {@code render()} on
 * {@code RenderGameOverlayEvent}, so the 240 "ticks" of an acquired-morph toast
 * are 240 frames — roughly 4&nbsp;s at 60&nbsp;fps and 8&nbsp;s at 30. That
 * framerate dependence is the 1.12.2 behaviour and is kept (the class javadoc's
 * earlier "drive {@code tick()} once per client tick" suggestion would have
 * tripled the on-screen lifetime).</p>
 *
 * <p><b>Air bar.</b> 1.20.4 draws the air bubbles inline inside the private
 * {@code InGameHud.renderStatusBars}, so there is no per-element event to
 * cancel. {@code InGameHudMixin} instead redirects the two inputs of that
 * segment — {@code PlayerEntity.getAir()} and
 * {@code PlayerEntity.isSubmergedIn(FluidTags.WATER)} — through
 * {@link #airBarValue} / {@link #airBarSubmerged}. Vanilla's own arithmetic is
 * bit-for-bit legacy's ({@code getMaxAir()} is 300, the bubble counts are
 * {@code ceil((air-2)*10/300)} full + {@code ceil(air*10/300) - full} bursting,
 * drawn right-to-left from {@code width/2 + 91} at the same
 * {@code GuiIngameForge.right_height} row), so redirecting the inputs
 * reproduces {@code GuiHud.renderSquidAir} exactly — including its
 * {@code squidAir < 300} visibility rule, which the {@code isSubmergedIn}
 * redirect restores.</p>
 *
 * <p><b>State source.</b> {@link #mirrorSquidAir} is the client half of legacy
 * {@code MorphHandler.onPlayerTick} ({@code if (player.world.isRemote) {
 * ClientProxy.hud.renderSquidAir = …; ClientProxy.hud.squidAir = …; }}), which
 * the port had left as a {@code SEAM(P54 client)} comment. It runs every client
 * tick off the local player's morphing component.</p>
 */
public final class MetamorphHudWiring
{
    /** Legacy {@code ClientProxy.morphOverlay}. */
    public static final GuiOverlay OVERLAY = new GuiOverlay();

    /** Legacy {@code ClientProxy.hud}. */
    public static final GuiHud HUD = new GuiHud();

    /** Legacy {@code GuiOverlay.render}'s label key. */
    public static final String ACQUIRED_KEY = "metamorph.gui.acquired";

    private static boolean installed;

    private MetamorphHudWiring()
    {}

    /**
     * Register the HUD render hook and the squid-air client-tick mirror.
     * Idempotent — a second call is a no-op, so re-running a client initializer
     * cannot double-draw the toasts (which would halve their lifetime).
     */
    public static void install()
    {
        if (installed)
        {
            return;
        }

        installed = true;

        /* Legacy RenderGameOverlayEvent.Post(ALL). HudRenderCallback fires after
         * the vanilla HUD, the same layering. */
        HudRenderCallback.EVENT.register((context, tickDelta) ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc != null && mc.getWindow() != null)
            {
                renderOverlay(context, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            }
        });

        /* Legacy MorphHandler.onPlayerTick's client-side block. */
        ClientTickEvents.END_CLIENT_TICK.register(MetamorphHudWiring::onClientTick);
    }

    public static boolean isInstalled()
    {
        return installed;
    }

    /**
     * The squid-air mirror's client-tick driver — legacy
     * {@code MorphHandler.onPlayerTick}'s {@code if (player.world.isRemote)}
     * block.
     *
     * <p><b>Pause parity (S22 P267).</b> Its legacy home is a client
     * {@code PlayerTickEvent}, which could not fire while the game was paused
     * ({@code Minecraft.runTick} guards {@code world.updateEntities()} with
     * {@code !isGamePaused}), so the mirror is gated with everything else that
     * descends from that event. It is the one gate in the P267 sweep with
     * <b>no observable effect</b>: the mirror is idempotent, and the capability
     * it reads is itself frozen once {@code PlayerHandler.endTickClient} is
     * gated, so a paused tick could only rewrite the same two values. It is here
     * so the rule has no exceptions to remember — "descends from the client
     * {@code PlayerTickEvent}" is the whole test.</p>
     */
    static void onClientTick(MinecraftClient client)
    {
        if (client != null && client.isPaused())
        {
            return;
        }

        mirrorSquidAir(client == null ? null : client.player);
    }

    /**
     * Legacy {@code GuiOverlay.render(width, height)}: draw every live toast,
     * then decrement its timer and drop it when it runs out.
     *
     * @return how many toasts were painted this frame (0 when the queue is
     *         empty — legacy's early return).
     */
    public static int renderOverlay(DrawContext context, int width, int height)
    {
        if (OVERLAY.morphs.isEmpty())
        {
            return 0;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc == null ? null : mc.player;
        TextRenderer font = mc == null ? null : mc.textRenderer;

        GuiDraw.bindDrawContext(context);

        String string = I18n.translate(ACQUIRED_KEY);
        int painted = 0;

        for (GuiOverlay.AcquiredMorph morph : OVERLAY.morphs)
        {
            int alpha = OVERLAY.alpha(morph.timer);
            int y = OVERLAY.y(morph.timer, height);
            int color = OVERLAY.color(morph.timer);

            MorphRenderUtils.renderOnScreen(morph.morph, player, 15, y, 15, (float) alpha / 255F);
            GuiDraw.drawString(font, string, 30, y - 7, color);

            painted++;
        }

        /* Legacy decremented + dropped inside the same loop; GuiOverlay.tick()
         * is that step, extracted — sweeping after the whole pass is identical
         * because every entry is visited exactly once either way. */
        OVERLAY.tick();

        return painted;
    }

    /**
     * Legacy {@code MorphHandler.onPlayerTick}'s {@code player.world.isRemote}
     * block, verbatim: with no morphing component the HUD falls back to
     * "no squid air, full 300", i.e. the vanilla bar is left alone.
     */
    public static void mirrorSquidAir(PlayerEntity player)
    {
        mirrorSquidAir(player == null ? null : Morphing.get(player));
    }

    /**
     * The mirror itself, split off the player lookup so it is drivable without a
     * client player.
     */
    public static void mirrorSquidAir(IMorphing capability)
    {
        boolean hasSquidAir = false;
        int squidAir = GuiHud.MAX_AIR;

        if (capability != null)
        {
            hasSquidAir = capability.getHasSquidAir();
            squidAir = capability.getSquidAir();
        }

        HUD.renderSquidAir = hasSquidAir;
        HUD.squidAir = squidAir;
    }

    /* ================= InGameHud mixin seams ================= */

    /**
     * Air value the vanilla bubble bar renders. While the current morph can't
     * breathe on land, the morph's squid air replaces the player's own — legacy
     * cancelled the AIR element and drew {@link GuiHud}'s bar off exactly this
     * number.
     */
    public static int airBarValue(int vanillaAir)
    {
        return HUD.renderSquidAir ? HUD.squidAir : vanillaAir;
    }

    /**
     * Whether vanilla should treat the player as submerged when deciding to draw
     * the bubble bar. Legacy's replacement bar drew <b>only</b> for
     * {@code squidAir < 300} ({@link GuiHud#shouldRender}) — never just because
     * the player is underwater — so while the takeover is active the submerged
     * arm of vanilla's {@code isSubmergedIn(WATER) || air < maxAir} condition is
     * suppressed and the bar is driven purely by the air value.
     */
    public static boolean airBarSubmerged(boolean vanillaSubmerged)
    {
        return HUD.renderSquidAir ? false : vanillaSubmerged;
    }
}
