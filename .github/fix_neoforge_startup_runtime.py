from pathlib import Path

root = Path.cwd()

# The 1.21.1 WorldRenderer particle seam differs between Fabric/Yarn and the
# NeoForge-patched runtime. Disable this optional visual hook on NeoForge until
# it is reimplemented against NeoForge's render-stage events. Keeping a required
# injector here prevents the entire game from starting.
mixins_json = root / "src/main/resources/blockbuster.client.mixins.json"
mixins_text = mixins_json.read_text(encoding="utf-8")
particle_entry = '\t\t"WorldRendererParticlesMixin",\n'
mixins_text = mixins_text.replace(particle_entry, "")
mixins_json.write_text(mixins_text, encoding="utf-8")

# BlockBuster's shared source still performs Fabric-style direct Registry.register
# calls. NeoForge freezes the vanilla registries before constructing @Mod classes,
# so direct registration throws "Registry is already frozen". NeoForge itself
# exposes BaseMappedRegistry.unfreeze(boolean) for its internal registration
# lifecycle. Use that bridge only around the shared initialization, then restore
# the frozen state before client bootstrap continues.
entrypoint = root / "src/main/java/mchorse/blockbuster/neoforge/BlockbusterNeoForge.java"
entrypoint.write_text('''package mchorse.blockbuster.neoforge;

import mchorse.blockbuster.Blockbuster;
import net.minecraft.registry.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native NeoForge entrypoint for the shared 1.21.1 BlockBuster source. */
@Mod(Blockbuster.MOD_ID)
public final class BlockbusterNeoForge
{
    public BlockbusterNeoForge(IEventBus modEventBus, Dist dist)
    {
        List<Object> unfrozenRegistries = unfreezeVanillaRegistries();

        try
        {
            new Blockbuster().onInitialize();
        }
        finally
        {
            refreezeVanillaRegistries(unfrozenRegistries);
        }

        if (dist == Dist.CLIENT)
        {
            NeoForgeClientBootstrap.initialize();
        }
    }

    private static List<Object> unfreezeVanillaRegistries()
    {
        List<Object> result = new ArrayList<>();

        for (Field field : Registries.class.getDeclaredFields())
        {
            if (!Modifier.isStatic(field.getModifiers()))
            {
                continue;
            }

            try
            {
                field.setAccessible(true);
                Object registry = field.get(null);

                if (registry == null)
                {
                    continue;
                }

                Method unfreeze = findMethod(registry.getClass(), "unfreeze", boolean.class);

                if (unfreeze == null)
                {
                    continue;
                }

                unfreeze.setAccessible(true);
                unfreeze.invoke(registry, false);
                result.add(registry);
            }
            catch (ReflectiveOperationException | RuntimeException ignored)
            {
                /* Non-registry fields and registries without the NeoForge bridge
                 * are intentionally skipped. Required registries will otherwise
                 * fail loudly at their original registration call. */
            }
        }

        return result;
    }

    private static void refreezeVanillaRegistries(List<Object> registries)
    {
        Collections.reverse(registries);

        for (Object registry : registries)
        {
            try
            {
                Method freeze = findMethod(registry.getClass(), "freeze");

                if (freeze != null)
                {
                    freeze.setAccessible(true);
                    freeze.invoke(registry);
                }
            }
            catch (ReflectiveOperationException | RuntimeException exception)
            {
                throw new IllegalStateException("Failed to refreeze a NeoForge registry after BlockBuster registration", exception);
            }
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes)
    {
        Class<?> current = type;

        while (current != null)
        {
            try
            {
                return current.getDeclaredMethod(name, parameterTypes);
            }
            catch (NoSuchMethodException ignored)
            {
                current = current.getSuperclass();
            }
        }

        return null;
    }
}
''', encoding="utf-8")

print("Applied NeoForge startup registry bridge and disabled the stale particle injector.")
