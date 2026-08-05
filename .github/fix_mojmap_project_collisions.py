from __future__ import annotations

from pathlib import Path

ROOT = Path.cwd()
MORPH_PATH = ROOT / "src/main/java/mchorse/blockbuster_pack/morphs/StructureMorph.java"

if not MORPH_PATH.exists():
    raise SystemExit(f"Missing expected morph source: {MORPH_PATH}")

text = MORPH_PATH.read_text(encoding="utf-8")
original = text

# Loom's mapping migration can mistake this project-owned StructureMorph for
# Minecraft's mapped StructureRenderer symbol. Repair only the BlockBuster
# morph class; the real client structure.StructureRenderer remains untouched.
text = text.replace(
    "public class StructureRenderer extends AbstractMorph",
    "public class StructureMorph extends AbstractMorph",
)
text = text.replace("public StructureRenderer(", "public StructureMorph(")
text = text.replace("StructureRenderer.DEFAULT_TRANSFORM", "StructureMorph.DEFAULT_TRANSFORM")

if text == original:
    raise SystemExit("The expected StructureMorph mapping collision was not found")

MORPH_PATH.write_text(text, encoding="utf-8")

old_fqcn = "mchorse.blockbuster_pack.morphs.StructureRenderer"
new_fqcn = "mchorse.blockbuster_pack.morphs.StructureMorph"
updated_references = 0

for source_root in (ROOT / "src/main/java", ROOT / "src/client/java"):
    if not source_root.exists():
        continue

    for path in source_root.rglob("*.java"):
        source = path.read_text(encoding="utf-8")
        updated = source.replace(old_fqcn, new_fqcn)

        if updated != source:
            path.write_text(updated, encoding="utf-8")
            updated_references += 1

print("Restored BlockBuster's StructureMorph after the Mojmap name collision.")
print(f"Updated {updated_references} fully-qualified StructureMorph references.")
