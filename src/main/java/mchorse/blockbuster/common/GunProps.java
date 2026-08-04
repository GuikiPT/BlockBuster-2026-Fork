package mchorse.blockbuster.common;

import java.util.function.BooleanSupplier;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.common.entity.EntityGunProjectile;
import mchorse.blockbuster.common.item.GunState;
import mchorse.metamorph.api.Morph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.World;

/**
 * Blockbuster gun properties (P193 — NBT property set only).
 *
 * <p>This savage fellow keeps all of the properties that a gun can have:
 * the gun itself, the projectile spawned from it, and projectile-impact
 * behaviour. Ported field-for-field from Blockbuster 2.7.2's
 * {@code mchorse.blockbuster.common.GunProps} for byte-exact NBT round-trip
 * parity.</p>
 *
 * <h2>Port scope (P193)</h2>
 * <ul>
 *   <li>Only the property bag + {@link #reset()}/{@link #fromNBT}/{@link
 *       #toNBT} land here. The fire/reload state machine (P194), projectile
 *       entity (P195), networking (P196), rendering (P197) and GUI (P198)
 *       live in later phases.</li>
 *   <li><b>Morph slots are kept as raw {@link NbtCompound}</b> (not
 *       {@code mchorse.metamorph.api.AbstractMorph}) so this class does not
 *       depend on the parallel Metamorph-core branch. The legacy code parses
 *       each slot through {@code MorphManager.morphFromNBT} and re-serialises
 *       via {@code morph.toNBT}; here each slot round-trips as the exact
 *       compound it was read from. Morph normalisation / the P71 id shim is
 *       the morph reader's job and gets wired in when P197 swaps these raw
 *       compounds for real morphs.</li>
 *   <li><b>Transforms are kept as raw {@link NbtCompound}</b> for the same
 *       reason ({@code mchorse.blockbuster.api.ModelTransform} is owned by S5,
 *       a parallel branch). A non-empty compound is written under its key;
 *       an absent/empty one is omitted, matching the legacy
 *       {@code !isDefault()} write guard for the common cases.</li>
 *   <li>The runtime/render members ({@code shot()}, {@code update()},
 *       {@code render*()}, {@code createEntity}/{@code getEntity}, the
 *       {@code current*} working morphs, {@code target}, {@code renderLock},
 *       {@code lastState}) are deferred to P197 — they depend on the
 *       Metamorph {@code Morph} holder and the render pipeline.</li>
 * </ul>
 *
 * <p>Legacy source of truth:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/common/GunProps.java}.</p>
 */
public class GunProps
{
    /* Gun properties */
    public NbtCompound defaultMorph;
    public NbtCompound firingMorph;
    public String fireCommand;
    public int delay;
    public int projectiles;
    public float scatterX;
    public float scatterY;
    public boolean launch;
    public boolean useTarget;
    public ItemStack ammoStack = ItemStack.EMPTY;

    /* Evanechecssss' options */
    public boolean staticRecoil;
    public float recoilXMin;
    public float recoilXMax;
    public float recoilYMin;
    public float recoilYMax;

    public boolean enableArmsShootingPose;
    public boolean alwaysArmsShootingPose;

    public float shootingOffsetX;
    public float shootingOffsetY;
    public float shootingOffsetZ;

    public NbtCompound inventoryMorph;
    public NbtCompound crosshairMorph;
    public NbtCompound handsMorph;
    public NbtCompound reloadMorph;
    public NbtCompound zoomOverlayMorph;

    public boolean hideCrosshairOnZoom;
    public boolean useInventoryMorph;
    public boolean hideHandsOnZoom;
    public boolean useZoomOverlayMorph;

    public float zoomFactor;
    public int ammo;
    public boolean useReloading;
    public long reloadingTime;
    public long shotDelay;
    public boolean shootWhenHeld;

    public String destroyCommand;
    public String meleeCommand;
    public String reloadCommand;
    public String zoomOnCommand;
    public String zoomOffCommand;

    public float meleeDamage;
    public float mouseZoom;
    public int durability;
    public boolean preventLeftClick;
    public boolean preventRightClick;
    public boolean preventEntityAttack;

    public int storedAmmo;
    public long storedReloadingTime;
    public long storedShotDelay;
    public int storedDurability;
    public GunState state = GunState.READY_TO_SHOOT;

    /* Projectile properties */
    public NbtCompound projectileMorph;
    public String tickCommand;
    public int ticking;
    public int lifeSpan;
    public boolean yaw;
    public boolean pitch;
    public boolean sequencer;
    public boolean random;
    public float hitboxX;
    public float hitboxY;
    public float speed;
    public float friction;
    public float gravity;
    public int fadeIn;
    public int fadeOut;

