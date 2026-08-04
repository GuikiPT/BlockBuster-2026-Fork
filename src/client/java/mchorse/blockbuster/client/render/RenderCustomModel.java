package mchorse.blockbuster.client.render;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.bodypart.IBodyPartProvider;
import mchorse.metamorph.client.render.MorphNameplate;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import javax.vecmath.Matrix4f;

/**
 * Custom-model / morph renderer (roadmap P80).
 *
 * <p>Port of Blockbuster 2.7.2's {@code client/render/RenderCustomModel}. On
 * 1.12.2 it extended {@code RenderLivingBase} and Forge's dispatcher called
 * {@code doRender}/{@code renderLivingAt}/{@code applyRotations}/
 * {@code preRenderCallback} in turn. On 1.20.4 it is <b>our own</b> renderer
 * (not registered for a vanilla entity type): morphs invoke {@link
 * #render} directly with an explicit {@link MatrixStack}, exactly like BBS's
 * hand-driven film actors. The four vanilla hooks become the explicit steps of
 * {@link #render} — <b>including the ones {@code RenderLivingBase} used to
 * contribute for free</b> ({@code rotateCorpse}'s {@code rotate(180 - bodyYaw,
 * Y)} and {@code prepareScale}'s {@code scale(-1,-1,1)} / {@code translate(0,
 * -1.501, 0)}). 1.20.4 uses the <i>same</i> matrix convention as 1.12.2 —
 * verified at bytecode level, same {@code -1.501} constant — so this is a
 * transcription of the inherited code, not a modernization of it.</p>
 *
 * <p><b>Testable core.</b> The load-bearing math is factored into pure static
 * methods so it is headless-verifiable without a live entity or GL context:
 * {@link #blockbusterRotations} (the bed/elytra/normal {@code applyRotations}
 * delta), {@link #armRotationPointY} (the magic first-person arm formula),
 * {@link #preRenderScale} (morph-scale × model-scale) and the two
 * {@code canRenderName} rule sets. The matrix-capture ref-count ({@link
 * #captureMatrix}/{@link #releaseMatrixRef}) preserves the legacy nested-render
 * semantics (only the outermost frame releases {@link MatrixUtils}).</p>
 *
 * <p><b>Seams.</b> {@link #current} is typed {@link IModelCustomMorph} rather
 * than {@code CustomMorph} (S10). Layer invocation (P76 held-item/armor/head/
 * elytra + {@code LayerBodyPart}) and the actual per-limb GL draw ride the S6
 * render pipeline; {@link #render} performs setup + rotations + scale + the
 * geometry pass and marks the layer hook as a documented seam.</p>
 *
 * Legacy source: blockbuster-1.12/.../client/render/RenderCustomModel.java
 */
public class RenderCustomModel
{
    /**
     * The skin the current morph render is drawing with (legacy
     * {@code RenderCustomModel.lastTexture}).
     *
     * <p>On 1.12.2 this doubled as the actually-bound GL texture; on 1.20.4 the
     * texture is chosen when the {@code RenderLayer} is picked, so this is only
     * the record of "which skin is in effect", which the OBJ/VOX per-group and
     * extruded-layer paths (P77/P79) fall back to when a group has no material
     * texture of its own.</p>
     */
    public static Identifier lastTexture;

    /**
     * The McLib form of {@link #lastTexture} — the skin as the model/morph
     * declared it, before it was flattened to an {@link Identifier}.
     *
     * <p>{@link mchorse.blockbuster.client.model.parsing.ModelExtrudedLayer} keys
     * its voxelised meshes on this type and needs the real one: a
     * {@link mchorse.mclib.utils.resources.MultiResourceLocation} has to stay a
     * {@code MultiResourceLocation} or the multiskin deferral inside
     * {@code generateLayer} cannot recognise it. {@code null} when the model has
     * no skin of its own — an {@code is3D} limb then has no pixels to extrude
     * and draws nothing, which is what legacy did with nothing bound.</p>
     */
    public static ResourceLocation lastSkin;

    /**
     * Neutral 1&times;1 opaque white skin for a model that has none of its own.
     * See {@link #skinOrBlank(Identifier, Model)}.
     */
    public static final Identifier BLANK_SKIN = new Identifier("blockbuster", "textures/entity/pixel.png");

    /** Legacy {@code bindLastTexture(ResourceLocation)} — record the skin. */
    public static void bindLastTexture(Identifier location)
    {
        lastTexture = location;
        lastSkin = null;
    }

    /**
     * Record the skin in both forms: the McLib location the caller resolved and
     * the {@link Identifier} the render layers are actually picked with. The two
     * can differ — a material-only OBJ model resolves to {@link #BLANK_SKIN}
     * with no McLib skin behind it at all.
     */
    public static void bindLastTexture(ResourceLocation skin, Identifier resolved)
    {
        lastTexture = resolved;
        lastSkin = skin;
    }

