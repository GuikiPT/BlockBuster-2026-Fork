from pathlib import Path

root = Path.cwd()
build = root / "build.gradle"
props = root / "gradle.properties"

build_text = build.read_text(encoding="utf-8")

fabric_repo = '''    maven {
        name = "Fabric"
        url = "https://maven.fabricmc.net/"
    }
'''
architectury_repo = '''    maven {
        name = "Architectury"
        url = "https://maven.architectury.dev/"
    }
'''

if architectury_repo not in build_text:
    if fabric_repo not in build_text:
        raise SystemExit("Expected Fabric Maven repository block was not found")
    build_text = build_text.replace(fabric_repo, architectury_repo + fabric_repo, 1)

plain_mappings = '    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"'
layered_mappings = '''    mappings loom.layered {
        it.mappings("net.fabricmc:yarn:${project.yarn_mappings}:v2")
        it.mappings("dev.architectury:yarn-mappings-patch-neoforge:${project.yarn_mappings_patch_neoforge_version}")
    }'''

if plain_mappings in build_text:
    build_text = build_text.replace(plain_mappings, layered_mappings, 1)
elif layered_mappings not in build_text:
    raise SystemExit("Expected Yarn mappings declaration was not found")

# FFAPI's distribution already embeds Forgified Fabric Loader. Keep its API on
# the compile classpath only and do not add a second runtime/full loader jar,
# which causes duplicate Java modules in NeoForge's development launch.
loader_implementation = '    implementation "org.sinytra:forgified-fabric-loader:${project.ffl_version}"'
loader_compile = '    compileOnly "org.sinytra:forgified-fabric-loader:${project.ffl_version}"'
loader_runtime = '    runtimeOnly "org.sinytra:forgified-fabric-loader:${project.ffl_version}:full"\n'
if loader_implementation in build_text:
    build_text = build_text.replace(loader_implementation, loader_compile, 1)
elif loader_compile not in build_text:
    raise SystemExit("Expected Forgified Fabric Loader compile dependency was not found")
build_text = build_text.replace(loader_runtime, "")

iris_old = '    modCompileOnly "maven.modrinth:iris:${project.iris_version}"'
iris_exact = '    modCompileOnly "maven.modrinth:YL57xq9U:oXIoDcGE"'
if iris_old in build_text:
    build_text = build_text.replace(iris_old, iris_exact, 1)
elif iris_exact not in build_text:
    raise SystemExit("Expected Iris dependency was not found")

build.write_text(build_text, encoding="utf-8")

props_lines = props.read_text(encoding="utf-8").splitlines()
property_name = "yarn_mappings_patch_neoforge_version"
if not any(line.startswith(property_name + "=") for line in props_lines):
    insertion = next(
        (index + 1 for index, line in enumerate(props_lines) if line.startswith("yarn_mappings=")),
        len(props_lines),
    )
    props_lines.insert(insertion, property_name + "=1.21+build.4")

props.write_text("\n".join(props_lines).rstrip() + "\n", encoding="utf-8")

# Forgified Fabric API does not inject FabricItem into NeoForge's Item class,
# so this compatibility method remains callable by BlockBuster but is not a
# Java override on NeoForge.
item_gun = root / "src/main/java/mchorse/blockbuster/common/item/ItemGun.java"
item_gun_text = item_gun.read_text(encoding="utf-8")
override_signature = "    @Override\n    public boolean allowComponentsUpdateAnimation("
plain_signature = "    public boolean allowComponentsUpdateAnimation("
if override_signature in item_gun_text:
    item_gun_text = item_gun_text.replace(override_signature, plain_signature, 1)
elif plain_signature not in item_gun_text:
    raise SystemExit("Expected ItemGun component-update animation method was not found")
item_gun.write_text(item_gun_text, encoding="utf-8")

# NeoForge's packet listener exposes send(Packet) rather than Fabric/Yarn's
# sendPacket bridge. Use that native method and remove the obsolete cast/import.
swipe = root / "src/main/java/mchorse/blockbuster/recording/actions/SwipeAction.java"
swipe_text = swipe.read_text(encoding="utf-8")

player_import = "import net.minecraft.server.network.PlayerAssociatedNetworkHandler;\n"
swipe_text = swipe_text.replace(player_import, "")

fabric_send = "serverPlayer.networkHandler.sendPacket("
cast_send = "((PlayerAssociatedNetworkHandler) serverPlayer.networkHandler).sendPacket("
neoforge_send = "serverPlayer.networkHandler.send("

if cast_send in swipe_text:
    swipe_text = swipe_text.replace(cast_send, neoforge_send, 1)
elif fabric_send in swipe_text:
    swipe_text = swipe_text.replace(fabric_send, neoforge_send, 1)
elif neoforge_send not in swipe_text:
    raise SystemExit("Expected SwipeAction packet-send call was not found")

swipe.write_text(swipe_text, encoding="utf-8")

