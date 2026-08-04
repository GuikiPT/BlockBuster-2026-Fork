package mchorse.blockbuster.recording.scene;

import java.util.Objects;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.utils.TextUtils;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

/**
 * Replay domain object.
 *
 * <p>This class is responsible for storing, and persisting to different
 * sources (to NBT and ByteBuf) its content. It's the per-actor configuration
 * inside a {@link Scene}.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/recording/scene/Replay.java}.
 * Field names, NBT keys, the conditional-key write set and the ByteBuf field
 * order are a save/wire contract and are preserved verbatim.</p>
 *
 * <p>The morph is a real {@link AbstractMorph}, deserialized through
 * {@code MorphManager.INSTANCE.morphFromNBT} on the way in and re-serialized
 * with {@code toNBT()} on the way out, exactly as 1.12.2 did. (P127 parked it
 * as an opaque {@link NbtCompound} while the Metamorph engine was unported;
 * that seam closed with the S10 actor morph binding.) A morph name this build
 * cannot resolve deserializes to {@code null} — the total-reader rule — and
 * the replay then simply carries no morph.</p>
 */
public class Replay
{
    /* Meta data */
    public String id = "";
    public String name = "";
    public String target = "";
    public boolean invincible = false;
    public boolean enableBurning = true;
    public boolean teleportBack = true;
    /**
     * Whether the food and XP recording should be played back
     */
    public boolean playBackXPFood = false;

    /* Visual data */
    /** The morph this replay dresses its target in (NBT key {@code "Morph"}). */
    public AbstractMorph morph;
    public boolean invisible = false;
    public boolean enabled = true;
    public boolean fake = false;
    public float health = 20F;
    public boolean renderLast = false;
    public int foodLevel = 20;
    public int totalExperience = 0;

    public Replay()
    {}

    public Replay(String id)
    {
        this.id = id;
    }

    /**
     * Apply replay on an entity (P130 orchestration dispatch).
     *
     * <p>Mirrors 1.12.2 {@code Replay.apply(EntityLivingBase)}: actors take the
     * full state pass; players take the player pass <b>only when the morph is
     * not a player morph</b> (a player morph supplies its own identity, so
     * re-morphing the target would clobber it).</p>
     */
    public void apply(LivingEntity entity)
    {
        if (entity instanceof EntityActor)
        {
            this.apply((EntityActor) entity);
        }
        else if (entity instanceof PlayerEntity)
        {
            if (!this.isPlayerMorph())
            {
                this.apply((PlayerEntity) entity);
            }
        }
    }

    /**
     * Whether {@link #morph} is a player disguise — legacy's
     * {@code this.morph instanceof PlayerMorph}, which is now an ordinary
     * {@link EntityMorph} named {@link EntityMorph#PLAYER_ID}.
     */
    public boolean isPlayerMorph()
    {
        return this.morph instanceof EntityMorph entity && entity.isPlayer();
    }

    /**
     * Apply replay on an actor (name, invulnerability, morph, invisibility,
     * burning, health + max-health attribute rule, render-last).
     */
    public void apply(EntityActor actor)
    {
        String name = TextUtils.processColoredText(this.name);

        actor.setCustomName(name.isEmpty() ? null : Text.literal(name));
        actor.setInvulnerable(this.invincible);
        actor.morph(MorphUtils.copy(this.morph), false);
        actor.invisible = this.invisible;
        actor.enableBurning = this.enableBurning;

        /* Legacy quirk: raise MAX_HEALTH only when strictly greater than 20
         * (health exactly 20 must not touch the attribute), and before
         * setHealth so the higher value isn't clamped. */
        if (this.health > 20)
        {
            EntityAttributeInstance instance = actor.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);

            if (instance != null)
            {
                instance.setBaseValue(this.health);
            }
        }

