from __future__ import annotations

from pathlib import Path
import re

ROOT = Path.cwd()
CALLBACK_PATH = ROOT / "src/client/java/net/fabricmc/fabric/api/client/rendering/v1/ItemOverlayCallback.java"

if not CALLBACK_PATH.exists():
    raise SystemExit(f"Missing expected compatibility callback: {CALLBACK_PATH}")

text = CALLBACK_PATH.read_text(encoding="utf-8")
original = text

# FFAPI 1.21.1 does not expose Fabric's HudRenderCallback. BlockBuster only
# uses this local interface through its own EVENT and onRenderItemOverlay hook,
# so it does not need to inherit the unavailable Fabric callback.
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

if text == original:
    raise SystemExit("The expected ItemOverlayCallback NeoForge compatibility seam was not found")

CALLBACK_PATH.write_text(text, encoding="utf-8")
print("Detached BlockBuster's item-overlay callback from unavailable HudRenderCallback API.")
