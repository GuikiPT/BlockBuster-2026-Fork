package mchorse.blockbuster.client.model;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelLimb.Holding;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.api.formats.obj.ShapeKey;
import mchorse.blockbuster.client.render.IModelCustomMorph;
import mchorse.blockbuster.common.OrientedBB;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Custom model (roadmap P75).
 *
 * <p>Port of Blockbuster 2.7.2's {@code client/model/ModelCustom}. The 1.12.2
 * class extended vanilla {@code ModelBiped} and carried the whole
 * gameplay-animation ({@code setRotationAngles}/{@code setHands}) and
 * held-item/armor/cape logic. On the S6 split, gameplay animation and the
 * biped feature layers are <b>P76/P80</b>; this core keeps the model
 * repository, the parsed limb graph, {@link #applyLimbPose} (the pose→transform
 * apply consumed by the P82 blend factor) and lifecycle.</p>
 */
public class ModelCustom
{
    /** Global client model repository — morphs look up models by key here. */
    public static final Map<String, ModelCustom> MODELS = new HashMap<String, ModelCustom>();

    public Model model;
    public ModelPose pose;

    /**
     * The morph this model is being drawn for, or {@code null} outside a morph
     * draw (the model-editor preview, model-block previews, tests).
     *
     * <p>Legacy typed this {@code CustomMorph} and read {@code current.keying}
     * (chroma-key reverse-subtract blend, now {@link #isKeying()}),
     * {@code current.orientedBBlimbs} and {@code current.cachedTranslation}.
     * {@code CustomMorph} lives in the common source set and cannot be named
     * from a client-only signature the model-editor also uses, so the concrete
     * type stays {@code Object} and the typed reads go through
     * {@link IModelCustomMorph} — which is what {@code RenderCustomModel}
     * assigns here.</p>
     *
     * <p>SEAM(morphs): {@code cachedTranslation} is still unread here;
     * {@code orientedBBlimbs} is read through {@link #getOrientedBBs()}
     * (P75.2).</p>
     */
    public Object current;

    /**
     * Ambient keying state for the limb pass — the 1.20.4 stand-in for the GL
     * blend state legacy set around it.
     *
     * <p>1.12.2 set {@code glBlendEquation}/{@code blendFunc} in
     * {@link #render} and every draw issued underneath inherited it, including
     * the per-material texture binds an OBJ limb makes for each of its groups.
     * On 1.20.4 that state is part of the {@link net.minecraft.client.render.RenderLayer}
     * each buffer is fetched from, and a material group fetches its own buffer
     * deep inside the limb pass ({@code MaterialTextures.ambient()}) — so the
     * flag has to be readable from there, exactly as the GL state used to be.
     * Saved and restored around the pass rather than simply cleared, so a
     * nested draw cannot strand it.</p>
     */
    private static boolean keyingFrame;

    public ModelCustomRenderer[] limbs;
    public ModelCustomRenderer[] renderable;

    public ModelCustomRenderer[] left;
    public ModelCustomRenderer[] right;
    public ModelCustomRenderer[] armor;

    public Map<String, ResourceLocation> materials;
    public List<ShapeKey> shapes;

    /**
     * Arm postures derived by {@link #setHands(PoseContext)} and consumed by the
     * "holding" block in {@link #setRotationAngles(PoseContext)}. Mirror the
     * {@code ModelBiped.rightArmPose}/{@code leftArmPose} fields the legacy class
     * inherited.
     */
    public ArmPose rightArmPose = ArmPose.EMPTY;
    public ArmPose leftArmPose = ArmPose.EMPTY;

    public ModelCustom(Model model)
    {
        this.model = model;
    }

    /**
     * Look up a limb renderer by limb name.
     */
    public ModelCustomRenderer get(String name)
    {
        for (ModelCustomRenderer renderer : this.limbs)
        {
            if (renderer.limb.name.equals(name))
            {
                return renderer;
            }
        }

        return null;
    }

