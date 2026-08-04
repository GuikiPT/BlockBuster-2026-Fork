package mchorse.metamorph.client.render;

import mchorse.blockbuster.client.render.Nameplate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.AbstractTeam;

/**
 * The morphed entity's nameplate (roadmap P80.1).
 *
 * <p>Port of legacy {@code RenderingHandler.onNameRender} / {@code
 * canRenderName} / {@code renderEntityName}. Legacy's chain was: the player
 * render is cancelled → the morph draws a dummy entity in its place → the
 * dummy's {@code RenderLivingEvent.Specials.Pre} fires → the handler
 * <b>cancels the dummy's own label</b> and draws the <b>host's</b> display name
 * instead, so a morphed player still reads as that player.</p>
 *
 * <p><b>This is an {@code EntityMorph}-only behaviour, and that is legacy's,
 * not a shortcut.</b> The whole chain hangs off {@code EntityMorph.renderEntity},
 * a static set only by {@code EntityMorph.render}, and off an event that only
 * fires for a rendered living entity. A player morphed into a
 * {@code CustomMorph} (or any other non-entity morph) drew no dummy, so no
 * event fired, so <b>1.12.2 showed no nameplate at all</b> — the only thing
 * {@code CustomMorph} ever put over a player's head was its "model is missing"
 * key. Widening this to every morph type would be a behaviour change, not a
 * fix.</p>
 *
 * <p><b>Actors and mobs are covered by the host clause, also legacy's.</b>
 * {@link #canRenderName} demands {@code hasCustomName()} of a non-player host,
 * so an {@code EntityActor} or a selector-substituted mob wearing an entity
 * morph gets no name unless it was explicitly named — the same suppression
 * 1.12.2 had, and separate from the {@code RenderCustomModel} /
 * {@code RenderCustomActor} pointed-entity rules that govern a custom-model
 * actor's own label.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/RenderingHandler.java
 */
public final class MorphNameplate
{
    /** Legacy's sneaking / standing nameplate cutoff, in blocks. */
    public static final float SNEAKING_DISTANCE = 32.0F;
    public static final float STANDING_DISTANCE = 64.0F;

    /**
     * Legacy's verbatim {@code int i = "deadmau5".equals(name) ? -10 : 0;} —
     * the one hard-coded username in vanilla's own nameplate code, kept because
     * the ears would otherwise sit through the label.
     */
    public static final int DEADMAU5_SHIFT = -10;

    private MorphNameplate()
    {}

    /**
     * The pure core of legacy {@code canRenderName}, with every client lookup
     * lifted into a parameter so the whole visibility matrix is headless.
     *
     * @param self           the host <i>is</i> the local player
     * @param hostTeam       the host's scoreboard team, or null
     * @param viewerTeam     the local player's scoreboard team, or null
     * @param visible        {@code !host.isInvisibleTo(localPlayer)} — legacy's {@code flag}
     * @param player         the host is a {@link PlayerEntity}
     * @param hasCustomName  the host has a custom name (only consulted for non-players)
     * @param hudEnabled     the game HUD is shown (F1 hides nameplates)
     * @param cameraEntity   the host is the entity the camera is attached to
     * @param hasPassengers  the host is being ridden
     */
    public static boolean canRenderName(boolean self, AbstractTeam hostTeam, AbstractTeam viewerTeam,
        boolean visible, boolean player, boolean hasCustomName,
        boolean hudEnabled, boolean cameraEntity, boolean hasPassengers)
    {
        boolean flag = visible;

        if (!self && hostTeam != null)
        {
            AbstractTeam.VisibilityRule rule = hostTeam.getNameTagVisibilityRule();

            switch (rule)
            {
                case ALWAYS:
                    return flag;
                case NEVER:
                    return false;
                case HIDE_FOR_OTHER_TEAMS:
                    return viewerTeam == null
                        ? flag
                        : hostTeam.isEqual(viewerTeam) && (hostTeam.shouldShowFriendlyInvisibles() || flag);
                case HIDE_FOR_OWN_TEAM:
                    return viewerTeam == null
                        ? flag
                        : !hostTeam.isEqual(viewerTeam) && flag;
                default:
                    return true;
            }
        }

        if (!player)
        {
            flag = flag && hasCustomName;
        }

        return hudEnabled && !cameraEntity && flag && !hasPassengers;
    }

    /**
     * Legacy's distance gate from {@code onNameRender}: the <b>rendered</b>
     * entity's squared distance to the camera against 32 blocks while sneaking
     * and 64 otherwise. (Legacy measured to the morph's dummy, not to the host,
     * and the dummy is kept at the host's position — so the two agree.)
     */
    public static boolean isWithinNameDistance(double squaredDistance, boolean sneaking)
    {
        float factor = sneaking ? SNEAKING_DISTANCE : STANDING_DISTANCE;

        return squaredDistance < factor * factor;
    }

    /**
     * Legacy's {@code float pz = entity.height + 0.5F - (sneaking ? 0.25F : 0)},
     * the world-space height the label floats at over the rendered entity.
     */
    public static float nameHeight(float height, boolean sneaking)
    {
        return height + 0.5F - (sneaking ? 0.25F : 0.0F);
    }

    /** Legacy's deadmau5 special case, on the formatted display name. */
    public static int textShift(String name)
    {
        return "deadmau5".equals(name) ? DEADMAU5_SHIFT : 0;
    }

    /**
     * The client-state half of {@code canRenderName}. Null-safe throughout —
     * no client, no player, no name.
     */
    public static boolean canRenderName(LivingEntity host)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (host == null || mc == null)
        {
            return false;
        }

        ClientPlayerEntity viewer = mc.player;

        if (viewer == null)
        {
            return false;
        }

        return canRenderName(host == viewer,
            host.getScoreboardTeam(), viewer.getScoreboardTeam(),
            !host.isInvisibleTo(viewer),
            host instanceof PlayerEntity,
            host.hasCustomName(),
            MinecraftClient.isHudEnabled(),
            host == mc.getCameraEntity(),
            host.hasPassengers());
    }

    /**
     * Draw the host's name over the morph being rendered, at the current matrix
     * origin.
     *
     * <p>Legacy drew this <i>inside</i> {@code EntityMorph.render}'s translate +
     * {@code morph.scale} frame, because the event fired inside the dummy's
     * own render — so an upscaled morph got an upscaled label. Reproduced by
     * calling from the same place.</p>
     *
     * @param host   the morphed entity whose name is shown
     * @param target the entity actually drawn (legacy's {@code event.getEntity()},
     *               the morph's dummy) — the sneak flag, the height and the
     *               camera distance all come off this one, not off the host
     */
    public static void draw(LivingEntity host, LivingEntity target, MatrixStack matrices, VertexConsumerProvider consumers, int light)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (host == null || target == null || mc == null)
        {
            return;
        }

        Entity camera = mc.getCameraEntity();

        if (camera == null || !canRenderName(host))
        {
            return;
        }

        boolean sneaking = target.isSneaking();

        if (!isWithinNameDistance(target.squaredDistanceTo(camera), sneaking))
        {
            return;
        }

        String name = host.getDisplayName() == null ? "" : host.getDisplayName().getString();

        Nameplate.draw(matrices, consumers, name, nameHeight(target.getHeight(), sneaking), textShift(name), sneaking, light);
    }
}
