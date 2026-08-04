package mchorse.metamorph.api.morphs;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.MorphSettings;
import mchorse.metamorph.api.abilities.IAbility;
import mchorse.metamorph.api.morphs.utils.Hitbox;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.vecmath.Vector3f;

/**
 * Base class for all different types of morphs (roadmap P47).
 *
 * <p>This is the byte-identical NBT contract for every embedded morph in
 * Blockbuster: {@code toNBT}/{@code fromNBT} discriminate on the {@code Name}
 * tag; {@code ForcedSettings} is always written (even when false), {@code
 * Settings} only when forced and non-empty. {@code equals} compares settings
 * only when both sides are forced. See {@code plan/S04} for the full quirk
 * ledger.</p>
 *
 * <p>Port note: the client-only render bodies ({@code render},
 * {@code renderOnScreen}) delegate through the {@link #renderDispatcher} seam
 * to per-morph renderers in the client source set, so {@code src/main} morphs
 * still load headless (null dispatcher → no-op). Size application ({@code updateSizeDefault}) can't poke
 * {@code width}/{@code height} fields on 1.20.4 — it delegates to the
 * {@link #sizeHandler} seam that P54's {@code getDimensions}/
 * {@code getActiveEyeHeight} mixins install, while the exact legacy clamp
 * math (0.2 clamp, 0.9 eyeFactor, {@code height - 0.1} cap, {@code 1.62F}
 * disable-pov constant) stays here.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/morphs/AbstractMorph.java
 */
public abstract class AbstractMorph
{
    /**
     * Seam for applying a morph's desired size/eye-height to a living entity.
     * On 1.20.4 entity dimensions are pose-driven, so P54's player mixins
     * install a handler that stores the requested dimensions on the morphing
     * component and triggers {@code calculateDimensions()}. Null until then.
     */
    public interface ISizeHandler
    {
        void apply(LivingEntity target, float width, float height, float eyeHeight, boolean applyEye);

        /**
         * Drop the override entirely, handing dimensions back to vanilla — the
         * demorph half of {@link #apply}.
         *
         * <p>1.12.2 restored the box by <i>writing</i> the vanilla player size
         * back ({@code setSize(0.6F, 1.8F)}), because the size was a mutable
         * field. On 1.20.4 dimensions are derived from the pose, so the
         * override is a flag the mixins consult and the correct undo is to
         * clear the flag rather than to write a size: a player who demorphs
         * while sneaking, swimming or elytra-flying then gets vanilla's box for
         * <i>that</i> pose instead of a hard-coded standing one.</p>
         */
        void clear(LivingEntity target);
    }

    public static ISizeHandler sizeHandler;

    public Vector3f cachedTranslation = new Vector3f();

    /**
     * At the moment no use -> would be useful to have realistic physics
     * for every limb.
     */
    public Vector3f angularVelocity = new Vector3f();

    public int age = 0;

    /* Meta information */

    /**
     * Morph's name
     */
    public String name = "";

    /**
     * Morph's display name
     */
    public String displayName = "";

    /* Survival morph properties */

    /**
     * Is this morph is favorite
     */
    public boolean favorite = false;

    /**
     * Keybind (int); -1 means unbound
     */
    public int keybind = -1;

    /* Morph Settings */

    /**
     * The authoritative settings for the morph.
     */
    @Deprecated
    public MorphSettings settings = MorphSettings.DEFAULT.copy();

    /**
     * If this is false, {@link #settings} will be initialized as needed.
     */
    protected boolean forcedSettings = false;

    protected boolean needSettingsUpdate = false;

    /**
     * The highest priority settings, defined through configuration.
     */
    protected MorphSettings activeSettings = null;

    /**
     * This is called to initialize settings for morphs, if settings are
     * out-of-date.
     */
    public void initializeSettings()
    {
        if (!this.needSettingsUpdate)
        {
            if (this.settings == null)
            {
                this.reportMissingSettings();

                this.settings = MorphSettings.DEFAULT_MORPHED.copy();
            }

            return;
        }

        this.settings = MorphSettings.DEFAULT_MORPHED.copy();

        if (this.activeSettings != null)
        {
            this.settings.applyOverrides(this.activeSettings);
        }

        finishInitializingSettings();
    }

