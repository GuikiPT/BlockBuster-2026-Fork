package mchorse.blockbuster.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.StructureReloader;
import mchorse.blockbuster.client.ActorsPack;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.SkinConverter;
import mchorse.blockbuster.client.model.parsing.ModelExporter;
import mchorse.blockbuster.client.model.parsing.ModelExporterOBJ;
import mchorse.blockbuster.commands.model.CombineJob;
import mchorse.blockbuster.commands.model.ModelReportBuilder;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketReloadModels;
import mchorse.blockbuster.utils.BlockbusterPaths;
import mchorse.blockbuster.utils.TextureUtils;
import mchorse.mclib.utils.files.GlobalTree;
import mchorse.mclib.utils.files.entries.AbstractEntry;
import mchorse.mclib.utils.files.entries.FolderEntry;
import mchorse.mclib.utils.resources.MultiResourceLocation;
import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.mclib.utils.resources.TextureProcessor;
import mchorse.metamorph.api.EntityUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

/**
 * Bundled Blockbuster client command {@code /model} (roadmap P73 + P74):
 * {@code reload}, {@code report}, {@code clear}, {@code combine} (batch 1) and
 * {@code export}, {@code export_obj}, {@code convert}, {@code clear_structures}
 * (batch 2).
 *
 * <p>Legacy {@code CommandModel} was a McLib {@code SubCommandBase} client
 * command (Forge {@code ClientCommandHandler}); {@code checkPermission} returned
 * {@code true} — no local permission check, the server only gates the reload
 * packet. This port registers it through Fabric's
 * {@link ClientCommandRegistrationCallback} (mirroring the bundled Aperture
 * {@code CameraCommands}), each subcommand a Brigadier literal with a trailing
 * {@code greedyString} tail split on spaces so the legacy loose-arg parsing
 * survives 1:1. The headless-testable cores live in
 * {@code mchorse.blockbuster.commands.model} ({@link ModelReportBuilder},
 * {@link CombineJob}); this class only parses, dispatches and touches the
 * client-only surfaces (clipboard, texture ops, background export).</p>
 *
 * <p>Batch 2 keeps its legacy cores where they already are: the entity → JSON
 * exporter is {@link ModelExporter}, the JSON → OBJ exporter is
 * {@link ModelExporterOBJ}, the skin pixel-copy table is {@link SkinConverter} and
 * {@code clear_structures} goes through the {@link StructureReloader} indirection
 * (1.12.2 called {@code StructureMorph.reloadStructures()} directly). What lives
 * here is only the argument parsing, the file/resource I/O and the chat feedback.</p>
 *
 * <p>Legacy sources:
 * {@code blockbuster-1.12/.../commands/CommandModel.java} and
 * {@code .../commands/model/SubCommandModel{Reload,Report,Clear,Combine,Export,
 * ExportObj,ConvertSkin,ClearStructures}.java}.</p>
 */
