package mchorse.metamorph.client.render;

import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.mixin.client.LivingEntityRendererFeaturesAccessor;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The real body-part pass (roadmap P80.2) — legacy {@code setupBodyPart} plus
 * the {@code bodyPartMap} layer cache it depended on.
 *
 * <p>Legacy attached one {@code LayerBodyPart} per {@code RenderLivingBase} the
 * first time a morph of that mob was set up, kept the mapping in a static
 * {@code Map<Render, LayerBodyPart>}, and then armed the layer by assigning
 * {@code layer.morph} before each draw. Both halves are here:
 * {@link #feature(LivingEntityRenderer)} is the cache, {@link #arm} is the
 * assignment — restoring the previous value on {@link #disarm}, which legacy did
 * not do (see {@link LayerBodyPartFeature}).</p>
 *
 * <p>{@code initBodyParts()} is called from {@link #arm} because legacy called it
 * from {@code EntityMorph.render}/{@code renderOnScreen} immediately before
 * {@code setupBodyPart}; it is the latch that gives each part its dummy host.</p>
 *
 * <p>This is the phase's installer: one {@link #install()}, one line in
 * {@code MetamorphClient.init()}.</p>
 */
public class EntityMorphBodyPartPass implements IEntityMorphBodyPartPass
{
    /** Legacy {@code EntityMorph.bodyPartMap}, by renderer identity. */
    private final Map<LivingEntityRenderer<?, ?>, LayerBodyPartFeature> features = new IdentityHashMap<LivingEntityRenderer<?, ?>, LayerBodyPartFeature>();

    /**
     * Renderers whose attachment was requested while a feature list was being
     * iterated. See {@link #inPass}.
     */
    private final Map<LivingEntityRenderer<?, ?>, Boolean> pending = new IdentityHashMap<LivingEntityRenderer<?, ?>, Boolean>();

    /**
     * True while a {@link LayerBodyPartFeature} is inside its {@code render} —
     * i.e. while vanilla is walking some renderer's {@code features} list.
     *
     * <p>An entity morph worn as a body part <i>of an entity morph</i> re-enters
     * {@link #arm} at that moment, and if the inner disguise happens to be the
     * same mob type, attaching a feature right there would append to the very
     * list being iterated — a {@code ConcurrentModificationException} mid-frame.
     * A first-time attachment is therefore deferred: the renderer goes into
     * {@link #pending} and is attached at the next top-level {@code arm}, so the
     * nested parts appear from the following frame instead of crashing this one.
     * Nothing in 1.12.2 hit this because a Forge {@code LayerRenderer} list was
     * only ever appended to at model-setup time.</p>
     */
    static boolean inPass;

    /**
     * Install the real pass into {@link EntityMorphRenderer}. Idempotent.
     */
    public static void install()
    {
        EntityMorphRenderer.bodyPartPass = new EntityMorphBodyPartPass();
    }

    @Override
    public Object arm(EntityRenderDispatcher dispatcher, LivingEntity dummy, EntityMorph morph)
    {
        if (!inPass && !this.pending.isEmpty())
        {
            this.drainPending();
        }

        if (dispatcher == null || dummy == null || morph == null || morph.parts.parts.isEmpty())
        {
            return null;
        }

        morph.parts.initBodyParts();

        EntityRenderer<?> renderer = dispatcher.getRenderer(dummy);

        if (!(renderer instanceof LivingEntityRenderer))
        {
            return null;
        }

        LayerBodyPartFeature feature = this.feature((LivingEntityRenderer<?, ?>) renderer);

        if (feature == null)
        {
            return null;
        }

        Armed armed = new Armed(feature, feature.morph);

        feature.morph = morph;

        return armed;
    }

    @Override
    public void disarm(Object armed)
    {
        if (armed instanceof Armed)
        {
            Armed state = (Armed) armed;

            state.feature.morph = state.previous;
        }
    }

    /**
     * The feature attached to this renderer, attaching it on first use. Null when
     * the attachment cannot be made <i>right now</i>: either the renderer's
     * feature list is unreachable (a mixin-less environment, which is what the
     * headless tests see) or a list is mid-iteration ({@link #inPass}), in which
     * case the renderer is queued for the next top-level draw.
     */
    public LayerBodyPartFeature feature(LivingEntityRenderer<?, ?> renderer)
    {
        LayerBodyPartFeature feature = this.features.get(renderer);

        if (feature != null)
        {
            return feature;
        }

        if (inPass)
        {
            this.pending.put(renderer, Boolean.TRUE);

            return null;
        }

        return this.attach(renderer);
    }

    /** Attach the queued renderers, now that no feature list is being walked. */
    private void drainPending()
    {
        for (LivingEntityRenderer<?, ?> renderer : new ArrayList<LivingEntityRenderer<?, ?>>(this.pending.keySet()))
        {
            this.attach(renderer);
        }

        this.pending.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LayerBodyPartFeature attach(LivingEntityRenderer<?, ?> renderer)
    {
        List<FeatureRenderer<?, ?>> list = features(renderer);

        if (list == null)
        {
            return null;
        }

        LayerBodyPartFeature feature = new LayerBodyPartFeature((FeatureRendererContext<LivingEntity, EntityModel<LivingEntity>>) (FeatureRendererContext) renderer);

        ((List) list).add(feature);
        this.features.put(renderer, feature);

        return feature;
    }

    /**
     * The renderer's feature list through the access mixin, or null when the
     * mixin is not applied (JUnit without the Knot classloader).
     */
    private static List<FeatureRenderer<?, ?>> features(LivingEntityRenderer<?, ?> renderer)
    {
        if (renderer instanceof LivingEntityRendererFeaturesAccessor)
        {
            return ((LivingEntityRendererFeaturesAccessor) renderer).metamorph$getFeatures();
        }

        return null;
    }

    /** What {@link #arm} hands to {@link #disarm}: the feature and what it held. */
    public static final class Armed
    {
        public final LayerBodyPartFeature feature;
        public final EntityMorph previous;

        public Armed(LayerBodyPartFeature feature, EntityMorph previous)
        {
            this.feature = feature;
            this.previous = previous;
        }
    }
}
