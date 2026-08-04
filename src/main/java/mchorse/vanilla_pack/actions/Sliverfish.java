package mchorse.vanilla_pack.actions;

import mchorse.metamorph.api.EntityUtils;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.vanilla_pack.morphs.BlockMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Silverfish action (roadmap P49.1, registry id {@code silverfish}).
 *
 * <p>Turns silverfish into immovable block at which it was looking (5 blocks of
 * reach), removing the block from the world.</p>
 *
 * <p><b>Class name kept as legacy spelled it</b> ({@code Sliverfish}) for
 * diff-ability against {@code .tools/legacy-src}; the registry id is the
 * correctly spelled {@code silverfish} and that id is the disk contract.</p>
 *
 * <p>API translation: {@code world.rayTraceBlocks(...)} →
 * {@link EntityUtils#rayTrace} (a {@link BlockHitResult}, {@code MISS} instead
 * of {@code null} on a miss); {@code world.setBlockToAir(pos)} →
 * {@code world.removeBlock(pos, false)}.</p>
 *
 * <p>Deviation (documented): legacy set the produced morph's name to the
 * pre-1.3 literal {@code "metamorph.Block"}, which its own remap table rewrote
 * to {@code block}. The port emits the modern {@code block} name directly (the
 * {@link BlockMorph} default) and keeps the remap entry for old saves.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/Sliverfish.java
 */
public class Sliverfish implements IAction
{
    @Override
    public void execute(LivingEntity target, AbstractMorph morph)
    {
        if (target.getWorld().isClient)
        {
            return;
        }

        float reachDistance = 5;

        BlockHitResult result = EntityUtils.rayTrace(target, reachDistance, 1.0F);

        if (result != null && result.getType() == HitResult.Type.BLOCK && target instanceof PlayerEntity)
        {
            BlockMorph block = new BlockMorph();

            block.blockPos = result.getBlockPos();
            block.block = target.getWorld().getBlockState(block.blockPos);
            block.name = "block";

            target.getWorld().removeBlock(block.blockPos, false);

            MorphAPI.morph((PlayerEntity) target, block, true);
        }
    }
}
