package mchorse.vanilla_pack;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.EntityUtils;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.vanilla_pack.morphs.BlockMorph;
import mchorse.vanilla_pack.morphs.ItemMorph;
import mchorse.vanilla_pack.morphs.LabelMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The vanilla-entity creative morph section (roadmap P224, S22).
 *
 * <p>1:1 port of Metamorph 1.4's {@code mchorse.vanilla_pack.MetamorphSection} —
 * the section that fills the creative morph picker with every living entity the
 * game knows about. It is registered by {@link MetamorphFactory#register} under
 * the legacy title {@code "entity"}, and without it the picker lists no vanilla
 * mobs at all (which is exactly the state this phase closes).</p>
 *
 * <p>Legacy algorithm, preserved step for step:</p>
 * <ol>
 *   <li>{@code update} is a no-op once the section is full, and <b>resets</b> a
 *       full section when {@code load_entity_morphs} has been turned off — the
 *       config toggle takes effect on the next picker open;</li>
 *   <li>every entity id is run through {@link MorphManager#remap(String)} and
 *       gated by {@link MetamorphFactory#hasMorph(String)} (living entities
 *       only);</li>
 *   <li>each surviving id is <b>instantiated</b> so its serialized NBT can be
 *       stripped into the morph's {@code EntityData} and its concrete class can
 *       pick a category;</li>
 *   <li>the {@code generic} category is then seeded with
 *       {@link BlockMorph}/{@link ItemMorph}/{@link LabelMorph} and the "Notch"
 *       player easter egg (an {@link EntityMorph} named
 *       {@link EntityMorph#PLAYER_ID}) — <b>in that order</b>, after the entity
 *       scan;</li>
 *   <li>empty categories are dropped, the survivors are appended to
 *       {@link #categories} in {@link HashMap} iteration order (see below) and
 *       each is sorted case-insensitively by morph name.</li>
 * </ol>
 *
 * <p><b>The category map stays a {@link HashMap}.</b> Legacy appends
 * {@code categoryMap.values()} to the section, so the on-screen category order
 * is literally {@code HashMap} iteration order over the category-name keys.
 * String hashing is stable across JVMs, so keeping the same map type reproduces
 * the 1.12.2 order exactly; switching to a {@code LinkedHashMap} or sorting
 * would silently reorder every user's picker.</p>
 *
 * <p><b>1.20.4 deltas</b> (all recorded in {@code plan/inbox/batchS-B.md}):</p>
 * <ul>
 *   <li>{@code EntityList.getEntityNameList()} becomes
 *       {@code Registries.ENTITY_TYPE.getIds()} — the {@link #entityIds()}
 *       seam.</li>
 *   <li>Entity creation goes through {@link #snapshot(World, String)} rather
 *       than being inlined, because on 1.20.4 <b>an entity cannot be built
 *       without a live {@link World}</b> ({@code EntityType.create} dereferences
 *       {@code world.getEnabledFeatures()}, mob constructors dereference
 *       {@code world.getProfilerSupplier()}) and {@code EntityType.getBaseClass()}
 *       returns a flat {@code Entity.class}, so neither the default NBT nor the
 *       concrete class is reachable off the type alone. The seam is what lets a
 *       headless test drive the whole assembly pass.</li>
 *   <li>{@code minecraft:player} is skipped explicitly: 1.12's {@code EntityList}
 *       did not contain the player, the modern entity registry does, and
 *       {@code EntityType.PLAYER.create(world)} just returns {@code null}. The
 *       skip keeps the legacy morph set <i>and</i> the log quiet.</li>
 *   <li>{@link #canBuild(World)} makes a null world abort the rebuild instead of
 *       throwing. Legacy would NPE; a null world reaches here from a headless
 *       picker ({@code GuiCreativeMorphs} passes {@code mc.world}), and the
 *       reader contract is warn-and-skip, never crash.</li>
 *   <li>{@code entity.serializeNBT()} becomes {@code entity.writeNbt(new
 *       NbtCompound())}, matching {@code EntityMorph.setEntity}.</li>
 *   <li>Failures log through {@link Metamorph#LOGGER} instead of legacy's
 *       {@code System.out.println} + {@code printStackTrace}.</li>
 * </ul>
 *
 * <p>Entities are created against the world the picker was opened with, on the
 * caller's thread — the same thread legacy used, and never at mod-init time:
 * nothing here runs until a picker opens or a blacklist arrives.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/MetamorphSection.java
 */
public class MetamorphSection extends MorphSection
{
    /**
     * The player entity type is in the modern entity registry but was never in
     * 1.12's {@code EntityList}; it also cannot be instantiated. Skipping it
     * keeps parity with the legacy morph set.
     */
    public static final String PLAYER_ENTITY_ID = "minecraft:player";

    /**
     * Everything the section needs out of a freshly created entity: the
     * concrete class (for the category cascade) and its serialized NBT. A port
     * addition — legacy passed the live entity around, which 1.20.4 cannot
     * produce without a world.
     */
    public static class EntitySnapshot
    {
        public final Class<?> type;
        public final NbtCompound data;

        public EntitySnapshot(Class<?> type, NbtCompound data)
        {
            this.type = type;
            this.data = data;
        }
    }

    private final MetamorphFactory factory;

    /**
     * Temporary per-update category bucket. Deliberately a {@link HashMap} —
     * its iteration order <b>is</b> the legacy category order.
     */
    private final Map<String, MorphCategory> categoryMap = new HashMap<String, MorphCategory>();

    public MetamorphSection(MetamorphFactory factory, String title)
    {
        super(title);

        this.factory = factory;
    }

    @Override
    public void update(World world)
    {
        boolean full = !this.categories.isEmpty();
        boolean loading = Metamorph.loadEntityMorphs.get();

        if (full || !loading)
        {
            if (full && !loading)
            {
                this.reset();
            }

            return;
        }

        /* Port addition: legacy NPE'd without a world. Aborting entirely (rather
         * than building a half section) matters — a partially built section
         * counts as "full" and would never be rebuilt. */
        if (!this.canBuild(world))
        {
            return;
        }

        for (Identifier rl : this.entityIds())
        {
            String id = rl.toString();

            if (PLAYER_ENTITY_ID.equals(id))
            {
                continue;
            }

            String name = MorphManager.INSTANCE.remap(id);

            if (this.factory.hasMorph(name))
            {
                this.addMorph(world, name);
            }
        }

        /* Miscellaneous morphs. The easter egg still goes through NBT with the
         * legacy bare "player" name, so the migration in EntityMorph.fromNBT is
         * exercised by the catalog itself. */
        EntityMorph notch = new EntityMorph();
        NbtCompound tag = new NbtCompound();

        tag.putString("Name", "player");
        tag.putString("Username", "Notch");
        notch.fromNBT(tag);

        this.get("generic").add(new BlockMorph());
        this.get("generic").add(new ItemMorph());
        this.get("generic").add(new LabelMorph());
        this.get("generic").add(notch);

        /* Add categories to the main list */
        Iterator<MorphCategory> it = this.categoryMap.values().iterator();

        while (it.hasNext())
        {
            if (it.next().getMorphs().isEmpty())
            {
                it.remove();
            }
        }

        this.categories.addAll(this.categoryMap.values());
        this.categoryMap.clear();

        for (MorphCategory category : this.categories)
        {
            category.sort();
        }
    }

    /**
     * Whether a rebuild can run at all. Production needs a live world to create
     * entities in; a headless test that stubs {@link #snapshot} does not.
     */
    protected boolean canBuild(World world)
    {
        return world != null;
    }

    /**
     * Entity-id source — legacy {@code EntityList.getEntityNameList()}. Copied
     * into a list so a mid-scan registry change cannot upset the iteration.
     */
    protected Iterable<Identifier> entityIds()
    {
        return new ArrayList<Identifier>(Registries.ENTITY_TYPE.getIds());
    }

    /**
     * Instantiate the entity and take everything the morph needs off it —
     * legacy {@code EntityList.createEntityByIDFromName(rl, world)} plus
     * {@code entity.serializeNBT()}.
     *
     * <p>Creation goes through {@link EntityUtils#createEntity(World, EntityType)}
     * rather than {@code EntityType.create(world)} directly (P252): the latter
     * answers {@code null} for any type behind an experimental feature flag —
     * on 1.20.4 {@code minecraft:breeze} — which dropped the mob from the picker
     * with nothing but a WARN. Legacy's rule was registry membership, which has
     * no feature-flag notion, so the helper falls back to the raw entity factory
     * for feature-disabled types.</p>
     *
     * @return {@code null} when the id is unknown, the type is not a
     *         {@link LivingEntity}, or the type declines to build (legacy's
     *         "because it's null!" branch)
     */
    protected EntitySnapshot snapshot(World world, String name)
    {
        Identifier key = Identifier.tryParse(name);

        if (key == null || !Registries.ENTITY_TYPE.containsId(key))
        {
            return null;
        }

        EntityType<?> type = Registries.ENTITY_TYPE.get(key);
        Entity created = EntityUtils.createEntity(world, type);

        if (!(created instanceof LivingEntity living))
        {
            return null;
        }

        NbtCompound data = new NbtCompound();

        living.writeNbt(data);

        return new EntitySnapshot(living.getClass(), data);
    }

    /**
     * Add an entity morph to the morph list
     */
    protected void addMorph(World world, String name)
    {
        try
        {
            EntityMorph morph = this.factory.morphFromName(name);
            EntitySnapshot entity = this.snapshot(world, name);

            if (entity == null)
            {
                Metamorph.LOGGER.warn("Couldn't add morph {}, because it's null!", name);

                return;
            }

            morph.name = name;

            /* Setting up a category — the legacy instanceof cascade, extracted
             * to MetamorphCategories so it stays headlessly testable. */
            String category = MetamorphCategories.getCategory(name, entity.type);

            EntityUtils.stripEntityNBT(entity.data);
            morph.setEntityData(entity.data);

            this.get(category).add(morph);
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("An error occured during insertion of " + name + " morph!", e);
        }
    }

    /**
     * Get a temporary category
     */
    private MorphCategory get(String name)
    {
        MorphCategory cat = this.categoryMap.get(name);

        if (cat == null)
        {
            this.categoryMap.put(name, cat = new MorphCategory(this, name));
        }

        return cat;
    }

    /**
     * Category titles in on-screen order — a read-only view the S22 parity test
     * uses to pin the legacy {@link HashMap} ordering quirk.
     */
    public List<String> getCategoryTitles()
    {
        List<String> titles = new ArrayList<String>();

        for (MorphCategory category : this.categories)
        {
            titles.add(category.title);
        }

        return titles;
    }

    @Override
    public void reset()
    {
        this.categories.clear();
    }
}
