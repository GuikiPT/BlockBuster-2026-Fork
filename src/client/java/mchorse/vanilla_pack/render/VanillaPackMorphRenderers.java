package mchorse.vanilla_pack.render;

import com.mojang.authlib.GameProfile;

import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.client.render.MorphRenderContext;
import mchorse.metamorph.client.render.MorphRendererRegistry;
import mchorse.vanilla_pack.morphs.BlockMorph;
import mchorse.vanilla_pack.morphs.ItemMorph;
import mchorse.vanilla_pack.morphs.LabelMorph;
import mchorse.vanilla_pack.morphs.PlayerMorphClientEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;

/**
 * Registers Metamorph's vanilla-pack morph renderers (roadmap P54).
 *
 * <p>The client half of {@code VanillaMorphFactory}: legacy declared each render
 * body on the morph class itself behind {@code @SideOnly(CLIENT)}, so
 * "registration" was class loading. Here each one is an {@code IMorphRenderer}
 * keyed by its morph class, resolved through {@link MorphRendererRegistry}'s
 * superclass walk.</p>
 *
 * <p><b>Registered:</b> {@link BlockMorph}, {@link ItemMorph}, {@link LabelMorph}.
 * {@code IronGolemMorph}/{@code ShulkerMorph}/{@code UndeadMorph} need nothing
 * here — they extend {@code EntityMorph} and the walk hands them
 * {@code EntityMorphRenderer}, which is how Java overriding gave them the render
 * body on 1.12.2.</p>
 *
 * <p>A player disguise needs no renderer of its own either — it <i>is</i> an
 * {@code EntityMorph} (named {@link EntityMorph#PLAYER_ID}) and the walk hands
 * it {@code EntityMorphRenderer} — but it does need an inner entity to draw,
 * and {@code minecraft:player} is the one entity type whose factory is null, so
 * it has to be built by hand out of a client-only class. The
 * {@link EntityMorph#clientPlayerFactory} seam is installed from here, so the
 * one place that knows about client render types stays the one place that wires
 * them. {@code ItemStackMorph} itself is abstract and never registered.</p>
 */
public final class VanillaPackMorphRenderers
{
    private VanillaPackMorphRenderers()
    {}

    public static void register()
    {
        MorphRendererRegistry.register(BlockMorph.class, new BlockMorphRenderer());
        MorphRendererRegistry.register(ItemMorph.class, new ItemMorphRenderer());
        MorphRendererRegistry.register(LabelMorph.class, new LabelMorphRenderer());

        EntityMorph.clientPlayerFactory = new EntityMorph.IClientPlayerFactory()
        {
            @Override
            public LivingEntity create(World world, GameProfile profile)
            {
                return createClientPlayer(world, profile);
            }

            @Override
            public void applySkinType(LivingEntity entity, String skinType)
            {
                if (entity instanceof PlayerMorphClientEntity player)
                {
                    player.skinType = skinType == null ? "" : skinType;
                }
            }
        };
    }

    /**
     * Legacy's {@code PlayerMorph.getPlayerClient}, now
     * {@link EntityMorph.IClientPlayerFactory}. The world is a
     * {@code ClientWorld} on every path that reaches here (the caller already
     * checked {@code world.isClient()}); the guard keeps the total-reader
     * contract if that ever stops being true.
     *
     * <p>The arm model is <b>not</b> set here — see
     * {@link EntityMorph.IClientPlayerFactory#applySkinType}, which the morph
     * calls once the entity's own NBT has been read back over it.</p>
     */
    public static LivingEntity createClientPlayer(World world, GameProfile profile)
    {
        if (!(world instanceof ClientWorld client) || profile == null)
        {
            return null;
        }

        return new PlayerMorphClientEntity(client, profile);
    }

    /**
     * The shared {@code lighting} flag as a packed lightmap coordinate, for
     * {@code ItemStackMorph}'s two concrete subclasses and {@code LabelMorph}.
     *
     * <p>Read the sense carefully — it is inverted from what the field name
     * suggests. Legacy pinned the lightmap coords to {@code (240, 240)} when
     * {@code lighting} was <b>false</b> and restored the previous coords after,
     * i.e. {@code lighting == false} means "ignore world light, draw
     * fullbright". On 1.20.4 light is a per-draw argument rather than global GL
     * state, so the save/restore disappears and only the choice remains.</p>
     */
    public static int lightOf(boolean lighting, int light)
    {
        return lighting ? light : MorphRenderContext.FULL_BRIGHT;
    }
}