    /**
     * The skin a model draw goes ahead with.
     *
     * <p>1.12.2 bound the skin when there was one and drew the model
     * <b>regardless</b> — an OBJ model whose texture lives entirely in its
     * {@code .mtl} materials has no {@code default} skin and never needed one,
     * because every material group bound its own texture inside
     * {@code renderDisplayList}. On 1.20.4 the texture is part of the
     * {@link net.minecraft.client.render.RenderLayer} the caller has to pick
     * <b>before</b> the limb walk, so "no skin" silently became "no draw" and
     * every OBJ/MTL and VOX model vanished entirely.</p>
     *
     * <p>The per-group selection survived the port intact
     * ({@code MaterialTextures.pick} / {@code ModelOBJRenderer.groupTexture}),
     * so textured groups and solid-colour strips still fetch their own buffers
     * — all the caller needs is a placeholder for the groups that opt out.
     * {@code providesMtl} is the "carries its own material textures" flag (VOX
     * sets it too), and is the same condition {@code CustomMorphRenderer}'s
     * on-screen gate already treated as a texture source.</p>
     *
     * @return {@code skin} when there is one, else the blank placeholder for a
     *         material-carrying model, else {@code null} — which still means
     *         there is genuinely nothing to draw with.
     */
    public static Identifier skinOrBlank(Identifier skin, Model data)
    {
        if (skin != null)
        {
            return skin;
        }

        return data != null && data.providesMtl ? BLANK_SKIN : null;
    }

    /**
     * Currently rendered morph. Legacy typed {@code CustomMorph}; see
     * {@link IModelCustomMorph} for why this is the seam interface.
     */
    public IModelCustomMorph current;

    /** Resolved model for the current frame ({@code null} → skip render). */
    public ModelCustom mainModel;

    /**
     * The pose input this frame was posed with, supplied by whoever ran the pose
     * pass ({@code CustomMorphRenderer.setup}). {@link LayerBodyPart} re-applies
     * it after each body part to undo the shared-model state a nested morph
     * render clobbers; a null context means "no pose pass to redo".
     */
    public PoseContext poseContext;

    /* Ref-counted matrix capture for recursive morph renders (morph inside a
     * body part inside a morph): only the outermost capturer releases. */
    private int captured;
    private boolean capturedByMe;

    /* --------------------------------------------------------------------- */
    /* setupModel — model + pose resolution                                  */
    /* --------------------------------------------------------------------- */

    /**
     * Pick the model and pose for {@link #current}, threading materials and
     * shape keys into the model. Ported verbatim from legacy
     * {@code setupModel}:
     * <ul>
     *   <li>no morph, or key not in {@code MODELS} → {@link #mainModel} stays
     *       {@code null} and the caller skips rendering;</li>
     *   <li>a {@code null} pose falls back to the model's {@code "standing"}
     *       pose;</li>
     *   <li>the morph's materials, per-frame shape keys and back-reference are
     *       stamped onto the shared {@link ModelCustom} instance.</li>
     * </ul>
     */
    public void setupModel(LivingEntity entity, float partialTicks)
    {
        ModelCustom model = null;
        ModelPose pose = null;

        if (this.current != null)
        {
            model = ModelCustom.MODELS.get(this.current.getKey());
            pose = this.current.getPose(entity, partialTicks);
        }

        if (model != null)
        {
            if (pose == null)
            {
                pose = model.model.getPose("standing");
            }

            model.materials = this.current.getMaterials();
            model.shapes = this.current.getShapesForRendering(partialTicks);
            model.pose = pose;
            model.current = this.current;
        }

        this.mainModel = model;
    }

