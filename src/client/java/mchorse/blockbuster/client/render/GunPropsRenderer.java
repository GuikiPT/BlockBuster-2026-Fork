package mchorse.blockbuster.client.render;

import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.common.GunProps;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * Client-only companion for the {@link GunProps} render methods (P197).
 *
 * <p>The 1.12.2 render / renderHands / renderZoomOverlay / renderInventoryMorph
 * lived on {@code GunProps} behind {@code @SideOnly(Side.CLIENT)}; the split
 * source set forbids client-only Minecraft classes in the main-source
 * {@code GunProps}, so the four GL-bound methods move here (the working-morph
 * state + the {@code renderLock}/{@code target}/{@code shot}/{@code update}
 * drivers stay on {@code GunProps}, which is main-safe).</p>
 *
 * <p>Each method ports the legacy sequence faithfully onto the {@link MatrixStack}:
 * the {@code renderLock} recursion guard (a gun morph that contains a gun must
 * not recurse), lazy dummy-actor creation, the yaw-neutralisation around the
 * morph render, the {@code (0.5, 0, 0.5)} translate, the first-person vs
 * third-person gun transform, and {@code setupEntity}. The actual morph draw
 * routes through the {@link GunMorphRenderer} seam (S6 pipeline; no-op until
 * wired — the transforms are still applied, keeping parity of the math).</p>
 *
 * Legacy source of truth: {@code blockbuster-1.12/.../common/GunProps.java}
 * (render/renderHands/renderZoomOverlay/renderInventoryMorph).
 */
public final class GunPropsRenderer
{
    private GunPropsRenderer()
    {}

    /** Legacy no-arg {@code createEntity()} — uses the client world. */
    public static void createEntity(GunProps props)
    {
        if (props.target == null)
        {
            props.createEntity(MinecraftClient.getInstance().world);
        }
    }

    /**
     * Legacy {@code GunProps.render}: the main gun morph, with the first-person
     * or third-person gun transform.
     */
    public static void render(GunProps props, LivingEntity holder, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float partialTicks, boolean firstPerson)
    {
        renderMorph(props, props.getCurrent().get(), holder, matrices, vertexConsumers, light, partialTicks, firstPerson ? props.gunTransformFirstPerson : props.gunTransform, false);
    }

    /**
     * Legacy {@code GunProps.renderHands}: the hands morph, gun transform
     * applied (first/third person).
     */
    public static void renderHands(GunProps props, LivingEntity holder, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float partialTicks, boolean firstPerson)
    {
        renderMorph(props, props.getCurrentHands().get(), holder, matrices, vertexConsumers, light, partialTicks, firstPerson ? props.gunTransformFirstPerson : props.gunTransform, false);
    }

    /**
     * Legacy {@code GunProps.renderZoomOverlay}: the zoom-overlay morph, no gun
     * transform.
     */
    public static void renderZoomOverlay(GunProps props, LivingEntity holder, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float partialTicks)
    {
        renderMorph(props, props.getCurrentZoomOverlay().get(), holder, matrices, vertexConsumers, light, partialTicks, null, false);
    }

    /**
     * Legacy {@code GunProps.renderInventoryMorph}: the inventory morph, no gun
     * transform, no yaw-neutralisation (legacy skipped the yaw juggle here and
     * enabled depth).
     */
    public static void renderInventoryMorph(GunProps props, LivingEntity holder, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float partialTicks)
    {
        renderMorph(props, props.getCurrentInventory().get(), holder, matrices, vertexConsumers, light, partialTicks, null, true);
    }

    private static void renderMorph(GunProps props, AbstractMorph morph, LivingEntity holder, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float partialTicks, NbtCompound transform, boolean inventory)
    {
        if (props.renderLock)
        {
            return;
        }

        props.renderLock = true;

        try
        {
            createEntity(props);

            LivingEntity entity = props.useTarget ? holder : props.target;

            if (morph != null && entity != null)
            {
                float bodyYaw = entity.bodyYaw;
                float prevBodyYaw = entity.prevBodyYaw;
                float headYaw = entity.headYaw;
                float prevHeadYaw = entity.prevHeadYaw;

                if (!inventory)
                {
                    entity.headYaw -= entity.bodyYaw;
                    entity.prevHeadYaw -= entity.prevBodyYaw;
                    entity.bodyYaw = entity.prevBodyYaw = 0.0F;
                }

                matrices.push();
                matrices.translate(0.5F, 0, 0.5F);

                applyTransform(matrices, transform);

                props.setupEntity();
                GunMorphRenderer.draw(morph, entity, matrices, vertexConsumers, light, partialTicks);

                matrices.pop();

                if (!inventory)
                {
                    entity.bodyYaw = bodyYaw;
                    entity.prevBodyYaw = prevBodyYaw;
                    entity.headYaw = headYaw;
                    entity.prevHeadYaw = prevHeadYaw;
                }
            }
        }
        finally
        {
            props.renderLock = false;
        }
    }

    /**
     * Apply a gun/projectile transform (raw NBT slot → {@link ModelTransform})
     * to the matrix in the legacy order: translate, then rotate Z/Y/X, then
     * scale (see {@code ModelTransform.transform}). A null/default transform is
     * the identity.
     */
    public static void applyTransform(MatrixStack matrices, NbtCompound transformNbt)
    {
        if (transformNbt == null || transformNbt.isEmpty())
        {
            return;
        }

        ModelTransform transform = new ModelTransform();
        transform.fromNBT(transformNbt);

        applyTransform(matrices, transform);
    }

    public static void applyTransform(MatrixStack matrices, ModelTransform transform)
    {
        ModelTransforms.apply(matrices, transform);
    }
}