    /**
     * Is the morph being drawn chroma-keyed? Legacy
     * {@code boolean keying = this.current != null && this.current.keying}
     * ({@code blockbuster-1.12/.../client/model/ModelCustom.java:114}), through
     * the {@link IModelCustomMorph} seam because {@link #current} is untyped.
     */
    public boolean isKeying()
    {
        return this.current instanceof IModelCustomMorph && ((IModelCustomMorph) this.current).isKeying();
    }

    /**
     * Legacy {@code this.current.orientedBBlimbs} — the morph's per-limb
     * oriented bounding boxes, or {@code null} outside a morph draw (P75.2).
     */
    public Map<ModelLimb, List<OrientedBB>> getOrientedBBs()
    {
        return this.current instanceof IModelCustomMorph ? ((IModelCustomMorph) this.current).getOrientedBBs() : null;
    }

    /**
     * Legacy {@code ModelCustom.render}'s opening block: every OBB the morph
     * owns has its {@code center} set to the entity's partial-tick-lerped
     * <b>absolute world</b> position, once per frame, before the limb walk
     * ({@code blockbuster-1.12/.../client/model/ModelCustom.java:126-140}).
     *
     * <p>{@code center} is the only piece of an OBB that is not derived from
     * the limb matrix; {@code updateObbs} adds the entity-relative limb offset
     * on top of it, so a stale centre drags the whole box along behind the
     * entity.</p>
     *
     * <p>It is a separate method rather than a line inside {@link #render}
     * because this class' {@code render} takes no entity — legacy inherited
     * {@code ModelBase.render(Entity, ...)}. The caller is
     * {@code RenderCustomModel.render}, immediately before
     * {@code mainModel.render(...)}, which is the same point in the frame
     * legacy ran it at (and past {@code setupModel}, so {@link #current} is
     * already assigned).</p>
     */
    public void updateObbCenters(double x, double y, double z)
    {
        Map<ModelLimb, List<OrientedBB>> obbs = this.getOrientedBBs();

        if (obbs == null)
        {
            return;
        }

        for (List<OrientedBB> list : obbs.values())
        {
            if (list == null)
            {
                continue;
            }

            for (OrientedBB obb : list)
            {
                obb.center.set(x, y, z);
            }
        }
    }

    /**
     * Whether the limb pass currently running is a keyed one — the read side of
     * {@link #keyingFrame}, for the per-material buffer selection that legacy
     * got for free from ambient GL state. False outside a limb pass.
     */
    public static boolean isKeyingFrame()
    {
        return keyingFrame;
    }

    /**
     * Render the parentless (renderable) limbs into a vertex consumer. Child
     * limbs receive their render call from their parent, mirroring legacy.
     *
     * <p>The keying blend legacy switched on here is now carried by the
     * {@code RenderLayer} {@code consumer} was fetched from (see
     * {@code McLibRenderLayers.model}) — the caller picks it, because on 1.20.4
     * the blend state <i>is</i> the buffer. What still has to happen here is
     * publishing that decision to the limb pass, since an OBJ limb fetches its
     * own per-material buffers underneath us and legacy's GL state covered
     * those too.</p>
     *
     * <p>The {@code OrientedBB.center} refresh legacy did at the top of this
     * method is {@link #updateObbCenters}, called by {@code RenderCustomModel}
     * just before this (P75.2). The scale factor is the legacy 1/16
     * ({@code 0.0625}).</p>
     */
    public void render(MatrixStack matrices, VertexConsumer consumer, float r, float g, float b, float a, int light, int overlay)
    {
        boolean previous = keyingFrame;

        keyingFrame = this.isKeying();

        try
        {
            for (ModelCustomRenderer limb : this.renderable)
            {
                limb.render(matrices, consumer, 0.0625F, r, g, b, a, light, overlay);
            }
        }
        finally
        {
            keyingFrame = previous;
            this.current = null;
        }
    }

    /* --------------------------------------------------------------------- */
    /* Pose / animation runtime (P82)                                        */
    /* --------------------------------------------------------------------- */

