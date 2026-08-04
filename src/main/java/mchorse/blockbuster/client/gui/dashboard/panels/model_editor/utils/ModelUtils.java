package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils;

import java.io.StringWriter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.json.ModelAdapter;
import mchorse.blockbuster.api.json.ModelLimbAdapter;
import mchorse.blockbuster.api.json.ModelPoseAdapter;

/**
 * Model utilities
 *
 * <p>The legacy FQCN
 * ({@code mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.ModelUtils})
 * is kept for diff-ability, but the class lives in the <b>main</b> source set —
 * it has no Minecraft-client dependency (pure Gson) and byte-parity golden tests
 * must call it headlessly. The GUI model editor (S13+) uses this same class.</p>
 */
public class ModelUtils
{
    /**
     * Save model to JSON
     *
     * This method is responsible for making the JSON output pretty printed and
     * 4 spaces indented (fuck 2 space indentation).
     */
    public static String toJson(Model model)
    {
        GsonBuilder builder = new GsonBuilder().setPrettyPrinting();

        builder.registerTypeAdapter(Model.class, new ModelAdapter());
        builder.registerTypeAdapter(ModelLimb.class, new ModelLimbAdapter());
        builder.registerTypeAdapter(ModelPose.class, new ModelPoseAdapter());
        builder.excludeFieldsWithoutExposeAnnotation();

        Gson gson = builder.create();
        StringWriter writer = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(writer);

        jsonWriter.setIndent("    ");
        gson.toJson(model, Model.class, jsonWriter);

        String output = writer.toString();

        /* Prettify arrays */
        output = output.replaceAll("\\n\\s+(?=-?\\d|\\])", " ");

        return output;
    }
}