    /* --------------------------------------------------------------------- */
    /* Matrix capture ref-count (legacy renderLivingAt / doRender tail)      */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code renderLivingAt} tail: capture the model-view once at the
     * outermost frame, then bump the ref-count. {@code modelView} is the
     * MatrixStack-derived matrix (1.12.2 read it straight from GL); it is the
     * {@code javax.vecmath} type {@link MatrixUtils} works in — see
     * {@link #toVecmath} for the joml bridge.
     */
    public void captureMatrix(Matrix4f modelView)
    {
        if (this.captured == 0)
        {
            this.capturedByMe = MatrixUtils.captureMatrix(modelView);
        }

        this.captured++;
    }

    /**
     * Legacy {@code doRender} tail: decrement the ref-count and release the
     * captured matrix only when this frame is the outermost one that captured
     * it.
     */
    public void releaseMatrixRef()
    {
        if (this.captured > 0)
        {
            this.captured--;

            if (this.captured == 0 && this.capturedByMe)
            {
                MatrixUtils.releaseMatrix();

                this.capturedByMe = false;
            }
        }
    }

    public int getCaptured()
    {
        return this.captured;
    }

    public boolean isCapturedByMe()
    {
        return this.capturedByMe;
    }

    /* --------------------------------------------------------------------- */
    /* applyRotations — bed / elytra / normal delta                          */
    /* --------------------------------------------------------------------- */

    /** Which branch of legacy {@code applyRotations} is active. */
    public enum RotationMode
    {
        /** Player sleeping in a bed. */
        SLEEPING,
        /** Flying an elytra. */
        ELYTRA,
        /** Everything else (all rotations handled by vanilla super). */
        NORMAL
    }

    /** Axis of a single rotation op. */
    public enum RotAxis
    {
        X, Y, Z
    }

    /** One {@code GlStateManager.rotate(deg, axis)} op, angle in degrees. */
    public static final class Rotation
    {
        public final float degrees;
        public final RotAxis axis;

        public Rotation(float degrees, RotAxis axis)
        {
            this.degrees = degrees;
            this.axis = axis;
        }
    }

    /**
     * Immutable snapshot of the entity state {@code applyRotations} reads.
     *
     * <p>It carries <b>two</b> groups of fields, because the legacy transform
     * came from two places. The {@code mode}-specific fields
     * ({@code bedOrientationDegrees} … {@code lookZ}) drive
     * {@link #blockbusterRotations}, Blockbuster's own {@code applyRotations}
     * override. The {@code bodyYaw}/{@code deathTime}/{@code flipUpsideDown}/
     * {@code entityHeight} group drives {@link #vanillaRotations}, the
     * {@code RenderLivingBase.rotateCorpse} base that the override called
     * {@code super} for — the half that was inherited on 1.12.2 and therefore
     * has to be written out here.</p>
     */
    public static final class ApplyRotationsInput
    {
        public final RotationMode mode;
        /** SLEEPING: bed orientation degrees; death-max rotation degrees. */
        public final float bedOrientationDegrees;
        public final float deathMaxRotation;
        /** ELYTRA: elytra-flying ticks + partialTicks already summed. */
        public final float ticksElytraFlying;
        public final float rotationPitch;
        public final double motionX;
        public final double motionZ;
        public final double lookX;
        public final double lookZ;

        /* --- vanilla rotateCorpse base (legacy super.applyRotations) ------- */

        /** Interpolated {@code renderYawOffset} — the body yaw, in degrees. */
        public final float bodyYaw;
        /** {@code entityLiving.deathTime}; {@code 0} → no death spin. */
        public final int deathTime;
        /** Partial ticks, needed by the death-spin ramp only. */
        public final float partialTicks;
        /** Dinnerbone/Grumm ({@code LivingEntityRenderer.shouldFlipUpsideDown}). */
        public final boolean flipUpsideDown;
        /** Entity height — the Dinnerbone lift is {@code height + 0.1}. */
        public final float entityHeight;

        private ApplyRotationsInput(RotationMode mode, float bedOrientationDegrees, float deathMaxRotation,
            float ticksElytraFlying, float rotationPitch, double motionX, double motionZ, double lookX, double lookZ,
            float bodyYaw, int deathTime, float partialTicks, boolean flipUpsideDown, float entityHeight)
        {
            this.mode = mode;
            this.bedOrientationDegrees = bedOrientationDegrees;
            this.deathMaxRotation = deathMaxRotation;
            this.ticksElytraFlying = ticksElytraFlying;
            this.rotationPitch = rotationPitch;
            this.motionX = motionX;
            this.motionZ = motionZ;
            this.lookX = lookX;
            this.lookZ = lookZ;
            this.bodyYaw = bodyYaw;
            this.deathTime = deathTime;
            this.partialTicks = partialTicks;
            this.flipUpsideDown = flipUpsideDown;
            this.entityHeight = entityHeight;
        }

        public static ApplyRotationsInput normal()
        {
            return new ApplyRotationsInput(RotationMode.NORMAL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0);
        }

        public static ApplyRotationsInput sleeping(float bedOrientationDegrees, float deathMaxRotation)
        {
            return new ApplyRotationsInput(RotationMode.SLEEPING, bedOrientationDegrees, deathMaxRotation, 0, 0, 0, 0, 0, 0,
                0, 0, 0, false, 0);
        }

        public static ApplyRotationsInput elytra(float ticksElytraFlying, float rotationPitch,
            double motionX, double motionZ, double lookX, double lookZ)
        {
            return new ApplyRotationsInput(RotationMode.ELYTRA, 0, 0, ticksElytraFlying, rotationPitch,
                motionX, motionZ, lookX, lookZ, 0, 0, 0, false, 0);
        }

        /**
         * Copy carrying the vanilla {@code rotateCorpse} base state. The
         * three mode factories above default it to "facing +Z, alive, not
         * Dinnerbone", which is what a bare unit test wants; production goes
         * through {@link #of(net.minecraft.entity.LivingEntity, float)}.
         */
        public ApplyRotationsInput withVanilla(float bodyYaw, int deathTime, float partialTicks,
            boolean flipUpsideDown, float entityHeight)
        {
            return new ApplyRotationsInput(this.mode, this.bedOrientationDegrees, this.deathMaxRotation,
                this.ticksElytraFlying, this.rotationPitch, this.motionX, this.motionZ, this.lookX, this.lookZ,
                bodyYaw, deathTime, partialTicks, flipUpsideDown, entityHeight);
        }

        /**
         * Build the full input from a live entity — the production factory,
         * and the fix for the "sleeping / elytra branches are unreachable"
         * defect (they had exactly one call site and it was hardcoded to
         * {@link #normal()}).
         *
         * <p>Branch selection is legacy's, verbatim: {@code isEntityAlive() &&
         * isPlayerSleeping()} → SLEEPING, {@code isElytraFlying()} → ELYTRA,
         * everything else NORMAL. The sleeping arm is gated on
         * {@link net.minecraft.entity.player.PlayerEntity} because 1.12.2's
         * {@code isPlayerSleeping()} was false for every non-player, while
         * 1.20.4's {@code LivingEntity.isSleeping()} is true for villagers and
         * foxes too — the guard keeps the 1.12.2 behaviour.</p>
         *
         * <p><b>The body yaw is read off the entity, not off any
         * {@code entityYaw} argument.</b> That is legacy: {@code
         * RenderLivingBase.doRender} recomputed {@code f} from
         * {@code prevRenderYawOffset}/{@code renderYawOffset} and handed
         * <i>that</i> to {@code rotateCorpse}; the {@code entityYaw} parameter
         * never reached the transform. It is load-bearing — body parts and the
         * model block both zero/overwrite {@code bodyYaw} on the entity and
         * expect the render to follow the field.</p>
         */
        public static ApplyRotationsInput of(LivingEntity entity, float partialTicks)
        {
            if (entity == null)
            {
                return normal();
            }

            float bodyYaw = MathHelper.lerpAngleDegrees(partialTicks, entity.prevBodyYaw, entity.bodyYaw);
            boolean flip = LivingEntityRenderer.shouldFlipUpsideDown(entity);
            boolean player = entity instanceof PlayerEntity;

            ApplyRotationsInput in;

            switch (modeOf(entity.isAlive(), player, entity.isSleeping(), entity.isFallFlying()))
            {
                case SLEEPING:
                    in = sleeping(bedOrientationDegrees(entity.getSleepingDirection()), DEATH_MAX_ROTATION);
                    break;

                case ELYTRA:
                {
                    Vec3d look = entity.getRotationVec(partialTicks);
                    Vec3d motion = entity.getVelocity();

                    in = elytra(entity.getRoll() + partialTicks, entity.getPitch(),
                        motion.x, motion.z, look.x, look.z);
                    break;
                }

                case NORMAL:
                default:
                    in = normal();
                    break;
            }

            return in.withVanilla(bodyYaw, entity.deathTime, partialTicks, flip, entity.getHeight());
        }
    }

    /**
     * Legacy's {@code getDeathMaxRotation} — a flat 90° for everything except
     * the spider renderers, which Blockbuster never used.
     */
    public static final float DEATH_MAX_ROTATION = 90.0F;

    /**
     * The {@code prepareScale} vertical offset. Applied <b>after</b> the model
     * scale, so it scales with the morph — which is why a 2× morph still has
     * its feet on the ground. Legacy quirk, keep it in that order.
     */
    public static final float MODEL_Y_OFFSET = -1.501F;

    /**
     * Pure branch selection for {@code applyRotations} (legacy's if/else-if
     * chain, as a truth table).
     */
    public static RotationMode modeOf(boolean alive, boolean isPlayer, boolean sleeping, boolean fallFlying)
    {
        if (alive && isPlayer && sleeping)
        {
            return RotationMode.SLEEPING;
        }

        if (fallFlying)
        {
            return RotationMode.ELYTRA;
        }

        return RotationMode.NORMAL;
    }

    /**
     * Legacy {@code EntityPlayer.getBedOrientationInDegrees()}. 1.20.4's own
     * equivalent ({@code LivingEntityRenderer.getYaw(Direction)}) is private,
     * and it is the same four-arm switch, so it is written out here.
     */
    public static float bedOrientationDegrees(Direction direction)
    {
        if (direction == null)
        {
            return 0.0F;
        }

        switch (direction)
        {
            case SOUTH:
                return 90.0F;
            case WEST:
                return 0.0F;
            case NORTH:
                return 270.0F;
            case EAST:
                return 180.0F;
            default:
                return 0.0F;
        }
    }

    /**
     * The Blockbuster-specific {@code applyRotations} rotations, as an ordered
     * list of ops. This is the <i>delta</i> over vanilla
     * {@code LivingEntityRenderer.setupTransforms}: the SLEEPING branch fully
     * replaces the vanilla rotations, while the ELYTRA branch is applied
     * <i>after</i> the vanilla super call (see {@link #render}). The NORMAL
     * branch adds nothing (vanilla does everything).
     *
     * <p>Ported verbatim from legacy {@code applyRotations}:
     * <ul>
     *   <li>SLEEPING: {@code rotate(bedOrientation, Y); rotate(deathMax, Z);
     *       rotate(270, Y)}.</li>
     *   <li>ELYTRA: {@code rotate(f1 * (-90 - pitch), X)} with {@code f1 =
     *       clamp(f*f/100, 0, 1)}, {@code f = ticksElytraFlying + partial};
     *       then a bank {@code rotate(signum(d3)*acos(d2) * 180/PI, Y)} toward
     *       the look vector, only when both the horizontal speed and the
     *       horizontal look length are positive.</li>
     * </ul></p>
     */
    public static List<Rotation> blockbusterRotations(ApplyRotationsInput in)
    {
        List<Rotation> ops = new ArrayList<Rotation>();

        switch (in.mode)
        {
            case SLEEPING:
                ops.add(new Rotation(in.bedOrientationDegrees, RotAxis.Y));
                ops.add(new Rotation(in.deathMaxRotation, RotAxis.Z));
                ops.add(new Rotation(270.0F, RotAxis.Y));
                break;

            case ELYTRA:
            {
                float f = in.ticksElytraFlying;
                float f1 = clamp(f * f / 100.0F, 0.0F, 1.0F);

                ops.add(new Rotation(f1 * (-90.0F - in.rotationPitch), RotAxis.X));

                double d0 = in.motionX * in.motionX + in.motionZ * in.motionZ;
                double d1 = in.lookX * in.lookX + in.lookZ * in.lookZ;

                if (d0 > 0.0D && d1 > 0.0D)
                {
                    double d2 = (in.motionX * in.lookX + in.motionZ * in.lookZ) / (Math.sqrt(d0) * Math.sqrt(d1));
                    double d3 = in.motionX * in.lookZ - in.motionZ * in.lookX;

                    ops.add(new Rotation((float) (Math.signum(d3) * Math.acos(d2)) * 180.0F / (float) Math.PI, RotAxis.Y));
                }

                break;
            }

            case NORMAL:
            default:
                break;
        }

        return ops;
    }

    /** Apply a {@link #blockbusterRotations} list to a live {@link MatrixStack}. */
    public static void applyRotations(MatrixStack matrices, ApplyRotationsInput in)
    {
        for (Rotation op : blockbusterRotations(in))
        {
            rotate(matrices, op.degrees, op.axis);
        }
    }

    private static void rotate(MatrixStack matrices, float degrees, RotAxis axis)
    {
        switch (axis)
        {
            case X:
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(degrees));
                break;
            case Y:
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(degrees));
                break;
            case Z:
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
                break;
        }
    }

    /* --------------------------------------------------------------------- */
    /* rotateCorpse — the vanilla base applyRotations is a delta over         */
    /* --------------------------------------------------------------------- */

    /**
     * One op of vanilla's {@code rotateCorpse} chain. Almost all of them are
     * rotations; the Dinnerbone/Grumm arm also lifts the model, so the list has
     * to be able to carry a translation.
     */
    public static final class VanillaOp
    {
        /** Non-null → rotate {@link #degrees} about this axis. */
        public final RotAxis axis;
        public final float degrees;
        /** Y translation, when {@link #axis} is null. */
        public final float translateY;

        private VanillaOp(RotAxis axis, float degrees, float translateY)
        {
            this.axis = axis;
            this.degrees = degrees;
            this.translateY = translateY;
        }

        public static VanillaOp rotate(float degrees, RotAxis axis)
        {
            return new VanillaOp(axis, degrees, 0F);
        }

        public static VanillaOp liftY(float y)
        {
            return new VanillaOp(null, 0F, y);
        }

        public boolean isRotation()
        {
            return this.axis != null;
        }
    }

    /**
     * Vanilla {@code RenderLivingBase.rotateCorpse} / {@code
     * LivingEntityRenderer.setupTransforms}, as an ordered op list.
     *
     * <p><b>This is the half the port was missing.</b> On 1.12.2
     * {@code RenderCustomModel extends RenderLivingBase}, so
     * {@code super.doRender} contributed {@code renderLivingAt} →
     * {@code rotateCorpse} → {@code prepareScale} and Blockbuster only
     * overrode the middle one, calling {@code super} from two of its three
     * arms. The port's renderer is not a subclass of anything, so every one of
     * those inherited ops has to be written out. Reproduced verbatim from
     * {@code RenderLivingBase.java:458-484} (the 1.20.4 bytecode is op-for-op
     * identical, including the {@code -1.501} in {@code prepareScale} — there
     * is no matrix-convention difference between the two versions):</p>
     *
     * <ol>
     *   <li>{@code rotate(180 - bodyYaw, Y)} — always;</li>
     *   <li>{@code deathTime > 0} → {@code rotate(sqrt(((deathTime +
     *       partial - 1) / 20) * 1.6) clamped to 1, times deathMaxRotation, Z)};</li>
     *   <li>otherwise Dinnerbone/Grumm → {@code translate(0, height + 0.1, 0)}
     *       then {@code rotate(180, Z)}.</li>
     * </ol>
     *
     * <p>The SLEEPING arm of {@link #blockbusterRotations} <b>replaces</b> this
     * (legacy did not call {@code super} there), which is why
     * {@link #applyAllRotations} skips it for that mode.</p>
     */
    public static List<VanillaOp> vanillaRotations(ApplyRotationsInput in)
    {
        List<VanillaOp> ops = new ArrayList<VanillaOp>();

        ops.add(VanillaOp.rotate(180.0F - in.bodyYaw, RotAxis.Y));

        if (in.deathTime > 0)
        {
            float f = ((float) in.deathTime + in.partialTicks - 1.0F) / 20.0F * 1.6F;

            f = (float) Math.sqrt(f);

            if (f > 1.0F)
            {
                f = 1.0F;
            }

            ops.add(VanillaOp.rotate(f * DEATH_MAX_ROTATION, RotAxis.Z));
        }
        else if (in.flipUpsideDown)
        {
            ops.add(VanillaOp.liftY(in.entityHeight + 0.1F));
            ops.add(VanillaOp.rotate(180.0F, RotAxis.Z));
        }

        return ops;
    }

    /** Apply a {@link #vanillaRotations} list to a live {@link MatrixStack}. */
    public static void applyVanillaRotations(MatrixStack matrices, ApplyRotationsInput in)
    {
        for (VanillaOp op : vanillaRotations(in))
        {
            if (op.isRotation())
            {
                rotate(matrices, op.degrees, op.axis);
            }
            else
            {
                matrices.translate(0.0F, op.translateY, 0.0F);
            }
        }
    }

    /**
     * The complete legacy {@code applyRotations}: the vanilla base, then
     * Blockbuster's delta — except in the SLEEPING arm, which replaced the base
     * outright.
     */
    public static void applyAllRotations(MatrixStack matrices, ApplyRotationsInput in)
    {
        if (in.mode != RotationMode.SLEEPING)
        {
            applyVanillaRotations(matrices, in);
        }

        applyRotations(matrices, in);
    }

    /* --------------------------------------------------------------------- */
    /* preRenderCallback — morph scale × model scale                         */
    /* --------------------------------------------------------------------- */

    /**
     * The preRenderCallback scale vector: {@code model.scale[i] * morphScale}.
     * Legacy multiplied the morph's uniform {@code scale} into every axis of
     * the model's per-axis scale.
     */
    public static float[] preRenderScale(Model model, float morphScale)
    {
        return new float[]
        {
            model.scale[0] * morphScale,
            model.scale[1] * morphScale,
            model.scale[2] * morphScale
        };
    }

    /* --------------------------------------------------------------------- */
    /* First-person arm alignment formula                                    */
    /* --------------------------------------------------------------------- */

    /**
     * The magic first-person arm {@code rotationPointY}:
     * {@code 13.8 - (sizeY > 8 ? sizeY : sizeY + 2)}. Do not rationalize it —
     * it is a hand-tuned alignment constant copied verbatim from legacy
     * {@code renderRightArm}/{@code renderLeftArm}.
     */
    public static float armRotationPointY(float sizeY)
    {
        return 13.8F - (sizeY > 8 ? sizeY : sizeY + 2F);
    }

    /**
     * Position the arm limbs for a first-person hand render. {@code leftSide}
     * chooses the {@code left}/{@code right} limb group and the sign of the
     * {@code ±6} X offset ({@code -6} right, {@code +6} left). Sets each limb's
     * rotation point; the caller (P80.1 hand seam) does the GL draw at
     * {@code 0.0625F}.
     */
    public static void positionArmLimbs(ModelCustomRenderer[] arms, boolean leftSide)
    {
        float px = leftSide ? 6.0F : -6.0F;

        for (ModelCustomRenderer arm : arms)
        {
            arm.rotateAngleX = 0;
            arm.rotationPointX = px;
            arm.rotationPointY = armRotationPointY(arm.limb.size[1]);
            arm.rotationPointZ = 0;
        }
    }

    /**
     * Legacy {@code renderRightArm}/{@code renderLeftArm} — one body, since the
     * two differed only in which limb group they walked and the sign of the
     * {@code ±6} offset.
     *
     * <p>Ported step for step: zero the swing, re-pose the whole model at the
     * neutral all-zeros pose ({@code setRotationAngles(0,0,0,0,0, 0.0625, player)}),
     * pin the arm limbs with {@link #positionArmLimbs}, and draw <b>only those
     * limbs</b> at {@code 0.0625}. The re-pose is not optional: the arm the hand
     * renderer wants is the one from a stationary, forward-facing model, not from
     * whatever the wearer's body was doing this frame.</p>
     *
     * <p>Legacy's {@code enableBlend}/{@code color(1,1,1)} around the draw are the
     * caller's {@code RenderLayer} here; legacy's {@code bindTexture} is the
     * layer's texture, chosen by whoever built {@code consumer}.</p>
     */
    public void renderArm(LivingEntity player, boolean leftSide, MatrixStack matrices,
        VertexConsumer consumer, int light, int overlay)
    {
        if (this.mainModel == null)
        {
            return;
        }

        PoseContext context = new PoseContext();

        context.living = player != null;

        this.mainModel.setRotationAngles(context);
        this.poseContext = context;

        ModelCustomRenderer[] arms = leftSide ? this.mainModel.left : this.mainModel.right;

        if (arms == null)
        {
            return;
        }

        positionArmLimbs(arms, leftSide);

        for (ModelCustomRenderer arm : arms)
        {
            arm.render(matrices, consumer, ARM_SCALE, 1F, 1F, 1F, 1F, light, overlay);
        }
    }

    /**
     * The scale legacy passed to {@code arm.render(0.0625F)} — the same 1/16 the
     * limb transforms are authored in.
     */
    public static final float ARM_SCALE = 0.0625F;

    /* --------------------------------------------------------------------- */
    /* Nametag rule (custom-model / morph-block variant)                     */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code RenderCustomModel.canRenderName}: only when the vanilla
     * check passes, the entity has a custom name, <b>and</b> the player is
     * pointing at it. (The actor renderer overrides this with the
     * always-render-names config — see {@link RenderCustomActor}.)
     */
    public static boolean canRenderName(boolean superCanRenderName, boolean hasCustomName, boolean isPointedEntity)
    {
        return superCanRenderName && hasCustomName && isPointedEntity;
    }

    /**
     * The renderer's nametag rule as the render path uses it — <b>virtual</b>,
     * because that is the whole point of legacy's
     * {@code protected boolean canRenderName(EntityLivingBase)}: one shared
     * renderer instance, and {@link RenderCustomActor} overrides the rule.
     * Every client lookup is lifted into a parameter so the polymorphic
     * dispatch is headless-testable.
     *
     * @param vanillaRule     legacy's {@code super.canRenderName(entity)} — the
     *                        {@code RenderLivingBase} team/visibility matrix
     * @param hasCustomName   {@code entity.hasCustomName()}
     * @param hudEnabled      {@code Minecraft.isGuiEnabled()} (unused by this
     *                        base rule; the actor override needs it)
     * @param isPointedEntity {@code entity == renderManager.pointedEntity}
     */
    protected boolean canRenderName(boolean vanillaRule, boolean hasCustomName, boolean hudEnabled, boolean isPointedEntity)
    {
        return canRenderName(vanillaRule, hasCustomName, isPointedEntity);
    }

    /**
     * Legacy's {@code Render.doRender} tail — {@code renderName(entity, x, y,
     * z)} — reproduced at the one place 1.12.2 ran it.
     *
     * <p>On 1.12.2 the nametag over a custom-model morph was <b>not</b> drawn by
     * {@code RenderActor}: that renderer overrode {@code doRender} and never
     * chained {@code super}, so it never reached {@code renderName}. The label
     * came out of {@code CustomMorph.render} → {@code ClientProxy.actorRenderer
     * .doRender} → {@code RenderLivingBase.doRender} → {@code Render.doRender}
     * → {@code renderName}, i.e. from <i>inside</i> the morph draw, which is
     * exactly where this sits. Without it the {@code actor_always_render_names}
     * config and the pointed-entity rule are both inert — the rules were ported
     * and unit-tested at P80 but nothing ever called them (S22, batch V-J).</p>
     *
     * <p><b>Frame.</b> Legacy called this <i>after</i>
     * {@code RenderLivingBase.doRender}'s {@code pushMatrix}/{@code popMatrix},
     * so the label never saw {@code applyRotations} or the model scale — only
     * the caller's {@code translate(x, y, z)}. The call site in {@link #render}
     * is after its {@code matrices.pop()} for the same reason.</p>
     *
     * <p><b>Gates, all legacy's.</b> {@code RenderLivingBase.renderName}'s
     * squared-distance cutoff (32 blocks sneaking, 64 standing) and
     * {@code Render.renderLivingLabel}'s {@code height + 0.5 - (sneaking ?
     * 0.25 : 0)} float height are shared with the morphed-player nameplate and
     * come from {@link MorphNameplate}; the vanilla half of the visibility
     * matrix comes from {@code MorphNameplate.canRenderName(host)},
     * which is 1.12.2 {@code RenderLivingBase.canRenderName} plus a
     * {@code hasCustomName} clause for non-players that this rule already
     * demands anyway.</p>
     *
     * <p><b>No double draw.</b> {@code MorphNameplate.draw} is the
     * {@code EntityMorph} dummy path and never runs for a {@code CustomMorph};
     * {@code EntityRendererNameMixin} only ever <i>removes</i> a label, and only
     * while an entity-morph dummy is being drawn. A morphed <i>player</i> is
     * unaffected in either direction because {@code PlayerEntity.hasCustomName()}
     * is false, which is also why 1.12.2 showed no custom-model label over a
     * player.</p>
     */
    protected void renderName(LivingEntity entity, MatrixStack matrices)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        MorphRenderContext frame = MorphRenderContext.current();

        if (entity == null || matrices == null || mc == null || frame == null || frame.consumers == null)
        {
            return;
        }

        boolean shown = this.canRenderName(MorphNameplate.canRenderName(entity), entity.hasCustomName(),
            MinecraftClient.isHudEnabled(), mc.targetedEntity == entity);

        if (!shown)
        {
            return;
        }

        Entity camera = mc.getCameraEntity();
        boolean sneaking = entity.isSneaking();

        if (camera == null || !MorphNameplate.isWithinNameDistance(entity.squaredDistanceTo(camera), sneaking))
        {
            return;
        }

        String name = entity.getDisplayName() == null ? "" : entity.getDisplayName().getString();

        Nameplate.draw(matrices, frame.consumers, name,
            MorphNameplate.nameHeight(entity.getHeight(), sneaking),
            MorphNameplate.textShift(name), sneaking, frame.light);
    }

    /* --------------------------------------------------------------------- */
    /* render — the four legacy hooks, in order                              */
    /* --------------------------------------------------------------------- */

    /**
     * Draw {@link #current} through the model pipeline. Mirrors legacy
     * {@code RenderCustomModel.doRender} — which was {@code setupModel} →
     * {@code super.doRender} → release — with {@code super} written out,
     * because there is no superclass here to inherit it from:
     *
     * <pre>
     * renderLivingAt   translate(x, y, z)           &lt;- the caller does this
     *                  capture model-view
     * rotateCorpse     rotate(180 - bodyYaw, Y)
     *                  death spin / Dinnerbone flip
     *                  + Blockbuster's applyRotations delta
     * prepareScale     scale(-1, -1, 1)
     *                  preRenderCallback: scale(model.scale * morph.scale)
     *                  translate(0, -1.501, 0)
     * renderModel      mainModel.render(..., 0.0625F)
     * renderLayers     the feature-layer pass
     * </pre>
     *
     * <p>All of it is load-bearing and the order is not negotiable: dropping
     * the flip renders every model upside down and 1.5 blocks low, dropping the
     * yaw rotation makes every model face one fixed direction. Both shipped
     * that way once (see {@code WorldFrameRenderProbeTest}).</p>
     *
     * <p>A {@code null} resolved model skips the whole render (legacy did the
     * same via the {@code this.mainModel != null} guard).</p>
     *
     * <p><b>Feature layers.</b> Legacy's {@code RenderLivingBase} ran its layer
     * list after the geometry pass and <i>inside</i> the same transform — so a
     * layer sees {@code applyRotations} and the model scale. {@link #renderLayers}
     * is invoked from exactly there. {@code LayerBodyPart} is wired;
     * {@code LayerElytra}/{@code LayerActorArmor}/{@code LayerCustomHead} (P76)
     * are built but not yet invoked from anywhere — see that method.</p>
     */
    public void render(LivingEntity entity, float partialTicks, ApplyRotationsInput rotations,
        MatrixStack matrices, VertexConsumer consumer,
        float r, float g, float b, float a, int light, int overlay)
    {
        this.setupModel(entity, partialTicks);

        if (this.mainModel == null)
        {
            return;
        }

        matrices.push();

        /* P278: the pop is in a finally. 1.12.2's caller owned a process-wide GL
         * matrix stack and so does 1.20.4's frame MatrixStack: if a limb, a body
         * part or a layer throws in here, MorphRenderUtils swallows it (that is
         * the errorRendering latch) and this frame would be left one level deep
         * — displacing every draw after it by this morph's transform. That is an
         * "everything is off-centre for one frame" bug with no visible culprit. */
        try
        {
            /* renderLivingAt's tail: legacy captured the model-view here — after
             * the caller's translate(x, y, z), before any rotation. Must stay
             * ahead of applyAllRotations. */
            this.captureMatrix(toVecmath(matrices.peek().getPositionMatrix()));

            /* rotateCorpse + Blockbuster's applyRotations delta. */
            applyAllRotations(matrices, rotations);

            /* prepareScale, in legacy's order: the mirroring flip, then
             * preRenderCallback's model×morph scale, then the -1.501 drop (which
             * therefore scales with the morph). */
            matrices.scale(-1.0F, -1.0F, 1.0F);

            float scale = this.current == null ? 1.0F : this.current.getScale();
            float[] s = preRenderScale(this.mainModel.model, scale);
            matrices.scale(s[0], s[1], s[2]);

            matrices.translate(0.0F, MODEL_Y_OFFSET, 0.0F);

            /* Legacy ModelCustom.render's opening block (P75.2): every OBB the
             * morph owns takes the entity's lerped world position as its centre,
             * before the limb walk that adds each limb's entity-relative offset. */
            if (entity != null)
            {
                Vec3d lerped = entity.getLerpedPos(partialTicks);

                this.mainModel.updateObbCenters(lerped.x, lerped.y, lerped.z);
            }

            this.mainModel.render(matrices, consumer, r, g, b, a, light, overlay);

            this.renderLayers(entity, partialTicks, matrices);
        }
        finally
        {
            matrices.pop();
        }

        /* Legacy's Render.doRender tail, outside RenderLivingBase.doRender's
         * push/pop — the nametag draws in the entity's own frame, unrotated and
         * unscaled. See renderName. */
        this.renderName(entity, matrices);

        this.releaseMatrixRef();
    }

    /**
     * The feature-layer pass (legacy {@code RenderLivingBase}'s layer loop),
     * invoked from inside {@code applyRotations} + the model scale — the
     * position legacy's layers ran at, so they see both.
     *
     * <p><b>This base class runs only the body-part layer.</b> That mirrors
     * 1.12.2 exactly: {@code RenderCustomModel} registered no layers at all, and
     * the five-layer stack was added by {@link RenderCustomActor}'s constructor
     * — which is why the base class is what the model-block and other bare
     * custom-model draws use, and the actor renderer is what a morph goes
     * through. (Legacy's {@code LayerBodyPart} was in that stack too; here it is
     * called directly because the GUI preview needs it without the rest, and it
     * is the one layer with no equipment or vanilla-model dependency.)</p>
     *
     * <p><b>The restore is load-bearing.</b> There is one shared renderer
     * instance (legacy {@code ClientProxy.actorRenderer}), so a body part whose
     * sub-morph is itself a {@code CustomMorph} re-enters this class and
     * overwrites {@link #current}/{@link #mainModel}/{@link #poseContext} with
     * the child's. Legacy's {@code LayerBodyPart} ended with
     * {@code renderer.current = morph; renderer.setupModel(...)} for exactly
     * that reason — and additionally because {@code ModelCustom.render} nulls
     * the model's own {@code current}. Both repairs happen here: the three
     * fields are snapshotted and put back, then {@code setupModel} re-stamps the
     * model from them. Subclass layers run <b>inside</b> that restore, so a
     * nested morph in a body part cannot leave the later layers pointed at the
     * child's model either.</p>
     */
    protected void renderLayers(LivingEntity entity, float partialTicks, MatrixStack matrices)
    {
        if (this.current == null || this.mainModel == null)
        {
            return;
        }

        AbstractMorph morph = this.current.getMorph();

        IModelCustomMorph current = this.current;
        ModelCustom mainModel = this.mainModel;
        PoseContext poseContext = this.poseContext;

        try
        {
            if (morph instanceof IBodyPartProvider)
            {
                LayerBodyPart.renderBodyParts(entity, morph,
                    ((IBodyPartProvider) morph).getBodyPart(),
                    mainModel, poseContext, partialTicks, BODY_PART_SCALE);
            }
        }
        finally
        {
            /* current and poseContext are put back by hand; mainModel is not —
             * setupModel re-derives it from current, and it is the authority. */
            this.current = current;
            this.poseContext = poseContext;

            this.setupModel(entity, partialTicks);
        }
    }

    /**
     * The scale {@code postRender} works in — legacy passed the {@code scale}
     * argument {@code RenderLivingBase.doRender} handed its layers, always
     * {@code 0.0625F} (1/16, the block-unit-to-pixel factor the limb transforms
     * are authored in).
     */
    public static final float BODY_PART_SCALE = 0.0625F;

    /** {@code MathHelper.clamp} mirror — kept local so the pure elytra math
     * pulls in no Minecraft class for headless tests. */
    private static float clamp(float value, float min, float max)
    {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Bridge the render-thread {@code org.joml.Matrix4f} (column-major
     * {@code m<col><row>}) into the {@code javax.vecmath.Matrix4f} (row-major)
     * that {@link MatrixUtils} operates in. Element mapping:
     * {@code vecmath.m<row><col> = joml.m<col><row>}.
     */
    private static Matrix4f toVecmath(org.joml.Matrix4f m)
    {
        return new Matrix4f(
            m.m00(), m.m10(), m.m20(), m.m30(),
            m.m01(), m.m11(), m.m21(), m.m31(),
            m.m02(), m.m12(), m.m22(), m.m32(),
            m.m03(), m.m13(), m.m23(), m.m33());
    }
}
