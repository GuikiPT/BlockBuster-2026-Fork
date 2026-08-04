package mchorse.blockbuster_pack.morphs;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.structure.PacketStructure;
import mchorse.blockbuster.network.server.ServerHandlerStructureRequest;
import mchorse.blockbuster_pack.morphs.structure.StructureAnimation;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.Animation;
import mchorse.metamorph.api.morphs.utils.IAnimationProvider;
import mchorse.metamorph.api.morphs.utils.IMorphGenerator;
import mchorse.metamorph.api.morphs.utils.ISyncableMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.File;

/**
 * Structure morph (roadmap P162) — renders a captured vanilla structure template
 * as a morph. 1:1 NBT/behaviour port of 2.7.2's {@code StructureMorph}.
 *
 * <p>The heavy client work (baking the template into retained vertex buffers,
 * the world-lighting swap, TE rendering) lives in the client source set behind
 * the {@link #client} seam so this data/model class loads headless. The
 * server-tick hot-reload ({@link #checkStructures()}) and the on-disk cache
 * ({@link #STRUCTURE_CACHE}) are server-side and stay here.</p>
 *
 * <p>NBT contract (written only when non-default): {@code Structure},
 * {@code Pose}, {@code Animation}, {@code Biome} (<b>omitted</b> when equal to
 * the default {@code minecraft:ocean}), {@code Lighting} (written only when
 * {@code false}), {@code AnchorX/Y/Z} (only when non-zero).</p>
 */
public class StructureMorph extends AbstractMorph implements IAnimationProvider, ISyncableMorph, IMorphGenerator
{
    public static final Identifier DEFAULT_BIOME = new Identifier("ocean");

    /**
     * Server-side cache of structure-file {@code lastModified} timestamps, keyed
     * by structure name. Polled each server tick by {@link #checkStructures()}.
     */
    public static final Map<String, Long> STRUCTURE_CACHE = new HashMap<String, Long>();

    /**
     * Client structure-cache seam. Installed by the client source set
     * ({@code StructureRenderers}); {@code null} on a dedicated server, where
     * the reload/request methods are inert. The <b>draw</b> is not part of this
     * seam — it is {@code StructureMorphRenderer}, reached through P54's morph
     * render dispatcher like every other morph type.
     */
    public static IStructureClient client;

    /**
     * The name of the structure which should be rendered.
     */
    public String structure = "";

    /**
     * The biome used for render (registry id).
     */
    public Identifier biome = DEFAULT_BIOME;

    /**
     * Whether this structure uses world lighting.
     */
    public boolean lighting = true;

    /**
     * TSR (transform) for the structure morph.
     */
    public ModelTransform pose = new ModelTransform();

    public StructureAnimation animation = new StructureAnimation();

    /* Rotation point */
    public float anchorX;
    public float anchorY;
    public float anchorZ;

    /**
     * Client structure-cache seam (installed by {@code StructureRenderers} in
     * the client source set). Keeps this main-source morph free of any
     * client-only {@code StructureRenderer} dependency.
     */
    public interface IStructureClient
    {
        void reloadStructures();

        void request();

        void cleanUp();
    }

    /** Client: re-request the whole structure list if none are cached. */
    public static void request()
    {
        if (client != null)
        {
            client.request();
        }
    }

    /** Client: drop all baked renderers and re-request (also the {@code /model clear_structures} tie-in). */
    public static void reloadStructures()
    {
        if (client != null)
        {
            client.reloadStructures();
        }
    }

    /** Client: delete all baked renderers. */
    public static void cleanUp()
    {
        if (client != null)
        {
            client.cleanUp();
        }
    }

    /**
     * Server tick: for every structure file, if its {@code lastModified} changed
     * push a null-tag {@link PacketStructure} (a <b>deletion</b> notice) to all
     * players, who then lazily re-request the fresh geometry. Faithful to 2.7.2
     * (the hot-reload never re-sends geometry directly).
     */
    public static void checkStructures()
    {
        for (String name : ServerHandlerStructureRequest.getAllStructures())
        {
            File file = ServerHandlerStructureRequest.getStructureFolder(name);
            Long modified = STRUCTURE_CACHE.get(name);

            if (modified == null)
            {
                modified = file.lastModified();
                STRUCTURE_CACHE.put(name, modified);
            }

            if (modified < file.lastModified())
            {
                STRUCTURE_CACHE.put(name, file.lastModified());

                Dispatcher.sendToAll(new PacketStructure(name, null));
            }
        }
    }

    public StructureMorph()
    {
        super();

        this.name = "structure";
    }

    @Override
    public Animation getAnimation()
    {
        return this.animation;
    }

    /**
     * The structure's render biome id, with the default-fallback preserved
     * (unknown biome silently falls back to the default — never an error).
     */
    public Identifier getBiome()
    {
        return this.biome == null ? DEFAULT_BIOME : this.biome;
    }