        actor.setHealth(this.health);
        actor.renderLast = this.renderLast;
        actor.notifyPlayers();
    }

    /**
     * Apply replay on a player (morph, health, food, XP-without-sound).
     */
    public void apply(PlayerEntity player)
    {
        MorphAPI.morph(player, MorphUtils.copy(this.morph), true);

        player.setHealth(this.health);
        player.getHungerManager().setFoodLevel(this.foodLevel);
        player.totalExperience = 0;
        player.experienceProgress = 0;
        player.experienceLevel = 0;
        this.setExperienceWithoutSound(player, this.totalExperience);
    }

    /**
     * Copied from {@code EntityPlayer.addExperience(int)} (yarn
     * {@code addExperience}) minus the level-up sound and scoreboard-criteria
     * handling that differ from the bar/level math. The float loop is preserved
     * verbatim so the resulting level/progress match 1.12.2 bit-for-bit.
     */
    private void setExperienceWithoutSound(PlayerEntity player, int amount)
    {
        player.addScore(amount);

        int i = Integer.MAX_VALUE - player.totalExperience;

        if (amount > i)
        {
            amount = i;
        }

        player.experienceProgress += (float) amount / (float) player.getNextLevelExperience();

        for (player.totalExperience += amount; player.experienceProgress >= 1.0F; player.experienceProgress /= (float) player.getNextLevelExperience())
        {
            player.experienceProgress = (player.experienceProgress - 1.0F) * (float) player.getNextLevelExperience();

            player.experienceLevel += 1;
        }
    }

    /**
     * Pure replication of the {@link #setExperienceWithoutSound} bar/level loop,
     * usable without a live {@link PlayerEntity} (headless XP test). Mutates
     * {@code state} in place; layout matches {@link ExperienceState}.
     *
     * @return the same {@code state} for chaining.
     */
    public static ExperienceState computeExperienceWithoutSound(ExperienceState state, int amount)
    {
        int i = Integer.MAX_VALUE - state.total;

        if (amount > i)
        {
            amount = i;
        }

        state.progress += (float) amount / (float) xpBarCap(state.level);

        for (state.total += amount; state.progress >= 1.0F; state.progress /= (float) xpBarCap(state.level))
        {
            state.progress = (state.progress - 1.0F) * (float) xpBarCap(state.level);

            state.level += 1;
        }

        return state;
    }

    /**
     * Experience required to advance a level from {@code level} — the exact
     * vanilla {@code PlayerEntity.getNextLevelExperience()} formula (1.12.2
     * {@code xpBarCap()}).
     */
    public static int xpBarCap(int level)
    {
        if (level >= 30)
        {
            return 112 + (level - 30) * 9;
        }
        else if (level >= 15)
        {
            return 37 + (level - 15) * 5;
        }

        return 7 + level * 2;
    }

    /**
     * Mutable holder mirroring the XP triple a player carries, so the level/bar
     * math can be exercised headlessly (see {@link #computeExperienceWithoutSound}).
     */
    public static class ExperienceState
    {
        public int level;
        public float progress;
        public int total;
    }

    /* to / from NBT */

    public void toNBT(NbtCompound tag)
    {
        tag.putString("Id", this.id);
        tag.putString("Name", this.name);
        tag.putString("Target", this.target);

        if (this.morph != null)
        {
            tag.put("Morph", this.morph.toNBT());
        }

        tag.putBoolean("Invincible", this.invincible);
        tag.putBoolean("Invisible", this.invisible);
        tag.putBoolean("EnableBurning", this.enableBurning);
        tag.putBoolean("Enabled", this.enabled);
        tag.putBoolean("Fake", this.fake);
        if (!this.teleportBack) tag.putBoolean("TP", this.teleportBack);
        if (this.health != 20) tag.putFloat("Health", this.health);
        if (this.foodLevel != 20) tag.putInt("FoodLevel", this.foodLevel);
        if (this.totalExperience != 0) tag.putInt("TotalExperience", this.totalExperience);
        if (this.renderLast) tag.putBoolean("RenderLast", this.renderLast);
        if (this.playBackXPFood) tag.putBoolean("PlaybackXPFoodLevel", this.playBackXPFood);
    }

    public void fromNBT(NbtCompound tag)
    {
        this.id = tag.getString("Id");
        this.name = tag.getString("Name");
        this.target = tag.getString("Target");
        /* Legacy: an absent key yields an empty compound, whose blank Name
         * resolves through no factory — so a missing or unresolvable morph is
         * null either way. */
        this.morph = MorphManager.INSTANCE.morphFromNBT(tag.getCompound("Morph"));

        this.invincible = tag.getBoolean("Invincible");
        this.invisible = tag.getBoolean("Invisible");
        this.enableBurning = tag.getBoolean("EnableBurning");
        this.fake = tag.getBoolean("Fake");
        this.foodLevel = tag.contains("FoodLevel") ? tag.getInt("FoodLevel") : this.foodLevel;
        this.totalExperience = tag.contains("TotalExperience") ? tag.getInt("TotalExperience") : this.totalExperience;

        if (tag.contains("Enabled")) this.enabled = tag.getBoolean("Enabled");
        if (tag.contains("TP")) this.teleportBack = tag.getBoolean("TP");
        if (tag.contains("Health")) this.health = tag.getFloat("Health");
        if (tag.contains("RenderLast")) this.renderLast = tag.getBoolean("RenderLast");
        if (tag.contains("PlaybackXPFoodLevel")) this.playBackXPFood = tag.getBoolean("PlaybackXPFoodLevel");
    }

    /* to / from ByteBuf */

    public void toBuf(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.id);
        ForgeByteBufUtils.writeUTF8String(buf, this.name);
        ForgeByteBufUtils.writeUTF8String(buf, this.target);
        MorphUtils.morphToBuf(buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf), this.morph);

        buf.writeBoolean(this.invincible);
        buf.writeBoolean(this.invisible);
        buf.writeBoolean(this.enableBurning);
        buf.writeBoolean(this.enabled);
        buf.writeBoolean(this.fake);
        buf.writeBoolean(this.teleportBack);
        buf.writeBoolean(this.renderLast);
        buf.writeFloat(this.health);
        buf.writeInt(this.foodLevel);
        buf.writeInt(this.totalExperience);
        buf.writeBoolean(this.playBackXPFood);
    }

    public void fromBuf(ByteBuf buf)
    {
        this.id = ForgeByteBufUtils.readUTF8String(buf);
        this.name = ForgeByteBufUtils.readUTF8String(buf);
        this.target = ForgeByteBufUtils.readUTF8String(buf);
        this.morph = MorphUtils.morphFromBuf(buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf));

        this.invincible = buf.readBoolean();
        this.invisible = buf.readBoolean();
        this.enableBurning = buf.readBoolean();
        this.enabled = buf.readBoolean();
        this.fake = buf.readBoolean();
        this.teleportBack = buf.readBoolean();
        this.renderLast = buf.readBoolean();
        this.health = buf.readFloat();
        this.foodLevel = buf.readInt();
        this.totalExperience = buf.readInt();
        this.playBackXPFood = buf.readBoolean();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Replay)
        {
            Replay replay = (Replay) obj;

            return Objects.equals(replay.id, this.id)
                && Objects.equals(replay.name, this.name)
                && Objects.equals(replay.target, this.target)
                && replay.invincible == this.invincible
                && replay.invisible == this.invisible
                && replay.enableBurning == this.enableBurning
                && replay.renderLast == this.renderLast
                && Objects.equals(replay.morph, this.morph);
        }

        return super.equals(obj);
    }

    public Replay copy()
    {
        Replay replay = new Replay();

        replay.id = this.id;
        replay.name = this.name;
        replay.target = this.target;
        replay.morph = MorphUtils.copy(this.morph);

        replay.invincible = this.invincible;
        replay.invisible = this.invisible;
        replay.enableBurning = this.enableBurning;
        replay.enabled = this.enabled;
        replay.fake = this.fake;
        replay.teleportBack = this.teleportBack;
        replay.renderLast = this.renderLast;
        replay.health = this.health;
        replay.foodLevel = this.foodLevel;
        replay.totalExperience = this.totalExperience;
        replay.playBackXPFood = this.playBackXPFood;

        return replay;
    }
}
