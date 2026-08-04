package mchorse.blockbuster.mixin.client.iris;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches Iris' private custom-uniform lists (S21 P218).
 *
 * <p>{@code CustomUniforms} keeps two parallel lists: {@code uniforms} (what
 * gets pushed) and {@code uniformOrder} (dependency-resolved push order). A
 * uniform appended to {@code uniformOrder} after {@code build(...)} has run is
 * pushed with the rest and needs no expression, which is exactly what an
 * option uniform is — a plain per-frame value with no dependencies.</p>
 *
 * <p>Raw {@link List} deliberately: the element type is
 * {@code net.irisshaders.iris.uniforms.custom.cached.CachedUniform}, and naming
 * it here would force this accessor's own class to resolve an Iris type at
 * verification time on installs where {@code IrisMixinPlugin} already decided
 * not to apply anything.</p>
 */
@Mixin(targets = "net.irisshaders.iris.uniforms.custom.CustomUniforms", remap = false)
public interface CustomUniformsAccessor
{
    @Accessor(value = "uniformOrder", remap = false)
    List blockbuster$uniformOrder();

    @Accessor(value = "uniforms", remap = false)
    List blockbuster$uniforms();
}
