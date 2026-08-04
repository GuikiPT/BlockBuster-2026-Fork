package mchorse.blockbuster.legacy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.utils.BlockbusterPaths;
import mchorse.mclib.utils.AtomicWrite;
import mchorse.mclib.utils.Patterns;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.BlockPos;

/**
 * S22 P266 — the on-disk half of the orphaned model-block rescue.
 *
 * <p>Sibling of {@code SceneManager.rescue} (S22 P252), and deliberately the same
 * shape: <b>write verbatim bytes, and only when no file exists under that
 * name</b>. See {@link mchorse.blockbuster.common.tileentity.TileEntityModel}
 * for why the data has to be moved rather than round-tripped, and
 * {@code plan/S20-migration-parity.md} open question #9 for the defect this
 * closes.</p>
 *
 * <h2>The dump format</h2>
 *
 * <p>{@code <world>/blockbuster/model_blocks/model_block_&lt;x&gt;_&lt;y&gt;_&lt;z&gt;.dat},
 * gzip NBT (the same compression every other {@code .dat} under
 * {@code <world>/blockbuster} uses, via {@link NbtIo#writeCompressed}):</p>
 *
 * <table border="1">
 *   <caption>Root compound of a model-block dump</caption>
 *   <tr><th>Key</th><th>Type</th><th>Meaning</th></tr>
 *   <tr><td>{@code Version}</td><td>int</td>
 *       <td>{@link #VERSION} — this is a rescue dump, not a hand-made file.</td></tr>
 *   <tr><td>{@code Block}</td><td>string</td>
 *       <td>{@link #BLOCK} — the block that has to be back at {@code X/Y/Z}
 *       before the tile entity means anything again.</td></tr>
 *   <tr><td>{@code X} / {@code Y} / {@code Z}</td><td>int</td>
 *       <td>The position vanilla handed the block entity, which is authoritative.
 *       The copy inside {@code Data} carries 1.12.2's own lowercase
 *       {@code x}/{@code y}/{@code z}; those agree in practice but are the
 *       <i>file's</i> claim, not the loader's.</td></tr>
 *   <tr><td>{@code Data}</td><td>compound</td>
 *       <td><b>The user's bytes, deep-copied and otherwise untouched</b> — the
 *       complete tile-entity compound as it sat in the chunk, {@code id} and
 *       coordinates included. Nothing is filtered, so nothing can be dropped for
 *       being un-modelled, and the compound stays a legal argument to
 *       {@code BlockEntity.createFromNbt} for whatever restores it later.</td></tr>
 * </table>
 *
 * <p><b>No {@code Data} key is interpreted here or at write time.</b> That is the
 * whole point: the real captured payload in {@code fixtures/worlds/model_te.nbt}
 * contains a {@code block} morph whose {@code Block} is
 * {@code moreblock:sculksensoractive} — a third-party pre-flattening id that the
 * P71 {@code LegacyIdMap} has no entry for and that a parse-and-re-serialize
 * rescue would replace with a placeholder <i>permanently</i>. Moving bytes cannot
 * do that.</p>
 */
public final class OrphanedModelBlocks
{
    /** Dump-format version. Bump only for a breaking change; readers accept older. */
    public static final int VERSION = 1;

    /** The registry id of the block a dumped tile entity belongs to. */
    public static final String BLOCK = Blockbuster.MOD_ID + ":model";

    /** Filename prefix — mirrors the director rescue's {@code director_block_}. */
    public static final String PREFIX = "model_block_";

    private OrphanedModelBlocks()
    {}

    /**
     * The dump id for a model block at {@code pos}:
     * {@code model_block_<x>_<y>_<z>}, raw coordinates, negatives included.
     *
     * <p>Carries <b>no dimension</b>, exactly like the legacy director scene id —
     * because the dimension is not knowable here (a block entity's {@code world}
     * is still null while {@code readNbt} runs; vanilla assigns it afterwards).
     * Two model blocks at the same coordinates in two dimensions therefore collide,
     * and the non-clobbering write below turns that collision into "the second one
     * is kept out and warned about" rather than "the second one overwrites the
     * first".</p>
     */
    public static String dumpId(BlockPos pos)
    {
        return PREFIX + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    /** Whether {@code id} is a legal {@code .dat} filename ({@code Patterns.FILENAME}). */
    public static boolean isValidFilename(String id)
    {
        return id != null && !id.isEmpty() && Patterns.FILENAME.matcher(id).matches();
    }

    /**
     * Wraps a tile-entity compound in the dump envelope. The source is
     * <b>deep-copied</b> — the chunk still owns it, and a rescue may not mutate
     * or alias the thing it is rescuing.
     */
    public static NbtCompound envelope(NbtCompound tileEntity, BlockPos pos)
    {
        NbtCompound root = new NbtCompound();

        root.putInt("Version", VERSION);
        root.putString("Block", BLOCK);
        root.putInt("X", pos.getX());
        root.putInt("Y", pos.getY());
        root.putInt("Z", pos.getZ());
        root.put("Data", tileEntity.copy());

        return root;
    }

    /**
     * Writes a dump, <b>only</b> when no file exists under that id.
     *
     * <p>Refusing to overwrite is what makes the rescue idempotent: an orphaned
     * tile entity is re-read on every load of its chunk, and its payload is frozen
     * legacy data that can never be newer than the file it would replace.</p>
     *
     * @return {@code true} when the dump was written, {@code false} when an
     *         existing file was kept.
     */
    public static boolean write(Path saveRoot, String id, NbtCompound root) throws IOException
    {
        File file = BlockbusterPaths.modelBlocks(saveRoot, id);

        if (file.exists())
        {
            return false;
        }

        /* P284: FileOutputStream truncates on open, and the exists() guard
         * above means a half-written dump is PERMANENT — every later chunk load
         * sees the file, refuses to re-rescue, and read() then returns null
         * forever. The rescue is the only surviving copy of that block's data,
         * so the write has to be all-or-nothing. */
        AtomicWrite.write(file, stream -> NbtIo.writeCompressed(root, stream));

        return true;
    }

    /**
     * Reads a dump back. Total: a missing or unreadable file logs and yields
     * {@code null} rather than throwing.
     */
    public static NbtCompound read(Path saveRoot, String id)
    {
        File file = BlockbusterPaths.modelBlocks(saveRoot, id);

        if (!file.isFile())
        {
            return null;
        }

        try (FileInputStream in = new FileInputStream(file))
        {
            return NbtIo.readCompressed(in);
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to read the model-block dump '" + id + "'", e);

            return null;
        }
    }

    /** Every dump id in the current world, for a future restore command / the GUI. */
    public static List<String> list(Path saveRoot)
    {
        return BlockbusterPaths.modelBlocks(saveRoot);
    }

    /** Convenience overload against the live world save root (null when no world). */
    public static List<String> list()
    {
        Path root = CommonProxy.saveRoot();

        return root == null ? List.of() : list(root);
    }
}