    /**
     * The "settings went null without asking for a rebuild" diagnostic
     * (S22 P252).
     *
     * <p><b>The state is legacy-normal, not a port defect.</b> Every
     * Blockbuster-pack morph implements {@code canMerge} as
     * {@code this.mergeBasic(morph)} (CustomMorph, ImageMorph, SnowstormMorph,
     * RecordMorph, StructureMorph, SequencerMorph, TrackerMorph), and
     * {@code mergeBasic} adopts the
     * incoming morph's {@link #settings} <i>reference</i> without adopting its
     * {@link #needSettingsUpdate} flag. A morph that just came off the wire has
     * {@code settings == null} / {@code needSettingsUpdate == true} (see
     * {@link #reset()}), so the surviving morph ends up {@code null} +
     * {@code false} — exactly this branch. That is the ordinary
     * morph-into/edit-a-morph path ({@code Morphing.setMorph} →
     * {@code Morph.set} → {@code canMerge} → {@code mergeBasic}, then
     * {@code current.getSettings().health} one line later), and Metamorph 1.4
     * does the identical thing.</p>
     *
     * <p>Legacy only ever <i>printed</i> it in a workspace
     * ({@code if (!FMLForgePlugin.RUNTIME_DEOBF)}); the port dropped that guard
     * in P47, which is why a modder-facing assertion turned into ~30 ERROR
     * lines per play session. The guard is restored via
     * {@link Metamorph#developmentEnvironment}.</p>
     *
     * <p>The heal below is legacy-exact and, when {@link #activeSettings} is
     * {@code null}, produces byte-for-byte what a real
     * {@link #initializeSettings()} would have — {@code DEFAULT_MORPHED.copy()}
     * with nothing to overlay. It is only lossy when the morph <i>has</i>
     * {@code morphs.json} overrides, because the heal cannot apply them; that
     * case (also legacy behaviour, and never reachable for the vanilla-entity
     * morphs {@code morphs.json} usually configures — {@link EntityMorph} never
     * merges) is the one thing left worth a production log line, once per morph
     * name.</p>
     */
    private void reportMissingSettings()
    {
        if (Metamorph.developmentEnvironment.getAsBoolean())
        {
            Metamorph.LOGGER.error("needSettingsUpdate was not set to true when changing morph settings, or the settings was set to null directly when it shouldn't be");
        }
        else if (this.activeSettings != null && REPORTED_DROPPED_SETTINGS.add(this.name == null ? "" : this.name))
        {
            Metamorph.LOGGER.warn("Morph \"{}\" reached its settings with none initialized while it has morphs.json overrides — falling back to the default morphed settings, so its configured settings are not applied to this instance (Metamorph 1.4 behaved the same way).", this.name);
        }
    }

    /**
     * Morph names already reported by {@link #reportMissingSettings} — the
     * dropped-overrides warning fires once per name, not once per merge.
     */
    private static final Set<String> REPORTED_DROPPED_SETTINGS = ConcurrentHashMap.newKeySet();

    protected void finishInitializingSettings()
    {
        this.needSettingsUpdate = false;
        this.forcedSettings = false;
    }

    /**
     * This sets the active settings for the morph, usually defined by the user
     * through JSON configuration. These settings usually have the highest
     * priority.
     */
    public void setActiveSettings(MorphSettings activeSettings)
    {
        this.activeSettings = activeSettings;
        this.needSettingsUpdate = true;
    }

    /**
     * This forces a morph to use the given settings.
     */
    public void forceSettings(MorphSettings settingsToForce)
    {
        if (settingsToForce == null)
        {
            settingsToForce = MorphSettings.DEFAULT_MORPHED.copy();
        }

        this.settings = settingsToForce;
        this.forcedSettings = true;
    }

    /**
     * This forces a morph to use the updated settings.
     */
    public void forceEditSettings(MorphSettings.Edit edit)
    {
        MorphSettings settingsCopy = this.getSettings().copy();
        edit.apply(settingsCopy);
        forceSettings(settingsCopy);
    }

    /**
     * Undoes the effects of {@link #forceSettings}
     */
    public void clearForcedSettings()
    {
        this.settings = null;
        this.forcedSettings = false;
        this.needSettingsUpdate = true;
    }

    /**
     * Gets the morph settings or initializes them if not defined.
     */
    public MorphSettings getSettings()
    {
        if (!this.forcedSettings)
        {
            initializeSettings();
        }

        return this.settings;
    }

    /**
     * Custom hitbox setting
     */
    public Hitbox hitbox = new Hitbox();

    /**
     * Whether this morph is erroring when rendering
     */
    public boolean errorRendering;

    /**
     * Get display name of this morph
     */
    public String getDisplayName()
    {
        if (this.displayName != null && !this.displayName.isEmpty())
        {
            return this.displayName;
        }

        return this.getSubclassDisplayName();
    }

    protected String getSubclassDisplayName()
    {
        return this.name;
    }

