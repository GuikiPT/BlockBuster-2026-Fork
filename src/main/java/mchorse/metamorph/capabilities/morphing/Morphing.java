package mchorse.metamorph.capabilities.morphing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.Morph;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;

/**
 * Default implementation of {@link IMorphing} (roadmap P52) — 1:1 port of
 * Metamorph 1.4's {@code Morphing} (minus the Forge {@code IStorage} plumbing,
 * which becomes {@link MorphingStorage}'s static NBT helpers, and the
 * client-only {@code renderPlayer}, which is a P54 seam).
 *
 * <p>Stores the current morph, acquired morphs, health scaling state, squid air
 * and the 20-tick morph-transition animation timer.</p>
 *
 * Legacy source:
 * .tools/legacy-src/metamorph/.../capabilities/morphing/Morphing.java
 */
public class Morphing implements IMorphing
{
    /**
     * Headless-test fallback: {@code PlayerEntity} instances that are not
     * {@link MorphingHolder}s (constructed outside the mixin environment) get a
     * stable in-memory capability keyed by identity, mirroring
     * {@code Recording.get}'s fallback.
     */
    private static final Map<PlayerEntity, IMorphing> FALLBACK = new WeakHashMap<>();

    /**
     * List of acquired abstract morphs
     */
    private List<AbstractMorph> acquiredMorphs = new ArrayList<AbstractMorph>();

    /**
     * Current used morph
     */
    private Morph morph = new Morph();

    /**
     * Used for animation
     */
    private AbstractMorph previousMorph;

    /**
     * Animation timer ({@code -1} = idle sentinel; not 0)
     */
    private int animation = -1;

    /**
     * The last damage source received by the player
     */
    private DamageSource lastDamageSource;

    /**
     * (health / max health) is stored here when the new max health ends up
     * very close to zero, and retrieved when the fraction is meaningful again
     */
    private float lastHealthRatio;

    /**
     * Whether or not the current player is in a morph which can drown on land
     * due to having the Swim ability
     */
    private boolean hasSquidAir = false;

    /**
     * The air value used for morphs with the Swim ability in place of regular
     * player air
     */
    private int squidAir = 300;

    /**
     * Last health that player had before morphing, should fix issue that people
     * complain about
     */
    private float lastHealth;

    /**
     * Legacy {@code Morphing.get(EntityPlayer)}: resolves the per-player
     * morphing capability. On 1.20.4 the instance rides the {@link
     * MorphingHolder} duck interface (mixin-attached, lazily created).
     * Non-holder players (headless tests) fall back to a stable in-memory
     * instance keyed by identity. {@code null} player returns {@code null}
     * (legacy parity).
     */
    public static IMorphing get(PlayerEntity player)
    {
        if (player == null)
        {
            return null;
        }

        if (player instanceof MorphingHolder)
        {
            return ((MorphingHolder) player).metamorph$getMorphing();
        }

        return FALLBACK.computeIfAbsent(player, p -> new Morphing());
    }

    /** Headless-test seam: drop the in-memory fallback state. */
    public static void resetFallback()
    {
        FALLBACK.clear();
    }

    @Override
    public boolean isAnimating()
    {
        if (Metamorph.disableMorphAnimation.get())
        {
            return false;
        }

        return this.animation != -1;
    }

    @Override
    public int getAnimation()
    {
        return this.animation;
    }

    @Override
    public AbstractMorph getPreviousMorph()
    {
        return this.previousMorph;
    }

    @Override
    public DamageSource getLastDamageSource()
    {
        return lastDamageSource;
    }

    @Override
    public void setLastDamageSource(DamageSource damageSource)
    {
        this.lastDamageSource = damageSource;
    }

    @Override
    public boolean acquireMorph(AbstractMorph morph)
    {
        if (morph == null || this.acquiredMorph(morph))
        {
            return false;
        }

        this.acquiredMorphs.add(morph);

        return true;
    }

