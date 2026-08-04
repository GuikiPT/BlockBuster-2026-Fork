package mchorse.blockbuster.commands.model;

import mchorse.blockbuster.api.ModelPack;
import mchorse.blockbuster.api.loaders.lazy.IModelLazyLoader;
import mchorse.blockbuster.api.loaders.lazy.ModelLazyLoaderJSON;
import mchorse.blockbuster.api.loaders.lazy.ModelLazyLoaderOBJ;
import mchorse.blockbuster.api.loaders.lazy.ModelLazyLoaderVOX;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;

import java.io.File;

/**
 * Headless core of {@code /model report} (roadmap P73).
 *
 * <p>Walks the {@code config/blockbuster/models} folder recursively and builds
 * the same clipboard string 1.12.2's {@code SubCommandModelReport} produced:
 * the {@code "Models folder skins report:\n\n"} header, then every file
 * annotated by its loader class and (for images) its texture-manager status.
 * The pure file-walk + loader-class annotation is fully testable now; the
 * texture-status lines are delegated to {@link ITextureRegistryOps} (no-op
 * until the S7 texture manager, roadmap P87).</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/.../commands/model/SubCommandModelReport.java}. The
 * loader annotation is by <em>exact loader-class identity</em>
 * ({@code loader.getClass() == ModelLazyLoaderJSON.class}) — subclasses
 * ({@code ModelLazyLoaderOBJ}/{@code ModelLazyLoaderVOX}) are matched by
 * {@code instanceof} guarded on the file extension, exactly as legacy.</p>
 */
public final class ModelReportBuilder
{
    private ModelReportBuilder()
    {}

    /**
     * Build the report string that legacy copied to the clipboard
     * ({@code output.toString().trim()}).
     *
     * @param models the {@code config/blockbuster/models} folder
     * @param pack   the loaded model pack (its {@code models} map decides the
     *               loader annotation); may be {@code null}
     * @param ops    texture-registry seam for the image status lines
     */
    public static String buildReport(File models, ModelPack pack, ITextureRegistryOps ops)
    {
        StringBuilder output = new StringBuilder();

        output.append("Models folder skins report:\n\n");

        processRecursively(output, models, "", "", false, pack, ops);

        return output.toString().trim();
    }

    private static void processRecursively(StringBuilder output, File models, String prefix, String indent, boolean isModel, ModelPack pack, ITextureRegistryOps ops)
    {
        if (!models.isDirectory())
        {
            return;
        }

        File[] files = models.listFiles();

        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            if (!file.isFile())
            {
                continue;
            }

            String name = file.getName();
            String aux = "";
            boolean obj = name.endsWith(".obj");
            boolean vox = name.endsWith(".vox");

            if (!isModel && (obj || name.equals("model.json") || vox))
            {
                IModelLazyLoader loader = pack == null ? null : pack.models.get(prefix);

                if (loader instanceof ModelLazyLoaderOBJ && obj)
                {
                    isModel = true;
                    aux += ", loaded OBJ";
                }
                else if (loader instanceof ModelLazyLoaderVOX && vox)
                {
                    isModel = true;
                    aux += ", loaded VOX";
                }
                else if (loader != null && loader.getClass() == ModelLazyLoaderJSON.class)
                {
                    isModel = true;
                    aux += ", loaded JSON";
                }
            }

            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif"))
            {
                ResourceLocation location = RLUtils.create("b.a:" + (prefix.isEmpty() ? "" : prefix + "/") + name);

                switch (ops.reportStatus(location))
                {
                    case MISSING:
                        aux += ", loaded but missing";
                        break;
                    case LOADED:
                        aux += ", loaded";
                        break;
                    default:
                        break;
                }
            }

            output.append(indent);
            output.append(name);
            output.append(aux);
            output.append("\n");
        }

        for (File file : files)
        {
            if (!file.isDirectory())
            {
                continue;
            }

            output.append(indent);
            output.append(file.getName());
            output.append("/\n");
            processRecursively(output, file, prefix.isEmpty() ? file.getName() : prefix + "/" + file.getName(), indent + "    ", isModel, pack, ops);
        }
    }
}
