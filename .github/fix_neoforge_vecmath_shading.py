from pathlib import Path

build = Path("build.gradle")
text = build.read_text(encoding="utf-8")

# NeoForge loads nested JarJar libraries as separate secure modules. The legacy
# javax.vecmath bundle is discovered, but its classes are not visible to the
# Blockbuster module in both the dev launch and production launcher. Unpack only
# javax.vecmath classes into Blockbuster's own resources/classes output instead.
configuration_anchor = "dependencies {\n"
configuration_block = '''configurations {
    vecmathShade
}

dependencies {
'''
if "vecmathShade" not in text:
    if configuration_anchor not in text:
        raise SystemExit("Expected dependencies block was not found")
    text = text.replace(configuration_anchor, configuration_block, 1)

old_dependencies = '''    implementation "javax.vecmath:vecmath:1.5.2"
    include "javax.vecmath:vecmath:1.5.2"
'''
new_dependencies = '''    implementation "javax.vecmath:vecmath:1.5.2"
    vecmathShade "javax.vecmath:vecmath:1.5.2"
'''
if old_dependencies in text:
    text = text.replace(old_dependencies, new_dependencies, 1)
elif new_dependencies not in text:
    raise SystemExit("Expected vecmath dependency declarations were not found")

process_anchor = '''processResources {
    inputs.property "version", project.version
'''
process_shading = '''processResources {
    inputs.property "version", project.version

    from({ configurations.vecmathShade.collect { zipTree(it) } }) {
        include "javax/vecmath/**"
    }
'''
if process_shading not in text:
    if process_anchor not in text:
        raise SystemExit("Expected processResources block was not found")
    text = text.replace(process_anchor, process_shading, 1)

build.write_text(text, encoding="utf-8")
print("Shaded javax.vecmath classes directly into the NeoForge mod output.")
