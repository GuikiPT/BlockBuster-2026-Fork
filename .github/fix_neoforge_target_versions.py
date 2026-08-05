from pathlib import Path

props = Path("gradle.properties")
text = props.read_text(encoding="utf-8")

versions = {
    "neoforge_version": "21.1.248",
    "ffapi_version": "0.116.15+2.3.1+1.21.1",
}

lines = text.splitlines()
seen = set()

for index, line in enumerate(lines):
    for key, value in versions.items():
        if line.startswith(key + "="):
            lines[index] = key + "=" + value
            seen.add(key)

missing = set(versions) - seen
if missing:
    raise SystemExit("Missing expected version properties: " + ", ".join(sorted(missing)))

props.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
print("Targeting NeoForge 21.1.248 and Forgified Fabric API 0.116.15+2.3.1+1.21.1.")
