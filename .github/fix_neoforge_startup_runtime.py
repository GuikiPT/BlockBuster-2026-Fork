from pathlib import Path
import json

root = Path.cwd()

# The generated client mixins still contain several 1.20-era injection points.
# On NeoForge 1.21.1, a missing optional visual/HUD injection must warn and skip
# instead of aborting the entire client. Working mixins continue to apply.
mixins_json = root / "src/main/resources/blockbuster.client.mixins.json"
mixins = json.loads(mixins_json.read_text(encoding="utf-8"))
mixins["required"] = False
mixins.setdefault("injectors", {})["defaultRequire"] = 0
mixins["client"] = [
    name for name in mixins.get("client", [])
    if name != "WorldRendererParticlesMixin"
]
mixins_json.write_text(json.dumps(mixins, indent="\t") + "\n", encoding="utf-8")

# BlockBuster's shared source still performs Fabric-style direct Registry.register
# calls. NeoForge freezes vanilla registries before constructing @Mod classes.
# Calling the added unfreeze method by name is not reliable across Yarn/Mojang/
# production namespaces, so toggle the actual frozen field, run shared content
# registration, and invoke the normal freeze method afterwards for validation and
# callbacks. This bridge is intentionally limited to the initialization window.
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Native NeoForge entrypoint for the shared 1.21.1 BlockBuster source. */
@Mod(Blockbuster.MOD_ID)
public final class BlockbusterNeoForge
{
    private record RegistryState(Object registry, Field frozenField, boolean wasFrozen) {}

    public BlockbusterNeoForge(IEventBus modEventBus, Dist dist)
    {
        List<RegistryState> registryStates = unfreezeVanillaRegistries();

        try
        {
            new Blockbuster().onInitialize();
        }
        finally
        {
            refreezeVanillaRegistries(registryStates);
        }

        if (dist == Dist.CLIENT)
        {
            NeoForgeClientBootstrap.initialize();
        }
    }

    private static List<RegistryState> unfreezeVanillaRegistries()
    {
        Map<Object, RegistryState> unique = new IdentityHashMap<>();

        for (Field holderField : Registries.class.getDeclaredFields())
        {
            if (!Modifier.isStatic(holderField.getModifiers()))
            {
                continue;
            }

            try
            {
                holderField.setAccessible(true);
                Object registry = holderField.get(null);

                if (registry == null || unique.containsKey(registry))
                {
                    continue;
                }

                Field frozen = findFrozenField(registry.getClass());

                if (frozen == null)
                {
                    continue;
                }

                frozen.setAccessible(true);
                boolean wasFrozen = frozen.getBoolean(registry);

                if (wasFrozen)
                {
                    frozen.setBoolean(registry, false);
                }

                unique.put(registry, new RegistryState(registry, frozen, wasFrozen));
            }
            catch (ReflectiveOperationException | RuntimeException ignored)
            {
                /* Non-registry constants are expected here. */
            }
        }

        if (unique.isEmpty())
        {
            throw new IllegalStateException("Could not access NeoForge vanilla registry frozen state");
        }

        return new ArrayList<>(unique.values());
    }

    private static void refreezeVanillaRegistries(List<RegistryState> states)
    {
        Collections.reverse(states);

        for (RegistryState state : states)
        {
            if (!state.wasFrozen())
            {
                continue;
            }

            try
            {
                Method freeze = findMethod(state.registry().getClass(), "freeze");

                if (freeze != null)
                {
                    freeze.setAccessible(true);
                    freeze.invoke(state.registry());
                }
                else
                {
                    state.frozenField().setBoolean(state.registry(), true);
                }
            }
            catch (ReflectiveOperationException | RuntimeException exception)
            {
                throw new IllegalStateException("Failed to refreeze a NeoForge registry after BlockBuster registration", exception);
            }
        }
    }

    private static Field findFrozenField(Class<?> type)
    {
        for (String name : new String[] {"frozen", "field_33085", "f_205845_"})
        {
            Class<?> current = type;

            while (current != null)
            {
                try
                {
                    Field field = current.getDeclaredField(name);

                    if (field.getType() == boolean.class)
                    {
                        return field;
                    }
                }
                catch (NoSuchFieldException ignored)
                {}

                current = current.getSuperclass();
            }
        }

        return null;
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

print("Applied namespace-independent NeoForge registry bridge and optional client mixins.")