    /**
     * Derive the two arm postures from the held-item state (legacy
     * {@code setHands(EntityLivingBase)}). Ported field-for-field:</p>
     *
     * <ul>
     *   <li>a present main-hand item is {@link ArmPose#ITEM}, upgraded to
     *       {@link ArmPose#BLOCK}/{@link ArmPose#BOW_AND_ARROW} when actively
     *       used ({@code itemInUseCount > 0}) with a {@code BLOCK}/{@code BOW}
     *       use action;</li>
     *   <li>gun items force {@link ArmPose#BOW_AND_ARROW} when
     *       {@code alwaysArmsShootingPose}, or when {@code enableArmsShootingPose}
     *       and the shoot keybind is held;</li>
     *   <li>the <b>off-hand only ever</b> reaches {@code ITEM}/{@code BLOCK} —
     *       never {@code BOW_AND_ARROW} (legacy quirk kept);</li>
     *   <li>non-living entities get {@link ArmPose#EMPTY} for both hands.</li>
     * </ul>
     */
    public void setHands(PoseContext context)
    {
        if (!context.living)
        {
            this.leftArmPose = ArmPose.EMPTY;
            this.rightArmPose = ArmPose.EMPTY;

            return;
        }

        ArmPose right = ArmPose.EMPTY;
        ArmPose left = ArmPose.EMPTY;

        if (context.rightItemPresent)
        {
            right = ArmPose.ITEM;

            if (context.itemInUseCount > 0)
            {
                if (context.rightUseAction == UseAction.BLOCK)
                {
                    right = ArmPose.BLOCK;
                }
                else if (context.rightUseAction == UseAction.BOW)
                {
                    right = ArmPose.BOW_AND_ARROW;
                }
            }

            if (context.rightItemIsGun)
            {
                if (context.gunAlwaysArmsShootingPose)
                {
                    right = ArmPose.BOW_AND_ARROW;
                }
                else if (context.gunEnableArmsShootingPose && context.gunShoot.getAsBoolean())
                {
                    right = ArmPose.BOW_AND_ARROW;
                }
            }
        }

        if (context.leftItemPresent)
        {
            left = ArmPose.ITEM;

            if (context.itemInUseCount > 0 && context.leftUseAction == UseAction.BLOCK)
            {
                left = ArmPose.BLOCK;
            }
        }

        this.rightArmPose = right;
        this.leftArmPose = left;
    }