    public boolean hasCustomName()
    {
        return this.displayName != null && !this.displayName.isEmpty();
    }

    /* Render methods (client seam — see IRenderDispatcher) */

    /**
     * Client-side render dispatch seam (roadmap P54).
     *
     * <p>Legacy declared {@code render}/{@code renderOnScreen} as
     * {@code @SideOnly(CLIENT)} bodies on every morph subclass. In the split
     * source set the morph classes live in {@code src/main} and cannot name a
     * single client render type, so the per-morph bodies move to client-side
     * {@code IMorphRenderer}s looked up by morph class, and the two instance
     * methods below delegate through this one static seam (the option the S4
     * plan sanctioned: "no-abstract hooks dispatched through a client-side
     * MorphRenderer registry").</p>
     *
     * <p>Null (dedicated server, headless tests) → both render methods are
     * no-ops, exactly as before.</p>
     */
    public interface IRenderDispatcher
    {
        void render(AbstractMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks);

        void renderOnScreen(AbstractMorph morph, PlayerEntity player, int x, int y, float scale, float alpha);

        /**
         * The first-person arm. Returns legacy's "I claimed this hand" boolean —
         * see {@link AbstractMorph#renderHand}. A dispatcher with no renderer for
         * the morph must fall back to {@link AbstractMorph#claimsHandByDefault}
         * rather than answering false, or a morph whose settings hide the hands
         * would start showing the player's arm again.
         */
        boolean renderHand(AbstractMorph morph, PlayerEntity player, Hand hand);
    }

    /** Installed by the client render pipeline; null → no morph draws. */
    public static IRenderDispatcher renderDispatcher;

    /**
     * Render this morph on 2D screen (used in GUIs). Routed to the client
     * renderer registered for this morph's class; no-op without a dispatcher.
     */
    public void renderOnScreen(PlayerEntity player, int x, int y, float scale, float alpha)
    {
        if (renderDispatcher != null)
        {
            renderDispatcher.renderOnScreen(this, player, x, y, scale, alpha);
        }
    }

    /**
     * Render the entity (in the world). Routed to the client renderer
     * registered for this morph's class; no-op without a dispatcher.
     */
    public void render(LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks)
    {
        if (renderDispatcher != null)
        {
            renderDispatcher.render(this, entity, x, y, z, entityYaw, partialTicks);
        }
    }

    /**
     * Render the arm for given hand.
     *
     * <p>The boolean is <b>"this morph has taken responsibility for the hand"</b>,
     * not "something was drawn": the caller cancels the vanilla arm when it is
     * true. So a morph whose {@link MorphSettings#hands} is false claims the hand
     * and draws nothing, which is how a hand-less disguise hides the player's arm
     * — see {@link #claimsHandByDefault}.</p>
     *
     * <p>Routed to the client renderer registered for this morph's class; without
     * a dispatcher (dedicated server, headless tests) only the settings rule
     * applies, which is also what an unported morph type falls back to.</p>
     */
    public boolean renderHand(PlayerEntity player, Hand hand)
    {
        if (renderDispatcher != null)
        {
            return renderDispatcher.renderHand(this, player, hand);
        }

        return this.claimsHandByDefault();
    }

    /**
     * Legacy {@code AbstractMorph.renderHand}'s whole body: {@code
     * !getSettings().hands}. Kept as its own method because it is both the base
     * behaviour and the fallback every layer of the dispatch chain needs when it
     * has nothing more specific to do.
     */
    public boolean claimsHandByDefault()
    {
        return !this.getSettings().hands;
    }

    /* Update loop */

    /**
     * Update the player based on its morph abilities and properties. This
     * method also responsible for updating AABB size.
     */
    public void update(LivingEntity target)
    {
        this.updateHitbox(target);

        MorphSettings settings = this.getSettings();

        if (settings.speed != 0.1F)
        {
            EntityAttributeInstance instance = target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);

            if (instance != null)
            {
                instance.setBaseValue(settings.speed);
            }
        }

        for (IAbility ability : settings.abilities)
        {
            ability.update(target);
        }

