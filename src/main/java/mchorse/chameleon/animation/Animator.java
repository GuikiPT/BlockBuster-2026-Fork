package mchorse.chameleon.animation;

import mchorse.chameleon.animation.ActionPlayback.Fade;
import mchorse.chameleon.lib.ChameleonModel;
import mchorse.chameleon.lib.data.animation.Animation;
import mchorse.chameleon.lib.data.animation.Animations;
import mchorse.chameleon.lib.data.model.Model;
import mchorse.chameleon.metamorph.ChameleonMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Animator class
 *
 * This class is responsible for applying currently running actions onto
 * morph (more specifically onto an armature).
 *
 * <p>Two tiers of playback. One <b>primary</b> action ({@link #active}, with
 * {@link #lastActive} cross-fading out of it) is chosen per tick from the
 * entity's state — dying, sleeping, swimming, riding, flying, crouching,
 * falling, sprinting, running, idle — and applies to <i>all</i> bones, decaying
 * un-animated ones back to the rest pose. Any number of <b>secondary</b> actions
 * ({@link #actions}: jump, swipe, hurt, land, shoot, consume) then apply on top
 * with {@code skipInitial}, so they only touch the bones they animate.</p>
 *
 * <p>Priorities only gate the primary slot, and only upward:
 * {@link #setActiveAction} refuses an action of lower priority than the current
 * one. Hurt (3) and jump (2) therefore cannot be interrupted by the ordinary
 * locomotion actions (1) until they finish.</p>
 *
 * <p>A missing animation is not an error — {@link #createAction} returns
 * {@code null} for it, every field here is nullable, and every call site
 * null-checks. A model with no {@code idle} animation simply has no idle.</p>
 *
 * <p>Port note: legacy was {@code @SideOnly(CLIENT)} because
 * {@link ChameleonMorph}'s render path was its only caller; the class itself
 * uses no client API, and it lives in the common source set so
 * {@code ChameleonMorph} can hold it as a typed field (see that class).</p>
 *
 * Legacy source: chameleon/.../animation/Animator.java
 */
public class Animator
{
    /* Actions */
    public ActionPlayback idle;
    public ActionPlayback running;
    public ActionPlayback sprinting;
    public ActionPlayback crouching;
    public ActionPlayback crouchingIdle;
    public ActionPlayback swimming;
    public ActionPlayback swimmingIdle;
    public ActionPlayback flying;
    public ActionPlayback flyingIdle;
    public ActionPlayback riding;
    public ActionPlayback ridingIdle;
    public ActionPlayback dying;
    public ActionPlayback falling;
    public ActionPlayback sleeping;

    public ActionPlayback jump;
    public ActionPlayback swipe;
    public ActionPlayback hurt;
    public ActionPlayback land;
    public ActionPlayback shoot;
    public ActionPlayback consume;

    /* Syncable action */
    public ActionPlayback animation;

    /* Action pipeline properties */
    public ActionPlayback active;
    public ActionPlayback lastActive;
    public List<ActionPlayback> actions = new ArrayList<ActionPlayback>();

    public double prevX = Float.MAX_VALUE;
    public double prevZ = Float.MAX_VALUE;
    public double prevMY;

    /* States */
    public boolean wasOnGround = true;
    public boolean wasShooting = false;
    public boolean wasConsuming = false;

    public ChameleonMorph morph;

    public Animator(ChameleonMorph morph)
    {
        this.morph = morph;
        this.refresh();
    }

    public void refresh()
    {
        ActionsConfig actions = this.morph.actions;

        this.idle = this.createAction(this.idle, actions.getConfig("idle"), true);
        this.running = this.createAction(this.running, actions.getConfig("running"), true);
        this.sprinting = this.createAction(this.sprinting, actions.getConfig("sprinting"), true);
        this.crouching = this.createAction(this.crouching, actions.getConfig("crouching"), true);
        this.crouchingIdle = this.createAction(this.crouchingIdle, actions.getConfig("crouching_idle"), true);
        this.swimming = this.createAction(this.swimming, actions.getConfig("swimming"), true);
        this.swimmingIdle = this.createAction(this.swimmingIdle, actions.getConfig("swimming_idle"), true);
        this.flying = this.createAction(this.flying, actions.getConfig("flying"), true);
        this.flyingIdle = this.createAction(this.flyingIdle, actions.getConfig("flying_idle"), true);
        this.riding = this.createAction(this.riding, actions.getConfig("riding"), true);
        this.ridingIdle = this.createAction(this.ridingIdle, actions.getConfig("riding_idle"), true);
        this.dying = this.createAction(this.dying, actions.getConfig("dying"), false);
        this.falling = this.createAction(this.falling, actions.getConfig("falling"), true);
        this.sleeping = this.createAction(this.sleeping, actions.getConfig("sleeping"), true);

        this.swipe = this.createAction(this.swipe, actions.getConfig("swipe"), false);
        this.jump = this.createAction(this.jump, actions.getConfig("jump"), false, 2);
        this.hurt = this.createAction(this.hurt, actions.getConfig("hurt"), false, 3);
        this.land = this.createAction(this.land, actions.getConfig("land"), false);
        this.shoot = this.createAction(this.shoot, actions.getConfig("shoot"), true);
        this.consume = this.createAction(this.consume, actions.getConfig("consume"), true);

        this.animation = this.createAction(this.animation, actions.getConfig("animation"), false);
    }

    /**
     * Create an action with default priority
     */
    public ActionPlayback createAction(ActionPlayback old, ActionConfig config, boolean looping)
    {
        return this.createAction(old, config, looping, 1);
    }

    /**
     * Create an action playback based on given arguments. This method
     * is used for creating actions so it was easier to tell which
     * actions are missing. Beside that, you can pass an old action so
     * in morph merging situation it wouldn't interrupt animation.
     */
    public ActionPlayback createAction(ActionPlayback old, ActionConfig config, boolean looping, int priority)
    {
        ChameleonModel model = this.morph.getModel();
        Animations animations = model == null ? null : model.animations;

        if (animations == null)
        {
            return null;
        }

        Animation action = animations.get(config.name);

        /* If given action is missing, then omit creation of ActionPlayback */
        if (action == null)
        {
            return null;
        }

        /* If old is the same, then there is no point creating a new one */
        if (old != null && old.action == action)
        {
            old.config = config;
            old.setSpeed(1);

            return old;
        }

        return new ActionPlayback(action, config, looping, priority);
    }

    /**
     * Update animator. This method is responsible for updating action
     * pipeline and also change current actions based on entity's state.
     */
    public void update(LivingEntity target)
    {
        /* Fix issue with morphs sudden running action */
        if (this.prevX == Float.MAX_VALUE)
        {
            this.prevX = target.getX();
            this.prevZ = target.getZ();
        }

        this.controlActions(target);

        /* Update primary actions */
        if (this.active != null)
        {
            this.active.update();
        }

        if (this.lastActive != null)
        {
            this.lastActive.update();
        }

        /* Update secondary actions */
        Iterator<ActionPlayback> it = this.actions.iterator();

        while (it.hasNext())
        {
            ActionPlayback action = it.next();

            action.update();

            if (action.finishedFading() && action.isFadingModeOut())
            {
                action.stopFade();
                it.remove();
            }
        }
    }

    /**
     * This method is designed specifically to isolate any controlling
     * code (i.e. the ones that is responsible for switching between
     * actions).
     */
    protected void controlActions(LivingEntity target)
    {
        double dx = target.getX() - this.prevX;
        double dz = target.getZ() - this.prevZ;
        boolean creativeFlying = target instanceof PlayerEntity && ((PlayerEntity) target).getAbilities().flying;
        boolean wet = target.isTouchingWater();
        final float threshold = creativeFlying ? 0.1F : (wet ? 0.025F : 0.01F);
        boolean moves = Math.abs(dx) > threshold || Math.abs(dz) > threshold;
        Vec3d motion = target.getVelocity();

        if (target.getHealth() <= 0)
        {
            this.setActiveAction(this.dying);
        }
        else if (target.isSleeping())
        {
            this.setActiveAction(this.sleeping);
        }
        else if (wet)
        {
            this.setActiveAction(!moves ? this.swimmingIdle : this.swimming);
        }
        else if (target.hasVehicle())
        {
            Entity riding = target.getVehicle();
            moves = Math.abs(riding.getX() - this.prevX) > threshold || Math.abs(riding.getZ() - this.prevZ) > threshold;

            this.prevX = riding.getX();
            this.prevZ = riding.getZ();
            this.setActiveAction(!moves ? this.ridingIdle : this.riding);
        }
        else if (creativeFlying || target.isFallFlying())
        {
            this.setActiveAction(!moves ? this.flyingIdle : this.flying);
        }
        else
        {
            if (target.isSneaking())
            {
                this.setActiveAction(!moves ? this.crouchingIdle : this.crouching);
            }
            else if (!target.isOnGround() && motion.y < 0 && target.fallDistance > 1.25)
            {
                this.setActiveAction(this.falling);
            }
            else if (target.isSprinting() && this.sprinting != null)
            {
                this.setActiveAction(this.sprinting);
            }
            else
            {
                this.setActiveAction(!moves ? this.idle : this.running);
            }

            if (target.isOnGround() && !this.wasOnGround && !target.isSprinting() && this.prevMY < -0.5)
            {
                this.addAction(this.land);
            }
        }

        if (!target.isOnGround() && this.wasOnGround && Math.abs(motion.y) > 0.2F)
        {
            this.addAction(this.jump);
            this.wasOnGround = false;
        }

        /* Bow and consumables */
        boolean shooting = this.wasShooting;
        boolean consuming = this.wasConsuming;
        ItemStack stack = target.getMainHandStack();

        if (!stack.isEmpty())
        {
            if (target.getItemUseTimeLeft() > 0)
            {
                UseAction action = stack.getUseAction();

                if (action == UseAction.BOW)
                {
                    if (!this.actions.contains(this.shoot))
                    {
                        this.addAction(this.shoot);
                    }

                    this.wasShooting = true;
                }
                else if (action == UseAction.DRINK || action == UseAction.EAT)
                {
                    if (!this.actions.contains(this.consume))
                    {
                        this.addAction(this.consume);
                    }

                    this.wasConsuming = true;
                }
            }
            else
            {
                this.wasShooting = false;
                this.wasConsuming = false;
            }
        }
        else
        {
            this.wasShooting = false;
            this.wasConsuming = false;
        }

        if (shooting && !this.wasShooting && this.shoot != null)
        {
            this.shoot.fadeOut();
        }

        if (consuming && !this.wasConsuming && this.consume != null)
        {
            this.consume.fadeOut();
        }

        if (target.hurtTime == target.maxHurtTime - 1)
        {
            this.addAction(this.hurt);
        }

        if (target.handSwinging && target.handSwingProgress == 0 && !target.isSleeping())
        {
            this.addAction(this.swipe);
        }

        this.prevX = target.getX();
        this.prevZ = target.getZ();
        this.prevMY = motion.y;

        this.wasOnGround = target.isOnGround();
    }

    /**
     * Set current active (primary) action
     */
    public void setActiveAction(ActionPlayback action)
    {
        if (this.active == action || action == null)
        {
            return;
        }

        if (this.active != null && action.priority < this.active.priority)
        {
            return;
        }

        if (this.active != null)
        {
            this.lastActive = this.active;
        }

        this.active = action;
        this.active.reset();
        this.active.fadeIn();
    }

    /**
     * Add an additional secondary action to the playback
     */
    public void addAction(ActionPlayback action)
    {
        if (action == null)
        {
            return;
        }

        if (this.actions.contains(action))
        {
            action.reset();

            return;
        }

        action.reset();
        action.fadeIn();
        this.actions.add(action);
    }

    /**
     * Apply currently running action pipeline onto given armature
     */
    public void applyActions(LivingEntity target, Model armature, float partialTicks)
    {
        if (this.animation != null && this.morph.isActionPlayer)
        {
            float ticks = this.morph.animation.getFactor(partialTicks) * this.morph.animation.duration;
            boolean doFade = this.morph.lastAnimAction != null;

            if (doFade)
            {
                this.applyAnimation(this.morph.lastAnimAction, target, armature, ticks, Fade.OUT);
                this.applyAnimation(this.animation, target, armature, ticks, Fade.IN);
            }
            else
            {
                this.applyAnimation(this.animation, target, armature, ticks, Fade.FINISHED);
            }

            return;
        }

        if (this.lastActive != null && this.active.isFading())
        {
            this.lastActive.apply(target, armature, partialTicks, 1F, false);
        }

        if (this.active != null)
        {
            float fade = this.active.isFading() ? this.active.getFadeFactor(partialTicks) : 1F;

            this.active.apply(target, armature, partialTicks, fade, false);
        }

        for (ActionPlayback action : this.actions)
        {
            if (action.isFading())
            {
                action.apply(target, armature, partialTicks, action.getFadeFactor(partialTicks), true);
            }
            else
            {
                action.apply(target, armature, partialTicks, 1F, true);
            }
        }
    }

    public void applyAnimation(ActionPlayback animation, LivingEntity target, Model armature, float ticks, Fade fade)
    {
        float progress = ticks * Math.abs(animation.config.speed);
        float fadeFactor = animation.config.fade < 0.0001F ? 1F : MathHelper.clamp(progress / animation.config.fade, 0F, 1F);

        if (fade == Fade.FINISHED)
        {
            fadeFactor = 1F;
        }
        else if (fade == Fade.OUT)
        {
            fadeFactor = fadeFactor >= 0.999F ? 0F : 1F;
        }

        if (fadeFactor < 0.0001F)
        {
            return;
        }

        progress += animation.config.clamp ? animation.config.tick : 0F;

        if (animation.config.speed < 0)
        {
            progress = (float) (animation.action.length) - progress;
        }

        animation.apply(target, armature, animation.config.speed == 0F ? 0F : progress / animation.config.speed, fadeFactor, false);
    }
}
