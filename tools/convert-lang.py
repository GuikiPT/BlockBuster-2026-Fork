#!/usr/bin/env python3
"""Convert legacy 1.12.2 .lang files to 1.20.4 JSON lang files (roadmap P8).

Usage: convert-lang.py <legacy-lang-dir> <output-assets-lang-dir>

Rules (see plan/S00-scaffolding.md P8):
- drop `#PARSE_ESCAPES` and `#` comment lines
- honor the escapes that directive enabled: \\n, \\t, \\uXXXX, \\\\, \\=, \\:, \\ ,
  matching Java Properties-with-escapes semantics 1.12.2 used
- keep `§` codes and `%s` positional args verbatim
- duplicate keys: last occurrence wins (Properties semantics), logged
- total converter: malformed lines are logged with file+line and skipped,
  never abort
- locale file names lowercase: en_US.lang -> en_us.json
"""

import json
import sys
from pathlib import Path


def unescape(value: str, where: str) -> str:
    """Java Properties-style value unescaping (the #PARSE_ESCAPES behavior)."""
    out = []
    i = 0

    while i < len(value):
        c = value[i]

        if c == "\\" and i + 1 < len(value):
            n = value[i + 1]

            if n == "n":
                out.append("\n")
                i += 2
            elif n == "t":
                out.append("\t")
                i += 2
            elif n == "u":
                hex4 = value[i + 2 : i + 6]

                try:
                    out.append(chr(int(hex4, 16)))
                    i += 6
                except ValueError:
                    print(f"[convert-lang] {where}: bad \\u escape '{hex4}', kept literally")
                    out.append(n)
                    i += 2
            else:
                # Properties drops the backslash for any other escaped char
                out.append(n)
                i += 2
        else:
            out.append(c)
            i += 1

    return "".join(out)


def convert(src: Path, dst: Path) -> None:
    entries = {}

    with src.open(encoding="utf-8") as f:
        for lineno, raw in enumerate(f, 1):
            line = raw.rstrip("\n")

            if not line.strip() or line.lstrip().startswith("#"):
                continue

            if "=" not in line:
                print(f"[convert-lang] {src.name}:{lineno}: no '=', skipped: {line!r}")
                continue

            key, _, value = line.partition("=")
            key = key.strip()

            if not key:
                print(f"[convert-lang] {src.name}:{lineno}: empty key, skipped")
                continue

            if key in entries:
                print(f"[convert-lang] {src.name}:{lineno}: duplicate key '{key}', last wins")

            entries[key] = unescape(value, f"{src.name}:{lineno}")

    dst.parent.mkdir(parents=True, exist_ok=True)

    with dst.open("w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(f"[convert-lang] {src.name} -> {dst.name}: {len(entries)} keys")


def main() -> None:
    if len(sys.argv) != 3:
        sys.exit(__doc__)

    src_dir = Path(sys.argv[1])
    dst_dir = Path(sys.argv[2])

    for lang in sorted(src_dir.glob("*.lang")):
        convert(lang, dst_dir / (lang.stem.lower() + ".json"))


if __name__ == "__main__":
    main()
