package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import mchorse.blockbuster.api.Model;
import mchorse.mclib.utils.AtomicWrite;

/**
 * P137 — headless core of {@code GuiModelEditorPanel.saveModel}. The GUI keeps
 * the OBJ/MTL companion copy ({@code previous.copyFiles}), the hot-reload
 * ({@code Blockbuster.proxy.loadModels}) and the dirty-flag flip; the file write
 * itself is extracted here so the save round-trip can be golden-tested.
 *
 * <p>Legacy contract (see
 * {@code blockbuster-1.12/.../model_editor/GuiModelEditorPanel.java#saveModel}):
 * an empty name returns {@code false} with no file written; otherwise the model
 * is serialized with {@link ModelUtils#toJson(Model)} and written UTF-8 to
 * {@code <modelsDir>/<name>/model.json} with the parent folder created. Any
 * exception is swallowed (logged) and returns {@code false} — never crashes.</p>
 */
public final class ModelEditorSaver
{
    private ModelEditorSaver()
    {}

    /**
     * Write {@code model} as {@code <modelsDir>/<name>/model.json}.
     *
     * @return {@code true} on a successful write; {@code false} when the name is
     *         empty or an I/O error occurred (no partial success is reported).
     */
    public static boolean saveModel(String name, Model model, Path modelsDir)
    {
        if (name == null || name.isEmpty())
        {
            return false;
        }

        try
        {
            Path folder = modelsDir.resolve(name);

            Files.createDirectories(folder);

            /* P284: Files.write truncates model.json before the serialized model
             * exists, and a custom model has no backup chain — the editor's
             * in-memory copy is the only other one, and it goes when the screen
             * closes. Same bytes, written to a sibling temp file and moved into
             * place, so a serialization or disk failure leaves the previous
             * model.json intact. */
            AtomicWrite.writeString(folder.resolve("model.json"), ModelUtils.toJson(model));

            return true;
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return false;
        }
    }
}
