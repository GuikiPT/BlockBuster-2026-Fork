package mchorse.blockbuster_pack;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.api.IModelMorphSection;
import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.structure.PacketStructureListRequest;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.blockbuster_pack.morphs.ImageMorph;
import mchorse.blockbuster_pack.morphs.ParticleMorph;
import mchorse.blockbuster_pack.morphs.RecordMorph;
import mchorse.blockbuster_pack.morphs.SequencerMorph;
import mchorse.blockbuster_pack.morphs.SnowstormMorph;
import mchorse.blockbuster_pack.morphs.StructureMorph;
import mchorse.blockbuster_pack.morphs.TrackerMorph;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.vanilla_pack.morphs.BlockMorph;
import mchorse.vanilla_pack.morphs.ItemMorph;
import mchorse.vanilla_pack.morphs.LabelMorph;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.world.World;
import org.apache.commons.io.FilenameUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Blockbuster creative morph-picker section (roadmap P157).
 *
 * <p>Faithful port of legacy {@code mchorse.blockbuster_pack.BlockbusterSection}.
 * Owns three kinds of categories:</p>
 *
 * <ul>
 *   <li>{@code blockbuster_extra} — constructor-seeded, never reloaded: the
 *       default image, particle, demo sequencer, record and snowstorm morphs, the
 *       three "by popular demand" vanilla-pack morphs
 *       ({@link ItemMorph}/{@link LabelMorph}/{@link BlockMorph}), the McHorse
 *       easter-egg {@link CustomMorph}, and the tracker morph — <b>order
 *       matters</b>.</li>
 *   <li>{@code blockbuster_structures} — kept in sync with the server's structure
 *       list ({@link #addStructures}/{@link #addStructure}/{@link #removeStructure}).</li>
 *   <li>per-folder {@code blockbuster_models} categories — one per model
 *       sub-folder, populated as {@link ModelHandler} loads models through the
 *       {@link IModelMorphSection} seam.</li>
 * </ul>
 *
 * <p>Load-bearing quirks preserved verbatim:</p>
 * <ul>
 *   <li>The "Really terrible hack to add sequences": {@code this.alex} is set by
 *       key {@code "alex"} and {@code this.steve} is set by key <b>{@code "fred"}</b>
 *       (yes, {@code steve} tracks {@code fred}); once both are seen and the demo
 *       {@link #sequencer} is still empty, the alex+fred pair is added exactly
 *       once per session.</li>
 *   <li>{@code getCategoryId} yields {@code ""} for root models (empty subtitle →
 *       plain title).</li>
 *   <li>{@link #reset()} clears <b>only</b> structures — model categories persist
 *       across world changes; the extra category is constructor-only.</li>
 *   <li>Easter-egg SNBT parse failure is silently swallowed (the morph is built
 *       before models load, so its {@code model} is null until {@code updateModel}).</li>
 * </ul>
 *
 * <p>Port note: implements {@link IModelMorphSection} so {@code ModelHandler}
 * wires this section into {@code ModelHandler.morphSection} and mirrors model
 * add/remove into the picker (the legacy {@code Blockbuster.proxy.factory.section}
 * link).</p>
 *
 * Legacy source: blockbuster-1.12/.../mchorse/blockbuster_pack/BlockbusterSection.java
 */
public class BlockbusterSection extends MorphSection implements IModelMorphSection
{
    public MorphCategory extra;
    public MorphCategory structures;
    public Map<String, MorphCategory> models = new HashMap<String, MorphCategory>();

    private boolean alex;
    private boolean steve;
    private SequencerMorph sequencer;

    public BlockbusterSection(String title)
    {
        super(title);

        this.extra = new MorphCategory(this, "blockbuster_extra");
        this.structures = new MorphCategory(this, "blockbuster_structures");

        /* Adding some default morphs which don't need to get reloaded */
        ImageMorph image = new ImageMorph();
        SnowstormMorph snow = new SnowstormMorph();
        SequencerMorph sequencer = new SequencerMorph();

        image.texture = RLUtils.create("b.a:image/skins/default.png");
        snow.setScheme("default_rain");

        this.extra.add(image);
        this.extra.add(new ParticleMorph());
        this.extra.add(this.sequencer = sequencer);
        this.extra.add(new RecordMorph());
        this.extra.add(snow);

        /* By popular demand */
        this.extra.add(new ItemMorph());
        this.extra.add(new LabelMorph());
        this.extra.add(new BlockMorph());

        this.addFromNBT("{DisplayName:\"McHorse\",Skin:\"blockbuster:textures/entity/mchorse/skin.png\",BodyParts:[{Limb:\"head\",Morph:{Name:\"blockbuster.mchorse/head\"}}],Name:\"blockbuster.fred_3d\"}");

        this.extra.add(new TrackerMorph());
    }

    private void addFromNBT(String nbt)
    {
        try
        {
            NbtCompound tag = StringNbtReader.parse(nbt);
            CustomMorph morph = new CustomMorph();

            morph.fromNBT(tag);
            this.extra.add(morph);
        }
        catch (Exception e)
        {}
    }

    public void addStructure(String name, boolean sort)
    {
        StructureMorph morph = new StructureMorph();

        morph.structure = name;
        this.structures.add(morph);

        if (sort)
        {
            this.structures.sort();
        }
    }

    public void addStructures(List<String> structures)
    {
        this.structures.clear();

        for (String name : structures)
        {
            this.addStructure(name, false);
        }

        this.structures.sort();
    }

    public void removeStructure(String name)
    {
        Iterator<AbstractMorph> it = this.structures.getMorphs().iterator();

        while (it.hasNext())
        {
            AbstractMorph morph = it.next();

            if (((StructureMorph) morph).structure.equals(name))
            {
                it.remove();
            }
        }
    }

    @Override
    public void add(String key, Model model, boolean isRemote)
    {
        String path = this.getCategoryId(key);
        MorphCategory category = this.models.get(path);

        if (category == null)
        {
            category = new BlockbusterCategory(this, "blockbuster_models", path);
            this.models.put(path, category);
            this.categories.add(category);
        }

        for (AbstractMorph morph : category.getMorphs())
        {
            if (morph instanceof CustomMorph && ((CustomMorph) morph).getKey().equals(key))
            {
                return;
            }
        }

        CustomMorph morph = new CustomMorph();

        morph.name = "blockbuster." + key;
        morph.model = model;

        if (isRemote)
        {
            morph.skin = this.getSkin(key, model);
        }

        category.add(morph);
        category.sort();

        /* Really terrible hack to add sequences */
        this.alex = this.alex || key.equals("alex");
        this.steve = this.steve || key.equals("fred");

        if (this.steve && this.alex && this.sequencer.morphs.isEmpty())
        {
            CustomMorph alex = new CustomMorph();
            CustomMorph fred = new CustomMorph();

            alex.name = "blockbuster.alex";
            alex.updateModel(true);
            fred.name = "blockbuster.fred";
            fred.updateModel(true);

            this.sequencer.morphs.add(new SequencerMorph.SequenceEntry(alex));
            this.sequencer.morphs.add(new SequencerMorph.SequenceEntry(fred));
        }
    }

    /**
     * The client skin-tree walk behind {@link #getSkin} (P88.1). Filled by
     * {@code BlockbusterSectionSkins.install()} from the client entrypoint; null
     * on a server and in headless tests, where it degrades to legacy's
     * "no skin found" answer.
     *
     * <p>Legacy marked {@code getSkin} {@code @SideOnly(Side.CLIENT)} and called
     * {@code ClientProxy.tree} inline. The port cannot: this class lives in the
     * common source set (the section is registered on both sides) and the tree —
     * along with {@code FolderEntry}'s picker semantics — is a client concern.
     * A static seam is the same idiom {@code AbstractMorph.IRenderDispatcher}
     * uses, and it keeps the common class free of client types instead of
     * relying on Fabric's bytecode stripping.</p>
     */
    public static ISkinResolver skinResolver;

    /**
     * The Snowstorm preset-library rescan behind {@link #update(World)} (S13
     * P154 / S22 batch U-K). Filled by
     * {@code ParticleLibraryWiring.install()} from the client entrypoint; null
     * on a dedicated server and in headless tests, where it degrades to a no-op.
     *
     * <p>Legacy line 262 of this method was a plain
     * {@code Blockbuster.proxy.particles.reload()} — its library lived on
     * {@code CommonProxy}, which predates split source sets. The port keeps
     * {@code BedrockLibrary} (and the whole Bedrock engine) in {@code src/client},
     * and this class is common, so the call crosses the boundary through the same
     * static-seam idiom as {@link #skinResolver} rather than a main&rarr;client
     * import.</p>
     */
    public static Runnable particleReloader;

    /**
     * Get the first skin which can be found.
     *
     * <p>Legacy walked {@code ClientProxy.tree.getByPath(key + "/skins")} then
     * {@code model.skins + "/skins"} and returned the first {@code FileEntry}'s
     * resource — see {@link #skinResolver} for where that walk now lives. Note
     * the server/common {@code add(...)} path always passes
     * {@code isRemote == false}, so this is only ever invoked from the client
     * remote-model path.</p>
     */
    private ResourceLocation getSkin(String key, Model model)
    {
        if (model.defaultTexture != null)
        {
            return null;
        }

        return skinResolver == null ? null : skinResolver.resolve(key, model);
    }

    /**
     * Client seam for {@link BlockbusterSection#getSkin}.
     */
    public interface ISkinResolver
    {
        public ResourceLocation resolve(String key, Model model);
    }

    @Override
    public void remove(String key)
    {
        String path = this.getCategoryId(key);
        String name = "blockbuster." + key;
        MorphCategory category = this.models.get(path);

        if (category == null)
        {
            return;
        }

        List<AbstractMorph> morphs = new ArrayList<AbstractMorph>();

        for (AbstractMorph m : category.getMorphs())
        {
            if (m.name.equals(name))
            {
                morphs.add(m);
            }
        }

        for (AbstractMorph morph : morphs)
        {
            category.remove(morph);
        }
    }

    private String getCategoryId(String key)
    {
        if (key.contains("/"))
        {
            key = FilenameUtils.getPath(key);

            return key.substring(0, key.length() - 1);
        }

        return "";
    }

    @Override
    public void update(World world)
    {
        /* Reload models and skin */
        CommonProxy.loadModels(false);

        /* S22 batch U-K: legacy line 262 — Blockbuster.proxy.particles.reload().
         * The Bedrock library is client-only in the port, so the call goes
         * through the particleReloader seam (see its javadoc). */
        if (particleReloader != null)
        {
            particleReloader.run();
        }

        /* S22 P237: ask the server for the structure-name list. This is the one
         * and only production send site of PacketStructureListRequest in 1.12.2
         * too — the reply (PacketStructureList) is what fills the
         * blockbuster_structures category, so without it that category is
         * permanently empty. update() is only ever reached from the creative /
         * survival morph pickers (GuiCreativeMorphs, GuiSurvivalMorphs), i.e.
         * always client-side; on a dedicated server McLib's dispatcher has no
         * client sender installed and logs-and-drops. */
        Dispatcher.sendToServer(new PacketStructureListRequest());

        this.rebuildCategories();
    }

    /**
     * The exact legacy category rebuild: clear, then re-add {@code extra},
     * {@code structures} and every model category. Extracted from {@code update}
     * so it can be exercised deterministically (headless) without the model
     * reload / particle reload / structure-request side effects.
     */
    void rebuildCategories()
    {
        this.categories.clear();
        this.add(this.extra);
        this.add(this.structures);

        /* Add models categories */
        for (MorphCategory category : this.models.values())
        {
            this.add(category);
        }
    }

    @Override
    public void reset()
    {
        this.structures.clear();
    }

    public static class BlockbusterCategory extends MorphCategory
    {
        public String subtitle;

        public BlockbusterCategory(MorphSection parent, String title, String subtitle)
        {
            super(parent, title);

            this.subtitle = subtitle;
        }

        @Override
        public String getTitle()
        {
            if (!this.subtitle.isEmpty())
            {
                return super.getTitle() + " (" + this.subtitle + ")";
            }

            return super.getTitle();
        }
    }
}