    /* Impact properties */
    public NbtCompound impactMorph;
    public String impactCommand;
    public String impactEntityCommand;
    public int impactDelay;
    public boolean vanish;
    public boolean bounce;
    public boolean sticks;
    public int hits;
    public float damage;
    public float knockbackHorizontal;
    public float knockbackVertical;
    public float bounceFactor;
    public String vanishCommand;
    public int vanishDelay;
    public float penetration;
    public boolean ignoreBlocks;
    public boolean ignoreEntities;

    /* Transforms (raw compounds — S5 ModelTransform swaps in later) */
    public NbtCompound gunTransform;
    public NbtCompound gunTransformFirstPerson;
    public NbtCompound projectileTransform;

    /* ------------------------------------------------------------------ *
     * Runtime render state (P197)                                         *
     * ------------------------------------------------------------------ *
     *
     * These are the working morph copies + dummy-actor plumbing the client
     * render path needs. They are deliberately kept free of client-only
     * Minecraft classes (only the metamorph {@code Morph}/{@code AbstractMorph}
     * holders + the {@code EntityActor} dummy, all main-source) so this class
     * stays in the main source set; the GL draw itself lives in the client-only
     * {@code GunPropsRenderer} companion.
     *
     * Note the morph slots above ({@code defaultMorph} etc.) are raw
     * {@code NbtCompound} (P193 deferred the real morph swap); the working
     * copies here are parsed from those slots on demand through the bundled
     * Metamorph {@code MorphManager} (total reader — unknown morphs become the
     * placeholder morph, never a crash).
     */

    private int shoot = 0;
    private final Morph current = new Morph();
    private final Morph currentHands = new Morph();
    private final Morph currentInventory = new Morph();
    private final Morph currentZoomOverlay = new Morph();
    public final Morph currentCrosshair = new Morph();
    public boolean renderLock;

    public LivingEntity target;
    public GunState lastState = GunState.READY_TO_SHOOT;

    public GunProps()
    {
        this.reset();
    }

    public GunProps(NbtCompound tag)
    {
        this.fromNBT(tag);
    }

    /**
     * Reset properties to default values.
     *
     * <p>These defaults are format-bearing: {@link #toNBT()} omits any field
     * still equal to its default, so changing a default here silently changes
     * the emitted NBT.</p>
     */
    public void reset()
    {
        /* Gun properties */
        this.defaultMorph = null;
        this.handsMorph = null;
        this.inventoryMorph = null;
        this.reloadMorph = null;
        this.crosshairMorph = null;
        this.zoomOverlayMorph = null;
        this.firingMorph = null;
        this.fireCommand = "";
        this.delay = 0;
        this.projectiles = 1;
        /* McHorse: screw organizing this */
        this.storedAmmo = 1;
        this.reloadingTime = 0;
        this.storedShotDelay = 0;
        this.shotDelay = 0;
        this.ammo = 1;
        this.storedReloadingTime = 0;
        this.scatterX = this.scatterY = 0F;
        this.launch = false;
        this.useInventoryMorph = false;
        this.useTarget = false;
        this.ammoStack = ItemStack.EMPTY;
        this.zoomFactor = 0;
        this.recoilXMin = 0;
        this.shootingOffsetX = 0;
        this.mouseZoom = 0.5F;
        this.meleeDamage = 0;
        this.shootingOffsetY = 0;
        this.shootingOffsetZ = 0;
        this.staticRecoil = true;
        this.recoilXMax = 0;
        this.recoilYMin = 0;
        this.recoilYMax = 0;
        /* Projectile properties */
        this.projectileMorph = null;
        this.tickCommand = "";
        this.zoomOffCommand = "";
        this.zoomOnCommand = "";
        this.reloadCommand = "";
        this.meleeCommand = "";
        this.destroyCommand = "";
        this.ticking = 0;
        this.durability = 0;
        this.storedDurability = 0;
        this.lifeSpan = 200;
        this.yaw = true;
        this.useZoomOverlayMorph = false;
        this.hideHandsOnZoom = false;
        this.hideCrosshairOnZoom = false;
        this.enableArmsShootingPose = false;
        this.preventRightClick = false;
        this.preventLeftClick = false;
        this.preventEntityAttack = false;
        this.shootWhenHeld = true;
        this.alwaysArmsShootingPose = false;
        this.pitch = true;
        this.sequencer = false;
        this.random = false;
        this.hitboxX = 0.25F;
        this.hitboxY = 0.25F;
        this.speed = 1.0F;
        this.friction = 0.99F;
        this.gravity = 0.03F;
        this.fadeIn = this.fadeOut = 10;

        /* Impact properties */
        this.impactMorph = null;
        this.impactCommand = "";
        this.impactEntityCommand = "";
        this.impactDelay = 0;
        this.vanish = true;
        this.bounce = false;
        this.sticks = false;
        this.hits = 1;
        this.state = GunState.READY_TO_SHOOT;
        this.damage = 0F;
        this.knockbackHorizontal = 0F;
        this.knockbackVertical = 0F;
        this.bounceFactor = 1F;
        this.vanishCommand = "";
        this.vanishDelay = 0;
        this.penetration = 0;
        this.ignoreBlocks = false;
        this.ignoreEntities = false;

        /* Transforms */
        this.gunTransform = null;
        this.gunTransformFirstPerson = null;
        this.projectileTransform = null;
    }

