package mchorse.blockbuster.mixin.client;

import java.io.File;
import java.io.InputStream;
import java.util.Optional;

import mchorse.blockbuster.client.ActorsPack;
import mchorse.blockbuster.client.ActorsResourcePack;
import mchorse.blockbuster.client.textures.Textures;
import mchorse.chameleon.client.ChameleonPack;
import mchorse.mclib.utils.resources.MultiResourceLocation;
import mchorse.mclib.utils.resources.MultiskinResources;
import mchorse.mclib.utils.resources.RLUtils;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S7 P88 — actor-pack virtual namespace seam.
 *
 * <p>Replaces 1.12.2's {@code ActorsPack implements IResourcePack} appended to
 * FML's resource-pack list. 1.20.4 has no equivalent pluggable seam, so the
 * lookup is intercepted at the resource-manager boundary: when an identifier is
 * in the {@code b.a} (skins on disk) namespace — or {@code http}/{@code https}
 * once P90's URL resolver is installed — {@link ActorsPack} resolves it and the
 * request is answered here instead of walking the vanilla pack stack. This is
 * the same seam McLib's multiskin coremod used, and since P249 the multiskin
 * branch (P91) is served here too: {@code mclib:multiskin/<id>} goes through
 * {@link MultiskinResources} to {@code RLUtils.getStreamForMultiskin}.</p>
 *
 * <p>{@code .mcmeta} sidecar requests are deliberately <b>not</b> intercepted
 * (legacy relied on {@code FileNotFoundException} to let them fall through), and
 * a miss returns without cancelling so vanilla still produces its own absent
 * result.</p>
 *
 * <p>Yarn signature verified via javap against the loom-cache named jar:
 * {@code Optional<Resource> getResource(Identifier)}.</p>
 */
@Mixin(ReloadableResourceManagerImpl.class)
public class ReloadableResourceManagerImplMixin
{
    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void blockbuster$actorsPack(Identifier id, CallbackInfoReturnable<Optional<Resource>> cir)
    {
        String namespace = id.getNamespace();
        String path = id.getPath();

        if (path.endsWith(".mcmeta"))
        {
            return;
        }

        /* P249 (defect F): mclib:multiskin/<id> — the 1.20.4 stand-in for
         * legacy's `instanceof MultiResourceLocation` coremod branch. */
        if (MultiskinResources.handles(namespace, path))
        {
            MultiResourceLocation multi = MultiskinResources.resolve(namespace, path);

            if (multi == null)
            {
                return;
            }

            /* The composite is about to be created in the texture map under
             * this id — record the key so ClientProxy-style F3+T eviction
             * (Textures.clearMultiTextures) can find it. */
            Textures.MULTISKIN_KEYS.add(id);

            InputSupplier<InputStream> supplier = () -> RLUtils.getStreamForMultiskin(multi);

            cir.setReturnValue(Optional.of(new Resource(ActorsResourcePack.INSTANCE, supplier)));

            return;
        }

        /* Chameleon port: c.s:<path> — the same seam, for the bundled Chameleon
         * mod's skin folder (legacy ChameleonPack implements IResourcePack). */
        if (ChameleonPack.handles(namespace, path))
        {
            ChameleonPack chameleon = ChameleonPack.INSTANCE;
            File chameleonFile = chameleon.findFile(path);

            if (chameleonFile == null)
            {
                return;
            }

            InputSupplier<InputStream> supplier = () -> chameleon.open(chameleonFile);

            cir.setReturnValue(Optional.of(new Resource(ActorsResourcePack.INSTANCE, supplier)));

            return;
        }

        if (!ActorsPack.handles(namespace, path))
        {
            return;
        }

        ActorsPack pack = ActorsPack.INSTANCE;

        if (namespace.equals(ActorsPack.DOMAIN))
        {
            File file = pack.findFile(namespace, path);

            if (file == null)
            {
                return;
            }

            InputSupplier<InputStream> supplier = () -> pack.openFile(namespace, path, file);

            cir.setReturnValue(Optional.of(new Resource(ActorsResourcePack.INSTANCE, supplier)));
        }
        else
        {
            /* http/https — only reached once ActorsPack.urlSkinResolver (P90)
             * is installed (handles() gates on it). */
            InputSupplier<InputStream> supplier = () -> pack.open(namespace, path);

            cir.setReturnValue(Optional.of(new Resource(ActorsResourcePack.INSTANCE, supplier)));
        }
    }
}
