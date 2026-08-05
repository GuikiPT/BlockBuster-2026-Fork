from pathlib import Path

root = Path.cwd()
build = root / "build.gradle"
props = root / "gradle.properties"

build_text = build.read_text(encoding="utf-8")
yarn_line = '    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"'
mojmap_line = '    mappings loom.officialMojangMappings()'

if yarn_line not in build_text:
    raise SystemExit("Expected generated Yarn mappings declaration was not found")

build.write_text(build_text.replace(yarn_line, mojmap_line), encoding="utf-8")

props_lines = props.read_text(encoding="utf-8").splitlines()
props_lines = [line for line in props_lines if not line.startswith("yarn_mappings=")]
props.write_text("\n".join(props_lines).rstrip() + "\n", encoding="utf-8")

print("Configured the generated NeoForge workspace to compile the migrated Mojmap source.")