    public void fromNBT(NbtCompound tag)
    {
        this.reset();

        /* Gun properties */
        this.defaultMorph = this.create(tag, "Morph");
        this.handsMorph = this.create(tag, "HandsMorph");
        this.inventoryMorph = this.create(tag, "InventoryMorph");
        this.reloadMorph = this.create(tag, "ReloadMorph");
        this.crosshairMorph = this.create(tag, "CrosshairMorph");
        this.zoomOverlayMorph = this.create(tag, "ZoomOverlayMorph");
        this.firingMorph = this.create(tag, "Fire");

        if (tag.contains("FireCommand")) this.fireCommand = tag.getString("FireCommand");
        if (tag.contains("Delay")) this.delay = tag.getInt("Delay");

        if (tag.contains("Projectiles")) this.projectiles = tag.getInt("Projectiles");
        if (tag.contains("StoredReloadingTime")) this.storedReloadingTime = getIntCoerced(tag, "StoredReloadingTime");

        if (tag.contains("Scatter"))
        {
            NbtElement scatter = tag.get("Scatter");

            if (scatter instanceof NbtList)
            {
                NbtList list = (NbtList) scatter;

                if (list.size() >= 2)
                {
                    this.scatterX = list.getFloat(0);
                    this.scatterY = list.getFloat(1);
                }
            }
            else
            {
                /* Old compatibility scatter */
                this.scatterX = this.scatterY = tag.getFloat("Scatter");
            }
        }
        if (tag.contains("ScatterY")) this.scatterY = tag.getFloat("ScatterY");
        if (tag.contains("Launch")) this.launch = tag.getBoolean("Launch");
        if (tag.contains("UseInventoryMorph")) this.useInventoryMorph = tag.getBoolean("UseInventoryMorph");
        if (tag.contains("UseReloading")) this.useReloading = tag.getBoolean("UseReloading");


        if (tag.contains("Target")) this.useTarget = tag.getBoolean("Target");
        if (tag.contains("AmmoStack")) this.ammoStack = ItemStack.fromNbt(tag.getCompound("AmmoStack"));

        /* Projectile properties */
        this.projectileMorph = this.create(tag, "Projectile");
        if (tag.contains("TickCommand")) this.tickCommand = tag.getString("TickCommand");
        if (tag.contains("MeleeCommand")) this.meleeCommand = tag.getString("MeleeCommand");
        if (tag.contains("DestroyCommand")) this.destroyCommand = tag.getString("DestroyCommand");
        if (tag.contains("ReloadCommand")) this.reloadCommand = tag.getString("ReloadCommand");
        if (tag.contains("ZoomOnCommand")) this.zoomOnCommand = tag.getString("ZoomOnCommand");
        if (tag.contains("ZoomOffCommand")) this.zoomOffCommand = tag.getString("ZoomOffCommand");


        if (tag.contains("Ticking")) this.ticking = tag.getInt("Ticking");
        if (tag.contains("StoredAmmo")) this.storedAmmo = tag.getInt("StoredAmmo");
        if (tag.contains("ReloadingTime")) this.reloadingTime = getIntCoerced(tag, "ReloadingTime");
        if (tag.contains("StoredShotDelay")) this.storedShotDelay = getLongCoerced(tag, "StoredShotDelay");
        if (tag.contains("ShotDelay")) this.shotDelay = getLongCoerced(tag, "ShotDelay");


        if (tag.contains("Ammo")) this.ammo = tag.getInt("Ammo");

        if (tag.contains("LifeSpan")) this.lifeSpan = tag.getInt("LifeSpan");
        if (tag.contains("Yaw")) this.yaw = tag.getBoolean("Yaw");
        if (tag.contains("UseZoomOverlayMorph")) this.useZoomOverlayMorph = tag.getBoolean("UseZoomOverlayMorph");
        if (tag.contains("HideHandsOnZoom")) this.hideHandsOnZoom = tag.getBoolean("HideHandsOnZoom");
        if (tag.contains("HideCrosshairOnZoom")) this.hideCrosshairOnZoom = tag.getBoolean("HideCrosshairOnZoom");


        if (tag.contains("ShootWhenHeld")) this.shootWhenHeld = tag.getBoolean("ShootWhenHeld");


        if (tag.contains("ArmPose")) this.enableArmsShootingPose = tag.getBoolean("ArmPose");
        if (tag.contains("ArmPoseAlways")) this.alwaysArmsShootingPose = tag.getBoolean("ArmPoseAlways");
        if (tag.contains("PreventLeftClick")) this.preventLeftClick = tag.getBoolean("PreventLeftClick");
        if (tag.contains("PreventRightClick")) this.preventRightClick = tag.getBoolean("PreventRightClick");
        if (tag.contains("PreventEntityAttack")) this.preventEntityAttack = tag.getBoolean("PreventEntityAttack");


        if (tag.contains("Pitch")) this.pitch = tag.getBoolean("Pitch");
        if (tag.contains("Sequencer")) this.sequencer = tag.getBoolean("Sequencer");
        if (tag.contains("Random")) this.random = tag.getBoolean("Random");
        if (tag.contains("HX")) this.hitboxX = tag.getFloat("HX");
        if (tag.contains("HY")) this.hitboxY = tag.getFloat("HY");
        if (tag.contains("Speed")) this.speed = tag.getFloat("Speed");
        if (tag.contains("Zoom")) this.zoomFactor = tag.getFloat("Zoom");
        if (tag.contains("RecoilMinX")) this.recoilXMin = tag.getFloat("RecoilMinX");
        if (tag.contains("ShootingOffsetX")) this.shootingOffsetX = tag.getFloat("ShootingOffsetX");
        if (tag.contains("MouseZoom")) this.mouseZoom = tag.getFloat("MouseZoom");
        if (tag.contains("MeleeDamage")) this.meleeDamage = tag.getFloat("MeleeDamage");


        if (tag.contains("ShootingOffsetY")) this.shootingOffsetY = tag.getFloat("ShootingOffsetY");
        if (tag.contains("ShootingOffsetZ")) this.shootingOffsetZ = tag.getFloat("ShootingOffsetZ");


        if (tag.contains("StaticRecoil")) this.staticRecoil = tag.getBoolean("StaticRecoil");

        if (tag.contains("RecoilMaxX")) this.recoilXMax = tag.getFloat("RecoilMaxX");
        if (tag.contains("RecoilMinY")) this.recoilYMin = tag.getFloat("RecoilMinY");
        if (tag.contains("RecoilMaxY")) this.recoilYMax = tag.getFloat("RecoilMaxY");

        if (tag.contains("Friction")) this.friction = tag.getFloat("Friction");
        if (tag.contains("Gravity")) this.gravity = tag.getFloat("Gravity");

        if (tag.contains("Durability")) this.durability = tag.getInt("Durability");
        if (tag.contains("StoredDurability")) this.storedDurability = tag.getInt("StoredDurability");


        if (tag.contains("FadeIn")) this.fadeIn = tag.getInt("FadeIn");
        if (tag.contains("FadeOut")) this.fadeOut = tag.getInt("FadeOut");
        /* Impact properties */
        this.impactMorph = this.create(tag, "Impact");
        if (tag.contains("ImpactCommand")) this.impactCommand = tag.getString("ImpactCommand");
        if (tag.contains("ImpactEntityCommand")) this.impactEntityCommand = tag.getString("ImpactEntityCommand");
        if (tag.contains("ImpactDelay")) this.impactDelay = tag.getInt("ImpactDelay");
        if (tag.contains("Vanish")) this.vanish = tag.getBoolean("Vanish");
        if (tag.contains("Bounce")) this.bounce = tag.getBoolean("Bounce");
        if (tag.contains("Stick")) this.sticks = tag.getBoolean("Stick");
        if (tag.contains("Hits")) this.hits = tag.getInt("Hits");

        if (tag.contains("State")) this.state = stateFromOrdinal(tag.getInt("State"));

        if (tag.contains("Damage")) this.damage = tag.getFloat("Damage");
        if (tag.contains("KnockbackH")) this.knockbackHorizontal = tag.getFloat("KnockbackH");
        if (tag.contains("KnockbackV")) this.knockbackVertical = tag.getFloat("KnockbackV");
        if (tag.contains("BFactor")) this.bounceFactor = tag.getFloat("BFactor");
        if (tag.contains("VanishCommand")) this.vanishCommand = tag.getString("VanishCommand");
        if (tag.contains("VDelay")) this.vanishDelay = tag.getInt("VDelay");
        if (tag.contains("Penetration")) this.penetration = tag.getFloat("Penetration");
        if (tag.contains("IBlocks")) this.ignoreBlocks = tag.getBoolean("IBlocks");
        if (tag.contains("IEntities")) this.ignoreEntities = tag.getBoolean("IEntities");

        /* Transforms */
        if (tag.contains("Gun", NbtElement.COMPOUND_TYPE)) this.gunTransform = tag.getCompound("Gun").copy();
        if (tag.contains("GunFirstPerson", NbtElement.COMPOUND_TYPE)) this.gunTransformFirstPerson = tag.getCompound("GunFirstPerson").copy();
        if (tag.contains("Transform", NbtElement.COMPOUND_TYPE)) this.projectileTransform = tag.getCompound("Transform").copy();

        /* Client-side working-morph rebuild (P197). Legacy guarded this with
         * FMLCommonHandler.getSide() == CLIENT; the Fabric equivalent is the
         * environment type. Minecraft keeps replacing the client's itemstack so
         * the working copies are never updated in place — every re-parse
         * rebuilds them from the (freshly-read) slots, and P196's
         * ClientHandlerGunInfo transplants only state back into the render
         * cache so animation continuity survives. */
        if (isClient())
        {
            try
            {
                if (this.state == GunState.RELOADING)
                {
                    this.current.set(this.morphFrom(this.reloadMorph));
                }
                else
                {
                    this.current.set(this.morphFrom(this.defaultMorph));
                }

                this.currentHands.set(this.morphFrom(this.handsMorph));
                this.currentZoomOverlay.set(this.morphFrom(this.zoomOverlayMorph));
                this.currentInventory.set(this.morphFrom(this.inventoryMorph));
                this.currentCrosshair.set(this.morphFrom(this.crosshairMorph));
            }
            catch (Exception e)
            {
                /* Totality: a malformed morph slot must not break gun parsing. */
                Blockbuster.LOGGER.warn("GunProps: failed to build client working morphs", e);
            }
        }
    }