    /**
     * The gameplay pose/animation pass (legacy {@code setRotationAngles}).
     *
     * <p>For every limb: snapshot the static pose angles via
     * {@link #applyLimbPose(ModelCustomRenderer)} (which also yields the
     * {@code anim} pose-freeze factor), layer on the procedural flag animations
     * (cape / look / swing / idle / swipe / hold / wheel / wing / roll), then
     * blend the procedural result back toward the snapshot by {@code anim}
     * ({@code angle = (procedural - pose) * anim + pose}).</p>
     *
     * <p>Every formula, sign, precedence rule and dead term is a bug-for-bug port
     * of the 1.12.2 source (see {@code plan/S06-model-rendering.md} P82 quirks):
     * the cape yaw term multiplied by {@code 0}, the double assignments in the
     * bow branch, {@code lookY→Z} when {@code invert}, {@code wing} overriding
     * {@code swiping}'s axis, {@code roll} using {@code ageInTicks % 1} as its
     * partial tick. Entity/world state is read only through {@link PoseContext},
     * making the whole pass headless-testable.</p>
     */
    public void setRotationAngles(PoseContext context)
    {
        this.setHands(context);

        float PI = (float) Math.PI;

        for (ModelCustomRenderer limb : this.limbs)
        {
            boolean mirror = limb.limb.mirror;
            boolean invert = limb.limb.invert;

            if (limb instanceof ModelOBJRenderer)
            {
                ModelOBJRenderer obj = (ModelOBJRenderer) limb;

                obj.materials = this.materials;
                obj.shapes = this.shapes;
            }

            float factor = mirror ^ invert ? -1 : 1;

            /* Snapshot the static pose angles (and the pose-freeze factor). */
            float anim = this.applyLimbPose(limb);
            float rotateX = limb.rotateAngleX;
            float rotateY = limb.rotateAngleY;
            float rotateZ = limb.rotateAngleZ;

            if (limb.limb.cape && context.capeActive)
            {
                double dX = context.capeDX;
                double dY = context.capeDY;
                double dZ = context.capeDZ;
                float bodyYaw = context.bodyYaw;
                double sin = MathHelper.sin(bodyYaw / 180 * PI);
                double cos = -MathHelper.cos(bodyYaw / 180 * PI);
                float h = (float) MathHelper.clamp(dY * 10.0F, -6.0F, 32.0F);
                float pitch = (float) (dX * sin + dZ * cos) * 100.0F;
                float yaw = (float) (dX * cos - dZ * sin) * 100.0F;

                if (pitch > 0.0F)
                {
                    pitch = -pitch;
                }

                h += MathHelper.sin(context.distanceWalked * 6.0F) * 32.0F * context.cameraYaw;

                limb.rotateAngleX += (6.0F + pitch / 2.0F + h) / 180 * PI;
                limb.rotateAngleY += (yaw / 2.0F * 0) / 180 * PI;
            }

            if ((limb.limb.lookX || limb.limb.lookY) && !limb.limb.wheel)
            {
                if (limb.limb.lookX)
                {
                    limb.rotateAngleX += context.headPitch * 0.017453292F;
                }

                if (limb.limb.lookY)
                {
                    if (invert)
                    {
                        limb.rotateAngleZ += context.netHeadYaw * 0.017453292F;
                    }
                    else
                    {
                        limb.rotateAngleY += context.netHeadYaw * 0.017453292F;
                    }
                }
            }

            if (limb.limb.swinging)
            {
                boolean flag = context.living && context.ticksElytraFlying > 4;
                float f = 1.0F;

                if (flag)
                {
                    f = (float) (context.motionX * context.motionX + context.motionY * context.motionY + context.motionZ * context.motionZ);
                    f = f / 0.2F;
                    f = f * f * f;
                }

                if (f < 1.0F)
                {
                    f = 1.0F;
                }

                float f2 = mirror ^ invert ? 1 : 0;
                float f3 = limb.limb.holding == Holding.NONE ? 1.4F : 1.0F;

                limb.rotateAngleX += MathHelper.cos(context.limbSwing * 0.6662F + PI * f2) * f3 * context.limbSwingAmount / f;
            }

            if (limb.limb.idle)
            {
                limb.rotateAngleZ += (MathHelper.cos(context.ageInTicks * 0.09F) * 0.05F + 0.05F) * factor;
                limb.rotateAngleX += (MathHelper.sin(context.ageInTicks * 0.067F) * 0.05F) * factor;
            }

            if (limb.limb.swiping && !limb.limb.wing && context.swingProgress > 0.0F)
            {
                float swing = context.swingProgress;
                float bodyY = MathHelper.sin(MathHelper.sqrt(swing) * PI * 2F) * 0.2F;

                swing = 1.0F - swing;
                swing = swing * swing * swing;
                swing = 1.0F - swing;

                float sinSwing = MathHelper.sin(swing * PI);
                float sinSwing2 = MathHelper.sin(context.swingProgress * PI) * -(0.0F - 0.7F) * 0.75F;

                limb.rotateAngleX = limb.rotateAngleX - (sinSwing * 1.2F + sinSwing2);
                limb.rotateAngleY += bodyY * 2.0F * factor;
                limb.rotateAngleZ += MathHelper.sin(context.swingProgress * PI) * -0.4F * factor;
            }

            if (limb.limb.holding != Holding.NONE)
            {
                boolean right = limb.limb.holding == Holding.RIGHT;
                ArmPose pose = right ? this.rightArmPose : this.leftArmPose;
                ArmPose opposite = right ? this.leftArmPose : this.rightArmPose;

                switch (pose)
                {
                    case BLOCK:
                        limb.rotateAngleX = limb.rotateAngleX * 0.5F - 0.9424779F;
                        limb.rotateAngleY = 0.5235988F * (right ? -1 : 1);
                        break;

                    case ITEM:
                        if (limb.limb.hold)
                        {
                            limb.rotateAngleX = limb.rotateAngleX * 0.5F - PI / 10F;
                        }
                        break;

                    default:
                        break;
                }

                float rotateAngleX = context.headPitch * 0.017453292F;
                float rotateAngleY = context.netHeadYaw * 0.017453292F;

                if (right && pose == ArmPose.BOW_AND_ARROW)
                {
                    limb.rotateAngleY = -0.1F + rotateAngleY - 0.4F;
                    limb.rotateAngleY = 0.1F + rotateAngleY;
                    limb.rotateAngleX = -(PI / 2F) + rotateAngleX;
                    limb.rotateAngleX = -(PI / 2F) + rotateAngleX;
                }
                else if (!right && opposite == ArmPose.BOW_AND_ARROW)
                {
                    limb.rotateAngleY = -0.1F + rotateAngleY;
                    limb.rotateAngleY = 0.1F + rotateAngleY + 0.4F;
                    limb.rotateAngleX = -(PI / 2F) + rotateAngleX;
                    limb.rotateAngleX = -(PI / 2F) + rotateAngleX;
                }
            }

            if (limb.limb.wheel)
            {
                limb.rotateAngleX += context.limbSwing * factor;

                if (limb.limb.lookY)
                {
                    limb.rotateAngleY = context.netHeadYaw / 180 * PI;
                }
            }

            if (limb.limb.wing)
            {
                float wingFactor = MathHelper.cos(context.ageInTicks * 1.3F) * PI * 0.25F * (0.5F + context.limbSwingAmount) * factor;

                if (limb.limb.swiping)
                {
                    limb.rotateAngleZ = wingFactor;
                }
                else
                {
                    limb.rotateAngleY = wingFactor;
                }
            }

            if (limb.limb.roll)
            {
                limb.rotateAngleZ += -context.roll / 180F * PI;
            }

            limb.rotateAngleX = (limb.rotateAngleX - rotateX) * anim + rotateX;
            limb.rotateAngleY = (limb.rotateAngleY - rotateY) * anim + rotateY;
            limb.rotateAngleZ = (limb.rotateAngleZ - rotateZ) * anim + rotateZ;
        }
    }

