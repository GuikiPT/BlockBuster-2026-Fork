package mchorse.blockbuster.client.render;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.KeyboardHandler;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.guns.PacketZoomCommand;
import mchorse.blockbuster.utils.NBTUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Gun zoom / crosshair render glue (P197) — port of the legacy
 * {@code GunMiscRender} Forge event bundle.
 *
 * <p>The three legacy concerns become:</p>
 * <ol>
 *   <li><b>Zoom ramp.</b> Driven per frame from {@link HudRenderCallback} (render
 *       cadence, like the legacy {@code RenderTickEvent} END), advancing the pure
 *       {@link GunZoomRamp} state machine and edge-sending
 *       {@code PacketZoomCommand}. The FOV / sensitivity <b>values</b> the ramp
 *       produces are read by the mixins ({@code GameRendererMixin#getFov} for
 *       FOV; sensitivity via {@link #sensitivityOverride}); the legacy option
 *       mutation + restore latches are dropped (behaviour-equal, no risk of
 *       corrupting options.txt on a crash — documented parity delta).</li>
 *   <li><b>Crosshair suppression.</b> {@code InGameHudMixin} cancels the vanilla
 *       crosshair through {@link #shouldCancelCrosshair}.</li>
 *   <li><b>Crosshair draw.</b> The custom crosshair morph is drawn here in the
 *       HUD callback via the {@link GunMorphRenderer} screen seam.</li>
 * </ol>
 *
 * Legacy source of truth:
 * {@code blockbuster-1.12/.../client/render/GunMiscRender.java}.
 */
public class GunMiscRender
{
    /** The held gun's zoom factor this frame (for the FOV mixin); 0 when none. */
    private static float zoomFactor;

    /** The held gun's mouse-zoom factor this frame (for sensitivity). */
    private static float mouseZoom = 0.5F;

    /** Whether a gun is currently held (gates the FOV / sensitivity overrides). */
    private static boolean holdingGun;

    private boolean registered;

    public void register()
    {
        if (this.registered)
        {
            return;
        }

        this.registered = true;

        HudRenderCallback.EVENT.register(this::onHud);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GunZoomRamp.reset());
    }

    private void onHud(DrawContext context, float tickDelta)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;

        if (player == null)
        {
            holdingGun = false;

            return;
        }

        ItemStack held = player.getMainHandStack();
        GunProps props = held.getItem() == Blockbuster.GUN ? NBTUtils.getGunProps(held) : null;

        holdingGun = props != null;

        if (props != null)
        {
            this.handleZoom(player, tickDelta);

            zoomFactor = props.zoomFactor;
            mouseZoom = props.mouseZoom;

            this.drawCrosshair(props, context);
        }
        else
        {
            zoomFactor = 0;
        }
    }

    private void handleZoom(ClientPlayerEntity player, float partialTick)
    {
        boolean keyDown = KeyboardHandler.zoom != null && KeyboardHandler.zoom.isPressed();

        GunZoomRamp.advance(keyDown, partialTick, zoomOn ->
            Dispatcher.sendToServer(new PacketZoomCommand(player.getId(), zoomOn)));
    }

    private void drawCrosshair(GunProps props, DrawContext context)
    {
        boolean zooming = KeyboardHandler.zoom != null && KeyboardHandler.zoom.isPressed();

        if (props.crosshairMorph == null || (zooming && props.hideCrosshairOnZoom))
        {
            return;
        }

        AbstractMorph morph = props.currentCrosshair.get();

        if (morph == null)
        {
            return;
        }

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        /* Legacy: renderOnScreen at (w/2 + cachedTranslation.x, h/2 +
         * cachedTranslation.y, scale 15, alpha 1). cachedTranslation is the
         * morph's own offset (S6). Routed through the screen seam. */
        GunMorphRenderer.drawOnScreen(morph, width / 2, height / 2, 15, 1F);
    }

    /* ------------------------------------------------------------------ *
     * Values read by the FOV / sensitivity mixins                         *
     * ------------------------------------------------------------------ */

    /**
     * The zoomed FOV, or null when no override applies. Active only while a gun
     * is held, the ramp is engaged ({@code ZOOM_TIME != 0}) and the zoom key is
     * down — matching the legacy {@code ZOOM_TIME != 0 && zoom key down} gate.
     */
    public static Double fovOverride(double baseFov)
    {
        if (isZoomActive())
        {
            return GunZoomRamp.zoomedFov(baseFov, GunZoomRamp.ZOOM_TIME, zoomFactor);
        }

        return null;
    }

    /**
     * The zoomed look sensitivity, or null when no override applies. Same gate
     * as {@link #fovOverride}.
     *
     * <p>S22/P242: consumed by {@code MouseMixin}'s {@code @Redirect} on the
     * {@code SimpleOption.getValue()} read inside {@code Mouse.updateMouse} —
     * the point at which legacy's mutated {@code gameSettings.mouseSensitivity}
     * was read. Until then this method had zero callers, so scoping changed the
     * FOV but not the look speed. The formula itself is unit-tested via
     * {@link GunZoomRamp#zoomedSensitivity}.</p>
     */
    public static Double sensitivityOverride(double baseSensitivity)
    {
        if (isZoomActive())
        {
            return GunZoomRamp.zoomedSensitivity(baseSensitivity, mouseZoom);
        }

        return null;
    }

    private static boolean isZoomActive()
    {
        return holdingGun
            && GunZoomRamp.ZOOM_TIME != 0
            && KeyboardHandler.zoom != null
            && KeyboardHandler.zoom.isPressed();
    }

    /**
     * Legacy crosshair-cancel predicate (HIGHEST-priority
     * {@code RenderGameOverlayEvent} CROSSHAIRS): cancel the vanilla crosshair
     * when hiding on zoom (and zoomed) or when a working crosshair morph exists.
     * Pure so it is headlessly testable.
     */
    public static boolean shouldCancelCrosshair(boolean hideCrosshairOnZoom, boolean zoomKeyDown, boolean currentCrosshairPresent)
    {
        return (hideCrosshairOnZoom && zoomKeyDown) || currentCrosshairPresent;
    }

    /**
     * Whether the vanilla crosshair should be cancelled for the currently held
     * item (called from the HUD mixin). Only applies to a held gun.
     */
    public static boolean shouldCancelCrosshair()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null)
        {
            return false;
        }

        ItemStack held = mc.player.getMainHandStack();

        if (held.getItem() != Blockbuster.GUN)
        {
            return false;
        }

        GunProps props = NBTUtils.getGunProps(held);

        if (props == null)
        {
            return false;
        }

        boolean zooming = KeyboardHandler.zoom != null && KeyboardHandler.zoom.isPressed();

        return shouldCancelCrosshair(props.hideCrosshairOnZoom, zooming, !props.currentCrosshair.isEmpty());
    }
}