# KeyMapping's key-to-binding map is named differently in Yarn source,
# NeoForge production, and the layered NeoForge development jar. A hard-coded
# Mixin accessor therefore crashes at startup in at least one namespace.
# Remove the accessor mixin and let the one consumer resolve the private static
# map defensively at runtime. If reflection is blocked, gun input degrades
# gracefully instead of preventing Minecraft from starting.
mixins_json = root / "src/main/resources/blockbuster.client.mixins.json"
mixins_text = mixins_json.read_text(encoding="utf-8")
mixin_entry = '\t\t"KeyBindingKeyMapAccessor",\n'
mixins_text = mixins_text.replace(mixin_entry, "")
mixins_json.write_text(mixins_text, encoding="utf-8")

gun_handler = root / "src/client/java/mchorse/blockbuster/events/GunShootHandler.java"
gun_text = gun_handler.read_text(encoding="utf-8")
gun_text = gun_text.replace(
    "import mchorse.blockbuster.mixin.client.KeyBindingKeyMapAccessor;\n",
    "",
)
gun_text = gun_text.replace(
    "import java.util.Map;\n",
    "import java.lang.reflect.Field;\nimport java.lang.reflect.Modifier;\nimport java.util.Map;\n",
    1,
)

instance_anchor = "    private static GunShootHandler instance;\n"
reflection_fields = '''    private static GunShootHandler instance;

    private static volatile Field keyToBindingsField;
    private static boolean keyToBindingsLookupAttempted;
'''
if instance_anchor in gun_text:
    gun_text = gun_text.replace(instance_anchor, reflection_fields, 1)
elif "private static volatile Field keyToBindingsField;" not in gun_text:
    raise SystemExit("Expected GunShootHandler instance field was not found")

old_lookup = "        Map<InputUtil.Key, KeyBinding> bindings = KeyBindingKeyMapAccessor.getKeyToBindings();\n\n        if (bindings.get(key) == shoot)"
new_lookup = '''        Map<InputUtil.Key, KeyBinding> bindings = keyToBindings();

        if (bindings == null)
        {
            return;
        }

        if (bindings.get(key) == shoot)'''
if old_lookup in gun_text:
    gun_text = gun_text.replace(old_lookup, new_lookup, 1)
elif "Map<InputUtil.Key, KeyBinding> bindings = keyToBindings();" not in gun_text:
    raise SystemExit("Expected KeyBinding accessor usage was not found")

helper_anchor = '''    /**
     * Held state of the shoot bind: read off the attack bind while the two share
'''
reflection_helper = '''    @SuppressWarnings("unchecked")
    private static Map<InputUtil.Key, KeyBinding> keyToBindings()
    {
        Field cached = keyToBindingsField;

        if (cached != null)
        {
            try
            {
                return (Map<InputUtil.Key, KeyBinding>) cached.get(null);
            }
            catch (IllegalAccessException ignored)
            {
                return null;
            }
        }

        if (keyToBindingsLookupAttempted)
        {
            return null;
        }

        keyToBindingsLookupAttempted = true;

        for (String name : new String[] {"KEY_TO_BINDINGS", "MAP", "field_1658", "f_90810_"})
        {
            try
            {
                Field field = KeyBinding.class.getDeclaredField(name);

                if (Modifier.isStatic(field.getModifiers()) && Map.class.isAssignableFrom(field.getType()))
                {
                    field.setAccessible(true);
                    keyToBindingsField = field;

                    return (Map<InputUtil.Key, KeyBinding>) field.get(null);
                }
            }
            catch (ReflectiveOperationException | RuntimeException ignored)
            {}
        }

        for (Field field : KeyBinding.class.getDeclaredFields())
        {
            if (!Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType()))
            {
                continue;
            }

            try
            {
                field.setAccessible(true);
                Object value = field.get(null);

                if (!(value instanceof Map<?, ?> map) || map.isEmpty())
                {
                    continue;
                }

                Map.Entry<?, ?> entry = map.entrySet().iterator().next();

                if (entry.getKey() instanceof InputUtil.Key && entry.getValue() instanceof KeyBinding)
                {
                    keyToBindingsField = field;

                    return (Map<InputUtil.Key, KeyBinding>) map;
                }
            }
            catch (IllegalAccessException | RuntimeException ignored)
            {}
        }

        return null;
    }

    /**
     * Held state of the shoot bind: read off the attack bind while the two share
'''
if helper_anchor in gun_text:
    gun_text = gun_text.replace(helper_anchor, reflection_helper, 1)
elif "private static Map<InputUtil.Key, KeyBinding> keyToBindings()" not in gun_text:
    raise SystemExit("Expected GunShootHandler shootDown documentation anchor was not found")

gun_handler.write_text(gun_text, encoding="utf-8")

print("Applied Architectury's NeoForge Yarn patch and BlockBuster compatibility fixes.")
