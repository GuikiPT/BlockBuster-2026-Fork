package mchorse.metamorph.api.morphs;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import mchorse.blockbuster.mixin.EntityStepSoundAccessor;
import mchorse.blockbuster.mixin.LivingEntityAccessor;
import mchorse.mclib.utils.NBTUtils;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.EntityUtils;
import mchorse.metamorph.api.MorphSettings;
import mchorse.metamorph.bodypart.BodyPartManager;
import mchorse.metamorph.bodypart.IBodyPartProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Entity morph — vanilla-entity disguise (roadmap P53).
 *
 * <p>NBT contract: {@code EntityData} (always), {@code Scale} (always),
 * {@code Texture} (only when set), {@code BodyParts} (only when non-empty). An
 * inner dummy {@link LivingEntity} instance powers the disguise; its settings
 * (health/hostile/speed, AI/nametag/hand overrides) are derived in
 * {@link #setEntity(LivingEntity)} and layered between
 * {@code DEFAULT_MORPHED} and {@code activeSettings} (three-tier
 * {@link #initializeSettings()}).</p>
 *
 * <p><b>Render/GL is out of scope here.</b> The client renderer, first-person
 * hands, texture replacement, limb {@code ModelPart} resolution and the
 * body-part layer live in the P54 client source set. Two data seams remain: the
 * legacy limb NAMES are a body-part contract, resolved through
 * {@link EntityMorphLimbs} (built here, verified in tests); and the render-only
 * per-tick fields (limb swing / hand swing) are refined by the P54 renderer.
 * Everything below is headless-safe state + NBT.</p>
 *
 * <p>Reflection replacements: hurt/death/step sounds use {@code @Invoker}
 * accessors ({@link LivingEntityAccessor}, {@link EntityStepSoundAccessor})
 * instead of the legacy SRG private-method reflection.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/morphs/EntityMorph.java
 */
public class EntityMorph extends AbstractMorph implements IBodyPartProvider
{
    /**
     * The entity id a player disguise carries. There is no {@code PlayerMorph}
     * class any more — a player is an ordinary entity morph named after the
     * vanilla entity, the shape BBS uses ({@code MobForm.mobID} =
     * {@code "minecraft:player"} + {@code MobForm.isPlayer()}).
     */
    public static final String PLAYER_ID = "minecraft:player";

    /**
     * Stand-in profile for a player disguise with no {@link #profile} of its
     * own. BBS keeps two real accounts here purely to select the arm model;
     * this port drives the arm model explicitly through {@link #slim}, so an
     * offline profile is enough and no one's account gets embedded.
     */
    public static final GameProfile DEFAULT_PLAYER_PROFILE = offlineProfile("Steve");

    /**
     * Client-entity seam for the player disguise.
     *
     * <p>{@code EntityType.PLAYER.create(world)} answers {@code null} — the
     * player type declines to build itself, which is why
     * {@link mchorse.metamorph.api.EntityUtils#createEntity} cannot serve this
     * one and BBS's {@code MobFormRenderer.ensureEntity} falls back to
     * constructing {@code OtherClientPlayerEntity} by hand. That type is
     * client-only, so the common side reaches it through this seam (installed
     * at client init, like {@code AbstractMorph.renderDispatcher}). Null
     * (dedicated server, headless tests) → no client entity, and the morph
     * simply draws nothing.</p>
     */
    public interface IClientPlayerFactory
    {
        LivingEntity create(World world, GameProfile profile);

        /**
         * Stamp the arm model onto an entity {@link #create} built.
         *
         * <p>Separate from creation because it has to run <b>after</b>
         * {@code readNbt}: the client entity persists its {@code SkinType} in
         * custom data, and {@code readCustomDataFromNbt} assigns that field
         * unconditionally — so anything set at construction is overwritten by
         * the {@code EntityData} tag a moment later, with an empty string when
         * the tag predates the flag. That is what made the editor's slim toggle
         * look inert.</p>
         */
        void applySkinType(LivingEntity entity, String skinType);
    }

    /** Installed by the client morph-renderer registration; null → no client entity. */
    public static IClientPlayerFactory clientPlayerFactory;

    /**
     * Body part manager
     */
    public BodyPartManager parts = new BodyPartManager();

    /**
     * Player disguise: the profile whose skin the inner player wears. Null on
     * every non-player morph, and on a player morph whose username never
     * resolved — {@link #resolveProfile()} falls back in that case.
     */
    public volatile GameProfile profile;

    /**
     * Player disguise: slim (Alex) arms. Only consulted when the profile is not
     * deciding the model — see {@link #skinTypeOverride()}.
     */
    public boolean slim;

    /**
     * Entity used by this morph to power morphing
     */
    protected LivingEntity entity;

    /**
     * Used for constructing an entity during the loop
     */
    protected NbtCompound entityData;

    /**
     * If the associated entity is being updated
     */
    protected boolean updatingEntity = false;

    public ResourceLocation userTexture;

    public float scale = 1F;

    /**
     * These are settings that are defined from the morph entity. They have
     * lower priority than activeSettings.
     */
    protected MorphSettings entitySettings = null;

    @Override
    public BodyPartManager getBodyPart()
    {
        return this.parts;
    }

    /** A player disguise shows its username rather than the raw entity id. */
    @Override
    protected String getSubclassDisplayName()
    {
        return this.profile == null ? super.getSubclassDisplayName() : this.profile.getName();
    }

    /* Three-tier settings layering */

    protected void setEntitySettings(MorphSettings entitySettings)
    {
        this.entitySettings = entitySettings;
        this.needSettingsUpdate = true;
    }

    @Override
    public void initializeSettings()
    {
        if (!this.needSettingsUpdate)
        {
            return;
        }

        this.settings = MorphSettings.DEFAULT_MORPHED.copy();

        if (this.entitySettings != null)
        {
            this.settings.applyOverrides(this.entitySettings);
        }

        if (this.activeSettings != null)
        {
            this.settings.applyOverrides(this.activeSettings);
        }

        finishInitializingSettings();
    }

    /* Entity lifecycle */

    /**
     * Set entity for this morph and derive its entity settings.
     */
    public void setEntity(LivingEntity entity)
    {
        this.entity = entity;

        entity.setHealth(entity.getMaxHealth());
        entity.noClip = true;
        entity.setCustomNameVisible(true);

        if (entity instanceof MobEntity)
        {
            ((MobEntity) entity).setLeftHanded(false);
        }

        MorphSettings entitySettings = new MorphSettings();
        entitySettings.health = (int) entity.getMaxHealth();
        entitySettings.hostile = entity instanceof HostileEntity || entity instanceof AnimalEntity;

        EntityAttributeInstance speedAttribute = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);

        if (speedAttribute != null)
        {
            /* By vanilla convention, mob movement speeds tend to be 2.5x what
             * the equivalent player speed would be. Squids and players being
             * the major exceptions. */
            if (entity instanceof WaterCreatureEntity)
            {
                entitySettings.speed = 0.1F;
            }
            else if (entity instanceof PlayerEntity)
            {
                entitySettings.speed = (float) speedAttribute.getBaseValue();
            }
            else
            {
                entitySettings.speed = 0.4F * (float) speedAttribute.getBaseValue();
            }
        }

        setEntitySettings(entitySettings);

        if (entity instanceof MobEntity && !(entity instanceof EnderDragonEntity))
        {
            ((MobEntity) entity).setAiDisabled(true);
        }

        if (this.entityData == null)
        {
            NbtCompound serialized = new NbtCompound();
            this.entity.writeNbt(serialized);
            this.entityData = EntityUtils.stripEntityNBT(serialized);
        }
    }

    public LivingEntity getEntity()
    {
        return this.entity;
    }

    /**
     * Get used entity of this morph, if there's no entity, just create it with
     * the provided world.
     */
    public LivingEntity getEntity(World world)
    {
        if (this.entity == null)
        {
            this.setupEntity(world);
        }

        return this.entity;
    }

    /**
     * Setup entity — creates the inner dummy {@link LivingEntity} from the
     * stripped {@code entityData}.
     *
     * <p>Total reader: an unknown/invalid entity id resolves to the entity
     * registry's default value (a placeholder) rather than crashing; if the
     * created entity isn't a {@link LivingEntity}, the morph stays empty.</p>
     *
     * <p>Creation goes through {@link EntityUtils#createEntity(World, EntityType)},
     * not {@code EntityType.create(world)} (P252) — the latter answers
     * {@code null} for a type behind an experimental feature flag
     * ({@code minecraft:breeze} on 1.20.4), which would leave the morph a
     * silently empty shell in every ordinary world.</p>
     */
    public void setupEntity(World world)
    {
        LivingEntity living = this.isPlayer() ? this.createPlayer(world) : this.createRegistryEntity(world);

        if (living == null)
        {
            return;
        }

        try
        {
            if (this.entityData != null)
            {
                living.readNbt(this.entityData);
            }
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("EntityMorph: failed to deserialize entity data for '" + this.name + "'", e);
        }

        living.deathTime = 0;
        living.hurtTime = 0;
        living.setFireTicks(0);

        /* After readNbt, deliberately — see IClientPlayerFactory.applySkinType. */
        if (this.isPlayer() && world.isClient() && clientPlayerFactory != null)
        {
            clientPlayerFactory.applySkinType(living, this.skinTypeOverride());
        }

        this.setEntity(living);

        /* SEAM(P54): client-side renderer/hands/limb-map/texture setup
         * (setupRenderer) lands in the client source set. */
    }

    /** The ordinary path: resolve {@link #name} against the entity registry. */
    private LivingEntity createRegistryEntity(World world)
    {
        EntityType<?> type = null;

        Identifier id = Identifier.tryParse(this.name);

        if (id != null && Registries.ENTITY_TYPE.containsId(id))
        {
            type = Registries.ENTITY_TYPE.get(id);
        }

        if (type == null)
        {
            Metamorph.log("EntityMorph: unknown entity id '" + this.name + "', skipping inner entity setup");

            return null;
        }

        Entity created = EntityUtils.createEntity(world, type);

        if (!(created instanceof LivingEntity living))
        {
            Metamorph.log("EntityMorph: entity id '" + this.name + "' is not a LivingEntity, skipping");

            return null;
        }

        return living;
    }

    /**
     * The player path. {@code minecraft:player} is a registered entity type
     * whose factory is {@code null}, so it can only be built by hand — the
     * client half through {@link #clientPlayerFactory}, the server half as an
     * isolated non-creative spectator (legacy {@code PlayerMorph}'s anonymous
     * {@code EntityPlayer}; its spawn position is irrelevant because
     * {@link #update} overwrites it from the wearer every tick).
     *
     * <p>BBS builds no server entity at all — its forms are renderers. This
     * port keeps one because {@link #getWidth}/{@link #getHeight} and the
     * hurt/death/step sounds all read the inner entity server-side.</p>
     */
    private LivingEntity createPlayer(World world)
    {
        GameProfile profile = this.resolveProfile();

        if (world.isClient())
        {
            if (clientPlayerFactory == null)
            {
                Metamorph.log("EntityMorph: no client player factory installed, skipping inner entity setup");

                return null;
            }

            return clientPlayerFactory.create(world, profile);
        }

        return new PlayerEntity(world, BlockPos.ORIGIN, 0F, profile)
        {
            @Override
            public boolean isSpectator()
            {
                return true;
            }

            @Override
            public boolean isCreative()
            {
                return false;
            }
        };
    }

    /** Whether this morph disguises the wearer as a player. */
    public boolean isPlayer()
    {
        return PLAYER_ID.equals(this.name);
    }

    /**
     * The profile the inner player is built with — its own when a username
     * resolved, otherwise {@link #DEFAULT_PLAYER_PROFILE}.
     */
    public GameProfile resolveProfile()
    {
        return this.profile == null ? DEFAULT_PLAYER_PROFILE : this.profile;
    }

    /**
     * The arm model handed to the client entity, in legacy's {@code SkinType}
     * spelling.
     *
     * <p>{@link #slim} always wins. An earlier revision let the profile decide
     * whenever it was also supplying the skin, which read well but made the
     * editor's toggle silently do nothing on the one morph anybody would use it
     * on — a control that no-ops is worse than one that overrides. The default
     * is not a guess either: {@link #setProfile} seeds the flag from the
     * resolved account's own model, so the toggle starts correct and only
     * departs from the account when the user asks it to.</p>
     */
    public String skinTypeOverride()
    {
        return this.slim ? "alex" : "default";
    }

    /**
     * Resolve a username into {@link #profile}.
     *
     * <p>The offline profile lands first so the morph has a name and something
     * to draw immediately. It is <b>not</b> enough on its own: its UUID is the
     * deterministic offline one rather than the account's, and it carries no
     * {@code textures} property — and a property-less profile is exactly what
     * makes {@code PlayerSkinProvider} fall back to a default skin, because
     * {@code MinecraftSessionService.getTextures} reads the profile it is
     * handed and never goes to the network itself. So the real lookup runs
     * after it (S7/P92, closing legacy's {@code TileEntitySkull
     * .updateGameprofile}) and swaps the resolved profile in.</p>
     *
     * <p>Async, cached and offline-tolerant — see
     * {@link #resolveProfileAsync}. A failed or unknown name leaves the offline
     * profile in place.</p>
     *
     * <p>The callback does the two cheapest possible things: publish the
     * profile and drop the inner entity. It deliberately does not rebuild the
     * entity — {@link #update} and {@link #getEntity(World)} both recreate a
     * null one at the next opportunity, which is where entity construction
     * belongs.</p>
     */
    public void setProfile(String username)
    {
        this.profile = offlineProfile(username);

        this.resolveProfileAsync(true);
    }

    /**
     * A name-only profile carrying the deterministic offline UUID — the same
     * fallback vanilla uses when it cannot resolve a real account.
     *
     * <p>Spelled out rather than {@code Uuids.getOfflinePlayerProfile(name)},
     * which does not exist on 1.20.1 (it arrived with 1.20.2). That helper is
     * this plus a "the string might already be a UUID" branch, which no caller
     * here needs — the input is always a username typed into the editor or read
     * from a legacy {@code Username} tag.</p>
     */
    public static GameProfile offlineProfile(String username)
    {
        return new GameProfile(Uuids.getOfflinePlayerUuid(username), username);
    }

    /**
     * Fill {@link #profile} in with the account's real UUID and {@code textures}
     * property — the S7/P92 lookup, closing legacy's
     * {@code TileEntitySkull.updateGameprofile}.
     *
     * <p>{@code SkullBlockEntity.loadProperties} is vanilla's own name → full
     * profile path (user cache for the UUID, session service for the
     * properties) and is public on 1.20.1, so no mixin is needed. It runs its
     * callback on the game executor and caches what it resolves.</p>
     *
     * <p><b>It always calls back</b>, including with the profile it was handed:
     * when the name did not resolve, when the services are absent (a client
     * that never had them, an offline session), and when the profile already
     * had textures. So the callback only acts on a profile that actually gained
     * a skin — swapping in an unresolved one would reset the entity for nothing
     * and, worse, re-seed {@link #slim} to wide off a texture-less profile,
     * silently undoing the user's toggle. The same test guards the entry, so a
     * profile that is already complete never round-trips at all.</p>
     *
     * @param seedSlim whether the resolved account's own arm model should
     *                 become {@link #slim}. True when the user just typed a
     *                 name (the toggle should start correct); false when
     *                 repairing a stored profile, where the saved flag is the
     *                 user's own choice and must not be overwritten.
     */
    private void resolveProfileAsync(boolean seedSlim)
    {
        if (this.profile == null || hasTextures(this.profile))
        {
            return;
        }

        SkullBlockEntity.loadProperties(this.profile, (resolved) ->
        {
            if (!hasTextures(resolved))
            {
                return;
            }

            this.profile = resolved;

            if (seedSlim)
            {
                this.slim = isSlimProfile(resolved);
            }

            this.resetEntity();
        });
    }

    /** Whether a profile carries the {@code textures} property a skin needs. */
    public static boolean hasTextures(GameProfile profile)
    {
        return profile != null && profile.getProperties().containsKey("textures");
    }

    /**
     * Whether a resolved profile's skin uses the slim (Alex) arm model, read off
     * the same {@code textures} property {@code PlayerSkinProvider} reads.
     *
     * <p>Used to seed {@link #slim} when a username resolves, so the toggle
     * starts on the account's real model and the user is overriding a correct
     * default rather than guessing. Anything unparseable answers wide, which is
     * vanilla's own fallback.</p>
     */
    public static boolean isSlimProfile(GameProfile profile)
    {
        if (profile == null)
        {
            return false;
        }

        for (Property property : profile.getProperties().get("textures"))
        {
            /* The value is base64 JSON; "slim" only ever appears in it as the
             * SKIN metadata model, so the substring test is enough and needs no
             * JSON parse of an attacker-supplied blob. */
            try
            {
                /* getValue(), not value(): 1.20.1 ships authlib 4.0.43, where
                 * Property is a plain class; the record accessors arrived with
                 * the 6.x authlib 1.20.2 pulled in. */
                if (new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8).contains("\"model\":\"slim\""))
                {
                    return true;
                }
            }
            catch (Exception e)
            {
                /* Malformed property — treat as wide, like vanilla does. */
            }
        }

        return false;
    }

    @Override
    public void update(LivingEntity target)
    {
        if (this.entity == null)
        {
            this.setupEntity(target.getWorld());
        }

        if (this.entity == null)
        {
            /* Total reader: no inner entity (unknown id) — still tick abilities
             * and body parts so the player behaves sanely. */
            super.update(target);
            this.parts.updateBodyLimbs(this, target);
            return;
        }

        /* Update entity */
        this.updatingEntity = true;
        this.entity.setInvulnerable(true);
        this.updateEntity(target);
        this.updatingEntity = false;
        this.entity.deathTime = target.deathTime;
        this.entity.hurtTime = target.hurtTime;

        /* SEAM(P54): rabbit auto-startJumping every 10 ticks while moving —
         * needs the client render-state limb swing and RabbitEntity's
         * package-private jump control. */

        /* Update player */
        super.update(target);

        /* Update entity's inventory (client-side only) */
        if (target.getWorld().isClient())
        {
            for (int i = 0; i < 6; i++)
            {
                EquipmentSlot slot = EntityUtils.slotForIndex(i);

                this.entity.equipStack(slot, target.getEquippedStack(slot));
            }

            this.entity.setInvisible(target.isInvisible());
        }

        /* Injecting player's properties */
        this.entity.setPosition(target.getX(), target.getY(), target.getZ());

        this.entity.lastRenderX = target.lastRenderX;
        this.entity.lastRenderY = target.lastRenderY;
        this.entity.lastRenderZ = target.lastRenderZ;

        this.entity.prevX = target.prevX;
        this.entity.prevY = target.prevY;
        this.entity.prevZ = target.prevZ;

        this.entity.setYaw(target.getYaw());
        this.entity.setPitch(target.getPitch());
        this.entity.setHeadYaw(target.getHeadYaw());
        this.entity.setBodyYaw(target.bodyYaw);

        this.entity.setVelocity(target.getVelocity());

        this.entity.prevYaw = target.prevYaw;
        this.entity.prevPitch = target.prevPitch;
        this.entity.prevHeadYaw = target.prevHeadYaw;
        this.entity.prevBodyYaw = target.prevBodyYaw;

        /* SEAM(P54): limb-swing / hand-swing render-state mirroring (legacy
         * limbSwing, swingProgress, prevSwingProgress) is refined by the
         * P54 renderer against 1.20.4's LimbAnimator / hand-swing fields. */

        if (this.entity instanceof MobEntity)
        {
            ((MobEntity) this.entity).setLeftHanded(target.getMainArm() == Arm.LEFT);
        }

        if (target instanceof PlayerEntity && ((PlayerEntity) target).getAbilities().creativeMode)
        {
            this.entity.fallDistance = 0;
        }
        else
        {
            this.entity.fallDistance = target.fallDistance;
        }

        this.entity.setSneaking(target.isSneaking());
        this.entity.setSprinting(target.isSprinting());
        this.entity.setOnGround(target.isOnGround());
        this.entity.age = target.age;

        /* Fighting with the death of entities like zombies */
        this.entity.setHealth(target.getHealth());

        /* Somewhat-riding support: mount the inner entity on an invisible pig */
        boolean targetRiding = target.hasVehicle();
        boolean entityRiding = this.entity.hasVehicle();

        if (targetRiding && !entityRiding)
        {
            this.entity.startRiding(new PigEntity(EntityType.PIG, this.entity.getWorld()), true);
        }
        else if (!targetRiding && entityRiding)
        {
            this.entity.stopRiding();
        }

        if (targetRiding)
        {
            /* One day, this cast is going to backfire, I'll wait for it... */
            Entity ride = this.entity.getVehicle();
            Entity targetRide = target.getVehicle();

            if (ride != null && targetRide != null)
            {
                ride.setYaw(targetRide.getYaw());
                ride.setPitch(targetRide.getPitch());
                ride.prevYaw = targetRide.prevYaw;
                ride.prevPitch = targetRide.prevPitch;

                if (ride instanceof LivingEntity rideLiving)
                {
                    if (targetRide instanceof LivingEntity trr)
                    {
                        rideLiving.setHeadYaw(trr.getHeadYaw());
                        rideLiving.setBodyYaw(trr.bodyYaw);
                        rideLiving.prevHeadYaw = trr.prevHeadYaw;
                        rideLiving.prevBodyYaw = trr.prevBodyYaw;
                    }
                    else
                    {
                        rideLiving.setHeadYaw(target.getHeadYaw());
                        rideLiving.setBodyYaw(target.bodyYaw);
                        rideLiving.prevHeadYaw = target.prevHeadYaw;
                        rideLiving.prevBodyYaw = target.prevBodyYaw;
                    }
                }
            }
        }

        /* SEAM(P54): horse saddle re-apply from entityData "SaddleItem" —
         * needs AbstractHorseEntity's saddle API. */

        this.parts.updateBodyLimbs(this, target);
    }

    protected void updateEntity(LivingEntity target)
    {
        /* A player disguise deliberately does not tick: legacy PlayerMorph
         * replaced this body outright, which is why it neither runs AI nor
         * plays idle sounds. Only the cape chase and the hand flip remain. */
        if (this.entity instanceof PlayerEntity player)
        {
            updatePlayerEntity(player, target);

            return;
        }

        if (getSettings().updates)
        {
            if (!Metamorph.showMorphIdleSounds.get())
            {
                this.entity.setSilent(true);
            }

            this.entity.tick();
            this.entity.setSilent(false);
        }
    }

    /**
     * Legacy {@code PlayerMorph.updateEntity}: flip the main arm and chase the
     * cape by hand, because the inner player never ticks.
     */
    protected static void updatePlayerEntity(PlayerEntity entity, LivingEntity target)
    {
        entity.setMainArm(flipHand(target.getMainArm()));

        /* Update the cape */
        CapeChase x = chaseCape(entity.getX(), entity.capeX);
        CapeChase y = chaseCape(entity.getY(), entity.capeY);
        CapeChase z = chaseCape(entity.getZ(), entity.capeZ);

        entity.capeX = x.chasing;
        entity.capeY = y.chasing;
        entity.capeZ = z.chasing;

        entity.prevCapeX = x.prevChasing;
        entity.prevCapeY = y.prevChasing;
        entity.prevCapeZ = z.prevChasing;
    }

    /**
     * Legacy's primary-hand flip, verbatim: the disguise's main arm is the
     * <b>opposite</b> of the wearer's.
     *
     * <p>It reads like a bug and is not — the inner player is drawn facing the
     * same way the wearer is, and 1.12.2's biped model swings the arm that the
     * flag names, so an unflipped disguise waves the wrong arm. Every 1.12.2
     * player morph was seen through this flip.</p>
     */
    public static Arm flipHand(Arm hand)
    {
        return hand == Arm.LEFT ? Arm.RIGHT : Arm.LEFT;
    }

    /**
     * One axis of legacy's manual cape chase (1.12.2 {@code chasingPos*}, yarn
     * {@code cape*}), extracted so the quirk is headless-testable.
     *
     * <p>The quirk: the {@code ±10} teleport clamp snaps the chase point onto
     * the player <i>and</i> collapses the previous value onto it, but the
     * delta was already sampled before the snap — so the final {@code +=
     * d * 0.25} still applies the whole pre-snap distance, overshooting by a
     * quarter of it. 1.12.2's cape flicks on a long teleport for exactly this
     * reason; reproduced, not corrected.</p>
     */
    public static CapeChase chaseCape(double pos, double chasing)
    {
        double prev = chasing;
        double d = pos - chasing;

        if (d > 10.0D || d < -10.0D)
        {
            chasing = pos;
            prev = chasing;
        }

        return new CapeChase(chasing + d * 0.25D, prev);
    }

    /** Result of {@link #chaseCape(double, double)}. */
    public static final class CapeChase
    {
        public final double chasing;
        public final double prevChasing;

        public CapeChase(double chasing, double prevChasing)
        {
            this.chasing = chasing;
            this.prevChasing = prevChasing;
        }
    }

    public boolean isUpdatingEntity()
    {
        return this.updatingEntity;
    }

    @Override
    protected void updateUserHitbox(LivingEntity target)
    {
        if (this.entity == null)
        {
            this.setupEntity(target.getWorld());
        }

        if (this.entity == null)
        {
            return;
        }

        float width = this.entity.getWidth();
        float height = this.entity.getHeight();

        boolean isAnimalChild = this.entity instanceof PassiveEntity
            && this.entityData != null && this.entityData.getInt("Age") < 0;

        /* Because Minecraft is shit at syncing data! The client changes to the
         * correct size of baby animals, but the server doesn't, so we rely on
         * the provided NBT data (Age < 0) to detect a baby, server-side only. */
        if (!target.getWorld().isClient() && isAnimalChild)
        {
            width *= 0.5;
            height *= 0.5;
        }

        this.updateSize(target, width, height);
    }

    public void setEntityData(NbtCompound tag)
    {
        this.entityData = tag;
    }

    public NbtCompound getEntityData()
    {
        return this.entityData;
    }

    /* Sizes */

    @Override
    public float getWidth(LivingEntity target)
    {
        if (this.entity == null)
        {
            this.setupEntity(target.getWorld());
        }

        return this.entity == null ? 0.6F : this.entity.getWidth();
    }

    @Override
    public float getHeight(LivingEntity target)
    {
        if (this.entity == null)
        {
            this.setupEntity(target.getWorld());
        }

        return this.entity == null ? 1.8F : this.entity.getHeight();
    }

    /* Sounds — via @Invoker accessors instead of legacy SRG reflection */

    @Override
    public SoundEvent getHurtSound(LivingEntity target, DamageSource damageSource)
    {
        LivingEntity entity = this.getEntity(target.getWorld());

        if (entity == null)
        {
            return null;
        }

        try
        {
            return ((LivingEntityAccessor) (Object) entity).metamorph$invokeGetHurtSound(damageSource);
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("EntityMorph: failed to get hurt sound for '" + this.name + "'", e);
        }

        return null;
    }

    @Override
    public SoundEvent getDeathSound(LivingEntity target)
    {
        LivingEntity entity = this.getEntity(target.getWorld());

        if (entity == null)
        {
            return null;
        }

        try
        {
            return ((LivingEntityAccessor) (Object) entity).metamorph$invokeGetDeathSound();
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("EntityMorph: failed to get death sound for '" + this.name + "'", e);
        }

        return null;
    }

    @Override
    public boolean hasCustomStepSound(LivingEntity target)
    {
        return true;
    }

    @Override
    public void playStepSound(LivingEntity target)
    {
        LivingEntity entity = this.getEntity(target.getWorld());

        if (entity == null)
        {
            return;
        }

        try
        {
            int x = MathHelper.floor(entity.getX());
            int y = MathHelper.floor(entity.getY() - 0.20000000298023224D);
            int z = MathHelper.floor(entity.getZ());
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = entity.getWorld().getBlockState(pos);

            ((EntityStepSoundAccessor) (Object) entity).metamorph$invokePlayStepSound(pos, state);
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("EntityMorph: failed to play step sound for '" + this.name + "'", e);
        }
    }

    @Override
    public void onChangeDimension(PlayerEntity player, int oldDim, int currentDim)
    {
        if (this.entity != null)
        {
            /* Reassign the world reference instead of recreating so animation
             * state survives (legacy: this.entity.world = player.world). */
            this.entity.setWorld(player.getWorld());
        }
    }

    /* Equality / copy / reset */

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof EntityMorph)
        {
            EntityMorph morph = (EntityMorph) obj;
            boolean theSame = EntityUtils.compareData(morph.entityData, this.entityData);

            result = result && theSame;
            result = result && Objects.equals(morph.parts, this.parts);
            result = result && morph.scale == this.scale;
            result = result && Objects.equals(morph.userTexture, this.userTexture);
            result = result && Objects.equals(morph.profile, this.profile);
            result = result && morph.slim == this.slim;
        }

        return result;
    }

    @Override
    public void reset()
    {
        this.parts.reset();
        this.resetEntity();
        this.entityData = null;
        this.scale = 1F;
        this.userTexture = null;
        this.profile = null;
        this.slim = false;

        super.reset();
    }

    public void resetEntity()
    {
        if (this.entity != null)
        {
            this.entity = null;
            setEntitySettings(null);
        }
    }

    @Override
    public AbstractMorph create()
    {
        return new EntityMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof EntityMorph)
        {
            EntityMorph morph = (EntityMorph) from;

            this.entitySettings = morph.entitySettings != null ? morph.entitySettings.copy() : null;
            this.entityData = morph.entityData != null ? NBTUtils.legacyCopy(morph.entityData) : null;
            this.parts.copy(morph.parts);
            this.scale = morph.scale;
            this.userTexture = RLUtils.clone(morph.userTexture);
            this.profile = morph.profile;
            this.slim = morph.slim;
        }
    }

    /* NBT */

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (this.entityData != null)
        {
            tag.put("EntityData", this.entityData);
        }

        tag.putFloat("Scale", this.scale);

        if (this.userTexture != null)
        {
            NbtElement texture = RLUtils.writeNbt(this.userTexture);

            if (texture != null)
            {
                tag.put("Texture", texture);
            }
        }

        NbtList bodyParts = this.parts.toNBT();

        if (bodyParts != null)
        {
            tag.put("BodyParts", bodyParts);
        }

        /* Player disguise. Both keys are written only when they carry
         * something, so every non-player morph resaves byte-identically. */
        if (this.profile != null)
        {
            NbtCompound profileTag = new NbtCompound();

            NbtHelper.writeGameProfile(profileTag, this.profile);
            tag.put("PlayerProfile", profileTag);
        }

        if (this.slim)
        {
            tag.putBoolean("Slim", true);
        }
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        /* Migration: 1.12.2 (and this port before the fold-in) stored a player
         * disguise under the bare name "player", because it was its own morph
         * class. It is an ordinary entity morph now, so the name becomes the
         * entity id it always described. Done here rather than in the factory so
         * every load path — records, scenes, body parts, item NBT — converges on
         * one spelling. Resaving writes the new name; a file that has been
         * through this build no longer opens on 2.7.2. */
        if ("player".equalsIgnoreCase(this.name))
        {
            this.name = PLAYER_ID;
        }

        if (tag.contains("PlayerProfile", NbtElement.COMPOUND_TYPE))
        {
            this.profile = NbtHelper.toGameProfile(tag.getCompound("PlayerProfile"));
        }
        else if (tag.contains("Username"))
        {
            this.setProfile(tag.getString("Username"));
        }

        this.slim = tag.getBoolean("Slim");

        /* Repair a stored profile that carries no skin. Two ways to get one:
         * a 1.12.2 record whose Username never resolved, and — the common case
         * — anything saved by a build where setProfile stopped at the offline
         * profile. Both would otherwise render a default skin forever, since
         * nothing else ever revisits a profile that is already non-null. The
         * stored Slim flag is left alone here; it is the user's, not the
         * account's. */
        this.resolveProfileAsync(false);

        this.entityData = tag.getCompound("EntityData");

        if (tag.contains("Scale"))
        {
            this.scale = tag.getFloat("Scale");
        }

        if (tag.contains("Texture"))
        {
            this.userTexture = RLUtils.create(tag.get("Texture"));
        }

        if (tag.contains("BodyParts", NbtElement.LIST_TYPE))
        {
            this.parts.fromNBT(tag.getList("BodyParts", NbtElement.COMPOUND_TYPE));

            /* P227: a 1.12.2 file stores each part's limb as a ModelBase *field*
             * name (bipedHead, leg1, ...) because that is what legacy's
             * reflection produced. Rewrite them to yarn part names here, once,
             * on load — not on the render path, and not for CustomMorph, whose
             * limb strings are model.json author names. Raw strings survive on
             * BodyPart.loadedLimb for byte-parity re-emit. */
            EntityMorphLimbs.translate(this.parts);
        }
    }
}
