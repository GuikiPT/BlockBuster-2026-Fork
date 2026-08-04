package mchorse.vanilla_pack.morphs;

import java.util.Objects;

import mchorse.blockbuster.legacy.LegacyIdMap;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

/**
 * Block morph — disguise as a block (roadmap P53.2).
 *
 * <p>NBT: {@code Block} (registry name string), {@code Meta} (<b>byte</b>,
 * pre-flattening blockstate meta), {@code Pos} (int-array; locks the player at
 * pos+0.5 with zeroed motion — the silverfish disguise). Hitbox fixed
 * 0.99×0.99; default {@code minecraft:stone}.</p>
 *
 * <p><b>Byte-parity policy (see plan P53.2 + open question "Meta byte reverse
 * mapping").</b> The internal render value is a {@link BlockState}. When the
 * morph is loaded from legacy data we keep the original {@code Block}/{@code
 * Meta} pair and re-emit it verbatim, so old files round-trip byte-for-byte
 * without needing the P71 flattening table. When the morph was built modern
 * (via {@link #setStack}) we write the modern block id with {@code Meta} 0
 * (a full 1.20.4 blockstate ↔ legacy-meta reverse map is a P71/SEAM concern).
 * The {@code Block}+{@code Meta} → {@code BlockState} flattening on read now
 * routes through {@link LegacyIdMap#blockState(String, int)} (P212), so a
 * legacy {@code (wool, 14)} pair renders as {@code red_wool} rather than an
 * unresolved/air block; orientation-only meta (stairs facing, log axis) still
 * collapses to the flattened block's default state, and NBT byte-parity is
 * unaffected (the raw {@code Block}/{@code Meta} pair is re-emitted verbatim).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/morphs/BlockMorph.java
 */
public class BlockMorph extends ItemStackMorph
{
    /**
     * Block state to render; meaningless on the server, used for rendering.
     */
    public BlockState block = Blocks.STONE.getDefaultState();

    /**
     * Block position to lock the target in.
     */
    public BlockPos blockPos;

    /* Legacy-loaded raw Block/Meta pair, preserved for byte-parity on write. */
    protected String loadedBlockId;
    protected byte loadedMeta;
    protected boolean loadedFromLegacy;

    public BlockMorph()
    {
        this.name = "block";
    }

    @Override
    public void setStack(ItemStack stack)
    {
        if (stack.getItem() instanceof BlockItem)
        {
            this.block = ((BlockItem) stack.getItem()).getBlock().getDefaultState();
            this.loadedFromLegacy = false;
            this.loadedBlockId = null;
        }
    }

    @Override
    public ItemStack getStack()
    {
        return new ItemStack(this.block.getBlock());
    }

    @Override
    public void update(LivingEntity target)
    {
        super.update(target);

        if (this.blockPos != null)
        {
            target.setVelocity(0, 0, 0);
            target.setPosition(this.blockPos.getX() + 0.5, this.blockPos.getY(), this.blockPos.getZ() + 0.5);
        }

        this.updateSize(target, getWidth(target), getHeight(target));
    }

    @Override
    public AbstractMorph create()
    {
        return new BlockMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof BlockMorph)
        {
            BlockMorph morph = (BlockMorph) from;

            this.block = morph.block;
            this.blockPos = morph.blockPos;
            this.loadedBlockId = morph.loadedBlockId;
            this.loadedMeta = morph.loadedMeta;
            this.loadedFromLegacy = morph.loadedFromLegacy;
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof BlockMorph)
        {
            BlockMorph morph = (BlockMorph) obj;

            result = result && Objects.equals(morph.block, this.block);
            result = result && Objects.equals(morph.blockPos, this.blockPos);
        }

        return result;
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Block"))
        {
            this.loadedBlockId = tag.getString("Block");
            this.loadedFromLegacy = true;
            this.loadedMeta = tag.contains("Meta") ? tag.getByte("Meta") : 0;

            /* P71: apply the pre-flattening (block + meta) pair through the
             * central id-translation shim, so a legacy morph (e.g. Block:
             * "minecraft:wool", Meta:14) renders as the flattened state
             * (red_wool) instead of an unresolved/air block. Unknown/modded ids
             * degrade to the shim's placeholder (stone). NBT byte-parity is
             * preserved separately via loadedBlockId/loadedMeta on write. */
            this.block = LegacyIdMap.blockState(this.loadedBlockId, this.loadedMeta);
        }

        if (tag.contains("Pos"))
        {
            int[] pos = tag.getIntArray("Pos");

            if (pos.length == 3)
            {
                this.blockPos = new BlockPos(pos[0], pos[1], pos[2]);
            }
        }
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (this.block != null)
        {
            if (this.loadedFromLegacy && this.loadedBlockId != null)
            {
                tag.putString("Block", this.loadedBlockId);
                tag.putByte("Meta", this.loadedMeta);
            }
            else
            {
                tag.putString("Block", Registries.BLOCK.getId(this.block.getBlock()).toString());

                /* SEAM(P71): reverse blockstate -> legacy meta. Modern-created
                 * morphs write meta 0 for now (parity notes). */
                tag.putByte("Meta", (byte) 0);
            }
        }

        if (this.blockPos != null)
        {
            tag.putIntArray("Pos", new int[] {this.blockPos.getX(), this.blockPos.getY(), this.blockPos.getZ()});
        }
    }

    @Override
    public float getWidth(LivingEntity target)
    {
        return 0.99F;
    }

    @Override
    public float getHeight(LivingEntity target)
    {
        return 0.99F;
    }
}