    /**
     * Injectable environment check (legacy {@code FMLCommonHandler.getSide() ==
     * CLIENT}). A supplier so headless tests can exercise both branches
     * deterministically; defaults to the real Fabric environment.
     */
    public static BooleanSupplier clientEnvironment =
        () -> FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;

    private static boolean isClient()
    {
        return clientEnvironment.getAsBoolean();
    }

    /**
     * Parse a raw morph slot into a live {@link AbstractMorph} through the
     * bundled Metamorph {@code MorphManager} (total reader; unknown {@code Name}
     * tags become the placeholder morph). {@code morphFromNBT} mutates the tag
     * (id remap) so a copy is passed. Null slot → null morph.
     */
    private AbstractMorph morphFrom(NbtCompound slot)
    {
        if (slot == null)
        {
            return null;
        }

        return MorphManager.INSTANCE.morphFromNBT(slot.copy());
    }

    /**
     * Read a slot morph as a raw compound (P193 keeps morphs as NBT). Legacy
     * only accepts a {@code TAG_COMPOUND} at the key; anything else yields
     * {@code null}.
     */
    private NbtCompound create(NbtCompound tag, String key)
    {
        if (tag.contains(key, NbtElement.COMPOUND_TYPE))
        {
            return tag.getCompound(key).copy();
        }

        return null;
    }

