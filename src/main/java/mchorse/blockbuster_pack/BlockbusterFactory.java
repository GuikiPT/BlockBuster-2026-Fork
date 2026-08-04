package mchorse.blockbuster_pack;

import mchorse.blockbuster.api.ModelHandler;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.blockbuster_pack.morphs.ImageMorph;
import mchorse.blockbuster_pack.morphs.ParticleMorph;
import mchorse.blockbuster_pack.morphs.RecordMorph;
import mchorse.blockbuster_pack.morphs.SequencerMorph;
import mchorse.blockbuster_pack.morphs.SnowstormMorph;
import mchorse.blockbuster_pack.morphs.StructureMorph;
import mchorse.blockbuster_pack.morphs.TrackerMorph;
import mchorse.metamorph.api.IMorphFactory;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.NbtCompound;

/**
 * Blockbuster morph factory (roadmap P157).
 *
 * <p>Faithful port of legacy {@code mchorse.blockbuster_pack.BlockbusterFactory}:
 * the {@link IMorphFactory} that produces every {@code blockbuster.*}/bare-id
 * morph provided by the mod, by the user (config models), or by the server (world
 * save models). It registers a single creative section — {@link BlockbusterSection}
 * — into the {@link MorphManager}'s morph list.</p>
 *
 * <p>Load-bearing quirks preserved verbatim:</p>
 * <ul>
 *   <li>{@link #getMorphFromNBT} strips the name <b>only up to the first dot</b>
 *       ({@code name.substring(name.indexOf(".") + 1)}) — a model key containing a
 *       dot resolves differently than a naive prefix-strip would.</li>
 *   <li>Any name not matching one of the seven reserved ids falls through to
 *       {@link CustomMorph} with {@code model = models.models.get(name)} — a
 *       <b>possibly-null</b> model is never an error (total reader; renders as the
 *       red morph-error text).</li>
 *   <li>{@link #hasMorph} asymmetry: bare {@code "record"}/{@code "image"} are
 *       <b>not</b> claimed (only the {@code blockbuster.}-prefixed forms, via the
 *       {@code startsWith} branch), while bare {@code "sequencer"},
 *       {@code "structure"}, {@code "particle"}, {@code "snowstorm"} and
 *       {@code "tracker"} are. This is a wire/disk contract with cross-factory
 *       {@link MorphManager} dispatch — do not "fix" it.</li>
 * </ul>
 *
 * <p>Port note: the legacy client-only {@code registerMorphEditors(Minecraft,
 * List&lt;GuiAbstractMorph&gt;)} is intentionally absent from this main-source
 * {@link IMorphFactory} (it cannot reference client-only GUI classes). Its
 * faithful port lives in the client source set as
 * {@code mchorse.blockbuster_pack.client.gui.BlockbusterMorphEditors}, an
 * {@code IMorphEditorFactory} registered into the client
 * {@code MorphEditorRegistry} (P165-registry).</p>
 *
 * Legacy source: blockbuster-1.12/.../mchorse/blockbuster_pack/BlockbusterFactory.java
 */
public class BlockbusterFactory implements IMorphFactory
{
    /**
     * The live factory — the port's {@code Blockbuster.proxy.factory} (S22
     * P237). 1.12.2 reached the creative section through that proxy field from
     * {@code ClientHandlerStructureList}; the port has no {@code proxy} object,
     * and {@code ModelHandler.morphSection} only exposes the narrow
     * {@code IModelMorphSection} add/remove contract (and is deliberately
     * swappable for a test double), so the factory publishes itself here.
     *
     * <p>Assigned by {@link #register(MorphManager)}, which is re-run on every
     * morph reload and re-creates {@link #section}.</p>
     */
    public static BlockbusterFactory INSTANCE;

    /** Model registry the custom-model dispatch reads (wired at init). */
    public ModelHandler models;

    /** The creative picker section this factory owns. */
    public BlockbusterSection section;

    /**
     * The live creative section, or {@code null} before the factory has been
     * registered — legacy {@code Blockbuster.proxy.factory.section}.
     */
    public static BlockbusterSection section()
    {
        return INSTANCE == null ? null : INSTANCE.section;
    }

    @Override
    public void register(MorphManager manager)
    {
        INSTANCE = this;

        manager.list.register(this.section = new BlockbusterSection("blockbuster"));

        /* Port seam (S4 ↔ P69): wire the freshly-built section into the model
         * handler so model add/remove mirrors into the creative picker (the
         * legacy {@code Blockbuster.proxy.factory.section} link). register() runs
         * on every morph reload, re-creating the section; re-point the seam to the
         * current one. */
        ModelHandler.morphSection = this.section;
    }

    @Override
    public AbstractMorph getMorphFromNBT(NbtCompound tag)
    {
        String name = tag.getString("Name");
        AbstractMorph morph;
        name = name.substring(name.indexOf(".") + 1);

        /* Utility */
        if (name.equals("image"))
        {
            morph = new ImageMorph();
        }
        else if (name.equals("sequencer"))
        {
            morph = new SequencerMorph();
        }
        else if (name.equals("record"))
        {
            morph = new RecordMorph();
        }
        else if (name.equals("structure"))
        {
            morph = new StructureMorph();
        }
        else if (name.equals("particle"))
        {
            morph = new ParticleMorph();
        }
        else if (name.equals("snowstorm"))
        {
            morph = new SnowstormMorph();
        }
        else if (name.equals("tracker"))
        {
            morph = new TrackerMorph();
        }
        else
        {
            /* Custom model morphs */
            CustomMorph custom = new CustomMorph();

            custom.model = this.models == null ? null : this.models.models.get(name);
            morph = custom;
        }

        morph.fromNBT(tag);

        return morph;
    }

    @Override
    public boolean hasMorph(String morph)
    {
        return morph.startsWith("blockbuster.") || morph.equals("sequencer") || morph.equals("structure") || morph.equals("particle") || morph.equals("snowstorm") || morph.equals("tracker");
    }
}
