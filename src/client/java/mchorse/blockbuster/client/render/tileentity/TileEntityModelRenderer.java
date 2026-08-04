package mchorse.blockbuster.client.render.tileentity;

import java.util.function.Function;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.RenderingHandler;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.common.tileentity.TileEntityModelSettings;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;

/**
 * Model block entity renderer (roadmap P96).
 *
 * <p>Port of 1.12.2 {@code client/render/tileentity/TileEntityModelRenderer}.
 * Drives the morph render on a lazily-created dummy {@code EntityActor} with
 * settings-driven placement / rotation / scale, an optional shadow, and the F3
 * debug visuals. The legacy {@code GlStateManager} matrix ops are reproduced on
 * the yarn {@link MatrixStack} (which post-multiplies the same way); the pixel
 * output itself is out of scope for headless verification, so the load-bearing
 * math is extracted into {@link ModelBlockTransform}, {@link ModelBlockRenderGate}
 * and {@link ModelBlockDebug}, which are unit-tested.</p>
 *
 * <h2>Seams</h2>
 * The actual morph draw ({@code MorphUtils.render}) is invoked through
 * {@link #morphDrawer}; the entity-morph override (a morphed host entity winning
 * over the TE morph, legacy {@code EntityUtils.getMorph}) rides
 * {@link #entityMorphOverride}; the vanilla drop shadow rides
 * {@link #shadowRenderer}. All three still default to null — nothing is drawn,
 * totally, with no crash and no invisible block outline — and all three are
 * assigned at client init by {@link ModelBlockRenderWiring} (roadmap P229).
 * Between P96 and P229 nothing assigned them, so the block drew nothing on a
 * real client; {@code ModelBlockRenderWiringTest} is the pin that keeps that
 * from happening again.
 */
public class TileEntityModelRenderer implements BlockEntityRenderer<TileEntityModel>
{
    /**
     * S6 morph draw seam (legacy {@code MorphUtils.render(morph, entity, 0, 0,
     * 0, 0, partialTicks)}). Null → no draw.
     */
    public static MorphDrawer morphDrawer;

    /**
     * Entity-morph override seam (legacy {@code EntityUtils.getMorph(entity)}):
     * when it returns a non-null morph, that morph wins over the TE morph. Null
     * seam or null result → the TE morph is used.
     */
    public static Function<LivingEntity, AbstractMorph> entityMorphOverride;

    /**
     * Shadow draw seam (legacy private {@code RenderShadow.doRenderShadowAndFire}).
     * Null → no shadow. See {@link RenderShadow} for the size/opacity contract.
     */
    public static ShadowDrawer shadowRenderer;

    /**
     * Shadow shim carrying the legacy {@code RenderShadow} size/opacity contract
     * ({@code setShadowSize(morph.getWidth(entity) * 0.8F)}, {@code shadowOpaque
     * = 0.8F}). The actual quad is drawn by {@link #shadowRenderer}; this keeps
     * the class inventory diff-able against the legacy source.
     */
    public RenderShadow renderer;

    public TileEntityModelRenderer(BlockEntityRendererFactory.Context context)
    {}

    @Override
    public void render(TileEntityModel te, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        TileEntityModelSettings settings = te.getSettings();

        /* P80.3, legacy TileEntityModel.shouldRenderInPass: a render-last block
         * enqueues itself and draws nothing at all now — not the morph, not the
         * shadow, not the F3 debug cube (legacy's hook suppressed the whole
         * renderer, so the debug visuals moved to the tail pass with it). During
         * the tail pass the re-entrancy guard makes this false and the block
         * draws for real. */
        boolean deferred = RenderingHandler.deferRenderLast(settings.isRenderLast(), te);

        if (ModelBlockRenderGate.shouldRender(te.morph.isEmpty(), Blockbuster.modelBlockDisableRendering.get(), settings.isRenderAlways(), settings.isEnabled(), deferred))
        {
            AbstractMorph morph = te.morph.get();

            if (this.renderer == null)
            {
                this.renderer = new RenderShadow();
            }

            if (te.entity == null)
            {
                te.createEntity(mc.world);
            }

            if (te.entity != null)
            {
                LivingEntity entity = te.entity;

                AbstractMorph override = entityMorphOverride == null ? null : entityMorphOverride.apply(entity);

                if (override != null)
                {
                    morph = override;
                }

                /* Apply entity placement + rotations (legacy setPositionAndRotation
                 * / rotationYawHead / renderYawOffset). yaw is forced to 0; the head,
                 * pitch and body use the settings' values. Velocity is zeroed so no
                 * interpolation drift bleeds into the render. */
                BlockPos pos = te.getPos();

                entity.refreshPositionAndAngles(pos.getX() + ModelBlockTransform.offsetX(settings), pos.getY() + ModelBlockTransform.offsetY(settings), pos.getZ() + ModelBlockTransform.offsetZ(settings), 0, 0);
                entity.headYaw = entity.prevHeadYaw = settings.getRotateYawHead();
                entity.setYaw(0);
                entity.prevYaw = 0;
                entity.setPitch(settings.getRotatePitch());
                entity.prevPitch = settings.getRotatePitch();
                entity.bodyYaw = entity.prevBodyYaw = settings.getRotateBody();
                entity.setVelocity(0, 0, 0);

                float xx = ModelBlockTransform.offsetX(settings);
                float yy = ModelBlockTransform.offsetY(settings);
                float zz = ModelBlockTransform.offsetZ(settings);

                /* Apply transformations (the BE matrix stack is already translated
                 * to the block's corner by WorldRenderer, which is exactly the
                 * frame legacy's TESR got — see ModelBlockTransform.CENTER_X).
                 * place() then apply() is legacy's translate-then-transform
                 * order: the rotation/scale pivot is the *shifted* point, not
                 * the block centre. */
                matrices.push();

                try
                {
                    ModelBlockTransform.place(matrices, settings);
                    ModelBlockTransform.apply(matrices, settings);

                    if (morphDrawer != null)
                    {
                        morphDrawer.draw(morph, entity, matrices, vertexConsumers, light, overlay, tickDelta);
                    }
                }
                finally
                {
                    /* P278: the pop is in a finally because a morph renderer that
                     * throws between its own push and pop leaves the *shared*
                     * frame matrix deeper than it found it, and every draw after
                     * this block entity in the frame would then be displaced by
                     * this block's transform — an "everything is off-centre"
                     * frame with no obvious culprit. MorphRenderUtils swallows
                     * the exception, so nothing else would report it. */
                    matrices.pop();
                }

                /* Shadow is drawn AFTER pop at the offset position, unaffected by
                 * the rotation/scale transforms (legacy quirk). */
                if (settings.isShadow() && shadowRenderer != null)
                {
                    this.renderer.setShadowSize(morph.getWidth(entity) * 0.8F);
                    shadowRenderer.draw(entity, matrices, vertexConsumers, xx, yy, zz, this.renderer.shadowSize, this.renderer.shadowOpaque, tickDelta);
                }
            }
        }

        /* Debug render (so people can find the block) — F3 gate. */
        if (!deferred && mc.getDebugHud().shouldShowDebugHud() && (!mc.options.hudHidden || Blockbuster.modelBlockRenderDebuginf1.get()))
        {
            this.renderDebug(te, settings, matrices, vertexConsumers);
        }
    }