    /* ------------------------------------------------------------------ *
     * Runtime render state accessors + drivers (P197)                     *
     * ------------------------------------------------------------------ */

    /** The working "current" morph (default/firing/reload, swapped over time). */
    public Morph getCurrent()
    {
        return this.current;
    }

    /**
     * The remaining firing-morph countdown (ticks until the working morph
     * reverts to the default). Exposed for render diagnostics / tests.
     */
    public int getShoot()
    {
        return this.shoot;
    }

    public Morph getCurrentHands()
    {
        return this.currentHands;
    }

    public Morph getCurrentInventory()
    {
        return this.currentInventory;
    }

    public Morph getCurrentZoomOverlay()
    {
        return this.currentZoomOverlay;
    }

    /* GuiGun (P198) morph-slot writers into the working copies. */

    public void setCurrent(AbstractMorph morph)
    {
        this.current.setDirect(morph);
    }

    public void setCurrentZoomOverlay(AbstractMorph morph)
    {
        this.currentZoomOverlay.setDirect(morph);
    }

    public void setHandsMorph(AbstractMorph morph)
    {
        this.currentHands.setDirect(morph);
    }

    public void setCrosshairMorph(AbstractMorph morph)
    {
        this.currentCrosshair.setDirect(morph);
    }

    public void setInventoryMorph(AbstractMorph morph)
    {
        this.currentInventory.setDirect(morph);
    }

    /**
     * Kick the firing animation: arms the {@code shoot} countdown from
     * {@code delay} and swaps the working morph to a copy of the firing morph.
     * A zero {@code delay} is a no-op (legacy early-return).
     */
    public void shot()
    {
        if (this.delay <= 0)
        {
            return;
        }

        this.shoot = this.delay;
        this.current.set(MorphUtils.copy(this.morphFrom(this.firingMorph)));
    }

