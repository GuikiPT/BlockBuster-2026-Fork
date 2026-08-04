package mchorse.metamorph.entity;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.models.IMorphProvider;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.network.Dispatcher;
import mchorse.metamorph.network.common.PacketMorphSpawnData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Morph ghost entity (roadmap P56.1) — {@code metamorph:morph}.
 *
 * <p>The floating, spinning, cyan pickup a player walks into to acquire the
 * morph of something they killed. Legacy's own comment: "This entity is similar
 * to {@code EntityXPOrb} or {@code EntityItem}, in terms of picking up."</p>
 *
 * <p><b>Not to be confused with</b>
 * {@link mchorse.metamorph.api.morphs.EntityMorph}, the <i>morph</i> that
 * disguises you as an entity. Legacy carries both classes under the same simple
 * name and the port keeps them, package-for-package, for diff-ability against
 * {@code .tools/legacy-src}.</p>
 *
 * <p><b>Lifecycle.</b> Two independent counters, and the order between them is
 * load-bearing:</p>
 * <ul>
 *   <li>{@link #timer} — a 30-tick spawn grace during which the ghost does
 *   <i>nothing</i>: no owner search, no lifetime decay, no pickup. It is what
 *   stops the killer from hoovering the ghost out of the air the instant it
 *   appears, and the renderer reads the same counter for its fade-in.</li>
 *   <li>{@link #lifetime} — 2400 ticks (2 minutes) by default, decremented
 *   only once the grace is over. <b>Negative means immortal</b>: a
 *   {@code /summon metamorph:morph} ghost with {@code LifeTime:-1} is a
 *   permanent decoration, and the {@code lifetime == 0} kill branch is written
 *   so that a negative value never reaches it.</li>
 * </ul>
 *
 * <p><b>Ownership.</b> A ghost spawned from a kill belongs to the killer by
 * UUID and only they can pick it up. A ghost with no owner
 * ({@link #ownerless}, the {@code /summon} default) is first-come-first-served
 * — the first player whose box intersects it gets the morph. A legacy
 * {@code Username} tag resolves by name instead of UUID; writing one flips the
 * ghost out of ownerless. The whole matrix is
 * {@link #resolveOwner(List, Function, Function, UUID, String) resolveOwner},
 * pure and headless-tested.</p>
 *
 * <p><b>1.20.4 mappings.</b> {@code onUpdate} → {@link #tick()};
 * {@code setDead} → {@link #discard()}; {@code motionX/motionZ = 0} →
 * {@link #setVelocity} on the current velocity's Y only;
 * {@code setSize(w, h)} → a cached {@link EntityDimensions} served from
 * {@link #getDimensions(EntityPose)} (1.20.4 sizes entities from their type, so
 * the clamp lives here and {@code calculateDimensions()} republishes it);
 * {@code collideWithNearbyEntities} → {@link #tickCramming()};
 * {@code SPacketCollectItem} → {@link ItemPickupAnimationS2CPacket};
 * {@code getEntityTracker().sendToTracking} →
 * {@code ServerWorld.getChunkManager().sendToOtherNearbyPlayers}.</p>
 *
 * <p><b>Spawn data.</b> Forge's {@code IEntityAdditionalSpawnData} carried the
 * owner UUID + morph alongside the vanilla spawn packet. Yarn's
 * {@code EntitySpawnS2CPacket} has no payload slot, so the port sends
 * {@link PacketMorphSpawnData} from {@link #onStartedTrackingBy} — the same
 * approach {@code PacketActorSpawnData} / {@code PacketGunProjectileSpawnData}
 * take. Without it the ghost renders as nothing at all: its appearance
 * <i>is</i> its morph.</p>
 *
 * <p><b>Recorded deviation.</b> Legacy registered the entity with
 * {@code registerModEntity(..., 64, 3, false)} — the trailing {@code false}
 * being "no velocity updates". 1.20.4's {@code EntityType.Builder} has no such
 * toggle; the ghost zeroes its own horizontal velocity every tick and never
 * moves, so the tracker has nothing to send either way.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/entity/EntityMorph.java
 */
public class EntityMorph extends LivingEntity implements IMorphProvider
{
    /** Legacy {@code timer = 30}: the spawn grace, in ticks. */
    public static final int GRACE = 30;

    /** Legacy {@code lifetime = 2400}: two minutes. */
    public static final int DEFAULT_LIFETIME = 2400;

    /** Legacy {@code setSize} clamps: no ghost is ever bigger than this. */
    public static final float MAX_WIDTH = 1.5F;
    public static final float MAX_HEIGHT = 2.0F;

    /**
     * The type's own box, and the fallback while the ghost has no morph.
     * 1.12.2's {@code Entity} constructor set exactly this.
     */
    public static final EntityDimensions DEFAULT_SIZE = EntityDimensions.changing(0.6F, 1.8F);

    /** Legacy custom name tag — command-block workflows select on it. */
    public static final String NAME_TAG = "Morph";

    private String username;
    private UUID owner;
    private boolean ownerless = true;

    /** Resolved owner, cached across ticks exactly as legacy did. */
    private PlayerEntity player;

    public int timer = GRACE;
    public int lifetime = DEFAULT_LIFETIME;
    public AbstractMorph morph;

    private EntityDimensions size = DEFAULT_SIZE;

    public EntityMorph(EntityType<? extends LivingEntity> type, World world)
    {
        super(type, world);

        this.setInvulnerable(true);
        this.setCustomName(Text.literal(NAME_TAG));
    }

    /**
     * Legacy's second constructor ({@code World, UUID, AbstractMorph}), as an
     * initializer — 1.20.4 fixes the entity constructor signature to
     * {@code (EntityType, World)}, so the owner/morph pair is applied after
     * construction.
     */
    public EntityMorph initialize(UUID owner, AbstractMorph morph)
    {
        this.owner = owner;
        this.morph = morph;
        this.ownerless = false;

        this.setSize(morph);

        return this;
    }

    @Override
    public AbstractMorph getMorph()
    {
        return this.morph;
    }

    public UUID getOwner()
    {
        return this.owner;
    }

    public String getUsername()
    {
        return this.username;
    }

    public boolean isOwnerless()
    {
        return this.ownerless;
    }

    /**
     * Legacy {@code getDisplayName}: the morph's own translation key, so a
     * ghost of a creeper reads "Creeper" rather than "Morph".
     */
    @Override
    public Text getDisplayName()
    {
        if (this.morph != null)
        {
            return Text.translatable("entity." + this.morph.name + ".name");
        }

        return super.getDisplayName();
    }

    /**
     * Legacy {@code setSize(AbstractMorph)} — a null morph leaves the previous
     * box alone (legacy's {@code if (morph != null)} guard).
     *
     * <p>1.12.2's {@code setSize} just assigned {@code width}/{@code height};
     * on 1.20.4 the dimensions are served from the type unless the entity
     * overrides {@link #getDimensions(EntityPose)}, so the new box has to be
     * republished through {@code calculateDimensions()}. That call reads the
     * world (it re-resolves collisions when an entity grows), which is why it
     * is skipped for the world-less instances headless tests build.</p>
     */
    public void setSize(AbstractMorph morph)
    {
        if (morph != null)
        {
            this.size = clampSize(morph.getWidth(this), morph.getHeight(this));

            if (this.getWorld() != null)
            {
                this.calculateDimensions();
            }
        }
    }

    /**
     * The clamp, pure: no ghost exceeds 1.5 wide × 2.0 tall however big the
     * morph it carries is (an ender-dragon ghost would otherwise be a
     * building-sized pickup box).
     */
    public static EntityDimensions clampSize(float width, float height)
    {
        return EntityDimensions.changing(
            MathHelper.clamp(width, 0F, MAX_WIDTH),
            MathHelper.clamp(height, 0F, MAX_HEIGHT));
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose)
    {
        return this.size;
    }

    /* Legacy canTriggerWalking() == false: no step sounds, no block triggers. */

    @Override
    public boolean isCollidable()
    {
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    /**
     * Not raycast-targetable — you cannot look "at" a ghost, only walk into it.
     *
     * <p>1.12.2 got this for free: {@code Minecraft.getMouseOver} filtered on
     * {@code canBeCollidedWith()}, so the ghost's {@code false} kept it out of
     * {@code pointedEntity} entirely. 1.20.4 splits the two — collision is
     * {@link #isCollidable()}, mouse-over is this — so without the override the
     * ghost would become targetable and, through {@code RenderMorph.hasLabel},
     * start showing a nameplate that 1.12.2 <b>never</b> showed. (Legacy's own
     * {@code canRenderName} is unreachable for exactly this reason; it is
     * ported anyway, for diff-ability.)</p>
     */
    @Override
    public boolean canHit()
    {
        return false;
    }

    /** Legacy {@code collideWithNearbyEntities()}, emptied. */
    @Override
    protected void tickCramming()
    {}

    @Override
    protected void pushAway(Entity entity)
    {}

    /**
     * Legacy {@code onUpdate}: hold still, burn the grace timer, then age and
     * look for the owner.
     */
    @Override
    public void tick()
    {
        /* Legacy: "Don't allow it move horizontally" */
        this.setVelocity(0, this.getVelocity().y, 0);

        super.tick();

        if (this.timer > 0)
        {
            this.timer--;

            return;
        }

        if (!this.getWorld().isClient && !this.isRemoved())
        {
            if (this.tickLifetime())
            {
                this.discard();
            }

            /* Legacy runs updateMorph() even on the tick the ghost expires
             * (setDead only flags it), so a player standing in an expiring
             * ghost still gets the morph. Kept verbatim. */
            this.updateMorph();
        }
    }

    /**
     * The lifetime step, split out of {@link #tick()} so the immortality rule
     * can be driven without a world.
     *
     * <p>Legacy's branch shape, verbatim: decrement while positive, kill only on
     * exactly zero. A negative lifetime therefore skips both branches forever —
     * that is the {@code /summon}'d permanent ghost, and "clamp it to 0" would
     * silently delete it.</p>
     *
     * @return whether the ghost should die this tick
     */
    public boolean tickLifetime()
    {
        if (this.lifetime > 0)
        {
            this.lifetime--;
        }
        else if (this.lifetime == 0)
        {
            return true;
        }

        return false;
    }

    /**
     * Legacy {@code updateMorph}: ownerless ghosts go to the first colliding
     * player; owned ghosts wait for their owner to walk into them.
     */
    private void updateMorph()
    {
        if (this.ownerless)
        {
            for (PlayerEntity player : this.getWorld().getNonSpectatingEntities(PlayerEntity.class, this.getBoundingBox()))
            {
                this.grantMorph(player);

                break;
            }
        }
        else
        {
            /* P277: legacy `this.player.isDead` — the field, i.e. "gone from the
             * world". `!isAlive()` additionally fires at health <= 0, so a dead
             * but not-yet-removed owner triggered a needless re-resolve. */
            if (this.player == null || this.player.isRemoved())
            {
                this.player = resolveOwner(this.getWorld().getPlayers(),
                    PlayerEntity::getUuid, p -> p.getName().getString(), this.owner, this.username);
            }

            if (this.player != null && this.getBoundingBox().intersects(this.player.getBoundingBox()))
            {
                this.grantMorph(this.player);
            }
        }
    }

    /**
     * The owner-resolution matrix, pure: UUID first, then username, then
     * nobody.
     *
     * <p>Legacy looked these up through {@code World.getPlayerEntityByUUID} /
     * {@code getPlayerEntityByName}, neither of which yarn 1.20.4 has on
     * {@code World}; a scan of the world's player list is the equivalent (and
     * the same thing those methods did). Note the {@code else if} — a ghost
     * that carries <i>both</i> an owner UUID and a username never falls through
     * to the username, so an offline owner's ghost is not pickable by someone
     * who happens to hold the name.</p>
     *
     * <p>Generic over the player type — a real {@code PlayerEntity} is not
     * constructible headlessly, and the rule is worth testing on its own.</p>
     */
    public static <T> T resolveOwner(List<? extends T> players, Function<T, UUID> uuid, Function<T, String> name, UUID owner, String username)
    {
        if (players == null)
        {
            return null;
        }

        if (owner != null)
        {
            for (T player : players)
            {
                if (owner.equals(uuid.apply(player)))
                {
                    return player;
                }
            }
        }
        else if (username != null)
        {
            for (T player : players)
            {
                if (username.equals(name.apply(player)))
                {
                    return player;
                }
            }
        }

        return null;
    }

    /**
     * Legacy {@code grantMorph}: hand the morph over, play the item-pickup
     * sound and the pickup animation, and die.
     *
     * <p>The {@code setDead()} is <b>outside</b> the acquire check — a ghost
     * whose morph the player already owns still vanishes on contact. Only the
     * sound and animation are conditional.</p>
     */
    protected void grantMorph(PlayerEntity player)
    {
        if (this.getWorld().isClient)
        {
            return;
        }

        if (MorphAPI.acquire(player, this.morph))
        {
            this.getWorld().playSound(player, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.AMBIENT, 1.0F, 1.0F);

            /* Make the pickup animation */
            if (this.getWorld() instanceof ServerWorld world)
            {
                world.getChunkManager().sendToOtherNearbyPlayers(this,
                    new ItemPickupAnimationS2CPacket(this.getId(), player.getId(), 1));
            }
        }

        this.discard();
    }

    /* Read / write */

    @Override
    public void writeCustomDataToNbt(NbtCompound compound)
    {
        super.writeCustomDataToNbt(compound);

        compound.putInt("LifeTime", this.lifetime);
        compound.putBoolean("Ownerless", this.ownerless);

        if (this.username != null && !this.username.isEmpty())
        {
            compound.putString("Username", this.username);
        }

        if (this.owner != null)
        {
            compound.putString("Owner", this.owner.toString());
        }

        if (this.morph != null)
        {
            NbtCompound tag = new NbtCompound();

            this.morph.toNBT(tag);
            compound.put("Morph", tag);
        }
    }

    /**
     * Legacy {@code readEntityFromNBT}. Two things worth not "cleaning up":
     * the {@code Username} branch is an {@code else if} over {@code Ownerless}
     * (a named ghost is owned whatever the flag says), and the owner UUID is
     * parsed from a plain string — {@code UUID.fromString} on a hand-edited tag
     * would throw, so the port guards it and warns instead, per the
     * total-reader rule.
     */
    @Override
    public void readCustomDataFromNbt(NbtCompound compound)
    {
        super.readCustomDataFromNbt(compound);

        this.owner = parseOwner(compound.getString("Owner"));

        if (compound.contains("LifeTime", NbtElement.NUMBER_TYPE))
        {
            this.lifetime = compound.getInt("LifeTime");
        }

        if (this.owner != null)
        {
            this.ownerless = false;
        }

        if (compound.contains("Username", NbtElement.STRING_TYPE))
        {
            this.username = compound.getString("Username");
            this.ownerless = false;
        }
        else if (compound.contains("Ownerless"))
        {
            this.ownerless = compound.getBoolean("Ownerless");
        }

        if (compound.contains("Morph", NbtElement.COMPOUND_TYPE))
        {
            this.morph = MorphManager.INSTANCE.morphFromNBT(compound.getCompound("Morph"));
        }

        this.setSize(this.morph);
    }

    /**
     * Total {@code Owner} parse: empty (the legacy sentinel for "no owner") and
     * malformed both yield null rather than a load-time crash.
     */
    public static UUID parseOwner(String owner)
    {
        if (owner == null || owner.isEmpty())
        {
            return null;
        }

        try
        {
            return UUID.fromString(owner);
        }
        catch (IllegalArgumentException e)
        {
            Metamorph.LOGGER.warn("Morph ghost carries a malformed Owner UUID '{}'; treating it as ownerless", owner);

            return null;
        }
    }

    /* Spawn data (the yarn replacement for writeSpawnData/readSpawnData) */

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player)
    {
        super.onStartedTrackingBy(player);

        Dispatcher.sendTo(new PacketMorphSpawnData(this.getId(), this.owner, this.morph), player);
    }

    /** Client half of legacy {@code readSpawnData}. */
    public void applySpawnData(PacketMorphSpawnData message)
    {
        this.owner = message.owner;
        this.morph = message.morph;

        this.setSize(this.morph);
    }

    /* Unused methods (legacy's own heading) */

    @Override
    public Iterable<ItemStack> getArmorItems()
    {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack)
    {}

    @Override
    public Arm getMainArm()
    {
        return Arm.RIGHT;
    }
}
