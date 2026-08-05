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

loader_compile = '    compileOnly "org.sinytra:forgified-fabric-loader:${project.ffl_version}"'
loader_implementation = '    implementation "org.sinytra:forgified-fabric-loader:${project.ffl_version}"'
if loader_compile in build_text:
    build_text = build_text.replace(loader_compile, loader_implementation, 1)
elif loader_implementation not in build_text:
    raise SystemExit("Expected Forgified Fabric Loader dependency was not found")

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

# NeoForge's mapped concrete handler omits the inherited Yarn bridge in this
# workspace. Invoke the stable PlayerAssociatedNetworkHandler API explicitly.
swipe = root / "src/main/java/mchorse/blockbuster/recording/actions/SwipeAction.java"
swipe_text = swipe.read_text(encoding="utf-8")
player_import = "import net.minecraft.server.network.PlayerAssociatedNetworkHandler;\n"
anchor_import = "import net.minecraft.server.network.ServerPlayerEntity;\n"
if player_import not in swipe_text:
    if anchor_import not in swipe_text:
        raise SystemExit("Expected ServerPlayerEntity import was not found")
    swipe_text = swipe_text.replace(anchor_import, player_import + anchor_import, 1)

concrete_send = "serverPlayer.networkHandler.sendPacket("
interface_send = "((PlayerAssociatedNetworkHandler) serverPlayer.networkHandler).sendPacket("
if concrete_send in swipe_text:
    swipe_text = swipe_text.replace(concrete_send, interface_send, 1)
elif interface_send not in swipe_text:
    raise SystemExit("Expected SwipeAction packet-send call was not found")
swipe.write_text(swipe_text, encoding="utf-8")

print("Applied Architectury's NeoForge Yarn patch and BlockBuster compile compatibility fixes.")