        this.age++;
    }

    /* Morph and demorph handlers */

    /**
     * Morph into the current morph
     */
    public void morph(LivingEntity target)
    {
        /* Apply the box now rather than waiting for the first update() tick.
         * Switching morphs runs demorph() (which hands the box back to vanilla)
         * immediately before this, so without it the player would spend a tick
         * at vanilla size between two morphs. */
        this.updateHitbox(target);

        for (IAbility ability : this.getSettings().abilities)
        {
            ability.onMorph(target);
        }
    }

    /**
     * Demorph from the current morph
     */
    public void demorph(LivingEntity target)
    {
        /* The morph's box is re-applied every tick by update(); once the morph
         * is gone nothing runs again, so the last size it asked for would stick
         * to the player forever. This is the release. */
        clearSizeDefault(target);

        for (IAbility ability : this.getSettings().abilities)
        {
            ability.onDemorph(target);
        }
    }

    /* Adjusting size */

    protected void updateHitbox(LivingEntity target)
    {
        if (this.hitbox.enabled)
        {
            float height = target.isSneaking() ? this.hitbox.sneakingHeight : this.hitbox.height;

            this.updateSize(target, this.hitbox.width, height, this.hitbox.eye);
        }
        else
        {
            this.updateUserHitbox(target);
        }
    }

    protected void updateUserHitbox(LivingEntity target)
    {}

    public void updateSize(LivingEntity target, float width, float height)
    {
        updateSizeDefault(target, width, height);
    }

    /**
     * Update player's size based on given width and height
     */
    public void updateSize(LivingEntity target, float width, float height, float eyeFactor)
    {
        updateSizeDefault(target, width, height, eyeFactor);
    }

    public static void updateSizeDefault(LivingEntity target, float width, float height)
    {
        updateSizeDefault(target, width, height, 0.9F);
    }

    public static void updateSizeDefault(LivingEntity target, float width, float height, float eyeFactor)
    {
        /* Any lower than this, and the morph will take damage when hitting the
         * ceiling. Likewise, an eye height less than this will cause
         * suffocation damage when standing on the ground. Hard-coded in
         * vanilla. */
        float minEyeToHeadDifference = 0.1F;
        height = Math.max(height, minEyeToHeadDifference * 2);

        boolean applyEye = false;
        float eyeHeight = 0F;

        if (target instanceof PlayerEntity && !Metamorph.disablePov.get())
        {
            applyEye = true;
            eyeHeight = height * eyeFactor;

            if (eyeHeight + minEyeToHeadDifference > height)
            {
                eyeHeight = height - minEyeToHeadDifference;
            }
        }

        if (sizeHandler != null)
        {
            sizeHandler.apply(target, width, height, eyeHeight, applyEye);
        }
    }

    /**
     * Release the morph size override so the target's dimensions come from
     * vanilla again — the counterpart to {@link #updateSizeDefault}. No-op
     * before the P54 handler is installed (headless), like its sibling.
     */
    public static void clearSizeDefault(LivingEntity target)
    {
        if (sizeHandler != null)
        {
            sizeHandler.clear(target);
        }
    }

    /* Safe shortcuts for activating action and attack */

    /**
     * Execute action with (or on) given player
     */
    public void action(LivingEntity target)
    {
        if (this.getSettings().action != null)
        {
            this.getSettings().action.execute(target, this);
        }
    }

    /**
     * Attack a target
     */
    public void attack(Entity target, LivingEntity source)
    {
        if (this.getSettings().attack != null)
        {
            this.getSettings().attack.attack(target, source);
        }
    }

    /**
     * Used when copying morphs. Subclasses must override.
     */
    public abstract AbstractMorph create();

    /**
     * Clone a morph
     */
    public final AbstractMorph copy()
    {
        AbstractMorph morph = this.create();
        assert (this.getClass().isInstance(morph));

        morph.copy(this);

        return morph;
    }

    /**
     * Copy this {@link AbstractMorph}. Subclasses with new data must override.
     */
    public void copy(AbstractMorph from)
    {
        this.name = from.name;
        this.displayName = from.displayName;
        this.favorite = from.favorite;
        this.settings = from.settings != null ? from.settings.copy() : null;
        this.activeSettings = from.activeSettings != null ? from.activeSettings.copy() : null;
        this.forcedSettings = from.forcedSettings;
        this.needSettingsUpdate = from.needSettingsUpdate;
        this.keybind = from.keybind;
        this.hitbox.copy(from.hitbox);
    }

    /* Getting size */

    /**
     * Get width of this morph
     */
    public abstract float getWidth(LivingEntity target);

    /**
     * Get height of this morph
     */
    public abstract float getHeight(LivingEntity target);

    /**
     * Get the eye height of this morph. Not used by updateSize.
     */
    public float getEyeHeight(LivingEntity target)
    {
        if (!Metamorph.disablePov.get())
        {
            return this.getHeight(target) * 0.9F;
        }
        else
        {
            return 1.62F;
        }
    }

    /**
     * Get the default sound that this morph makes when it is hurt
     */
    public final SoundEvent getHurtSound(LivingEntity target)
    {
        return getHurtSound(target, null);
    }

    /**
     * Get the sound that this morph makes when it is hurt by the given
     * DamageSource, or return null for no change.
     */
    public SoundEvent getHurtSound(LivingEntity target, DamageSource damageSource)
    {
        return null;
    }

    /**
     * Get the sound that this morph makes when it is killed, or return null
     * for no change.
     */
    public SoundEvent getDeathSound(LivingEntity target)
    {
        return null;
    }

    /**
     * Make this return true if you override playStepSound(..)
     */
    public boolean hasCustomStepSound(LivingEntity target)
    {
        return false;
    }

    /**
     * Plays the sound that this morph makes when it takes a step.
     */
    public void playStepSound(LivingEntity target)
    {}

    /**
     * Called when the player just changed dimensions
     */
    public void onChangeDimension(PlayerEntity player, int oldDim, int currentDim)
    {}

    /**
     * Check either if given object is the same as this morph
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof AbstractMorph)
        {
            AbstractMorph morph = (AbstractMorph) obj;

            return Objects.equals(this.name, morph.name) &&
                Objects.equals(this.displayName, morph.displayName) &&
                Objects.equals(this.hitbox, morph.hitbox) &&
                Objects.equals(this.forcedSettings ? this.settings : null, morph.forcedSettings ? morph.settings : null);
        }

        return super.equals(obj);
    }

    /**
     * Check whether the morph can be merged.
     */
    public boolean canMerge(AbstractMorph morph)
    {
        return false;
    }

    /**
     * Collect the data from previous morph
     */
    public void afterMerge(AbstractMorph morph)
    {}

    /**
     * Whether to activate the use target option in GUIs by default
     */
    public boolean useTargetDefault()
    {
        return false;
    }

    /**
     * This method should be used by any morphs that support merging to copy
     * essential whenever they merge. Copies the {@link #settings}
     * <b>reference</b> (not a copy) — kept as legacy.
     */
    protected void mergeBasic(AbstractMorph morph)
    {
        this.displayName = morph.displayName;
        this.settings = morph.settings;
        this.forcedSettings = morph.forcedSettings;
        this.hitbox.copy(morph.hitbox);
    }

    /**
     * Reset data for editing
     */
    public void reset()
    {
        setActiveSettings(null);
        clearForcedSettings();
        this.hitbox.reset();
    }

    /* Reading / writing to NBT */

    public final NbtCompound toNBT()
    {
        NbtCompound tag = new NbtCompound();

        this.toNBT(tag);

        return tag;
    }

    /**
     * Save abstract morph's properties to NBT compound
     */
    public void toNBT(NbtCompound tag)
    {
        tag.putString("Name", this.name);

        if (this.forcedSettings)
        {
            NbtCompound settings = new NbtCompound();

            this.getSettings().toNBT(settings);

            if (!settings.isEmpty())
            {
                tag.put("Settings", settings);
            }
        }

        tag.putBoolean("ForcedSettings", this.forcedSettings);

        if (this.displayName != null && !this.displayName.isEmpty())
        {
            tag.putString("DisplayName", this.displayName);
        }

        if (this.favorite)
        {
            tag.putBoolean("Favorite", this.favorite);
        }

        if (this.keybind >= 0)
        {
            tag.putInt("Keybind", this.keybind);
        }

        if (!this.hitbox.isDefault())
        {
            tag.put("Hitbox", this.hitbox.toNBT());
        }
    }

    /**
     * Read abstract morph's properties from NBT compound
     */
    public void fromNBT(NbtCompound tag)
    {
        this.reset();

        this.name = tag.getString("Name");

        boolean hasForcedSettings = false;
        if (tag.contains("ForcedSettings"))
        {
            hasForcedSettings = tag.getBoolean("ForcedSettings");
        }

        if (tag.contains("Settings"))
        {
            this.settings = new MorphSettings();
            this.settings.fromNBT(tag.getCompound("Settings"));

            if (hasForcedSettings)
            {
                this.forcedSettings = true;
            }
        }

        if (tag.contains("DisplayName"))
        {
            this.displayName = tag.getString("DisplayName");
        }

        if (tag.contains("Favorite"))
        {
            this.favorite = tag.getBoolean("Favorite");
        }

        if (tag.contains("Keybind"))
        {
            this.keybind = tag.getInt("Keybind");
        }

        if (tag.contains("Hitbox"))
        {
            this.hitbox.fromNBT(tag.getCompound("Hitbox"));
        }
    }
}
