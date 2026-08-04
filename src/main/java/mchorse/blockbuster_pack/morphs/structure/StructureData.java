package mchorse.blockbuster_pack.morphs.structure;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

/**
 * Decoded block model of a captured structure template (roadmap P162).
 *
 * <p>This is the modern, GL-free replacement for 2.7.2's "paste the template
 * into a {@code FakeWorld} then read it back" bake preamble. The template NBT
 * (a {@code size} + a {@code palette}/{@code palettes} of block states + a
 * {@code blocks} list of {@code {pos, state, nbt}}) is decoded straight into a
 * {@link BlockPos}-keyed {@link BlockState} map plus the block-entity NBT map,
 * after routing the palette through the {@link StructurePalette} P71 id shim.
 * The client {@code StructureRenderer} then bakes this map into retained vertex
 * buffers with {@code BlockRenderManager}; keeping the decode here makes it
 * unit-testable without a client.</p>
 *
 * <p><b>Total by contract:</b> a missing/garbled palette index or an unknown
 * block name degrades to air (via {@link NbtHelper#toBlockState}) rather than
 * throwing.</p>
 */
public class StructureData
{
    /** Bounding size of the structure (blocks). */
    public Vec3i size = Vec3i.ZERO;

    /** Non-air block states, keyed by their in-template position. */
    public final Map<BlockPos, BlockState> blocks = new LinkedHashMap<BlockPos, BlockState>();

    /** Block-entity NBT (the {@code nbt} sub-tag of a block entry), keyed by position. */
    public final Map<BlockPos, NbtCompound> blockEntities = new LinkedHashMap<BlockPos, NbtCompound>();

    /**
     * Decode a template NBT into a {@link StructureData}. The tag is translated
     * in place through {@link StructurePalette} first (legacy palettes → 1.20.4
     * ids). Uses the read-only block registry wrapper as the state lookup.
     */
    public static StructureData parse(NbtCompound template)
    {
        return parse(template, Registries.BLOCK.getReadOnlyWrapper());
    }

    public static StructureData parse(NbtCompound template, RegistryEntryLookup<Block> lookup)
    {
        StructureData data = new StructureData();

        if (template == null)
        {
            return data;
        }

        StructurePalette.translate(template);

        data.size = readVec3i(template.getList(StructureTemplate.SIZE_KEY, NbtElement.INT_TYPE));

        NbtList palette = pickPalette(template);
        BlockState[] states = new BlockState[palette.size()];

        for (int i = 0; i < palette.size(); i++)
        {
            states[i] = NbtHelper.toBlockState(lookup, palette.getCompound(i));
        }

        NbtList blocks = template.getList(StructureTemplate.BLOCKS_KEY, NbtElement.COMPOUND_TYPE);

        for (int i = 0; i < blocks.size(); i++)
        {
            NbtCompound entry = blocks.getCompound(i);

            if (!entry.contains(StructureTemplate.BLOCKS_STATE_KEY) || !entry.contains(StructureTemplate.BLOCKS_POS_KEY, NbtElement.LIST_TYPE))
            {
                continue;
            }

            int index = entry.getInt(StructureTemplate.BLOCKS_STATE_KEY);

            if (index < 0 || index >= states.length)
            {
                continue;
            }

            BlockState state = states[index];

            if (state == null || state.isAir())
            {
                continue;
            }

            BlockPos pos = readPos(entry.getList(StructureTemplate.BLOCKS_POS_KEY, NbtElement.INT_TYPE));

            data.blocks.put(pos, state);

            if (entry.contains(StructureTemplate.BLOCKS_NBT_KEY, NbtElement.COMPOUND_TYPE))
            {
                data.blockEntities.put(pos, entry.getCompound(StructureTemplate.BLOCKS_NBT_KEY));
            }
        }

        return data;
    }

    /** Single {@code palette}, else the first of {@code palettes}, else empty. */
    private static NbtList pickPalette(NbtCompound template)
    {
        if (template.contains(StructureTemplate.PALETTE_KEY, NbtElement.LIST_TYPE))
        {
            return template.getList(StructureTemplate.PALETTE_KEY, NbtElement.COMPOUND_TYPE);
        }

        if (template.contains(StructureTemplate.PALETTES_KEY, NbtElement.LIST_TYPE))
        {
            NbtList palettes = template.getList(StructureTemplate.PALETTES_KEY, NbtElement.LIST_TYPE);

            if (!palettes.isEmpty())
            {
                return palettes.getList(0);
            }
        }

        return new NbtList();
    }

    private static Vec3i readVec3i(NbtList list)
    {
        if (list.size() < 3)
        {
            return Vec3i.ZERO;
        }

        return new Vec3i(list.getInt(0), list.getInt(1), list.getInt(2));
    }

    private static BlockPos readPos(NbtList list)
    {
        if (list.size() < 3)
        {
            return BlockPos.ORIGIN;
        }

        return new BlockPos(list.getInt(0), list.getInt(1), list.getInt(2));
    }
}
