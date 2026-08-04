package mchorse.blockbuster.mixin;

import mchorse.blockbuster.legacy.LegacyBlockEntityIds;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * P93.1 — block-entity {@code id} aliasing.
 *
 * <p>{@code BlockEntity.createFromNbt} turns the saved {@code id} string into an
 * {@link Identifier} and looks the type up; an unknown id logs
 * {@code "Skipping BlockEntity with id {}"} and drops the block entity, which
 * for a model block silently loses its morph, transform and settings while
 * leaving the block standing. 1.20.4 has no registry-alias mechanism, so the
 * one call that parses the string is redirected instead.</p>
 *
 * <p>Only the parse is replaced — the NBT compound is not mutated and every
 * non-aliased id (i.e. everything vanilla and every other mod) goes through
 * {@link Identifier#tryParse(String)} unchanged. Because the alias resolves to
 * the canonical type, the block entity re-saves under the canonical id and the
 * chunk self-heals.</p>
 *
 * @see LegacyBlockEntityIds
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin
{
    @Redirect(
        method = "createFromNbt",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Identifier;tryParse(Ljava/lang/String;)Lnet/minecraft/util/Identifier;"))
    private static Identifier blockbuster$aliasLegacyBlockEntityId(String raw)
    {
        return LegacyBlockEntityIds.resolve(raw);
    }
}