public class ModelCommands
{
    private static final String[] EMPTY = new String[0];

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register(ModelCommands::registerCommands);
    }

    /** Visible for the registration test — the eight literals are a user contract. */
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registry)
    {
        dispatcher.register(ClientCommandManager.literal("model")
            .then(sub("reload", ModelCommands::reload))
            .then(sub("report", ModelCommands::report))
            .then(sub("clear", ModelCommands::clear))
            .then(sub("combine", ModelCommands::combine))
            .then(sub("export", ModelCommands::export, ModelCommands::completeExport))
            .then(sub("export_obj", ModelCommands::exportObj, ModelCommands::completeExportObj))
            .then(sub("convert", ModelCommands::convert, ModelCommands::completeConvert))
            .then(sub("clear_structures", ModelCommands::clearStructures)));
    }

    /**
     * A subcommand literal that runs {@code handler} with no args, or with the
     * space-split tail of a trailing {@code greedyString} — the legacy
     * string-array command parsing.
     */
    private static LiteralArgumentBuilder<FabricClientCommandSource> sub(String literal, SubHandler handler)
    {
        return sub(literal, handler, null);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> sub(String literal, SubHandler handler, TabCompleter completer)
    {
        RequiredArgumentBuilder<FabricClientCommandSource, String> args =
            ClientCommandManager.argument("args", StringArgumentType.greedyString())
                .executes(context ->
                {
                    handler.run(context.getSource(), StringArgumentType.getString(context, "args").split(" "));

                    return 1;
                });

        if (completer != null)
        {
            args.suggests((context, builder) -> suggest(completer, builder));
        }

        return ClientCommandManager.literal(literal)
            .executes(context ->
            {
                handler.run(context.getSource(), EMPTY);

                return 1;
            })
            .then(args);
    }

    private interface SubHandler
    {
        void run(FabricClientCommandSource source, String[] args);
    }

    /**
     * The legacy {@code getTabCompletions(server, sender, args, pos)} surface,
     * reduced to what the client subcommands actually used: the space-split args so
     * far, with a trailing {@code ""} for the token being typed.
     */
    private interface TabCompleter
    {
        List<String> complete(String[] args);
    }

    /**
     * Suggestion adapter for the trailing {@code greedyString}, mirroring bundled
     * McLib's {@code McCommandBase.suggest}: split the tail, offset the builder onto
     * the last token, and filter by it the way legacy's
     * {@code getListOfStringsMatchingLastWord} did.
     */
    private static CompletableFuture<Suggestions> suggest(TabCompleter completer, SuggestionsBuilder builder)
    {
        String remaining = builder.getRemaining();
        /* -1 keeps the trailing "" that represents the token being typed */
        String[] args = remaining.isEmpty() ? new String[] {""} : remaining.split(" ", -1);

        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        String last = args[args.length - 1].toLowerCase();

        for (String completion : completer.complete(args))
        {
            if (completion.toLowerCase().startsWith(last))
            {
                offset.suggest(completion);
            }
        }

        return offset.buildFuture();
    }

    /* /model reload [force] */
    private static void reload(FabricClientCommandSource source, String[] args)
    {
        boolean force = args.length >= 1 && !args[0].isEmpty() && parseBoolean(args[0]);

        /* Reload models and skin locally, then ask the server to reload its
         * domain models (op-gated server side in ServerHandlerReloadModels). */
        CommonProxy.loadModels(force);

        Dispatcher.sendToServer(new PacketReloadModels(force));
    }

    /* /model report */
    private static void report(FabricClientCommandSource source, String[] args)
    {
        File models = BlockbusterPaths.models().toFile();
        String report = ModelReportBuilder.buildReport(models, CommonProxy.pack, ClientTextureRegistryOps.INSTANCE);

        MinecraftClient.getInstance().keyboard.setClipboard(report);

        source.sendFeedback(Blockbuster.l10n.success("commands.model_report"));
    }

    /* /model clear [path] */
    private static void clear(FabricClientCommandSource source, String[] args)
    {
        String prefix = args.length == 0 ? "" : args[0];

        ClientTextureRegistryOps.INSTANCE.clearTextures(prefix);
    }

    /* /model combine <paths...> */
    private static void combine(FabricClientCommandSource source, String[] args)
    {
        if (args.length == 0 || args[0].isEmpty())
        {
            /* Legacy getRequiredArgs() == 1 → the usage/wrapper path. */
            source.sendFeedback(Blockbuster.l10n.error("commands.combining_empty", 0));
            return;
        }

        List<FolderEntry> entries = CombineJob.resolve(args);

        if (entries.isEmpty())
        {
            source.sendFeedback(Blockbuster.l10n.error("commands.combining_empty", 0));
            return;
        }

        List<MultiResourceLocation> toExport = CombineJob.generate(entries);

        if (toExport.isEmpty())
        {
            source.sendFeedback(Blockbuster.l10n.error("commands.combining_folders_empty", 0));
            return;
        }

        source.sendFeedback(Blockbuster.l10n.info("commands.started_combining", toExport.size()));

        try
        {
            new Thread(new CombineThread(source, toExport)).start();
        }
        catch (Exception e)
        {}
    }

    /* /model export <entity_name> [entity_tag] */
    private static void export(FabricClientCommandSource source, String[] args)
    {
        if (args.length == 0 || args[0].isEmpty())
        {
            /* Legacy getRequiredArgs() == 1 → the usage/wrapper path */
            source.sendError(Blockbuster.l10n.error("model.export.wrong_type", ""));

            return;
        }

        String type = args[0];
        Entity entity = createEntity(type, source.getWorld());

        if (entity == null)
        {
            source.sendError(Blockbuster.l10n.error("model.export.wrong_type", type));

            return;
        }

        if (args.length > 1)
        {
            try
            {
                NbtCompound tag = new NbtCompound();

                entity.writeNbt(tag);
                tag.copyFrom(StringNbtReader.parse(String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
                entity.readNbt(tag);
            }
            catch (Exception e)
            {
                source.sendError(Text.translatable("metamorph.error.morph.nbt", e.getMessage()));

                return;
            }
        }

        EntityRenderer<?> render = source.getClient().getEntityRenderDispatcher().getRenderer(entity);

        if (!(render instanceof LivingEntityRenderer) || !(entity instanceof LivingEntity))
        {
            Blockbuster.LOGGER.warn("Can't export a model for " + type + ": no living entity renderer");
            source.sendError(Blockbuster.l10n.error("model.export.wrong_type", type));

            return;
        }

        /* Export the model */
        ModelExporter exporter = new ModelExporter((LivingEntity) entity, (LivingEntityRenderer<?, ?>) render);

        String output = exporter.exportJSON(type);
        File exportFolder = exportFolder();

        exportFolder.mkdirs();

        /* Save exported model */
        File destination = new File(exportFolder, sanitize(type) + ".json");

        try (PrintWriter writer = new PrintWriter(destination))
        {
            writer.print(output);
        }
        catch (Exception e)
        {
            source.sendError(Blockbuster.l10n.error("model.export.error_save"));

            return;
        }

        MutableText file = Text.literal(destination.getName());

        file.setStyle(file.getStyle()
            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, destination.getAbsolutePath()))
            .withUnderline(Boolean.TRUE));

        source.sendFeedback(Blockbuster.l10n.success("model.export.saved", type, file));
    }

    /**
     * Legacy created the entity through {@code EntityList.createEntityByIDFromName},
     * which answered {@code null} for anything not in the entity registry — including
     * the player, which is why {@code /model export minecraft:player} was never a
     * thing. {@code EntityType.create} is the same door on 1.20.4, and an
     * unparseable id (legacy's {@code new ResourceLocation(...)} would have thrown)
     * comes back {@code null} too so the caller reports one error for both.
     *
     * <p>P252: the actual construction is {@link EntityUtils#createEntity} so a
     * feature-gated type ({@code minecraft:breeze} on 1.20.4, where
     * {@code EntityType.create(world)} answers {@code null} without the
     * experimental pack) exports like any other mob — same registry-membership
     * rule the creative morph section follows.</p>
     */
    private static Entity createEntity(String type, World world)
    {
        if (world == null)
        {
            return null;
        }

        Identifier id = Identifier.tryParse(type);

        if (id == null)
        {
            return null;
        }

        EntityType<?> entityType = Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);

        if (entityType == null)
        {
            return null;
        }

        try
        {
            return EntityUtils.createEntity(world, entityType);
        }
        catch (Exception e)
        {
            e.printStackTrace();

            return null;
        }
    }

    /* /model export_obj <model_name> [pose] */
    private static void exportObj(FabricClientCommandSource source, String[] args)
    {
        if (args.length == 0 || args[0].isEmpty())
        {
            /* Legacy getRequiredArgs() == 1 → the usage/wrapper path */
            source.sendError(Blockbuster.l10n.error("model.export.no_model", ""));

            return;
        }

        String modelName = args[0];
        ModelCustom model = ModelCustom.MODELS.get(modelName);

        if (model == null)
        {
            source.sendError(Blockbuster.l10n.error("model.export.no_model", modelName));

            return;
        }

        Model data = model.model;
        ModelPose pose = args.length >= 2 ? data.getPose(args[1]) : data.getPose("standing");
        String obj = new ModelExporterOBJ(data, pose).export(modelName);

        /* Save */
        String filename = sanitize(modelName);
        File exportFolder = exportFolder();
        File destination = new File(exportFolder, filename + ".obj");

        exportFolder.mkdirs();

        if (data.defaultTexture != null)
        {
            try
            {
                String mtl = "# MTL generated by Blockbuster (version " + Blockbuster.VERSION + ")\n\nnewmtl default\nKd 1.000000 1.000000 1.000000\nNi 1.000000\nd 1.000000\nillum 2\nmap_Kd " + filename + ".png";

                FileUtils.writeStringToFile(new File(exportFolder, filename + ".mtl"), mtl, StandardCharsets.UTF_8);
            }
            catch (Exception e)
            {}

            try
            {
                BufferedImage image = readTexture(source.getClient(), data.defaultTexture);

                ImageIO.write(image, "png", new File(exportFolder, filename + ".png"));
            }
            catch (Exception e)
            {}
        }

        try
        {
            FileUtils.writeStringToFile(destination, obj, StandardCharsets.UTF_8);

            source.sendFeedback(Blockbuster.l10n.success("model.export.obj", modelName));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            source.sendError(Blockbuster.l10n.error("model.export.obj", modelName));
        }
    }

    /**
     * Read a texture's pixels for the OBJ sidecar PNG.
     *
     * <p>Legacy went straight to {@code Minecraft.getResourceManager().getResource},
     * because its {@code b.a} skin domain was served by an {@code IResourcePack}
     * registered with FML. 1.20.4 has no such hook, so Blockbuster's own domain is
     * resolved through {@link ActorsPack} (P88) first and everything else — a vanilla
     * {@code minecraft:textures/entity/…} default, which is what
     * {@code /model export} writes — through the resource manager.</p>
     */
    private static BufferedImage readTexture(MinecraftClient mc, ResourceLocation location) throws IOException
    {
        String domain = location.getResourceDomain();
        String path = location.getResourcePath();

        if (ActorsPack.handles(domain, path))
        {
            try (InputStream stream = ActorsPack.INSTANCE.open(domain, path))
            {
                return ImageIO.read(stream);
            }
        }

        try (InputStream stream = mc.getResourceManager().open(location.toIdentifier()))
        {
            return ImageIO.read(stream);
        }
    }

    /* /model convert <steve|fred> <skin> */
    private static void convert(FabricClientCommandSource source, String[] args)
    {
        if (args.length < 2 || args[0].isEmpty() || args[1].isEmpty())
        {
            /* Legacy getRequiredArgs() == 2 → the usage/wrapper path */
            source.sendError(Blockbuster.l10n.error("commands.convert_model", args.length == 0 ? "" : args[0]));

            return;
        }

        String model = args[0];
        String skin = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (!(model.equals("steve") || model.equals("fred")))
        {
            source.sendError(Blockbuster.l10n.error("commands.convert_model", model));

            return;
        }

        /* If extension or path exist, then it means we need to use the full path, not
         * not the shortened version... */
        String path = model + "/" + (skin.contains(".") || skin.contains("/") ? "skins/" + skin : skin);

        try
        {
            BufferedImage image;

            try (InputStream stream = ActorsPack.INSTANCE.open(ActorsPack.DOMAIN, path))
            {
                image = ImageIO.read(stream);
            }

            int w = image.getWidth();
            int h = image.getHeight();

            /* Check for correct aspect ratio */
            if (!SkinConverter.isValidAspect(w, h))
            {
                source.sendError(Blockbuster.l10n.error("commands.convert_skin_size", w, h));

                return;
            }

            BufferedImage target = SkinConverter.convert(image, model.equals("steve"));

            /* Set target to opposite model */
            String targetModel = model.equals("steve") ? "fred" : "steve";
            File file = new File(BlockbusterPaths.models().toFile(), targetModel + "/skins/" + skin);

            /* DEVIATION: legacy called file.mkdirs() on the *destination file*, which
             * created a directory where the PNG was about to be written — so the write
             * only ever succeeded when it was overwriting a skin that already existed
             * (mkdirs() no-ops on an existing path). Creating the parent instead is
             * what it meant to do; broken output is not load-bearing. */
            file.getParentFile().mkdirs();
            ImageIO.write(target, "png", file);

            target.flush();
            image.flush();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            source.sendError(Blockbuster.l10n.error("commands.convert_skin", model, skin, String.valueOf(e.getMessage())));

            return;
        }

        source.sendFeedback(Blockbuster.l10n.success("commands.convert_skin", model, skin));
    }

    /* /model clear_structures */
    private static void clearStructures(FabricClientCommandSource source, String[] args)
    {
        StructureReloader.reload();
    }

    /* Tab completion */

    private static List<String> completeExport(String[] args)
    {
        if (args.length != 1)
        {
            return Collections.emptyList();
        }

        List<String> names = new ArrayList<String>();

        for (Identifier id : Registries.ENTITY_TYPE.getIds())
        {
            names.add(id.toString());
        }

        return names;
    }

    private static List<String> completeExportObj(String[] args)
    {
        return args.length == 1 ? new ArrayList<String>(ModelCustom.MODELS.keySet()) : Collections.<String>emptyList();
    }

    /**
     * Legacy's convert completion: {@code steve}/{@code fred} for the model, then the
     * chosen model's skins straight off McLib's global file tree.
     */
    private static List<String> completeConvert(String[] args)
    {
        if (args.length == 1)
        {
            return Arrays.asList("steve", "fred");
        }

        if (!Arrays.asList("steve", "fred").contains(args[0]))
        {
            return Collections.emptyList();
        }

        String skin = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String path = ActorsPack.DOMAIN + "/" + args[0] + "/skins/" + skin;
        FolderEntry skins = GlobalTree.TREE.getByPath(path, null);
        String name = FilenameUtils.getBaseName(FilenameUtils.getPathNoEndSeparator(path));

        if (skins == null || !skins.title.equals(name))
        {
            return Collections.emptyList();
        }

        List<String> strings = new ArrayList<String>();
        String prefix = skin.contains("/") ? skin.substring(0, skin.lastIndexOf("/") + 1) : "";

        for (AbstractEntry entry : skins.getEntries())
        {
            if (entry.title.contains(".."))
            {
                continue;
            }

            strings.add(prefix + entry.title);
        }

        return strings;
    }

    /** {@code config/blockbuster/export} — both exporters' destination. */
    private static File exportFolder()
    {
        return new File(BlockbusterPaths.configRoot().toFile(), "export");
    }

    /**
     * Legacy's output-filename sanitizer, shared by both export subcommands —
     * identical file naming matters for users' scripts.
     */
    private static String sanitize(String name)
    {
        return name.replaceAll("[^\\w\\d_-]", "_");
    }

    private static boolean parseBoolean(String input)
    {
        return input.equals("true") || input.equals("1");
    }

    /**
     * Background image-combining job — port of legacy
     * {@code SubCommandModelCombine.CombineThread}. Composites each
     * {@link MultiResourceLocation} through the P15 {@link TextureProcessor} and
     * writes {@code config/blockbuster/export/combined_<i>.png}; feedback is
     * marshalled back onto the client thread (a modern-idiom refinement over the
     * legacy off-thread chat send).
     */
    public static class CombineThread implements Runnable
    {
        public final FabricClientCommandSource source;
        public final List<MultiResourceLocation> locations;

        public CombineThread(FabricClientCommandSource source, List<MultiResourceLocation> locations)
        {
            this.source = source;
            this.locations = locations;
        }

        @Override
        public void run()
        {
            int i = 0;

            for (MultiResourceLocation location : this.locations)
            {
                try
                {
                    BufferedImage image = TextureProcessor.process(location);
                    File folder = new File(BlockbusterPaths.configRoot().toFile(), "export");
                    File file = TextureUtils.getFirstAvailableFile(folder, "combined_" + i);

                    folder.mkdirs();
                    ImageIO.write(image, "png", file);

                    this.feedback(Blockbuster.l10n.info("commands.combined", i));

                    Thread.sleep(50);
                }
                catch (Exception e)
                {}

                i += 1;
            }

            this.feedback(Blockbuster.l10n.info("commands.finished_combining"));
        }

        private void feedback(Text message)
        {
            MinecraftClient.getInstance().execute(() -> this.source.sendFeedback(message));
        }
    }
}
