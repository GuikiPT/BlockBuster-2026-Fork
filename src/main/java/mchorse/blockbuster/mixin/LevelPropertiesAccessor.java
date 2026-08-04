package mchorse.blockbuster.mixin;

import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.LevelProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Write access to the save's {@link LevelInfo} (roadmap P207.5, bundled McLib
 * {@code /cheats}).
 *
 * <p>Legacy 1.12.2 had a plain setter — {@code WorldInfo.setAllowCommands(boolean)}.
 * On 1.20.4 {@code LevelInfo.allowCommands} is a private final field of an
 * immutable holder and {@link LevelProperties#areCommandsAllowed()} is
 * read-only (verified with {@code javap} against the loom-cache named jar:
 * no {@code setAllowCommands} anywhere), so the flag is flipped by swapping in
 * a copied {@code LevelInfo}. Technique validated by BBS's own
 * {@code LevelPropertiesAccessor} (reference only — re-implemented here).</p>
 */
@Mixin(LevelProperties.class)
public interface LevelPropertiesAccessor
{
    @Accessor("levelInfo")
    void mclib$setLevelInfo(LevelInfo info);
}