    /**
     * F3 debug overlay: status cube (teal/orange/red), offset marker cube, and
     * — when the offset distance exceeds {@link ModelBlockDebug#BEAM_THRESHOLD}
     * — the connecting beam. The GL-shader save/restore and depth/texture toggles
     * of the legacy code are obsolete under the core profile; the modern
     * equivalent is the debug line {@link RenderLayer}. The math is delegated to
     * {@link ModelBlockDebug} (unit-tested). The {@code blockbuster.light}
     * nameplate remains a documented seam (billboarded text needs the frame's
     * text renderer + camera, out of scope for the headless port).
     */
    private void renderDebug(TileEntityModel te, TileEntityModelSettings settings, MatrixStack matrices, VertexConsumerProvider vertexConsumers)
    {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getLines());

        boolean error = !te.morph.isEmpty() && te.morph.get().errorRendering;
        float[] color = ModelBlockDebug.statusColor(settings.isEnabled(), error);

        float x = settings.getX();
        float y = settings.getY();
        float z = settings.getZ();

        /* Status cube around the block centre (block corner origin). */
        WorldRenderer.drawBox(matrices, buffer, 0.25F, 0.25F, 0.25F, 0.75F, 0.75F, 0.75F, color[0], color[1], color[2], 0.35F);

        /* White marker cube at the settings offset. */
        WorldRenderer.drawBox(matrices, buffer, 0.45F + x, y, 0.45F + z, 0.55F + x, 0.1F + y, 0.55F + z, 1, 1, 1, 0.85F);

        if (ModelBlockDebug.shouldDrawBeam(x, y, z))
        {
            WorldRenderer.drawBox(matrices, buffer, 0.45F, 0, 0.45F, 0.55F, 0.1F, 0.55F, 1, 1, 1, 0.85F);

            double distance = ModelBlockDebug.distance(x, y, z);
            double yaw = ModelBlockDebug.beamYaw(x, z);
            double pitch = ModelBlockDebug.beamPitch(x, y, z);

            matrices.push();
            matrices.translate(0.5, 0.05F, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) yaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) pitch));

            WorldRenderer.drawBox(matrices, buffer, -0.025F, -0.025F, 0, 0.025F, 0.025F, -distance, 0, 0, 0, 0.5F);

            matrices.pop();
        }
    }

    /**
     * {@link #getRenderDistance()} would clamp the block to the vanilla BE render
     * distance; {@code global} model blocks skip frustum culling and draw at any
     * distance (legacy {@code isGlobalRenderer}).
     */
    @Override
    public boolean rendersOutsideBoundingBox(TileEntityModel te)
    {
        return te.getSettings().isGlobal();
    }

    /** S6 morph draw seam. */
    public interface MorphDrawer
    {
        void draw(AbstractMorph morph, LivingEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, float tickDelta);
    }

    /** Shadow draw seam (see {@link RenderShadow}). */
    public interface ShadowDrawer
    {
        void draw(LivingEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, double x, double y, double z, float shadowSize, float shadowOpacity, float tickDelta);
    }

    /**
     * Shim carrying the legacy {@code RenderShadow} size/opacity contract. Kept
     * as a named class so the inventory matches the legacy source; the actual
     * shadow quad is drawn by {@link TileEntityModelRenderer#shadowRenderer}.
     */
    public static class RenderShadow
    {
        public float shadowSize;
        public float shadowOpaque = 0.8F;

        public void setShadowSize(float size)
        {
            this.shadowSize = size;
            this.shadowOpaque = 0.8F;
        }
    }
}
