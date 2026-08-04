package mchorse.blockbuster.client.render;

import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.formats.obj.ShapeKey;
import mchorse.blockbuster.common.OrientedBB;
import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;

import java.util.List;
import java.util.Map;

/**
 * P80 render seam for the "current morph" that {@link RenderCustomModel} draws.
 *
 * <p>Legacy {@code RenderCustomModel} typed its {@code current} field as
 * {@code blockbuster_pack.morphs.CustomMorph} and read
 * {@code getKey()}/{@code getPose(entity, partialTicks)}/{@code materials}/
 * {@code getShapesForRendering(partialTicks)}/{@code scale} off it. That morph
 * class lives in the not-yet-ported blockbuster_pack morph phase (S10), so the
 * renderer here talks to this narrow interface instead — mirroring how
 * {@link mchorse.blockbuster.client.model.ModelCustom#current} is typed
 * {@code Object} until the concrete morph lands. {@code CustomMorph} will
 * implement this seam when it arrives; nothing else needs to change.</p>
 */
public interface IModelCustomMorph
{
    /** Model repository key ({@code ModelCustom.MODELS} lookup). */
    String getKey();

    /**
     * Pose for this frame, or {@code null} to fall back to the model's
     * {@code "standing"} pose. May inspect the entity (pose-per-action).
     */
    ModelPose getPose(LivingEntity entity, float partialTicks);

    /** Per-render material overrides (OBJ path); may be {@code null}. */
    Map<String, ResourceLocation> getMaterials();

    /** Shape-key weights for this frame; may be {@code null}. */
    List<ShapeKey> getShapesForRendering(float partialTicks);

    /** Morph-level uniform scale multiplier (multiplies model scale). */
    float getScale();

    /**
     * Legacy {@code CustomMorph.keying} — the chroma-key blend
     * ({@code glBlendEquation(GL_FUNC_REVERSE_SUBTRACT)} +
     * {@code blendFunc(ZERO, ZERO)}) that 1.12.2's {@code ModelCustom.render}
     * wrapped the limb loop in, read off {@code this.current}
     * ({@code blockbuster-1.12/.../client/model/ModelCustom.java:114}).
     *
     * <p>Default {@code false} for the same reason the rest of this interface
     * exists: the model-editor preview and the test stubs have no morph behind
     * them, and legacy's {@code current == null} branch drew with the ordinary
     * alpha blend.</p>
     */
    default boolean isKeying()
    {
        return false;
    }

    /**
     * Legacy {@code CustomMorph.orientedBBlimbs} — the per-limb oriented
     * bounding boxes this morph owns (cloned off the model blueprint by
     * {@code CustomMorph.fillObbs}).
     *
     * <p>Read by {@code ModelCustom}/{@code ModelCustomRenderer} exactly where
     * 1.12.2 read {@code current.orientedBBlimbs}: the per-frame {@code center}
     * refresh and {@code updateObbs}. {@code null} (the default) means "no
     * morph behind this draw" — the model-editor preview and the test stubs —
     * which is legacy's {@code orientedBBlimbs != null} guard.</p>
     */
    default Map<ModelLimb, List<OrientedBB>> getOrientedBBs()
    {
        return null;
    }

    /**
     * The morph itself, for the feature layers that need more than the pose —
     * {@code LayerBodyPart} reads its {@code BodyPartManager} (via
     * {@code IBodyPartProvider}) and uses it as the animation source for the
     * body-part transform tween. Implementations that are not backed by a real
     * morph (test stubs) may return {@code null}, and the layer skips.
     */
    default AbstractMorph getMorph()
    {
        return null;
    }
}