    @Override
    public boolean acquiredMorph(AbstractMorph morph)
    {
        for (AbstractMorph acquired : this.acquiredMorphs)
        {
            if (acquired.equals(morph))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<AbstractMorph> getAcquiredMorphs()
    {
        return acquiredMorphs;
    }

    @Override
    public void setAcquiredMorphs(List<AbstractMorph> morphs)
    {
        this.acquiredMorphs.clear();
        this.acquiredMorphs.addAll(morphs);
    }

    @Override
    public AbstractMorph getCurrentMorph()
    {
        return this.morph.get();
    }

    @Override
    public boolean setCurrentMorph(AbstractMorph morph, PlayerEntity player, boolean force)
    {
        if (morph == null)
        {
            this.demorph(player);

            return true;
        }

        boolean creative = player != null && player.isCreative();

        if (force || creative || this.acquiredMorph(morph))
        {
            if (player != null)
            {
                if (this.morph.isEmpty())
                {
                    this.lastHealth = (float) player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).getBaseValue();
                }
                else
                {
                    this.morph.get().demorph(player);
                }
            }

            this.setMorph(morph);

            if (player != null && !this.morph.isEmpty())
            {
                AbstractMorph current = this.morph.get();

                this.setHealth(player, current.getSettings().health);
                current.morph(player);
            }

            return true;
        }

        return false;
    }

    @Override
    public void demorph(PlayerEntity player)
    {
        if (player != null && !this.morph.isEmpty())
        {
            this.morph.get().demorph(player);
        }

        if (player != null)
        {
            /* 20 is default player's health */
            this.setHealth(player, this.lastHealth <= 0.0F ? 20.0F : this.lastHealth);
        }

        this.setMorph(null);
    }

    /**
     * Set current morph, as well as update animation information
     */
    protected void setMorph(AbstractMorph morph)
    {
        AbstractMorph previous = this.morph.get();

        if (this.morph.set(morph))
        {
            if (!Metamorph.disableMorphAnimation.get())
            {
                this.animation = 20;
            }

            this.previousMorph = previous;
        }
    }

    @Override
    public boolean isMorphed()
    {
        return !this.morph.isEmpty();
    }

    @Override
    public void favorite(int index)
    {
        if (index >= 0 && index < this.acquiredMorphs.size())
        {
            AbstractMorph morph = this.acquiredMorphs.get(index);

            morph.favorite = !morph.favorite;
        }
    }

    @Override
    public void keybind(int index, int keycode)
    {
        if (index >= 0 && index < this.acquiredMorphs.size())
        {
            AbstractMorph morph = this.acquiredMorphs.get(index);

            morph.keybind = keycode;
        }
    }

    @Override
    public boolean remove(int index)
    {
        if (index >= 0 && index < this.acquiredMorphs.size())
        {
            this.acquiredMorphs.remove(index);

            return true;
        }

        return false;
    }

    @Override
    public void removeAcquired()
    {
        this.acquiredMorphs.clear();
    }

    @Override
    public void copy(IMorphing morphing, PlayerEntity player)
    {
        this.acquiredMorphs.addAll(morphing.getAcquiredMorphs());

        if (morphing.getCurrentMorph() != null)
        {
            this.setCurrentMorph(morphing.getCurrentMorph().copy(), player, true);
        }
        else
        {
            this.setCurrentMorph(null, player, true);
        }
    }

    @Override
    public float getLastHealthRatio()
    {
        return lastHealthRatio;
    }

    @Override
    public void setLastHealthRatio(float lastHealthRatio)
    {
        this.lastHealthRatio = lastHealthRatio;
    }

    @Override
    public boolean getHasSquidAir()
    {
        return hasSquidAir;
    }

    @Override
    public void setHasSquidAir(boolean hasSquidAir)
    {
        this.hasSquidAir = hasSquidAir;
    }

    @Override
    public int getSquidAir()
    {
        return squidAir;
    }

    @Override
    public void setSquidAir(int squidAir)
    {
        this.squidAir = squidAir;
    }

    @Override
    public float getLastHealth()
    {
        return this.lastHealth;
    }

    @Override
    public void setLastHealth(float lastHealth)
    {
        this.lastHealth = lastHealth;
    }

    @Override
    public void update(PlayerEntity player)
    {
        if (this.animation >= 0)
        {
            this.animation--;
        }

        if (this.animation == 16 && !player.getWorld().isClient && !Metamorph.disableMorphAnimation.get())
        {
            /* Pop! Legacy spawned 25 EXPLOSION_NORMAL particles + item-pickup
             * sound at tick 16. In the 1.13 flattening the 1.12 "explode" /
             * EXPLOSION_NORMAL particle was renamed to minecraft:poof (yarn
             * ParticleTypes.POOF) — the small puff cloud. (1.12 "largeexplode"
             * / EXPLOSION_LARGE is what became minecraft:explosion.) */
            ((ServerWorld) player.getWorld()).spawnParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.5, player.getZ(), 25, 0.5, 0.5, 0.5, 0.05);

            player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0F, 1.0F);
        }

