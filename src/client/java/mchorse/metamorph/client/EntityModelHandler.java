package mchorse.metamorph.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import mchorse.mclib.utils.AtomicWrite;
import mchorse.mclib.utils.JsonUtils;
import mchorse.mclib.utils.PastCopies;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.capabilities.render.EntitySelector;
import mchorse.metamorph.capabilities.render.EntitySelectorAdapter;
import mchorse.metamorph.capabilities.render.ModelRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Entity model handler (roadmap P54.1).
 *
 * <p>Client-only holder for the entity-selector system: the shared {@link
 * #selectors} list, the pretty-printing Gson with the {@link
 * EntitySelectorAdapter}, {@code config/metamorph/selectors.json}
 * persistence, and the per-entity {@link ModelRenderer} attachment (a
 * {@link WeakHashMap}, chosen over a common-set duck mixin because the feature
 * is purely client-side). Render cancellation lives in the {@code
 * LivingEntityRendererSelectorMixin}, which routes through {@link
 * #renderEntity} with the {@link #currentRendering} re-entrancy guard —
 * selector morphs may themselves be EntityMorphs that render living
 * entities.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/EntityModelHandler.java
 */
public class EntityModelHandler
{
    /** Shared list of entity selectors (loaded from selectors.json). */
    public static final List<EntitySelector> selectors = new ArrayList<EntitySelector>();

    /**
     * Client singleton, set by {@code MetamorphClient.init}. The selector
     * render mixin reads it null-safely (inert until the client wires it).
     */
    public static EntityModelHandler INSTANCE;

    /** Pretty-printing Gson with the selector adapter. */
    public final Gson entitySelector;

    /** Per-entity model-renderer attachment (client-only). */
    private final Map<LivingEntity, ModelRenderer> renderers = new WeakHashMap<LivingEntity, ModelRenderer>();

    /** Re-entrancy guard for {@link #renderEntity}. */
    public Entity currentRendering;

    private static final Type SELECTOR_LIST = new TypeToken<List<EntitySelector>>() {}.getType();

    public EntityModelHandler()
    {
        GsonBuilder gson = new GsonBuilder();

        gson.registerTypeAdapter(EntitySelector.class, new EntitySelectorAdapter());
        gson.setPrettyPrinting();

        this.entitySelector = gson.create();
    }

    public ModelRenderer get(LivingEntity entity)
    {
        return this.renderers.get(entity);
    }

    public ModelRenderer getOrCreate(LivingEntity entity)
    {
        return this.renderers.computeIfAbsent(entity, (e) -> new ModelRenderer());
    }

    /**
     * Legacy {@code onUpdateEntity(LivingUpdateEvent)} — the client-side,
     * once-per-tick driver for every living entity's {@link ModelRenderer}
     * (P290).
     *
     * <p>Legacy read the capability off the entity (Forge attached one to
     * every {@code EntityLivingBase}) and called {@code cap.update(entity)}
     * whenever {@code entity.world.isRemote}. The port keeps the attachment in
     * a {@link WeakHashMap}, so "every entity has one" becomes
     * {@link #getOrCreate}. Everything downstream is unchanged:
     * {@code ModelRenderer.update} re-evaluates the selector on its own 10-tick
     * timer (and on a {@code selectorsUpdate} bump) and then ticks the matched
     * morph — which is the only thing that advances that morph's
     * {@code Animation.progress}.</p>
     *
     * <p>There is deliberately <b>no</b> {@code selectors.isEmpty()} fast path.
     * It looks free — "no selectors, nothing to tick" — but clearing is exactly
     * the observable thing: once the render path stopped calling
     * {@link ModelRenderer#update} (see {@link #renderEntity}), this loop became
     * the <i>only</i> writer of {@code selector}/{@code morph}. Skipping it on
     * an empty list means an entity that matched a selector the user has since
     * deleted keeps {@code canRender() == true} and goes on being drawn as the
     * deleted selector's morph for the rest of the session. Legacy iterated
     * unconditionally (every {@code EntityLivingBase} carried the capability and
     * {@code onUpdateEntity} fired regardless), and the work on an empty list is
     * an empty inner loop.</p>
     *
     * @param entities the client world's living entities; null is legal.
     */
    public void updateEntities(Iterable<? extends Entity> entities)
    {
        if (entities == null || !Metamorph.opEntitySelector.get())
        {
            return;
        }

        for (Entity entity : entities)
        {
            if (entity instanceof LivingEntity)
            {
                this.getOrCreate((LivingEntity) entity).update((LivingEntity) entity);
            }
        }
    }

    /**
     * The {@code LivingEntityRenderer.render} HEAD hook. Returns whether
     * vanilla rendering must be cancelled. Gated on the OP-synced {@code
     * entity_selectors} config and guarded against re-entrancy.
     */
    public boolean renderEntity(LivingEntity entity, double x, double y, double z,
        MatrixStack matrices, VertexConsumerProvider consumers, int light, float partialTicks)
    {
        if (!Metamorph.opEntitySelector.get() || this.currentRendering != null)
        {
            return false;
        }

        ModelRenderer cap = this.getOrCreate(entity);

        /* P290: this used to be `cap.update(entity)` — the legacy client
         * LivingUpdateEvent tick, relocated into the render path. That made a
         * per-TICK driver a per-FRAME one: `ModelRenderer.update` runs
         * `morph.update(target)`, which is `Animation.progress++`, so a
         * `duration = 10` morph animation finished in ten *frames* (~1/6 s at
         * 60 fps) instead of ten ticks (0.5 s) — visually instantaneous, i.e.
         * indistinguishable from "the animation never plays". It was also
         * frame-rate dependent and stopped dead whenever the entity was culled.
         * The tick driver is back where legacy had it, in
         * {@link #updateEntities}; the render path only reads. */

        if (!cap.canRender())
        {
            return false;
        }

        this.currentRendering = entity;

        boolean cancel = cap.render(entity, x, y, z, matrices, consumers, light, partialTicks);

        this.currentRendering = null;

        return cancel;
    }

    /**
     * <p><b>P284:</b> {@link #selectorsUnreadable} is set when the file exists
     * but cannot be read, and {@link #saveSelectors(File)} then refuses to
     * write. The editor saves on a 1-second debounce armed by any edit — and by
     * a transiently-invalid Mojangson keystroke, which nulls the selector's
     * match — so without this a corrupt {@code selectors.json} became an empty
     * one within a second of the user opening the editor.</p>
     */
    public void loadSelectors(File selectorsFile)
    {
        if (selectorsFile == null || !selectorsFile.exists())
        {
            this.selectorsUnreadable = false;

            return;
        }

        try
        {
            String json = new String(Files.readAllBytes(selectorsFile.toPath()), StandardCharsets.UTF_8);
            List<EntitySelector> loaded = this.entitySelector.fromJson(json, SELECTOR_LIST);

            selectors.clear();

            if (loaded != null)
            {
                for (EntitySelector selector : loaded)
                {
                    if (selector != null)
                    {
                        selectors.add(selector);
                    }
                }
            }

            ModelRenderer.selectorsUpdate = System.currentTimeMillis();
            this.selectorsUnreadable = false;
        }
        catch (Exception e)
        {
            this.selectorsUnreadable = true;

            Metamorph.LOGGER.error("Could not read entity selectors from " + selectorsFile
                + " — saving is disabled for this session so the file is not replaced with an empty list.", e);
        }
    }

    /** P284 — see {@link #loadSelectors(File)}. */
    public boolean selectorsUnreadable;

    public void saveSelectors(File selectorsFile)
    {
        if (this.selectorsUnreadable && selectorsFile != null && selectorsFile.exists())
        {
            return;
        }

        try
        {
            selectorsFile.getParentFile().mkdirs();

            JsonElement element = this.entitySelector.toJsonTree(selectors, SELECTOR_LIST);

            /* P284: rotate + atomic, like every other user-content writer. */
            PastCopies.rotate(selectorsFile, ".json");
            AtomicWrite.writeString(selectorsFile, JsonUtils.jsonToPretty(element));
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("Failed to save entity selectors to " + selectorsFile, e);
        }
    }

    /** Serialize the current selector list to a pretty JSON string. */
    public String toJson()
    {
        return JsonUtils.jsonToPretty(this.entitySelector.toJsonTree(selectors, SELECTOR_LIST));
    }

    /** Parse a selector list from a JSON string (total: null → empty list). */
    public List<EntitySelector> fromJson(String json)
    {
        List<EntitySelector> loaded = this.entitySelector.fromJson(json, SELECTOR_LIST);

        return loaded == null ? new ArrayList<EntitySelector>() : loaded;
    }
}
