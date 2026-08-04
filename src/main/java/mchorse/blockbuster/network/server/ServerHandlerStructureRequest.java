package mchorse.blockbuster.network.server;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.structure.PacketStructure;
import mchorse.blockbuster.network.common.structure.PacketStructureRequest;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

/**
 * Server handler for {@link PacketStructureRequest} (roadmap P162) — replies
 * with one {@link PacketStructure} per requested template (empty name = ALL).
 *
 * <p>1.12.2 read templates from {@code <save>/structures/*.nbt} via
 * {@code DimensionManager.getCurrentSaveRootDirectory()} and the world's
 * {@code TemplateManager}. On Fabric the save-root is not a global static, so
 * {@link #saveRoot} is a seam wired from {@code ServerLifecycleEvents.SERVER_STARTED}
 * (batch-4) and set opportunistically from the requesting player's server here.
 * The static {@link #getAllStructures()} / {@link #getStructureFolder(String)}
 * remain callable from {@code StructureMorph.checkStructures} (server tick, no
 * player) exactly as legacy.</p>
 *
 * <p><b>Parity decision (open question #1):</b> the port scans the legacy
 * {@code <save>/structures} directory (what migrated 2.7.2 worlds contain) and
 * reads the template NBT from that file directly when present; only when the
 * file is absent does it fall back to the vanilla
 * {@link StructureTemplateManager} (which serves {@code <save>/generated/<ns>/structures}
 * for worlds authored by 1.20.4 structure blocks). Recorded in the notes.</p>
 *
 * <p>That decision originally reached only the <b>fetch</b> half: enumeration
 * still listed the legacy folder alone, so on a world authored by a 1.20.4
 * structure block {@link #getAllStructures()} returned nothing and the
 * {@code blockbuster_structures} picker category was permanently empty (the
 * per-tick hot-reload poll went dark for the same reason). Both
 * {@link #getAllStructures()} and {@link #getStructureFolder(String)} now cover
 * the two layouts, legacy first.</p>
 *
 * <p>No permission checks — faithful to 1.12.2 (flagged for S19 permissions).</p>
 */
public class ServerHandlerStructureRequest extends ServerMessageHandler<PacketStructureRequest>
{
    /**
     * Save-root seam (the {@code <save>} directory). {@code getStructureFolder}
     * resolves {@code <save>/structures/...} beneath it. Null until wired.
     */
    public static Supplier<File> saveRoot;

    /**
     * The namespace whose templates keep a bare name, so a structure morph saved
     * by 2.7.2 (which had no namespaces) still resolves unchanged.
     */
    private static final String DEFAULT_NAMESPACE = "minecraft";

    public static List<String> getAllStructures()
    {
        /* Ordered + de-duplicated: a name present in both layouts is one entry,
         * and the legacy file is the one getStructureFolder then prefers. */
        Set<String> structures = new LinkedHashSet<String>();

        collectLegacy(structures);
        collectGenerated(structures);

        return new ArrayList<String>(structures);
    }

    /**
     * Scan the flat legacy {@code <save>/structures} folder — the 1.12.2 layout,
     * still what a migrated 2.7.2 world carries. Names are bare, as legacy.
     */
    private static void collectLegacy(Set<String> structures)
    {
        File files = getStructureFolder("");

        if (files == null || !files.isDirectory())
        {
            return;
        }

        File[] listed = files.listFiles();

        if (listed == null)
        {
            return;
        }

        for (File file : listed)
        {
            String name = file.getName();

            if (file.isFile() && name.endsWith(".nbt"))
            {
                structures.add(name.substring(0, name.lastIndexOf(".")));
            }
        }
    }

    /**
     * Scan every {@code <save>/generated/<ns>/structures} tree — what the 1.20.4
     * structure block actually writes when you hit save.
     */
    private static void collectGenerated(Set<String> structures)
    {
        File[] namespaces = getGeneratedFolder().listFiles();

        if (namespaces == null)
        {
            return;
        }

        for (File namespace : namespaces)
        {
            File folder = new File(namespace, "structures");

            if (namespace.isDirectory() && folder.isDirectory())
            {
                collectTemplates(structures, folder, namespace.getName(), "");
            }
        }
    }

    /**
     * Walk one namespace's {@code structures} tree. Recursive because vanilla
     * allows {@code /} inside a template name, which the structure block writes
     * out as real nested folders.
     */
    private static void collectTemplates(Set<String> structures, File folder, String namespace, String prefix)
    {
        File[] listed = folder.listFiles();

        if (listed == null)
        {
            return;
        }

        for (File file : listed)
        {
            String name = file.getName();

            if (file.isDirectory())
            {
                collectTemplates(structures, file, namespace, prefix + name + "/");

                continue;
            }

            if (!file.isFile() || !name.endsWith(".nbt"))
            {
                continue;
            }

            String path = prefix + name.substring(0, name.lastIndexOf("."));

            structures.add(DEFAULT_NAMESPACE.equals(namespace) ? path : namespace + ":" + path);
        }
    }

