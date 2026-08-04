package mchorse.blockbuster.client.render;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.KeyboardHandler;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.blockbuster.utils.NBTUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Fills a {@link PoseContext} from a live entity (roadmap P54).
 *
 * <p>{@link PoseContext} was built as the headless-testable input struct for
 * {@code ModelCustom.setRotationAngles}; this is the other half — the one place
 * that reads real entity state, so the pose math itself never touches a world.
 * Every value is the vanilla {@code LivingEntityRenderer.render} interpolation
 * that 1.12.2's {@code RenderLivingBase} computed before calling
 * {@code setRotationAngles}, in yarn 1.20.4 terms:</p>
 *
 * <ul>
 *   <li>{@code limbSwing}/{@code limbSwingAmount} — 1.12 lerped
 *       {@code limbSwing - limbSwingAmount * (1 - partialTicks)} and
 *       {@code prevLimbSwingAmount + delta * partialTicks} by hand; 1.20.4's
 *       {@link net.minecraft.entity.LimbAnimator} does exactly that in
 *       {@code getPos(tickDelta)}/{@code getSpeed(tickDelta)}.</li>
 *   <li>{@code netHeadYaw} — {@code lerpAngleDegrees(headYaw) -
 *       lerpAngleDegrees(bodyYaw)}, the vanilla head-relative-to-body delta.</li>
 *   <li>{@code ageInTicks} — {@code entity.age + partialTicks}. Legacy also fed
 *       {@code handSwingProgress} through {@code getHandSwingProgress}.</li>
 * </ul>
 *
 * <p>Cape inputs are supplied separately by {@link #applyCape} because the
 * spring state lives on the morph, not the entity (legacy read
 * {@code this.current.prevCapeX/capeX} off the {@code CustomMorph}).</p>
 *
 * <p>Totality: a null entity yields the struct's idle defaults rather than
 * throwing, so a preview with no player still poses.</p>
 */
public final class PoseContexts
{
    private PoseContexts()
    {}

    public static PoseContext fromEntity(LivingEntity entity, float partialTicks)
    {
        PoseContext context = new PoseContext();

        if (entity == null)
        {
            return context;
        }

        float bodyYaw = MathHelper.lerpAngleDegrees(partialTicks, entity.prevBodyYaw, entity.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(partialTicks, entity.prevHeadYaw, entity.headYaw);

        context.limbSwing = entity.limbAnimator.getPos(partialTicks);
        context.limbSwingAmount = entity.limbAnimator.getSpeed(partialTicks);
        context.ageInTicks = entity.age + partialTicks;
        context.netHeadYaw = headYaw - bodyYaw;
        context.headPitch = MathHelper.lerp(partialTicks, entity.prevPitch, entity.getPitch());
        context.swingProgress = entity.getHandSwingProgress(partialTicks);

        context.living = true;
        context.bodyYaw = bodyYaw;

        context.ticksElytraFlying = entity.getRoll();

        /* S22 P238: the only writer of PoseContext.roll. Legacy read it inside
         * ModelCustom.setRotationAngles as EntityUtils.getRoll(entityIn,
         * ageInTicks % 1) — the partial-tick argument is that expression, not
         * the passed partialTicks, and it is kept verbatim (they agree for a
         * live entity, where ageInTicks = age + partialTicks). */
        context.roll = EntityUtils.getRoll(entity, context.ageInTicks % 1);

        Vec3d velocity = entity.getVelocity();

        context.motionX = velocity.x;
        context.motionY = velocity.y;
        context.motionZ = velocity.z;

        context.onGround = entity.isOnGround();
        context.inWater = entity.isTouchingWater();

        context.distanceWalked = MathHelper.lerp(partialTicks, entity.prevHorizontalSpeed, entity.horizontalSpeed);

        /* Legacy read cameraYaw off EntityPlayer only; for every other entity
         * the term is 0, which makes the walk-distance cape sway vanish. */
        if (entity instanceof PlayerEntity)
        {
            PlayerEntity player = (PlayerEntity) entity;

            context.cameraYaw = MathHelper.lerp(partialTicks, player.prevStrideDistance, player.strideDistance);
        }

        applyHands(context, entity);

        return context;
    }

    /**
     * Fill the {@code setHands} inputs (held items + the gun shooting-pose
     * flags, mirroring {@code PlayerEntityRendererArmPoseMixin}'s decision for
     * the vanilla player model).
     */
    private static void applyHands(PoseContext context, LivingEntity entity)
    {
        ItemStack right = entity.getMainHandStack();
        ItemStack left = entity.getOffHandStack();

        context.rightItemPresent = right != null && !right.isEmpty();
        context.leftItemPresent = left != null && !left.isEmpty();
        context.itemInUseCount = entity.getItemUseTimeLeft();

        if (context.rightItemPresent)
        {
            context.rightUseAction = right.getUseAction();
        }

        if (context.leftItemPresent)
        {
            context.leftUseAction = left.getUseAction();
        }

        if (context.rightItemPresent && right.getItem() == Blockbuster.GUN)
        {
            GunProps props = NBTUtils.getGunProps(right);

            if (props != null)
            {
                context.rightItemIsGun = true;
                context.gunAlwaysArmsShootingPose = props.alwaysArmsShootingPose;
                context.gunEnableArmsShootingPose = props.enableArmsShootingPose;
                context.gunShoot = () -> KeyboardHandler.gunShoot != null && KeyboardHandler.gunShoot.isPressed();
            }
        }
    }

    /**
     * Feed the morph's cape spring into the context. The three deltas are
     * {@code lerp(cape) - lerp(entity position)}, verbatim from legacy
     * {@code ModelCustom.setRotationAngles}'s cape branch — the subtraction has
     * to happen here because {@link PoseContext} stores the already-differenced
     * values.
     */
    public static void applyCape(PoseContext context, LivingEntity entity, float partialTicks,
        double prevCapeX, double capeX, double prevCapeY, double capeY, double prevCapeZ, double capeZ)
    {
        if (context == null || entity == null)
        {
            return;
        }

        context.capeActive = true;
        context.capeDX = MathHelper.lerp(partialTicks, prevCapeX, capeX) - MathHelper.lerp(partialTicks, entity.prevX, entity.getX());
        context.capeDY = MathHelper.lerp(partialTicks, prevCapeY, capeY) - MathHelper.lerp(partialTicks, entity.prevY, entity.getY());
        context.capeDZ = MathHelper.lerp(partialTicks, prevCapeZ, capeZ) - MathHelper.lerp(partialTicks, entity.prevZ, entity.getZ());
    }
}
