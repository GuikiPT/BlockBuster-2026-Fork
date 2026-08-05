from pathlib import Path
import re

root = Path.cwd()
invalid_import = re.compile(r"(?m)^import\s+[A-Za-z_$][A-Za-z0-9_$]*;\s*\n")
fixed_files = []
removed = 0

for source_root in (root / "src/main/java", root / "src/client/java"):
    if not source_root.exists():
        continue

    for path in source_root.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        updated, count = invalid_import.subn("", text)

        if count:
            path.write_text(updated, encoding="utf-8")
            fixed_files.append(path.relative_to(root).as_posix())
            removed += count

print(f"Removed {removed} invalid unqualified imports from {len(fixed_files)} Mojmap-migrated source files.")
for path in fixed_files:
    print(f"  {path}")