    /**
     * Lazily create the dummy {@link EntityActor} used as the render/animation
     * target for the gun morphs. Idempotent (legacy no-op when already made).
     */
    public void createEntity(World world)
    {
        if (this.target != null)
        {
            return;
        }

        EntityActor actor = new EntityActor(world);

        actor.setOnGround(true);
        actor.setYaw(0);
        actor.prevYaw = 0;
        actor.headYaw = actor.prevHeadYaw = 0;
        actor.setPitch(0);
        actor.prevPitch = 0;

        this.target = actor;
    }

    /**
     * Sync the dummy actor to the projectile's position so its morph animates
     * at the projectile's location (legacy {@code getEntity}).
     */
    public LivingEntity getEntity(EntityGunProjectile entity)
    {
        if (this.target != null)
        {
            this.target.prevX = entity.prevX;
            this.target.prevY = entity.prevY;
            this.target.prevZ = entity.prevZ;

            this.target.setPos(entity.getX(), entity.getY(), entity.getZ());
        }

        return this.target;
    }

    /**
     * Reset the dummy actor's transform before a morph render, matching the
     * legacy {@code setupEntity} (guards against a morph's update mutating the
     * shared dummy).
     */
    public void setupEntity()
    {
        if (this.target == null)
        {
            return;
        }

        this.target.refreshPositionAndAngles(0, 0, 0, 0, 0);
        this.target.headYaw = this.target.prevHeadYaw = 0;
        this.target.setYaw(0);
        this.target.prevYaw = 0;
        this.target.setPitch(0);
        this.target.prevPitch = 0;
        this.target.bodyYaw = this.target.prevBodyYaw = 0;
        this.target.setVelocity(0, 0, 0);
    }

    /**
     * Per-client-tick working-morph update (legacy {@code update}). Advances the
     * dummy actor's animation clock, swaps default↔reload on the RELOADING state
     * edge (via {@code lastState}), updates every non-empty working morph, and
     * runs down the {@code shoot} firing countdown — reverting to the default
     * morph when it hits zero. Driven from the client cache pump (P197), never
     * from render.
     */
    public void update()
    {
        if (this.target != null)
        {
            this.target.age++;

            boolean lastReload = this.lastState == GunState.RELOADING;
            boolean currentReload = this.state == GunState.RELOADING;

            if (currentReload != lastReload)
            {
                this.current.set(currentReload ? this.morphFrom(this.reloadMorph) : this.morphFrom(this.defaultMorph));
            }

            this.lastState = this.state;

            AbstractMorph morph = this.current.get();

            if (morph != null)
            {
                morph.update(this.target);
            }

            if (!this.currentCrosshair.isEmpty())
            {
                this.currentCrosshair.get().update(this.target);
            }

            if (!this.currentHands.isEmpty())
            {
                this.currentHands.get().update(this.target);
            }

            if (!this.currentInventory.isEmpty())
            {
                this.currentInventory.get().update(this.target);
            }

            if (!this.currentZoomOverlay.isEmpty())
            {
                this.currentZoomOverlay.get().update(this.target);
            }
        }

        if (this.shoot > 0)
        {
            this.shoot--;

            if (this.shoot == 0)
            {
                this.current.set(MorphUtils.copy(this.morphFrom(this.defaultMorph)));
            }
        }
    }

