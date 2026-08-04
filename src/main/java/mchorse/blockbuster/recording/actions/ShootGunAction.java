package mchorse.blockbuster.recording.actions;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.network.server.gun.ServerHandlerGunInteract;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.utils.NBTUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * Gun shot action (id 20, {@code use_gun}).
 *
 * <p>Original author: Evanechecssss (https://evanechecssss.github.io).</p>
 *
 * <p>Records a single gun trigger-pull. On playback ({@link #apply}) the stored
 * gun {@link ItemStack} is re-hydrated, the actor's dimensions/position/rotation
 * (from the current recording frame) and held items are mirrored onto the
 * interaction player, and {@link ServerHandlerGunInteract#interactWithGun} is
 * invoked with the actor as the shooter and {@code player == null} (the
 * null-player branch skips the client echo — actor shots don't ping a recording
 * player). 1:1 port of 2.7.2's {@code ShootGunAction}.</p>
 *
 * <p>The interaction player is the shared
 * {@link Action#resolvePlayer(LivingEntity)}: a recorded player replays onto
 * itself, an {@link EntityActor} onto its
 * {@link EntityActor.EntityFakePlayer}.</p>
 *
 * <p>Port note: the gun stack is held as raw legacy ItemStack NBT (key
 * {@code Stack}, {@link ItemStack#writeNbt}) so records with gun shots
 * round-trip losslessly; the wire form uses {@code writeNbt}/{@code
 * readInfiniteTag} (gun stacks embed morphs and can exceed the serverbound NBT
 * size limit — same reason {@link EquipAction} and {@link MorphAction} bypass
 * it), which is the codebase-consistent stand-in for Forge's {@code
 * ByteBufUtils.writeItemStack}.</p>
 */
public class ShootGunAction extends Action
{
    private NbtCompound stack;

    public ShootGunAction()
    {
        this.stack = new NbtCompound();
    }

    public ShootGunAction(NbtCompound stack)
    {
        this.stack = stack == null ? new NbtCompound() : stack;
    }

    public ShootGunAction(ItemStack stack)
    {
        this.stack = new NbtCompound();

        if (stack != null)
        {
            stack.writeNbt(this.stack);
        }
    }

    public NbtCompound getStack()
    {
        return this.stack;
    }

    @Override
    public void apply(LivingEntity actor)
    {
        RecordPlayer record = EntityUtils.getRecordPlayer(actor);

        if (record == null)
        {
            return;
        }

        Frame frame = record.getCurrentFrame();

        if (frame == null)
        {
            return;
        }

        PlayerEntity player = Action.resolvePlayer(actor);

        if (player == null)
        {
            return;
        }

        ItemStack stack = ItemStack.fromNbt(this.stack);
        GunProps props = mchorse.blockbuster.utils.NBTUtils.getGunProps(stack);

        if (props == null)
        {
            /* getGunProps only returns null for a non-gun stack — a gun item
             * with no "Gun" tag yields fresh defaults. Legacy logs and skips. */
            Blockbuster.LOGGER.error("Null gun props");

            return;
        }

        /* Mirror the actor's transform onto the interaction player so the shot
         * originates from the actor. copyActor handles position/yaw/pitch, both
         * hands and — through EntityFakePlayer's pose override — the dimension
         * parts (width/height/eyeHeight/bounding box). */
        this.copyActor(actor, player, frame);

        ServerHandlerGunInteract.interactWithGun(null, actor, stack);
    }

    @Override
    public void fromBuf(PacketByteBuf buf)
    {
        super.fromBuf(buf);

        this.stack = NBTUtils.readInfiniteTag(buf);
    }

    @Override
    public void toBuf(PacketByteBuf buf)
    {
        super.toBuf(buf);

        buf.writeNbt(this.stack);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        this.stack = tag.getCompound("Stack");
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        tag.put("Stack", this.stack == null ? new NbtCompound() : this.stack);
    }
}
