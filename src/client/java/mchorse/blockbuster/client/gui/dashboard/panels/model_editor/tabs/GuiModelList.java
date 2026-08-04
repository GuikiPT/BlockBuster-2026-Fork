package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.tabs;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.loaders.lazy.ModelLazyLoaderJSON;
import mchorse.blockbuster.api.resource.StreamEntry;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.GuiModelEditorPanel;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.parsing.ModelExporter;
import mchorse.blockbuster.utils.BlockbusterPaths;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringSearchListElement;
import mchorse.mclib.client.gui.framework.elements.modals.GuiListModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiMessageModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiPromptModal;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Patterns;
import mchorse.metamorph.api.EntityUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Model editor Models tab (roadmap P137) — the searchable model list plus the
 * three sidebar actions: save-as (dupe), export-from-a-vanilla-mob, and open the
 * model's folder.
 *
 * <p>Legacy source (ported 1:1):
 * {@code blockbuster-1.12/.../model_editor/tabs/GuiModelList.java}.</p>
 *
 * <p><b>1.20.4 mapping.</b></p>
 * <ul>
 * <li><b>The mob list.</b> Legacy walked every {@code ForgeRegistries.ENTITIES}
 * entry's superclass chain looking for {@code EntityLivingBase}. 1.20.4's
 * {@code EntityType.getBaseClass()} is useless for that (it answers
 * {@code Entity.class} for every type — verified against its bytecode), and
 * constructing ~130 entities to test each with {@code instanceof} is a heavy way
 * to ask a static question. {@link DefaultAttributeRegistry#hasDefinitionFor} is
 * the precise equivalent: registering default attributes is mandatory for a
 * living entity type and impossible for a non-living one. See
 * {@link #livingEntityIds()} for the one explicit exclusion.</li>
 * <li><b>Spawning + the renderer.</b> {@code EntityList.createEntityByIDFromName}
 * &rarr; {@code EntityType.create(world)};
 * {@code RenderManager.getEntityRenderObject} &rarr;
 * {@code EntityRenderDispatcher.getRenderer}; {@code RenderLivingBase} &rarr;
 * {@link LivingEntityRenderer}. Same two casts, both guarded the way legacy's
 * blanket {@code catch} was.</li>
 * <li>{@code GuiUtils.openFolder(new File(ClientProxy.configFile, "models/" +
 * name))} &rarr; the same call over {@link BlockbusterPaths#models()}.</li>
 * <li>{@code I18n.format(key, args)} &rarr; {@link IKey#format(String, Object...)}.</li>
 * </ul>
 *
 * <p><b>Legacy quirks preserved.</b> {@link #FILENAME_SLASHES} is McLib's
 * filename pattern with {@code /} allowed (models live in nested folders);
 * save-as silently does nothing when the name already exists (no message
 * modal); the export name has its {@code :} replaced with {@code _} and is
 * loaded as an <b>unsaved</b> model through an empty {@link StreamEntry}, so it
 * only reaches disk when the user presses save; and the list defaults to
 * {@code steve} when nothing is selected yet.</p>
 */
public class GuiModelList extends GuiModelEditorTab
{
    public static final Pattern FILENAME_SLASHES = Pattern.compile(Patterns.FILENAME.pattern().replace("]*$", "/]*$"));

    public GuiStringSearchListElement models;
    private GuiIconElement dupe;
    private GuiIconElement export;
    private GuiIconElement folder;

    public GuiModelList(MinecraftClient mc, GuiModelEditorPanel panel)
    {
        super(mc, panel);

        this.title = IKey.lang("blockbuster.gui.me.models.title");

        this.models = new GuiStringSearchListElement(mc, (str) -> this.panel.setModel(str.get(0)));
        this.models.flex().relative(this.area).y(20).w(140).h(1, -20);
        this.models.list.scroll.scrollSpeed = 16;

        this.dupe = new GuiIconElement(mc, Icons.DUPE, (b) -> this.saveModel());
        this.export = new GuiIconElement(mc, Icons.UPLOAD, (b) -> this.exportModel());
        this.folder = new GuiIconElement(mc, Icons.FOLDER, (b) -> this.openFolder());

        GuiElement sidebar = Elements.row(mc, 0, 0, 20, this.dupe, this.export, this.folder);

        sidebar.flex().relative(this.models).x(1F).y(-20).h(20).anchorX(1F).row(0).resize();

        this.add(this.models, sidebar);
    }

    public void updateModelList()
    {
        String current = this.models.list.getCurrentFirst();

        this.models.list.clear();
        this.models.list.add(ModelCustom.MODELS.keySet());
        this.models.list.sort();

        if (current == null)
        {
            current = "steve";
        }

        this.models.list.setCurrentScroll(current);
    }

    private void saveModel()
    {
        GuiModal.addFullModal(this, () ->
        {
            GuiPromptModal modal = new GuiPromptModal(mc, IKey.lang("blockbuster.gui.me.models.name"), this::saveModel).setValue(this.panel.modelName);

            modal.text.validator((string) -> FILENAME_SLASHES.matcher(string).find());

            return modal;
        });
    }

    private void saveModel(String name)
    {
        boolean exists = ModelCustom.MODELS.containsKey(name);

        if (!exists)
        {
            if (!this.panel.saveModel(name))
            {
                return;
            }

            this.models.list.add(name);
            this.models.list.sort();
            this.models.list.setCurrent(name);
        }
    }

    /**
     * Every registered entity type that is a living entity, sorted — the export
     * modal's contents.
     *
     * <p>{@code minecraft:player} is filtered out. Legacy never listed it
     * because 1.12.2's entity registry had no player entry, and its
     * {@code EntityType.create(world)} answers {@code null} here, so offering it
     * would only produce an error modal.</p>
     */
    public static List<String> livingEntityIds()
    {
        List<String> mobs = new ArrayList<String>();

        for (Identifier id : Registries.ENTITY_TYPE.getIds())
        {
            EntityType<?> type = Registries.ENTITY_TYPE.get(id);

            if (type == EntityType.PLAYER || !DefaultAttributeRegistry.hasDefinitionFor(type))
            {
                continue;
            }

            mobs.add(id.toString());
        }

        Collections.sort(mobs);

        return mobs;
    }

    private void exportModel()
    {
        List<String> mobs = livingEntityIds();

        GuiModal.addFullModal(this, () ->
        {
            GuiListModal modal = new GuiListModal(this.mc, IKey.lang("blockbuster.gui.me.models.pick"), this::exportModel);

            return modal.addValues(mobs);
        });
    }

    private void exportModel(String name)
    {
        if (name.isEmpty())
        {
            return;
        }

        try
        {
            Identifier id = Identifier.tryParse(name);
            EntityType<?> type = id == null ? null : Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
            /* P252: EntityUtils.createEntity, not EntityType.create — the
             * latter answers null for a feature-gated type (minecraft:breeze),
             * which livingEntityIds() *does* list, so exporting it raised an
             * error modal for a mob the list itself offered. */
            Entity entity = type == null ? null : EntityUtils.createEntity(this.mc.world, type);

            if (!(entity instanceof LivingEntity))
            {
                throw new IllegalArgumentException(name);
            }

            EntityRenderer<?> render = this.mc.getEntityRenderDispatcher().getRenderer(entity);

            if (!(render instanceof LivingEntityRenderer))
            {
                throw new IllegalArgumentException(name);
            }

            ModelExporter exporter = new ModelExporter((LivingEntity) entity, (LivingEntityRenderer<?, ?>) render);
            Model model = exporter.exportModel(name);

            name = name.replaceAll(":", "_");
            model.fillInMissing();
            this.panel.setModel(name, model, new ModelLazyLoaderJSON(new StreamEntry("", 0)));
        }
        catch (Exception e)
        {
            GuiModal.addFullModal(this, () -> new GuiMessageModal(this.mc, IKey.format("blockbuster.gui.me.models.error", e.getMessage())));

            e.printStackTrace();
        }
    }

    private void openFolder()
    {
        GuiUtils.openFolder(BlockbusterPaths.models().resolve(this.panel.modelName).toFile().getAbsolutePath());
    }

    @Override
    public void draw(GuiContext context)
    {
        this.area.draw(0xaa000000);

        super.draw(context);
    }
}