    /**
     * Render the model for stencil limb-picking (roadmap P84).
     *
     * <p>Port of legacy {@code ModelCustom.renderForStencil}: each limb is
     * tagged with its <b>array index + 1</b> ({@code i + 1}) as its stencil
     * value — index 0 is reserved for "nothing" — via
     * {@link ModelCustomRenderer#setupStencilRendering(int)}, then the
     * parentless (renderable) limbs are drawn. On draw, each limb emits a
     * {@code glStencilFunc(GL_ALWAYS, stencilIndex, -1)} before its geometry
     * (see {@link ModelCustomRenderer#render}); the caller
     * ({@link mchorse.mclib.client.gui.framework.elements.GuiModelRenderer#tryPicking})
     * reads back the pixel and maps it through {@link #getStencilLimbName(int)}.</p>
     *
     * <p>The index-assignment pass is GL-free and headless-testable; the draw
     * itself is a GL boundary. {@code buffers} flushes the previous limb's
     * geometry before each limb's {@code glStencilFunc} is emitted (see
     * {@link ModelCustomRenderer#renderForStencil}) — batching every limb into
     * one deferred buffer would rasterise them all under the last limb's index.
     * The caller must flush once more after this method returns.</p>
     *
     * <p>The {@code stencilRendering} flags are cleared for <b>every</b> limb
     * afterwards, not just the ones reachable through {@link #renderable}: the
     * blueprint is the process-wide {@link #MODELS} cache entry, and a flag left
     * on an unreached limb would fire a stray {@code glStencilFunc} during the
     * next ordinary world render of that model.</p>
     */
    public void renderForStencil(MatrixStack matrices, ModelCustomRenderer.StencilConsumers buffers, int light, int overlay)
    {
        for (int i = 0; i < this.limbs.length; i++)
        {
            this.limbs[i].setupStencilRendering(i + 1);
        }

        try
        {
            for (ModelCustomRenderer limb : this.renderable)
            {
                limb.renderForStencil(matrices, buffers, 0.0625F, light, overlay);
            }
        }
        finally
        {
            for (ModelCustomRenderer limb : this.limbs)
            {
                limb.stencilRendering = false;
            }
        }
    }