    public NbtCompound toNBT()
    {
        NbtCompound tag = new NbtCompound();

        /* Gun properties */
        if (this.defaultMorph != null) tag.put("Morph", this.defaultMorph.copy());
        if (this.firingMorph != null) tag.put("Fire", this.firingMorph.copy());
        if (!this.fireCommand.isEmpty()) tag.putString("FireCommand", this.fireCommand);
        if (this.delay != 0) tag.putInt("Delay", this.delay);
        if (this.projectiles != 1) tag.putInt("Projectiles", this.projectiles);
        if (this.scatterX != 0F || this.scatterY != 0F)
        {
            NbtList scatter = new NbtList();

            scatter.add(NbtFloat.of(this.scatterX));
            scatter.add(NbtFloat.of(this.scatterY));

            tag.put("Scatter", scatter);
        }
        if (this.launch) tag.putBoolean("Launch", this.launch);
        if (this.useTarget) tag.putBoolean("Target", this.useTarget);
        if (!this.ammoStack.isEmpty()) tag.put("AmmoStack", this.ammoStack.writeNbt(new NbtCompound()));

        /* Evanechecssss' options */
        if (!this.staticRecoil) tag.putBoolean("StaticRecoil", this.staticRecoil);
        if (this.recoilXMin != 0) tag.putFloat("RecoilMinX", this.recoilXMin);
        if (this.recoilXMax != 0) tag.putFloat("RecoilMaxX", this.recoilXMax);
        if (this.recoilYMin != 0) tag.putFloat("RecoilMinY", this.recoilYMin);
        if (this.recoilYMax != 0) tag.putFloat("RecoilMaxY", this.recoilYMax);

        if (this.enableArmsShootingPose) tag.putBoolean("ArmPose", this.enableArmsShootingPose);
        if (this.alwaysArmsShootingPose) tag.putBoolean("ArmPoseAlways", this.alwaysArmsShootingPose);

        if (this.shootingOffsetX != 0) tag.putFloat("ShootingOffsetX", this.shootingOffsetX);
        if (this.shootingOffsetY != 0) tag.putFloat("ShootingOffsetY", this.shootingOffsetY);
        if (this.shootingOffsetZ != 0) tag.putFloat("ShootingOffsetZ", this.shootingOffsetZ);

        if (this.inventoryMorph != null) tag.put("InventoryMorph", this.inventoryMorph.copy());
        if (this.crosshairMorph != null) tag.put("CrosshairMorph", this.crosshairMorph.copy());
        if (this.handsMorph != null) tag.put("HandsMorph", this.handsMorph.copy());
        if (this.reloadMorph != null) tag.put("ReloadMorph", this.reloadMorph.copy());
        if (this.zoomOverlayMorph != null) tag.put("ZoomOverlayMorph", this.zoomOverlayMorph.copy());

        if (this.hideCrosshairOnZoom) tag.putBoolean("HideCrosshairOnZoom", this.hideCrosshairOnZoom);
        if (this.useInventoryMorph) tag.putBoolean("UseInventoryMorph", this.useInventoryMorph);
        if (this.hideHandsOnZoom) tag.putBoolean("HideHandsOnZoom", this.hideHandsOnZoom);
        if (this.useZoomOverlayMorph) tag.putBoolean("UseZoomOverlayMorph", this.useZoomOverlayMorph);

        if (this.zoomFactor != 0) tag.putFloat("Zoom", this.zoomFactor);
        if (this.ammo != 1) tag.putInt("Ammo", this.ammo);
        if (this.useReloading) tag.putBoolean("UseReloading", this.useReloading);
        if (this.reloadingTime != 0) tag.putLong("ReloadingTime", this.reloadingTime);
        if (this.shotDelay != 0) tag.putLong("ShotDelay", this.shotDelay);
        if (!this.shootWhenHeld) tag.putBoolean("ShootWhenHeld", this.shootWhenHeld);

        if (!this.destroyCommand.isEmpty()) tag.putString("DestroyCommand", this.destroyCommand);
        if (!this.meleeCommand.isEmpty()) tag.putString("MeleeCommand", this.meleeCommand);
        if (!this.reloadCommand.isEmpty()) tag.putString("ReloadCommand", this.reloadCommand);
        if (!this.zoomOnCommand.isEmpty()) tag.putString("ZoomOnCommand", this.zoomOnCommand);
        if (!this.zoomOffCommand.isEmpty()) tag.putString("ZoomOffCommand", this.zoomOffCommand);

        if (this.meleeDamage != 0) tag.putFloat("MeleeDamage", this.meleeDamage);
        if (this.mouseZoom != 0.5F) tag.putFloat("MouseZoom", this.mouseZoom);
        if (this.durability != 0) tag.putInt("Durability", this.durability);
        if (this.preventLeftClick) tag.putBoolean("PreventLeftClick", this.preventLeftClick);
        if (this.preventRightClick) tag.putBoolean("PreventRightClick", this.preventRightClick);
        if (this.preventEntityAttack) tag.putBoolean("PreventEntityAttack", this.preventEntityAttack);

        if (this.storedAmmo != 1) tag.putInt("StoredAmmo", this.storedAmmo);
        if (this.storedReloadingTime != 0) tag.putLong("StoredReloadingTime", this.storedReloadingTime);
        if (this.storedShotDelay != 0) tag.putLong("StoredShotDelay", this.storedShotDelay);
        if (this.storedDurability != 0) tag.putInt("StoredDurability", this.storedDurability);
        if (this.state != GunState.READY_TO_SHOOT) tag.putInt("State", this.state.ordinal());

        /* Projectile properties */
        if (this.projectileMorph != null) tag.put("Projectile", this.projectileMorph.copy());
        if (!this.tickCommand.isEmpty()) tag.putString("TickCommand", this.tickCommand);
        if (this.ticking != 0) tag.putInt("Ticking", this.ticking);
        if (this.lifeSpan != 200) tag.putInt("LifeSpan", this.lifeSpan);
        if (!this.yaw) tag.putBoolean("Yaw", this.yaw);
        if (!this.pitch) tag.putBoolean("Pitch", this.pitch);
        if (this.sequencer) tag.putBoolean("Sequencer", this.sequencer);
        if (this.random) tag.putBoolean("Random", this.random);
        if (this.hitboxX != 0.25F) tag.putFloat("HX", this.hitboxX);
        if (this.hitboxY != 0.25F) tag.putFloat("HY", this.hitboxY);
        if (this.speed != 1.0F) tag.putFloat("Speed", this.speed);
        if (this.friction != 0.99F) tag.putFloat("Friction", this.friction);
        if (this.gravity != 0.03F) tag.putFloat("Gravity", this.gravity);
        if (this.fadeIn != 10) tag.putInt("FadeIn", this.fadeIn);
        if (this.fadeOut != 10) tag.putInt("FadeOut", this.fadeOut);

        /* Impact properties */
        if (this.impactMorph != null) tag.put("Impact", this.impactMorph.copy());
        if (!this.impactCommand.isEmpty()) tag.putString("ImpactCommand", this.impactCommand);
        if (!this.impactEntityCommand.isEmpty()) tag.putString("ImpactEntityCommand", this.impactEntityCommand);
        if (this.impactDelay != 0) tag.putInt("ImpactDelay", this.impactDelay);
        if (!this.vanish) tag.putBoolean("Vanish", this.vanish);
        if (this.bounce) tag.putBoolean("Bounce", this.bounce);
        if (this.sticks) tag.putBoolean("Stick", this.sticks);
        if (this.hits != 1) tag.putInt("Hits", this.hits);
        if (this.damage != 0) tag.putFloat("Damage", this.damage);
        if (this.knockbackHorizontal != 0F) tag.putFloat("KnockbackH", this.knockbackHorizontal);
        if (this.knockbackVertical != 0F) tag.putFloat("KnockbackV", this.knockbackVertical);
        if (this.bounceFactor != 1F) tag.putFloat("BFactor", this.bounceFactor);
        if (!this.vanishCommand.isEmpty()) tag.putString("VanishCommand", this.vanishCommand);
        if (this.vanishDelay != 0) tag.putInt("VDelay", this.vanishDelay);
        if (this.penetration != 0) tag.putFloat("Penetration", this.penetration);
        if (this.ignoreBlocks) tag.putBoolean("IBlocks", this.ignoreBlocks);
        if (this.ignoreEntities) tag.putBoolean("IEntities", this.ignoreEntities);

        /* Transforms — write a non-empty compound only (an empty/default
         * transform is omitted, matching the legacy !isDefault() guard). */
        if (this.gunTransform != null && !this.gunTransform.isEmpty()) tag.put("Gun", this.gunTransform.copy());
        if (this.gunTransformFirstPerson != null && !this.gunTransformFirstPerson.isEmpty()) tag.put("GunFirstPerson", this.gunTransformFirstPerson.copy());
        if (this.projectileTransform != null && !this.projectileTransform.isEmpty()) tag.put("Transform", this.projectileTransform.copy());

        return tag;
    }