    @Override
    public void pause(AbstractMorph previous, int offset)
    {
        this.animation.pause(offset);

        if (previous instanceof StructureMorph)
        {
            StructureMorph structure = (StructureMorph) previous;

            this.animation.last = new ModelTransform();
            this.animation.last.copy(structure.pose);
        }
    }

    @Override
    public boolean isPaused()
    {
        return this.animation.paused;
    }

    @Override
    public boolean canGenerate()
    {
        return this.animation.isInProgress();
    }

    @Override
    public AbstractMorph genCurrentMorph(float partialTicks)
    {
        StructureMorph morph = (StructureMorph) this.copy();

        this.animation.apply(morph.pose, partialTicks);
        morph.animation.duration = this.animation.progress;

        return morph;
    }

    @Override
    protected String getSubclassDisplayName()
    {
        String suffix = this.structure != null && !this.structure.isEmpty() ? " (" + this.structure + "-" + this.getBiome().getPath() + ")" : "";

        return Text.translatable("blockbuster.morph.structure").getString() + suffix;
    }

    @Override
    public void update(LivingEntity target)
    {
        super.update(target);

        this.animation.update();
    }

    @Override
    public AbstractMorph create()
    {
        return new StructureMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof StructureMorph)
        {
            StructureMorph morph = (StructureMorph) from;

            this.structure = morph.structure;
            this.pose.copy(morph.pose);
            this.animation.copy(morph.animation);
            this.biome = morph.biome;
            this.lighting = morph.lighting;
            this.anchorX = morph.anchorX;
            this.anchorY = morph.anchorY;
            this.anchorZ = morph.anchorZ;

            this.animation.reset();
        }
    }

    @Override
    public float getWidth(LivingEntity target)
    {
        return 0.6F;
    }

    @Override
    public float getHeight(LivingEntity target)
    {
        return 1.8F;
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof StructureMorph)
        {
            StructureMorph morph = (StructureMorph) obj;

            result = result && Objects.equals(this.structure, morph.structure);
            result = result && Objects.equals(this.pose, morph.pose);
            result = result && Objects.equals(this.animation, morph.animation);
            result = result && Objects.equals(this.biome, morph.biome);
            result = result && this.lighting == morph.lighting;
            result = result && this.anchorX == morph.anchorX;
            result = result && this.anchorY == morph.anchorY;
            result = result && this.anchorZ == morph.anchorZ;
        }

        return result;
    }

    @Override
    public boolean canMerge(AbstractMorph morph)
    {
        if (morph instanceof StructureMorph)
        {
            StructureMorph structure = (StructureMorph) morph;

            this.mergeBasic(morph);

            if (!structure.animation.ignored)
            {
                ModelTransform pose = new ModelTransform();

                pose.copy(this.pose);

                this.copy(structure);
                this.animation.merge(this, structure);
                this.animation.last = pose;
                this.animation.progress = 0;
            }

            return true;
        }

        return super.canMerge(morph);
    }

    @Override
    public void reset()
    {
        super.reset();

        this.animation.reset();
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Structure")) this.structure = tag.getString("Structure");
        if (tag.contains("Pose")) this.pose.fromNBT(tag.getCompound("Pose"));
        if (tag.contains("Animation")) this.animation.fromNBT(tag.getCompound("Animation"));
        if (tag.contains("Biome")) this.biome = new Identifier(tag.getString("Biome"));
        if (tag.contains("Lighting")) this.lighting = tag.getBoolean("Lighting");
        if (tag.contains("AnchorX")) this.anchorX = tag.getFloat("AnchorX");
        if (tag.contains("AnchorY")) this.anchorY = tag.getFloat("AnchorY");
        if (tag.contains("AnchorZ")) this.anchorZ = tag.getFloat("AnchorZ");
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (!this.structure.isEmpty())
        {
            tag.putString("Structure", this.structure);
        }

        if (!this.pose.isDefault())
        {
            tag.put("Pose", this.pose.toNBT());
        }

        NbtCompound animation = this.animation.toNBT();

        if (!animation.isEmpty())
        {
            tag.put("Animation", animation);
        }

        if (!this.getBiome().equals(DEFAULT_BIOME))
        {
            tag.putString("Biome", this.getBiome().toString());
        }

        if (!this.lighting)
        {
            tag.putBoolean("Lighting", this.lighting);
        }

        if (this.anchorX != 0)
        {
            tag.putFloat("AnchorX", this.anchorX);
        }

        if (this.anchorY != 0)
        {
            tag.putFloat("AnchorY", this.anchorY);
        }

        if (this.anchorZ != 0)
        {
            tag.putFloat("AnchorZ", this.anchorZ);
        }
    }
}