    /**
     * Map a stencil pixel value back to the limb name it tags (roadmap P84).
     *
     * <p>Port of legacy {@code GuiBBModelRenderer.getStencilValue}:
     * {@code limbs[value - 1].limb.name} (the inverse of the {@code i + 1}
     * assignment in {@link #renderForStencil}). Made <b>total</b>: value 0
     * ("nothing" — the cleared stencil background) and any out-of-range value
     * return {@code null} rather than throwing, per the ground rule that every
     * reader is crash-free.</p>
     */
    public String getStencilLimbName(int value)
    {
        int index = value - 1;

        if (this.limbs == null || index < 0 || index >= this.limbs.length)
        {
            return null;
        }

        return this.limbs[index].limb.name;
    }

    /**
     * Apply the current pose's transform to a limb and return the blend factor
     * consumed by {@link #setRotationAngles(PoseContext)}. Ported verbatim,
     * including the identity-collapse quirk (absent transform →
     * {@link ModelTransform#DEFAULT}).
     *
     * <p>The pose-freeze factor is {@code 1 - transform.getFixed()}: a plain
     * {@link ModelTransform} yields {@code 1F} (fully animated); the
     * {@code CustomMorph.LimbProperties} subclass (S4) reports its {@code fixed}
     * field via {@link ModelTransform#getFixed()}, freezing the limb partially or
     * fully. This keeps the legacy {@code LimbProperties} blend without a hard
     * dependency on the not-yet-ported morph class.</p>
     */
    public float applyLimbPose(ModelCustomRenderer limb)
    {
        ModelTransform trans = this.pose.limbs.get(limb.limb.name);
        ModelTransform resolved = trans == null ? ModelTransform.DEFAULT : trans;

        limb.applyTransform(resolved);

        return 1F - resolved.getFixed();
    }

    public ModelCustomRenderer[] getRenderForArm(boolean leftSide)
    {
        return leftSide ? this.left : this.right;
    }

    /**
     * Clean up resources used by this model.
     */
    public void delete()
    {
        for (ModelCustomRenderer renderer : this.limbs)
        {
            renderer.delete();
        }
    }

    /* --------------------------------------------------------------------- */
    /* Lifecycle (client half of legacy ModelClientHandler)                  */
    /* --------------------------------------------------------------------- */

    /**
     * Remove a model from the repository and schedule its GL deletion on the
     * render thread. Legacy {@code ModelClientHandler.removeModel} scheduled the
     * {@code model.delete()} via {@code Minecraft.addScheduledTask} because
     * deleting from the integrated-server thread kicked the player with a GL
     * state exception; on 1.20.4 that is {@code MinecraftClient.execute}.
     *
     * <p>The {@link Executor} is injected so lifecycle can be unit-tested
     * without a {@code MinecraftClient}. SEAM(P79): legacy also called
     * {@code ModelExtrudedLayer.clearByModel(model)} inside the scheduled task.</p>
     */
    public static void removeModel(String key, Executor renderThread)
    {
        ModelCustom model = MODELS.remove(key);

        if (model != null)
        {
            renderThread.execute(model::delete);
        }
    }
}
