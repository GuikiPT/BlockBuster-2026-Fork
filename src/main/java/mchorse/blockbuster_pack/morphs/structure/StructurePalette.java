package mchorse.blockbuster_pack.morphs.structure;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import mchorse.blockbuster.legacy.LegacyIdMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;

/**
 * P71 routing for the block palette embedded in a structure-template NBT
 * (roadmap P162).
 *
 * <p>A 2.7.2 template stores its blocks as a {@code palette} (or {@code palettes}
 * for multi-variant) of {@code {Name, Properties}} block-state compounds whose
 * {@code Name} is a <b>pre-flattening</b> registry id (e.g. {@code minecraft:wool}
 * with {@code Properties:{color:"red"}}). Vanilla's {@code DataFixerUpper} never
 * touches this modded-container NBT, so before the vanilla
 * {@link StructureTemplate#readNbt} parses it we rewrite each legacy {@code Name}
 * through the central {@link LegacyIdMap} shim ({@code minecraft:wool} + colour
 * &rarr; {@code minecraft:red_wool}).</p>
 *
 * <p><b>Total by contract:</b> a {@code Name} that already resolves to a 1.20.4
 * block (a template authored by a modern structure block, or an id the shim
 * leaves unchanged) is kept as-is; an unmappable / modded id is left untouched
 * so the vanilla reader degrades it to air rather than crashing. Orientation
 * properties (stairs {@code facing}, log {@code axis}, slab {@code half}) survive
 * by name; a stale colour/variant property left on a colour-flattened block is
 * harmlessly ignored by {@code NbtHelper.toBlockState}. The 1.12 metadata that
 * the shim needs is reconstructed from the palette entry's {@code Properties}
 * (colour / variant / type / metadata), defaulting to {@code 0}.</p>
 */
public final class StructurePalette
{
    /** Dye-colour name &rarr; 1.12 wool metadata index (matches the shim's colour list). */
    private static final Map<String, Integer> DYE_META = new HashMap<String, Integer>();

    static
    {
        String[] order = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
        };

        for (int i = 0; i < order.length; i++)
        {
            DYE_META.put(order[i], i);
        }

        /* 1.12 spelled light gray "silver" for the wool/carpet families. */
        DYE_META.put("silver", 8);
    }

    private StructurePalette()
    {}

    /**
     * Rewrite every legacy block-state {@code Name} in the template tag in place
     * and return the same tag (so callers can chain). Handles both the single
     * {@code palette} and the multi-variant {@code palettes} layouts.
     */
    public static NbtCompound translate(NbtCompound template)
    {
        if (template == null)
        {
            return null;
        }

        if (template.contains(StructureTemplate.PALETTE_KEY, NbtElement.LIST_TYPE))
        {
            translatePalette(template.getList(StructureTemplate.PALETTE_KEY, NbtElement.COMPOUND_TYPE));
        }

        if (template.contains(StructureTemplate.PALETTES_KEY, NbtElement.LIST_TYPE))
        {
            NbtList palettes = template.getList(StructureTemplate.PALETTES_KEY, NbtElement.LIST_TYPE);

            for (int i = 0; i < palettes.size(); i++)
            {
                translatePalette(palettes.getList(i));
            }
        }

        return template;
    }

    private static void translatePalette(NbtList palette)
    {
        for (int i = 0; i < palette.size(); i++)
        {
            translateState(palette.getCompound(i));
        }
    }

    private static void translateState(NbtCompound state)
    {
        if (!state.contains("Name", NbtElement.STRING_TYPE))
        {
            return;
        }

        String name = state.getString("Name");

        /* Already a valid 1.20.4 block id: leave it (and its Properties) alone. */
        if (isModernBlock(name))
        {
            return;
        }

        int meta = metaFromProperties(state);
        String flattened = LegacyIdMap.blockId(name, meta);

        if (flattened != null && isModernBlock(flattened))
        {
            state.putString("Name", flattened);
        }
    }

    private static boolean isModernBlock(String id)
    {
        Identifier rl = Identifier.tryParse(id);

        return rl != null && Registries.BLOCK.containsId(rl);
    }

    /**
     * Reconstruct the 1.12 metadata subtype from the palette entry's
     * {@code Properties}. Only the colour / variant-style properties carry meta
     * that the flattening needs; orientation-only properties contribute nothing
     * and default to {@code 0}.
     */
    private static int metaFromProperties(NbtCompound state)
    {
        if (!state.contains("Properties", NbtElement.COMPOUND_TYPE))
        {
            return 0;
        }

        NbtCompound props = state.getCompound("Properties");

        if (props.contains("color", NbtElement.STRING_TYPE))
        {
            Integer meta = DYE_META.get(props.getString("color").toLowerCase(Locale.ROOT));

            if (meta != null)
            {
                return meta;
            }
        }

        for (String key : new String[] {"variant", "type", "metadata", "damage"})
        {
            if (props.contains(key, NbtElement.STRING_TYPE))
            {
                try
                {
                    return Integer.parseInt(props.getString(key).trim());
                }
                catch (NumberFormatException ignored)
                {
                    /* Named variants (e.g. "smooth") aren't numeric — fall
                     * through to the default; the shim's rename/pick tables key
                     * off the base name for those. */
                }
            }
        }

        return 0;
    }
}
