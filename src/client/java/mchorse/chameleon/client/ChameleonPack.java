package mchorse.chameleon.client;

import mchorse.chameleon.mclib.ChameleonTree;
import mchorse.mclib.utils.resources.VerbatimPaths;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Chameleon's skin pack resolver — everything under {@code c.s:} maps to a file
 * inside the Chameleon models folder.
 *
 * <p>Port note: 1.12.2's {@code ChameleonPack} <i>was</i> an
 * {@code IResourcePack} appended to FML's pack list. 1.20.4 has no equivalent
 * pluggable seam, so — exactly as {@code mchorse.blockbuster.client.ActorsPack}
 * (P88) — the class survives as a plain <b>resolver</b> and the lookup is
 * intercepted at the resource-manager boundary by
 * {@code ReloadableResourceManagerImplMixin}.</p>
 *
 * <p>Legacy resolved {@code new File(this.file, location.getResourcePath())} with
 * no path checks at all. This port keeps that lookup but refuses to escape the
 * root folder, so a crafted {@code c.s:../../…} identifier cannot read arbitrary
 * files. (An {@code Identifier} cannot legally contain {@code ".."} anyway; the
 * check is a belt-and-braces boundary guard on a path that takes untrusted
 * strings from morph NBT.)</p>
 */
public class ChameleonPack
{
    /** Shared resolver instance the resource-manager mixin routes through. */
    public static ChameleonPack INSTANCE;

    public File file;

    public ChameleonPack(File file)
    {
        this.file = file;
    }

    /**
     * Whether a {@code domain:path} belongs to Chameleon's skin namespace and a
     * pack is installed. The mixin uses this to decide whether to intercept.
     */
    public static boolean handles(String domain, String path)
    {
        return INSTANCE != null && ChameleonTree.DOMAIN.equals(domain) && !path.endsWith(".mcmeta");
    }

    /**
     * Resolve a {@code c.s} path to an existing file under the models folder, or
     * {@code null} on a miss (which lets the lookup fall through to vanilla).
     *
     * <p>The sanitized path is tried against the {@link VerbatimPaths} side table
     * first, for the same reason {@code ActorsPack.findFile} does (P249, defect
     * E): {@link net.minecraft.util.Identifier} is lowercase-only, so a skin
     * named {@code MySkin.png} arrives here spelled {@code myskin.png} and
     * {@code new File(folder, path)} misses on every case-sensitive filesystem.
     * {@code VerbatimPaths} is domain-agnostic and is populated by
     * {@code ResourceLocation.toIdentifier()}, which is exactly how
     * {@code FolderImageEntry} mints these skins — so Chameleon's spellings are
     * already in it. Recorded spellings are tried in registration order, then the
     * sanitized path itself, so a lookup that never went through
     * {@code toIdentifier()} behaves as before.</p>
     */
    public File findFile(String path)
    {
        if (this.file == null)
        {
            return null;
        }

        for (String verbatim : VerbatimPaths.candidates(ChameleonTree.DOMAIN, path))
        {
            File file = this.resolve(verbatim);

            if (file != null)
            {
                return file;
            }
        }

        return this.resolve(path);
    }

    private File resolve(String path)
    {
        File target = new File(this.file, path);

        try
        {
            File root = this.file.getCanonicalFile();
            File canonical = target.getCanonicalFile();

            if (!canonical.toPath().startsWith(root.toPath()))
            {
                return null;
            }

            return canonical.isFile() ? canonical : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public InputStream open(File file) throws IOException
    {
        return Files.newInputStream(file.toPath());
    }

    public String getPackName()
    {
        return "Chameleon's skin pack";
    }
}
