package mchorse.metamorph;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.capabilities.render.MorphDimensionHandler;
import mchorse.metamorph.entity.EntityMorph;
import mchorse.metamorph.entity.SoundHandlerWiring;
import mchorse.vanilla_pack.MetamorphFactory;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Common-side Metamorph runtime wiring installed at mod init (roadmap P54).
 *
 * <p>Installs the {@code AbstractMorph.sizeHandler} seam so the P54 dimension
 * mixins serve morph hitboxes, and registers the vanilla-pack
 * {@link MetamorphFactory} into {@link MorphManager#INSTANCE} (legacy
 * {@code Metamorph.preInit} order: vanilla pack first, so later factories —
 * S14's BlockbusterFactory — win the reverse-order dispatch), and (S22/P242)
 * installs the sound-replacement lookup {@code SoundHandler.provider} over the
 * P52 morphing component. The morph-render side ({@code MorphRenderer.provider})
 * is client-only and is installed by {@code MorphRenderPipeline}; this class is
 * the single common install point.</p>
 */
public final class MetamorphCommon
{
    /**
     * The morph ghost entity type ({@code metamorph:morph}, roadmap P56.1).
     *
     * <p>Legacy {@code CommonProxy.registerModEntity(EntityMorph.class,
     * "Morph", 0, 64, 3, false)}: 64 blocks of tracking range (4 chunks here —
     * yarn counts chunks), an update every 3 ticks, and no velocity updates
     * (which 1.20.4's builder has no equivalent of; the ghost pins its own
     * horizontal velocity to zero every tick regardless).</p>
     *
     * <p>Neither {@code disableSummon} nor {@code disableSaving} is set, on
     * purpose: {@code /summon metamorph:morph {LifeTime:-1}} is a supported
     * decoration workflow in 1.12.2 and the ghost writes itself to disk.</p>
     */
    public static EntityType<EntityMorph> MORPH;

    private MetamorphCommon()
    {}

    /**
     * Registry-touching setup, split from {@link #init()} so it can run inside
     * Blockbuster's {@code registerContent()} (which headless tests call too)
     * rather than at entrypoint time. Idempotent.
     */
    public static synchronized void registerContent()
    {
        if (MORPH != null)
        {
            return;
        }

        MORPH = Registry.register(Registries.ENTITY_TYPE, new Identifier(Metamorph.MOD_ID, "morph"),
            EntityType.Builder.<EntityMorph>create(
                    (type, world) -> new EntityMorph(type, world), SpawnGroup.MISC)
                .setDimensions(EntityMorph.DEFAULT_SIZE.width,
                    EntityMorph.DEFAULT_SIZE.height)
                .maxTrackingRange(4)
                .trackingTickInterval(3)
                .build("metamorph:morph"));

        FabricDefaultAttributeRegistry.register(MORPH, LivingEntity.createLivingAttributes());
    }

    public static synchronized void init()
    {
        MorphDimensionHandler.install();

        /* S22/P242: the morph hurt/death/step sound lookup (P54.2's
         * SoundHandler.provider). The three PlayerEntitySoundMixin injections
         * shipped live but resolved every player to "not morphed". */
        SoundHandlerWiring.install();

        /* Register the vanilla-pack factory exactly once. Guard on list content
         * rather than a one-shot flag so registration self-heals if the shared
         * MorphManager.INSTANCE singleton has been cleared (headless test
         * isolation clears the factory list between classes); the production
         * call — invoked once at mod init — still registers exactly one
         * instance, and a repeat call never duplicates it. */
        boolean present = MorphManager.INSTANCE.factories.stream()
            .anyMatch(f -> f instanceof MetamorphFactory);

        if (!present)
        {
            MorphManager.INSTANCE.factories.add(new MetamorphFactory());
        }
    }
}
