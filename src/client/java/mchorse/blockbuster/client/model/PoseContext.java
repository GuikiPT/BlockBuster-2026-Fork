package mchorse.blockbuster.client.model;

import net.minecraft.util.UseAction;

import java.util.function.BooleanSupplier;

/**
 * Per-frame pose/animation input snapshot (roadmap P82).
 *
 * <p>Blockbuster 2.7.2 drove {@code ModelCustom.setRotationAngles}/{@code
 * setHands} directly from a live {@code Entity}/{@code EntityLivingBase} and a
 * pile of inherited {@code ModelBiped} fields ({@code swingProgress},
 * {@code rightArmPose}, …) plus {@code Minecraft.getRenderPartialTicks()}. That
 * couples the animation math to the game client and makes it impossible to
 * verify headlessly.</p>
 *
 * <p>This class factors <b>every</b> entity/render-state input the pose pass
 * reads into a plain data holder. {@code RenderActor}/{@code RenderCustomActor}
 * (P80) fills it from the real entity each frame (using vanilla
 * {@code LivingEntityRenderer} deltas and {@code LimbAnimator} values); the JUnit
 * angle probes fill it with scripted values. The math in {@link ModelCustom} then
 * touches no Minecraft world state at all.</p>
 *
 * <p>All fields are public and mutable — this is a struct, not an API. Defaults
 * describe an idle, standing, empty-handed non-player living entity.</p>
 */
public class PoseContext
{
    /* ----- Core animation drivers (vanilla setRotationAngles args) ------- */

    /** Walk cycle phase. Vanilla {@code LimbAnimator.getPos(tickDelta)}. */
    public float limbSwing;
    /** Walk cycle amplitude (0 = still). Vanilla {@code LimbAnimator.getSpeed}. */
    public float limbSwingAmount;
    /** {@code entity.age + tickDelta}. Drives idle/wing oscillation. */
    public float ageInTicks;
    /** Head yaw relative to body, degrees. */
    public float netHeadYaw;
    /** Head pitch, degrees. */
    public float headPitch;
    /** Hand-swing (swiping) progress in [0, 1]; vanilla {@code getHandSwingProgress}. */
    public float swingProgress;

    /* ----- Entity classification --------------------------------------- */

    /**
     * Whether the rendered entity is a {@code LivingEntity}. Non-living entities
     * get {@link ArmPose#EMPTY} for both hands and never animate the cape.
     */
    public boolean living = true;

    /* ----- Swinging (elytra damping) ----------------------------------- */

    /**
     * Elytra-flight tick counter (legacy {@code getTicksElytraFlying()}, yarn
     * {@code LivingEntity.getRoll()}). Values {@code > 4} trigger the
     * motion-based swing damping.
     */
    public int ticksElytraFlying;
    public double motionX;
    public double motionY;
    public double motionZ;

    /* ----- Roll (Aperture / actor) ------------------------------------- */

    /**
     * Pre-computed roll in degrees (legacy {@code EntityUtils.getRoll(entity,
     * ageInTicks % 1)}). Written by {@link mchorse.blockbuster.client.render
     * .PoseContexts#fromEntity} (S22 P238) from the actor's recorded
     * {@code prevRoll → roll} pair, or from Aperture's camera roll when the
     * rendered entity is the local player.
     */
    public float roll;

    /* ----- Cape physics ------------------------------------------------ */

    /**
     * Whether the cape flag should animate. Legacy gated this on
     * {@code limb.cape && entity instanceof EntityLivingBase && current != null}
     * — {@code current} is the {@code CustomMorph} carrying the cape simulation
     * state (S4). P80 sets this to {@code living && morph-has-cape-state}.
     */
    public boolean capeActive;
    /** Lerped cape-anchor minus lerped entity position, world space. */
    public double capeDX;
    public double capeDY;
    public double capeDZ;
    /** Lerped render yaw offset (body yaw), degrees. */
    public float bodyYaw;
    /**
     * Lerped {@code cameraYaw} bob (players only; {@code 0} for non-players, which
     * makes the walk-distance cape sway term vanish exactly as legacy).
     */
    public float cameraYaw;
    /** Lerped walk distance ({@code distanceWalkedModified}). */
    public float distanceWalked;

    /* ----- P81 vanilla-mob shim inputs --------------------------------- */

    /** Ground contact (chicken flap gate, squid). */
    public boolean onGround = true;
    /** In-water flag (squid tentacle animation). */
    public boolean inWater;

    /* ----- setHands inputs --------------------------------------------- */

    public boolean rightItemPresent;
    public boolean leftItemPresent;
    /**
     * Legacy {@code getItemInUseCount()} (yarn {@code getItemUseTimeLeft()}).
     * {@code > 0} upgrades a held item to its use-action pose.
     */
    public int itemInUseCount;
    /** Main-hand item use action (yarn {@code ItemStack.getUseAction()}). */
    public UseAction rightUseAction = UseAction.NONE;
    /** Off-hand item use action. */
    public UseAction leftUseAction = UseAction.NONE;

    /* ----- Gun pose (S9 gun props + S17 keybind seam) ------------------ */

    public boolean rightItemIsGun;
    public boolean gunAlwaysArmsShootingPose;
    public boolean gunEnableArmsShootingPose;
    /**
     * Whether the "shoot" keybind is held. S17 wires this to
     * {@code KeyboardHandler.gunShoot.isKeyDown()}; defaults to {@code false}.
     */
    public BooleanSupplier gunShoot = () -> false;

    public PoseContext()
    {}

    /** Convenience for the P81 shim probes: the five vanilla driver args. */
    public PoseContext(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.ageInTicks = ageInTicks;
        this.netHeadYaw = netHeadYaw;
        this.headPitch = headPitch;
    }
}