        if (!this.morph.isEmpty())
        {
            AbstractMorph morph = this.morph.get();

            if (!Metamorph.disableHealth.get())
            {
                this.setMaxHealth(player, morph.getSettings().health);
            }

            morph.update(player);
        }
    }

    /* Adjusting health */

    /**
     * Set player's health proportional to the current health with given max
     * health.
     *
     * @author asanetargoss
     */
    protected void setHealth(LivingEntity target, float health)
    {
        if (Metamorph.disableHealth.get())
        {
            return;
        }

        float maxHealth = target.getMaxHealth();
        float currentHealth = target.getHealth();

        /* A sanity check to prevent "healing" health when morphing to and from
         * a mob with essentially zero health — see {@link #scaleHealth}. Only
         * players carry a morphing capability that stores the ratio. */
        IMorphing capability = null;

        if (target instanceof PlayerEntity)
        {
            capability = Morphing.get((PlayerEntity) target);
        }

        this.setMaxHealth(target, health);
        /* We need to retrieve the max health of the target after modifiers are
         * applied to get a sensible value */
        float proportionalHealth = scaleHealth(capability, maxHealth, currentHealth, health, target.getMaxHealth());

        target.setHealth(proportionalHealth);
    }

    /**
     * Pure, headless-testable core of the legacy {@code setHealth} ratio math.
     *
     * <p>Given the {@code oldMax}/{@code oldHealth} the target had before its
     * max-health attribute was changed, the requested {@code newMax}, and the
     * {@code newMaxAfterModifiers} the target actually ended up with, returns
     * the health value to set. Preserves the legacy
     * {@link IMorphing#REASONABLE_HEALTH_VALUE} heal-exploit guard, mutating the
     * capability's stored ratio exactly as legacy did, and floors the result at
     * {@code Float.MIN_VALUE} (never 0).</p>
     */
    public static float scaleHealth(IMorphing capability, float oldMax, float oldHealth, float newMax, float newMaxAfterModifiers)
    {
        float ratio = oldHealth / oldMax;

        if (capability != null)
        {
            /* Check if a health ratio makes sense for the old health value */
            if (oldMax > IMorphing.REASONABLE_HEALTH_VALUE)
            {
                /* If it makes sense, store that ratio in the capability */
                capability.setLastHealthRatio(ratio);
            }
            else if (newMax > IMorphing.REASONABLE_HEALTH_VALUE)
            {
                /* If it doesn't make sense, BUT the new max health makes sense,
                 * retrieve the ratio from the capability and use that instead */
                ratio = capability.getLastHealthRatio();
            }
        }

        float proportionalHealth = newMaxAfterModifiers * ratio;

        return proportionalHealth <= 0.0F ? Float.MIN_VALUE : proportionalHealth;
    }

    /**
     * Set target's max health via the {@code GENERIC_MAX_HEALTH} attribute base
     * value (legacy {@code SharedMonsterAttributes.MAX_HEALTH}).
     */
    protected void setMaxHealth(LivingEntity target, float health)
    {
        if (target.getMaxHealth() != health)
        {
            EntityAttributeInstance instance = target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);

            if (instance != null)
            {
                instance.setBaseValue(health);
            }
        }
    }
}
