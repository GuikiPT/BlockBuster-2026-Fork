package mchorse.chameleon.metamorph;

import mchorse.chameleon.Chameleon;
import mchorse.chameleon.animation.ActionPlayback;
import mchorse.chameleon.animation.ActionsConfig;
import mchorse.chameleon.animation.Animator;
import mchorse.chameleon.lib.ChameleonModel;
import mchorse.chameleon.lib.data.model.Model;
import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.data.model.ModelTransform;
import mchorse.chameleon.metamorph.pose.AnimatedPose;
import mchorse.chameleon.metamorph.pose.AnimatedPoseTransform;
import mchorse.chameleon.metamorph.pose.PoseAnimation;
import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.metamorph.api.models.IMorphProvider;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.Animation;
import mchorse.metamorph.api.morphs.utils.IAnimationProvider;
import mchorse.metamorph.api.morphs.utils.IMorphGenerator;
import mchorse.metamorph.api.morphs.utils.ISyncableMorph;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.BodyPartManager;
import mchorse.metamorph.bodypart.IBodyPartProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * A morph backed by a Bedrock ({@code .geo.json}) model plus its Molang
 * animations — Chameleon's whole reason to exist.
 *
 * <p>Named {@code chameleon.<key>}, where {@code key} is the model's folder path
 * under {@code config/chameleon/models} (so {@code chameleon.packs/wolf} is
 * legal and {@link #getKey()} strips only the leading {@code chameleon.}).</p>
 *
 * <h3>Port note — where the render body went</h3>
 *
 * <p>Legacy declared {@code render}/{@code renderOnScreen} here, annotated
 * {@code @SideOnly(CLIENT)}. In the split source set a main-source morph cannot
 * name a client render type, so those two bodies live in
 * {@code mchorse.chameleon.client.render.ChameleonMorphRenderer}, registered
 * into {@code MorphRendererRegistry} — the same treatment every Blockbuster pack
 * morph got (P54). The state and logic they need
 * ({@link #getAnimator()}, {@link #checkAnimator()}, {@link #applyPose},
 * {@link #getScale}) stay here and are public rather than private for that
 * reason alone.</p>
 *
 * <p>Everything else — the animator, the action pipeline, the pose — is
 * genuinely side-agnostic and stays in the common source set, so a dedicated
 * server loads, merges and saves these morphs exactly as it does any other; it
 * simply has no models to resolve (see {@link Chameleon#MODELS}).</p>
 *
 * Legacy source: chameleon/src/main/java/mchorse/chameleon/metamorph/ChameleonMorph.java
 */
public class ChameleonMorph extends AbstractMorph implements IBodyPartProvider, ISyncableMorph, IAnimationProvider, IMorphGenerator
{
    /**
     * Injectable environment check — legacy {@code FMLCommonHandler.instance()
     * .getEffectiveSide() == Side.CLIENT}, guarding the three places that build
     * {@link #lastAnimAction}. Same seam shape as {@code GunProps.clientEnvironment}.
     *
     * <p><b>Documented deviation.</b> Forge's <i>effective</i> side was
     * thread-sensitive: on an integrated server it answered SERVER while running
     * the server thread. Fabric has no equivalent, so this is the coarser
     * <i>environment</i> check and a single-player server thread now also builds
     * a {@link #lastAnimAction}. That is inert — the field is read only by
     * {@code Animator.applyActions} on the render path, and it is in neither
     * {@link #toNBT} nor {@link #equals} — so the deviation costs a few objects
     * per morph transition in single-player and changes nothing observable.</p>
     */
    public static BooleanSupplier clientEnvironment =
        () -> FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;

    public ResourceLocation skin;
    public AnimatedPose pose;
    public ActionsConfig actions = new ActionsConfig();
    public BodyPartManager parts = new BodyPartManager();

    public float scale = 1F;
    public float scaleGui = 1F;
    private float lastScale = 1F;

    public PoseAnimation animation = new PoseAnimation();

    /* Syncable action */
    public boolean isActionPlayer;

    public ActionPlayback lastAnimAction;

    /**
     * Cached key value
     */
    private String key;

    private Animator animator;

    private boolean updateAnimator = false;
    private long lastUpdate;

    private static boolean isClient()
    {
        return clientEnvironment.getAsBoolean();
    }

    public float getScale(float partialTick)
    {
        if (this.animation.isInProgress())
        {
            return this.animation.interp.interpolate(this.lastScale, this.scale, this.animation.getFactor(partialTick));
        }

        return this.scale;
    }

    /**
     * The action pipeline, created on first use.
     *
     * <p>Public (legacy: {@code protected}) because the client renderer drives it
     * — see the class javadoc.</p>
     */
    public Animator getAnimator()
    {
        if (this.animator == null)
        {
            this.animator = new Animator(this);
        }

        return this.animator;
    }

    @Override
    public void pause(AbstractMorph previous, int offset)
    {
        this.animation.pause(offset);

        while (previous instanceof IMorphProvider)
        {
            previous = ((IMorphProvider) previous).getMorph();
        }

        AnimatedPose pose = null;

        if (previous instanceof ChameleonMorph)
        {
            ChameleonMorph morph = (ChameleonMorph) previous;

            pose = morph.pose;

            if (pose != null)
            {
                pose = pose.clone();
            }

            this.lastScale = morph.scale;

            if (isClient() && this.isActionPlayer && morph.isActionPlayer)
            {
                morph.checkAnimator();

                if (morph.animator != null && morph.actions.getConfig("animation") != null)
                {
                    this.lastAnimAction = morph.animator.createAction(morph.animator.animation, morph.actions.getConfig("animation").clone(), false);
                }
                else
                {
                    this.lastAnimAction = null;
                }

                if (this.lastAnimAction != null)
                {
                    this.lastAnimAction.config.tick += morph.animation.getFactor(0F) * morph.animation.duration * Math.abs(this.lastAnimAction.config.speed);
                }
            }
        }

        this.animation.last = pose == null ? (previous == null ? this.pose : new AnimatedPose()) : pose;
        this.parts.pause(previous, offset);

        this.updateAnimator = true;
    }

    @Override
    public boolean isPaused()
    {
        return this.animation.paused;
    }

    @Override
    public Animation getAnimation()
    {
        return this.animation;
    }

    @Override
    public BodyPartManager getBodyPart()
    {
        return this.parts;
    }

    @Override
    public boolean canGenerate()
    {
        return this.animation.isInProgress();
    }

    @Override
    public AbstractMorph genCurrentMorph(float partialTicks)
    {
        ChameleonMorph morph = (ChameleonMorph) this.copy();

        morph.pose = this.getCurrentPose(partialTicks);
        morph.animation.duration = this.animation.progress;

        morph.parts.parts.clear();

        for (BodyPart part : this.parts.parts)
        {
            morph.parts.parts.add(part.genCurrentBodyPart(this, partialTicks));
        }

        return morph.copy();
    }

    public String getKey()
    {
        if (this.key == null)
        {
            this.key = this.name.replaceAll("^chameleon\\.", "");
        }

        return this.key;
    }

    public void updateAnimator()
    {
        this.updateAnimator = true;
    }

    /**
     * Rebuild the action pipeline if anything invalidated it (an edit in the
     * editor, a merge, a model file changing on disk).
     *
     * <p>Public (legacy: {@code private}) for the client renderer; it is also
     * called cross-instance from {@link #pause}/{@link #afterMerge}.</p>
     */
    public void checkAnimator()
    {
        if (this.updateAnimator)
        {
            this.updateAnimator = false;
            this.getAnimator().refresh();
        }
    }

    /**
     * Stamp this morph's pose onto the model's live bone transforms.
     *
     * <p>Public (legacy: {@code private}) for the client renderer. Must run
     * <b>after</b> the animator has applied its actions — the pose lerps against
     * whatever the animation left in {@code bone.current}, weighted by each
     * bone's {@code fixed} value.</p>
     */
    public void applyPose(Model model, float partialTicks)
    {
        AnimatedPose pose = this.pose;
        boolean inProgress = this.animation.isInProgress();

        if (inProgress)
        {
            pose = this.animation.calculatePose(this.pose, this.getModel(), partialTicks);
        }

        for (ModelBone bone : model.bones)
        {
            this.applyPose(bone, pose);
        }
    }

    private void applyPose(ModelBone bone, AnimatedPose pose)
    {
        if (pose != null && pose.bones.containsKey(bone.id))
        {
            AnimatedPoseTransform transform = pose.bones.get(bone.id);
            ModelTransform initial = bone.initial;
            ModelTransform current = bone.current;
            float factor = transform.fixed * pose.animated;
            final float piToDegrees = (float) (180D / Math.PI);

            current.translate.x = Interpolations.lerp(initial.translate.x, current.translate.x, factor) + transform.x;
            current.translate.y = Interpolations.lerp(initial.translate.y, current.translate.y, factor) + transform.y;
            current.translate.z = Interpolations.lerp(initial.translate.z, current.translate.z, factor) + transform.z;

            current.rotation.x = Interpolations.lerp(initial.rotation.x, current.rotation.x, factor) + transform.rotateX * piToDegrees;
            current.rotation.y = Interpolations.lerp(initial.rotation.y, current.rotation.y, factor) + transform.rotateY * piToDegrees;
            current.rotation.z = Interpolations.lerp(initial.rotation.z, current.rotation.z, factor) + transform.rotateZ * piToDegrees;

            current.scale.x = Interpolations.lerp(initial.scale.x, current.scale.x, factor) + (transform.scaleX - 1);
            current.scale.y = Interpolations.lerp(initial.scale.y, current.scale.y, factor) + (transform.scaleY - 1);
            current.scale.z = Interpolations.lerp(initial.scale.z, current.scale.z, factor) + (transform.scaleZ - 1);

            bone.absoluteBrightness = transform.absoluteBrightness;
            bone.glow = transform.glow;
            bone.color.copy(transform.color);
        }

        for (ModelBone childBone : bone.children)
        {
            this.applyPose(childBone, pose);
        }
    }

    private AnimatedPose getCurrentPose(float partialTicks)
    {
        if (this.getModel() == null)
        {
            return this.pose == null ? new AnimatedPose() : this.pose.clone();
        }
        else
        {
            return this.animation.calculatePose(this.pose, this.getModel(), partialTicks).clone();
        }
    }

    /**
     * The loaded model this morph names, or {@code null} — never an error. On a
     * dedicated server this is always {@code null} (no models are loaded there),
     * which is exactly the legacy {@code @SideOnly(CLIENT)} outcome.
     */
    public ChameleonModel getModel()
    {
        return Chameleon.getModel(this.getKey());
    }

    @Override
    public void update(LivingEntity target)
    {
        this.animation.update();
        this.parts.updateBodyLimbs(this, target);

        super.update(target);

        if (target.getWorld().isClient)
        {
            this.updateClient(target);
        }
    }

    private void updateClient(LivingEntity target)
    {
        ChameleonModel model = getModel();

        if (model == null || model.isStatic())
        {
            return;
        }

        if (this.lastUpdate < model.lastUpdate)
        {
            this.lastUpdate = model.lastUpdate;
            this.updateAnimator = true;
        }

        this.checkAnimator();
        this.getAnimator().update(target);
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof ChameleonMorph)
        {
            ChameleonMorph morph = (ChameleonMorph) obj;

            result = result && Objects.equals(morph.skin, this.skin);
            result = result && Objects.equals(morph.pose, this.pose);
            result = result && Objects.equals(morph.parts, this.parts);
            result = result && Objects.equals(morph.actions, this.actions);
            result = result && Objects.equals(morph.animation, this.animation);
            result = result && morph.scale == this.scale;
            result = result && morph.scaleGui == this.scaleGui;
            result = result && morph.isActionPlayer == this.isActionPlayer;
        }

        return result;
    }

    @Override
    public boolean canMerge(AbstractMorph morph)
    {
        if (morph instanceof ChameleonMorph)
        {
            ChameleonMorph animated = (ChameleonMorph) morph;

            if (Objects.equals(this.getKey(), animated.getKey()))
            {
                this.mergeBasic(morph);

                this.lastScale = this.getScale(0);
                this.animation.paused = false;
                this.animation.last = this.pose == null ? new AnimatedPose() : this.pose.clone();

                if (isClient() && this.isActionPlayer && animated.isActionPlayer)
                {
                    this.checkAnimator();

                    if (this.animator != null && this.actions.getConfig("animation") != null)
                    {
                        this.lastAnimAction = this.animator.createAction(this.animator.animation, this.actions.getConfig("animation").clone(), false);
                    }
                    else
                    {
                        this.lastAnimAction = null;
                    }

                    if (this.lastAnimAction != null)
                    {
                        this.lastAnimAction.config.tick += this.animation.getFactor(0F) * this.animation.duration * Math.abs(this.lastAnimAction.config.speed);
                    }
                }

                this.isActionPlayer = animated.isActionPlayer;

                this.skin = RLUtils.clone(animated.skin);
                this.pose = animated.pose == null ? null : animated.pose.clone();
                this.actions.copy(animated.actions);
                this.parts.merge(animated.parts);
                this.animation.merge(animated.animation);
                this.scale = animated.scale;
                this.scaleGui = animated.scaleGui;

                this.updateAnimator = true;

                return true;
            }
        }

        return false;
    }

    @Override
    public void afterMerge(AbstractMorph morph)
    {
        super.afterMerge(morph);

        while (morph instanceof IMorphProvider)
        {
            morph = ((IMorphProvider) morph).getMorph();
        }

        if (morph instanceof IBodyPartProvider)
        {
            this.recursiveAfterMerge(this, (IBodyPartProvider) morph);
        }

        if (morph instanceof ChameleonMorph)
        {
            ChameleonMorph animated = (ChameleonMorph) morph;

            if (Objects.equals(this.getKey(), animated.getKey()))
            {
                this.animation.last = this.pose == null ? new AnimatedPose() : this.pose.clone();

                if (isClient() && this.isActionPlayer && animated.isActionPlayer)
                {
                    animated.checkAnimator();

                    if (animated.animator != null && animated.actions.getConfig("animation") != null)
                    {
                        this.lastAnimAction = animated.animator.createAction(animated.animator.animation, animated.actions.getConfig("animation").clone(), false);
                    }
                    else
                    {
                        this.lastAnimAction = null;
                    }

                    if (this.lastAnimAction != null)
                    {
                        this.lastAnimAction.config.tick += animated.animation.getFactor(0F) * animated.animation.duration * Math.abs(this.lastAnimAction.config.speed);
                    }
                }

                if (animated.animator != null)
                {
                    this.animator = animated.animator;
                    this.animator.morph = this;
                    this.updateAnimator = true;
                }
            }
        }
    }

    private void recursiveAfterMerge(IBodyPartProvider target, IBodyPartProvider destination)
    {
        for (int i = 0, c = target.getBodyPart().parts.size(); i < c; i++)
        {
            if (i >= destination.getBodyPart().parts.size())
            {
                break;
            }

            AbstractMorph a = target.getBodyPart().parts.get(i).morph.get();
            AbstractMorph b = destination.getBodyPart().parts.get(i).morph.get();

            if (a != null)
            {
                a.afterMerge(b);
            }
        }
    }

    @Override
    public AbstractMorph create()
    {
        return new ChameleonMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof ChameleonMorph)
        {
            ChameleonMorph morph = (ChameleonMorph) from;

            this.skin = RLUtils.clone(morph.skin);

            if (morph.pose != null)
            {
                this.pose = morph.pose.clone();
            }

            this.actions.copy(morph.actions);
            this.parts.copy(morph.parts);
            this.animation.copy(morph.animation);
            this.scale = morph.scale;
            this.scaleGui = morph.scaleGui;
            this.isActionPlayer = morph.isActionPlayer;
        }
    }

    @Override
    public float getWidth(LivingEntity target)
    {
        return 0.6F;
    }

    @Override
    public float getHeight(LivingEntity target)
    {
        return 1.8F;
    }

    @Override
    public void reset()
    {
        super.reset();

        this.key = null;
        this.skin = null;
        this.pose = null;
        this.actions = new ActionsConfig();
        this.parts.reset();

        this.scale = this.scaleGui = this.lastScale = 1;

        this.isActionPlayer = false;

        this.updateAnimator = true;
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (this.skin != null)
        {
            tag.put("Skin", RLUtils.writeNbt(this.skin));
        }

        if (this.pose != null)
        {
            tag.put("Pose", this.pose.toNBT());
        }

        NbtList bodyParts = this.parts.toNBT();

        if (bodyParts != null)
        {
            tag.put("BodyParts", bodyParts);
        }

        NbtCompound animation = this.animation.toNBT();

        if (!animation.isEmpty())
        {
            tag.put("Transition", animation);
        }

        NbtCompound actions = this.actions.toNBT();

        if (actions != null)
        {
            tag.put("Actions", actions);
        }

        if (this.scale != 1F)
        {
            tag.putFloat("Scale", this.scale);
        }

        if (this.scaleGui != 1F)
        {
            tag.putFloat("ScaleGUI", this.scaleGui);
        }

        if (this.isActionPlayer)
        {
            tag.putBoolean("ActionPlayer", this.isActionPlayer);
        }
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Skin"))
        {
            this.skin = RLUtils.create(tag.get("Skin"));
        }

        if (tag.contains("Pose", NbtElement.COMPOUND_TYPE))
        {
            this.pose = new AnimatedPose();
            this.pose.fromNBT(tag.getCompound("Pose"));
        }

        if (tag.contains("BodyParts", NbtElement.LIST_TYPE))
        {
            this.parts.fromNBT(tag.getList("BodyParts", NbtElement.COMPOUND_TYPE));
        }

        if (tag.contains("Transition"))
        {
            this.animation.fromNBT(tag.getCompound("Transition"));
        }

        if (tag.contains("Actions"))
        {
            this.actions.fromNBT(tag.getCompound("Actions"));
        }

        if (tag.contains("Scale"))
        {
            this.scale = tag.getFloat("Scale");
        }

        if (tag.contains("ScaleGUI"))
        {
            this.scaleGui = tag.getFloat("ScaleGUI");
        }

        if (tag.contains("ActionPlayer"))
        {
            this.isActionPlayer = tag.getBoolean("ActionPlayer");
        }
    }
}
