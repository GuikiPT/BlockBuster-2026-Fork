package mchorse.metamorph.capabilities.morphing;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;

/**
 * Morphing capability (roadmap P52) — 1:1 port of Metamorph 1.4's
 * {@code IMorphing}.
 *
 * <p>This interface is responsible for morphing. See {@link Morphing} for the
 * default implementation. On 1.12.2 Forge the instance rode
 * {@code ICapabilityProvider}; the rewrite (per the technique ledger — no
 * Cardinal dependency, mirroring the P112 {@code RecordingHolder} pattern)
 * attaches it to every {@code PlayerEntity} through the {@link MorphingHolder}
 * duck interface (mixin-created, lazily). {@link Morphing#get} casts through
 * the duck.</p>
 *
 * <p>Port note: the legacy client-only {@code renderPlayer(EntityPlayer, …)}
 * method is intentionally omitted from this main-source interface — it pulls in
 * {@code GlStateManager} / {@code MorphUtils.render}. The morph-transition
 * animation <b>state</b> ({@link #getAnimation()},
 * {@link #getPreviousMorph()}, {@link #isAnimating()}) stays here so the P54
 * client renderer can drive the two-phase render from the component fields.
 * SEAM(P54): client render entry point.</p>
 *
 * Legacy source:
 * .tools/legacy-src/metamorph/.../capabilities/morphing/IMorphing.java
 */
public interface IMorphing
{
    /**
     * When a morph's new maximum health ends up extremely close to zero (e.g.
     * morphing to/from a mob with essentially no health), the health/max-health
     * ratio is preserved through this guard value to prevent a heal exploit.
     */
    public static final float REASONABLE_HEALTH_VALUE = Float.MIN_VALUE * 100;

    /**
     * Whether this morph is in the process of animation
     */
    public boolean isAnimating();

    /**
     * Get animation tick ({@code -1} = idle sentinel; 20-tick countdown).
     */
    public int getAnimation();

    /**
     * Get previous animation morph
     */
    public AbstractMorph getPreviousMorph();

    /**
     * Check the last damage source received by the player. (This value is
     * volatile and not stored.)
     */
    public DamageSource getLastDamageSource();

    /**
     * Record the last damage source received by the player. (This value is
     * volatile and not stored.)
     */
    public void setLastDamageSource(DamageSource damageSource);

    /**
     * Add a morph
     */
    public boolean acquireMorph(AbstractMorph morph);

    /**
     * Check if this capability has acquired a morph
     */
    public boolean acquiredMorph(AbstractMorph morph);

    /**
     * Get all acquired morphs
     */
    public List<AbstractMorph> getAcquiredMorphs();

    /**
     * Set acquired morphs
     */
    public void setAcquiredMorphs(List<AbstractMorph> morphs);

    /**
     * Get current morph
     */
    public AbstractMorph getCurrentMorph();

    /**
     * Set current morph
     */
    public boolean setCurrentMorph(AbstractMorph morph, PlayerEntity player, boolean force);

    /**
     * Demorph this capability
     */
    public void demorph(PlayerEntity player);

    /**
     * Is this capability morphed at all
     */
    public boolean isMorphed();

    /**
     * Favorite or unfavorite a morph by given index
     */
    public void favorite(int index);

    /**
     * Change keybind
     */
    public void keybind(int index, int keybind);

    /**
     * Remove a morph at given index
     */
    public boolean remove(int index);

    /**
     * Remove all acquired morphs
     */
    public void removeAcquired();

    /**
     * Copy data from other morph
     */
    public void copy(IMorphing morphing, PlayerEntity player);

    /**
     * Get the last recorded finite health fraction of the player
     */
    public float getLastHealthRatio();

    /**
     * Determines what the player's new health will be if the player morphs out
     * of a morph with very low health
     */
    public void setLastHealthRatio(float lastHealthRatio);

    /**
     * Gets whether the player is in a morph which drowns on land due to the
     * Swim ability
     */
    public boolean getHasSquidAir();

    /**
     * Sets whether the player is in a morph which drowns on land due to the
     * Swim ability
     */
    public void setHasSquidAir(boolean hasSquidAir);

    /**
     * Gets the air value used when in a morph with the Swim ability
     */
    public int getSquidAir();

    /**
     * Sets the air value of a morph in the Swim ability
     */
    public void setSquidAir(int squidAir);

    /**
     * Get last health
     */
    public float getLastHealth();

    /**
     * Set last health
     */
    public void setLastHealth(float lastHealth);

    /**
     * Update the player
     */
    public void update(PlayerEntity player);
}