    /**
     * Total-reader mapping of a stored {@code "State"} ordinal to a
     * {@link GunState}. Legacy did {@code GunState.values()[ordinal]}, which
     * crashes on a corrupt ordinal; per the cross-cutting total-reader rule an
     * out-of-range value logs a warning and falls back to
     * {@link GunState#READY_TO_SHOOT}. Valid ordinals (0/1/2) are unchanged.
     */
    private static GunState stateFromOrdinal(int ordinal)
    {
        GunState[] values = GunState.values();

        if (ordinal < 0 || ordinal >= values.length)
        {
            Blockbuster.LOGGER.warn("GunProps: invalid State ordinal {}, defaulting to READY_TO_SHOOT", ordinal);

            return GunState.READY_TO_SHOOT;
        }

        return values[ordinal];
    }

    /**
     * Read a numeric tag as an int regardless of its stored tag type,
     * reproducing 1.12.2's {@code NBTTagCompound.getInteger} coercion. Needed
     * because {@code ReloadingTime}/{@code StoredReloadingTime} are written
     * with {@code setLong} but read with {@code getInteger} in legacy — a
     * silent coercion the 1.20.4 API does not always guarantee for a plain
     * {@code getInt}. Returns 0 for a non-numeric/absent tag.
     */
    private static long getIntCoerced(NbtCompound tag, String key)
    {
        NbtElement element = tag.get(key);

        if (element instanceof AbstractNbtNumber number)
        {
            return number.intValue();
        }

        return 0;
    }

    /**
     * Read a numeric tag as a long regardless of its stored tag type,
     * reproducing 1.12.2's {@code NBTTagCompound.getLong} coercion. Returns 0
     * for a non-numeric/absent tag.
     */
    private static long getLongCoerced(NbtCompound tag, String key)
    {
        NbtElement element = tag.get(key);

        if (element instanceof AbstractNbtNumber number)
        {
            return number.longValue();
        }

        return 0;
    }
}
