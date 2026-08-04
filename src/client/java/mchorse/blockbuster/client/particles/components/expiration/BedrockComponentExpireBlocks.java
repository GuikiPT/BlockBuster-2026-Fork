package mchorse.blockbuster.client.particles.components.expiration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.blockbuster.legacy.LegacyIdMap;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import javax.vecmath.Vector3d;
import java.util.ArrayList;
import java.util.List;

/**
 * Block-expiration component base (roadmap P150).
 *
 * <p>Ported from the 1.12.2 legacy engine. The JSON form is a <b>bare array of
 * block registry names</b>; the two concrete modes
 * ({@link BedrockComponentExpireInBlocks} / {@link BedrockComponentExpireNotInBlocks})
 * kill a particle when the block at its global position is (respectively is not)
 * in the resolved list. Comparison is by {@link Block} identity only — no
 * blockstate / metadata (1.12 parity). {@code null} world resolves to
 * {@link Blocks#AIR}.</p>
 *
 * <p><b>P71 id-translation:</b> user files hold pre-flattening ids (e.g.
 * {@code minecraft:snow_layer}); every parsed id is routed through
 * {@link LegacyIdMap} first, then falls back to a direct registry lookup for
 * already-modern / modded ids. Unresolvable ids are dropped with a logged
 * warning (total reader). On write we emit the resolved block's current
 * (flattened) registry id.</p>
 */
public abstract class BedrockComponentExpireBlocks extends BedrockComponentBase
{
    public List<Block> blocks = new ArrayList<Block>();

    private BlockPos.Mutable pos = new BlockPos.Mutable();

    @Override
    public BedrockComponentBase fromJson(JsonElement element, MolangParser parser) throws MolangException
    {
        if (element.isJsonArray())
        {
            for (JsonElement value : element.getAsJsonArray())
            {
                String id = value.getAsString();
                Block block = resolveBlock(id);

                if (block != null)
                {
                    this.blocks.add(block);
                }
                else
                {
                    Blockbuster.LOGGER.warn("Snowstorm expire-blocks: block id \"{}\" has no 1.20.4 mapping — dropping", id);
                }
            }
        }

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonArray array = new JsonArray();

        for (Block block : this.blocks)
        {
            Identifier rl = Registries.BLOCK.getId(block);

            if (rl != null)
            {
                array.add(rl.toString());
            }
        }

        return array;
    }

    /**
     * Resolve a (possibly pre-flattening) block id to a modern {@link Block}, or
     * {@code null} when unmappable. Legacy translation (P71) is tried first, then
     * a direct registry lookup covers already-modern and modded ids.
     */
    private static Block resolveBlock(String id)
    {
        String translated = LegacyIdMap.blockId(id, 0);
        Block block = lookup(translated);

        if (block == null)
        {
            block = lookup(id);
        }

        return block;
    }

    private static Block lookup(String id)
    {
        Identifier rl = id == null ? null : Identifier.tryParse(id);

        return rl != null ? Registries.BLOCK.getOrEmpty(rl).orElse(null) : null;
    }

    public Block getBlock(BedrockEmitter emitter, BedrockParticle particle)
    {
        if (emitter.world == null)
        {
            return Blocks.AIR;
        }

        Vector3d position = particle.getGlobalPosition(emitter);

        this.pos.set(position.x, position.y, position.z);

        return emitter.world.getBlockState(this.pos).getBlock();
    }
}
