from __future__ import annotations

from pathlib import Path
import re

ROOT = Path.cwd()
candidates = list((ROOT / "src").rglob("ItemOverlayCallback.java")) if (ROOT / "src").exists() else []

if not candidates:
    print("No local ItemOverlayCallback shim was generated; no HUD compatibility patch was needed.")
    raise SystemExit(0)

patched = 0
for callback_path in candidates:
    text = callback_path.read_text(encoding="utf-8")
    original = text

    # FFAPI 1.21.1 may omit Fabric's HudRenderCallback. BlockBuster's local
    # item-overlay interface only needs its own EVENT and render hook, so it
    # does not need to inherit that unavailable callback.
    text = re.sub(
        r"public\s+interface\s+ItemOverlayCallback\s+extends\s+HudRenderCallback",
        "public interface ItemOverlayCallback",
        text,
    )
    text = re.sub(
        r"(?m)^import\s+net\.fabricmc\.fabric\.api\.client\.rendering\.v1\.HudRenderCallback;\s*\n",
        "",
        text,
    )
    text = re.sub(
        r"(?m)^import\s+net\.minecraft\.[^;]*(?:RenderTickCounter|DeltaTracker);\s*\n",
        "",
        text,
    )
    text = re.sub(
        r"\n\s*@Override\s*\n\s*default\s+void\s+onHudRender\s*\([^)]*\)\s*\{\s*\}\s*",
        "\n",
        text,
        flags=re.DOTALL,
    )

    if text != original:
        callback_path.write_text(text, encoding="utf-8")
        patched += 1
        print(f"Patched {callback_path.relative_to(ROOT)}")

if patched:
    print(f"Detached {patched} item-overlay callback shim(s) from unavailable HudRenderCallback API.")
else:
    print("Discovered ItemOverlayCallback source already required no NeoForge HUD compatibility changes.")
