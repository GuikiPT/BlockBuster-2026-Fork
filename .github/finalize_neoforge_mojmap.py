from pathlib import Path

root = Path.cwd()
build = root / "build.gradle"
props = root / "gradle.properties"

build_text = build.read_text(encoding="utf-8")
yarn_line = '    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"'
mojmap_line = '    mappings loom.officialMojangMappings()'

if yarn_line not in build_text:
    raise SystemExit("Expected generated Yarn mappings declaration was not found")

build_text = build_text.replace(yarn_line, mojmap_line)

loader_compile = '    compileOnly "org.sinytra:forgified-fabric-loader:${project.ffl_version}"'
loader_implementation = '    implementation "org.sinytra:forgified-fabric-loader:${project.ffl_version}"'
if loader_compile not in build_text:
    raise SystemExit("Expected Forgified Fabric Loader compile dependency was not found")
build_text = build_text.replace(loader_compile, loader_implementation)

iris_old = '    modCompileOnly "maven.modrinth:iris:${project.iris_version}"'
iris_exact = '    modCompileOnly "maven.modrinth:YL57xq9U:oXIoDcGE"'
if iris_old not in build_text:
    raise SystemExit("Expected generated Iris dependency was not found")
build_text = build_text.replace(iris_old, iris_exact)

build.write_text(build_text, encoding="utf-8")

props_lines = props.read_text(encoding="utf-8").splitlines()
props_lines = [
    line
    for line in props_lines
    if not line.startswith("yarn_mappings=") and not line.startswith("iris_version=")
]
props.write_text("\n".join(props_lines).rstrip() + "\n", encoding="utf-8")

print("Configured the generated NeoForge workspace for Mojmap, FFL, and NeoForge Iris.")
