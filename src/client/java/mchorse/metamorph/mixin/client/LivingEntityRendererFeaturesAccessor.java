package mchorse.metamorph.mixin.client;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Access to a living-entity renderer's feature list (roadmap P80.2).
 *
 * <p>Legacy attached its body-part layer with
 * {@code RenderLivingBase.addLayer(layer)}, a public method. Yarn's counterpart,
 * {@code LivingEntityRenderer.addFeature}, is {@code protected final}, and the
 * {@code features} list it appends to is {@code protected final} as well — so the
 * layer needs one of the two widened. The list accessor is used rather than an
 * {@code @Invoker} on {@code addFeature} because the list is also what
 * {@link mchorse.metamorph.client.render.EntityMorphBodyPartPass} inspects to
 * keep the attachment idempotent (legacy's {@code bodyPartMap} cache).</p>
 *
 * <p>Generic erasure note: the declared element type is only for the Java
 * compiler; the mixin matches on the erased {@code Ljava/util/List;} descriptor.</p>
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererFeaturesAccessor
{
    @Accessor("features")
    List<FeatureRenderer<?, ?>> metamorph$getFeatures();
}
