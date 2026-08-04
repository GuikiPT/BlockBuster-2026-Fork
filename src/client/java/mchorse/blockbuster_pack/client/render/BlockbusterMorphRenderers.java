package mchorse.blockbuster_pack.client.render;

import mchorse.blockbuster.client.render.GunMorphRenderer;
import mchorse.blockbuster.client.textures.TextureSizes;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.blockbuster_pack.morphs.ImageMorph;
import mchorse.blockbuster_pack.morphs.ParticleMorph;
import mchorse.blockbuster_pack.morphs.RecordMorph;
import mchorse.blockbuster_pack.morphs.SequencerMorph;
import mchorse.blockbuster_pack.morphs.SnowstormClient;
import mchorse.blockbuster_pack.morphs.SnowstormMorph;
import mchorse.blockbuster_pack.morphs.StructureMorph;
import mchorse.blockbuster_pack.morphs.TrackerMorph;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.metamorph.client.render.MorphRenderPipeline;
import mchorse.metamorph.client.render.MorphRendererRegistry;
import net.minecraft.client.MinecraftClient;

/**
 * Registers Blockbuster's pack-morph client renderers and fills the gun render
 * seams (roadmap P54).
 *
 * <p>This is the client half of {@code BlockbusterFactory}: legacy declared the
 * render bodies on the morph classes themselves, so "registration" was just
 * class loading. In the split source set each pack morph gets an
 * {@code IMorphRenderer} keyed by its morph class here.</p>
 *
 * <p><b>Registered:</b> {@link CustomMorph} (the flagship model morph),
 * {@link SequencerMorph}, {@link RecordMorph} and {@link TrackerMorph} — the
 * three whose draw is mostly indirection (an entry's morph, a ghost actor's
 * morph, a pointer gizmo) — {@link ImageMorph} and {@link StructureMorph}, which
 * emit their own geometry, and {@link ParticleMorph} and {@link SnowstormMorph},
 * whose "draw" is really a per-frame anchor handed to a particle engine.</p>
 *
 * <p>Metamorph's vanilla_pack morphs have their own registration class,
 * {@code mchorse.vanilla_pack.render.VanillaPackMorphRenderers}.</p>
 */
public final class BlockbusterMorphRenderers
{
    private BlockbusterMorphRenderers()
    {}

    public static void register()
    {
        MorphRendererRegistry.register(CustomMorph.class, new CustomMorphRenderer());
        MorphRendererRegistry.register(SequencerMorph.class, new SequencerMorphRenderer());
        MorphRendererRegistry.register(RecordMorph.class, new RecordMorphRenderer());
        MorphRendererRegistry.register(TrackerMorph.class, new TrackerMorphRenderer());
        MorphRendererRegistry.register(ImageMorph.class, new ImageMorphRenderer());
        MorphRendererRegistry.register(StructureMorph.class, new StructureMorphRenderer());
        MorphRendererRegistry.register(ParticleMorph.class, new ParticleMorphRenderer());
        MorphRendererRegistry.register(SnowstormMorph.class, new SnowstormMorphRenderer());

        /* P230: the Snowstorm morph's client seam. Until this landed, only the
         * tests ever called it, so on a real client SnowstormMorph.CLIENT was
         * null and every emitter-touching call short-circuited: no emitter, no
         * scheme changes, no display name, no per-tick keep-alive. Order does
         * not matter relative to the registration above — SnowstormMorphRenderer
         * resolves the seam lazily, per frame. */
        SnowstormClient.install();

        /* P159: the image morph asks the texture for its pixel size, which
         * legacy read straight off the bound GL texture. */
        TextureSizes.install();

        /* P197: the gun renderers hold their own draw seams (projectile morph,
         * gun-item morph, crosshair morph) rather than reaching into
         * MorphRenderer, because they draw against a dummy actor, not a
         * player. Both route to the same trapped draw. */
        GunMorphRenderer.drawer = MorphRenderPipeline::drawEntity;
        GunMorphRenderer.screenDrawer = (morph, x, y, scale, alpha) ->
            MorphRenderUtils.renderOnScreen(morph, MinecraftClient.getInstance().player, x, y, scale, alpha);
    }
}