    /**
     * Resolve a structure name to the file backing it, preferring the legacy
     * {@code <save>/structures/<name>.nbt} when that file exists and otherwise
     * pointing at the vanilla {@code <save>/generated/<ns>/structures/<path>.nbt}.
     *
     * <p>An empty name still yields the legacy <i>folder</i>, the contract
     * {@link #collectLegacy} relies on. When neither layout has the template the
     * legacy path comes back, so callers see the same non-existent-{@link File}
     * result legacy gave them — {@link #loadTemplateTag} then falls through to
     * the template manager and {@code checkStructures} reads {@code 0}.</p>
     */
    public static File getStructureFolder(String name)
    {
        File root = saveRoot == null ? null : saveRoot.get();

        /* No save-root wired yet — mirror legacy's graceful empty result
         * (isDirectory() will be false for a bare relative path). */
        String suffix = name.isEmpty() ? "" : "/" + name + ".nbt";
        File legacy = root == null ? new File("structures" + suffix) : new File(root, "structures" + suffix);

        if (name.isEmpty() || legacy.isFile())
        {
            return legacy;
        }

        File generated = getGeneratedFile(name);

        return generated == null ? legacy : generated;
    }

    /**
     * The vanilla generated-template root, {@code <save>/generated}.
     */
    public static File getGeneratedFolder()
    {
        File root = saveRoot == null ? null : saveRoot.get();

        return root == null ? new File("generated") : new File(root, "generated");
    }

    /**
     * Resolve {@code name} ({@code path} or {@code ns:path}) to its vanilla
     * {@code <save>/generated/<ns>/structures/<path>.nbt} file, or {@code null}
     * when the name is not a valid {@link Identifier} — the same names
     * {@link #loadTemplateTag}'s template-manager fallback can serve.
     */
    public static File getGeneratedFile(String name)
    {
        Identifier id = Identifier.tryParse(name);

        if (id == null)
        {
            return null;
        }

        return new File(getGeneratedFolder(), id.getNamespace() + "/structures/" + id.getPath() + ".nbt");
    }

    @Override
    public void run(ServerPlayerEntity player, PacketStructureRequest message)
    {
        MinecraftServer server = player.getServer();

        if (server != null && saveRoot == null)
        {
            final MinecraftServer captured = server;

            saveRoot = () -> captured.getSavePath(WorldSavePath.ROOT).toFile();
        }

        try
        {
            if (!message.name.isEmpty())
            {
                this.sendTemplate(player, message.name);

                return;
            }

            for (String struct : getAllStructures())
            {
                this.sendTemplate(player, struct);
            }
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to send structure template(s) for request \"{}\"", message.name, e);
        }
    }

    /**
     * Resolve the template NBT for {@code key} and push it to the player.
     * Prefers the legacy {@code <save>/structures/<key>.nbt} file (byte-for-byte
     * what 2.7.2 stored); falls back to the vanilla template manager.
     */
    public void sendTemplate(ServerPlayerEntity player, String key)
    {
        NbtCompound tag = this.loadTemplateTag(player, key);

        if (tag != null)
        {
            Dispatcher.sendTo(new PacketStructure(key, tag), player);
        }
    }

    private NbtCompound loadTemplateTag(ServerPlayerEntity player, String key)
    {
        File file = getStructureFolder(key);

        if (file != null && file.isFile())
        {
            try
            {
                return NbtIo.readCompressed(file.toPath(), NbtTagSizeTracker.ofUnlimitedBytes());
            }
            catch (Exception e)
            {
                Blockbuster.LOGGER.warn("Failed to read legacy structure file {}; falling back to vanilla template manager", file, e);
            }
        }

        MinecraftServer server = player.getServer();

        if (server == null)
        {
            return null;
        }

        StructureTemplateManager manager = server.getStructureTemplateManager();
        Identifier id = Identifier.tryParse(key);

        if (id == null)
        {
            return null;
        }

        StructureTemplate template = manager.getTemplate(id).orElse(null);

        if (template == null)
        {
            return null;
        }

        return template.writeNbt(new NbtCompound());
    }

    /**
     * Path helper exposed for tests / batch-4 wiring.
     */
    public static Path getStructureRootPath()
    {
        File root = saveRoot == null ? null : saveRoot.get();

        return root == null ? null : new File(root, "structures").toPath();
    }
}
