package mchorse.metamorph.mixin;

import com.mojang.authlib.GameProfile;

import net.minecraft.block.entity.SkullBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Invoker for {@code SkullBlockEntity.fetchProfile(String)} — the username →
 * full {@link GameProfile} lookup a player disguise needs (roadmap S7/P92).
 *
 * <p>Legacy resolved a player morph's skin through
 * {@code TileEntitySkull.updateGameprofile}. Its 1.20.4 counterpart is this
 * method, and it is exactly the right one: it goes through
 * {@code apiServices.userCache().findByNameAsync} for the name → UUID step and
 * then {@code sessionService.fetchProfile(uuid, true)} for the properties, so
 * the profile comes back carrying the {@code textures} property that
 * {@code PlayerSkinProvider} needs. Results are memoized in a 256-entry,
 * 10-minute {@code LoadingCache}, so repeated lookups of the same name cost
 * nothing.</p>
 *
 * <p>It is {@code private static}, hence the invoker. The name-only overload is
 * the one to call, not the three-argument one — the latter bypasses that cache
 * and would need the {@code ApiServices} handle we do not have.</p>
 *
 * <p><b>Total by contract.</b> When {@code setServices} has not run the backing
 * cache is null and the method answers an immediately-completed
 * {@code Optional.empty()} rather than throwing — the same answer an offline or
 * rate-limited session service gives. {@code MinecraftClient} sets the services
 * up in its constructor, so the client always has them; a dedicated server that
 * does not simply keeps the offline profile.</p>
 */
@Mixin(SkullBlockEntity.class)
public interface SkullBlockEntityProfileInvoker
{
    @Invoker("fetchProfile")
    static CompletableFuture<Optional<GameProfile>> metamorph$fetchProfile(String name)
    {
        throw new AssertionError();
    }
}
