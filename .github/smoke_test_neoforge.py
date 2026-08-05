from pathlib import Path
import os
import re
import subprocess
import zipfile

root = Path.cwd()
log_path = root / 'build' / 'neoforge-client-smoke.log'
log_path.parent.mkdir(parents=True, exist_ok=True)

# This compatibility regression is not visible at the title screen because
# Pixelmon is optional and is not redistributed in CI. Verify that the exact
# source bridge and adapter made it through generation and into the production
# JAR before launching the client.
pixelmon_source = root / 'src/main/java/mchorse/metamorph/compat/PixelmonEntityMorphAdapter.java'
compat_source = root / 'src/main/java/mchorse/metamorph/compat/EntityMorphCompatibility.java'
entity_source = root / 'src/main/java/mchorse/metamorph/api/morphs/EntityMorph.java'
renderer_source = root / 'src/client/java/mchorse/metamorph/client/render/EntityMorphRenderer.java'

source_checks = {
    'Pixelmon adapter source': pixelmon_source.exists(),
    'Pixelmon head policy': pixelmon_source.exists() and 'mirrorsVanillaHeadRotation' in pixelmon_source.read_text(encoding='utf-8'),
    'shared rotation bridge': compat_source.exists() and 'public static void mirrorRotations' in compat_source.read_text(encoding='utf-8'),
    'tick path bridge': entity_source.exists() and 'mirrorRotations(this.entity, target)' in entity_source.read_text(encoding='utf-8'),
    'render path bridge': renderer_source.exists() and 'mirrorRotations(to, from)' in renderer_source.read_text(encoding='utf-8'),
}
missing_source = [name for name, ok in source_checks.items() if not ok]
if missing_source:
    raise SystemExit('Pixelmon head-rotation source validation failed: ' + ', '.join(missing_source))

jars = sorted(
    path for path in (root / 'build' / 'libs').glob('blockbuster-*.jar')
    if not path.name.endswith('-sources.jar')
)
if not jars:
    raise SystemExit('No production Blockbuster JAR was available for compatibility validation.')

with zipfile.ZipFile(jars[-1]) as archive:
    packaged = set(archive.namelist())

required_classes = {
    'mchorse/metamorph/compat/PixelmonEntityMorphAdapter.class',
    'mchorse/metamorph/compat/EntityMorphCompatibility.class',
    'mchorse/metamorph/compat/EntityMorphAdapter.class',
}
missing_classes = sorted(required_classes - packaged)
if missing_classes:
    raise SystemExit('Pixelmon head-rotation classes were not packaged: ' + ', '.join(missing_classes))

command = ['xvfb-run', '-a', './gradlew', 'runClient', '--no-daemon', '--stacktrace']

try:
    completed = subprocess.run(
        command,
        cwd=root,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        timeout=120,
        env=os.environ.copy(),
    )
    output = completed.stdout
    status = completed.returncode
except subprocess.TimeoutExpired as error:
    output = error.stdout or ''
    if isinstance(output, bytes):
        output = output.decode(errors='replace')
    status = 124

log_path.write_text(output, encoding='utf-8')
print(output)

# Match structural crash markers rather than every exception printed by
# Minecraft. A headless Linux client routinely logs recoverable narrator and
# OpenAL IllegalStateExceptions, then continues running normally.
fatal = re.compile(
    r'MixinApplyError|InvalidMixinException|MixinTransformerError|'
    r'Could not execute entrypoint|Failed to create mod instance|'
    r'Exception in thread "(?:Render thread|Server thread|main)"|'
    r'Unreported exception thrown|Preparing crash report|Game crashed!|'
    r'ModLoadingException|Crash Report UUID|'
    r'java\.lang\.(?:NoSuchMethodError|NoSuchFieldError)',
    re.IGNORECASE,
)

match = fatal.search(output)
if match:
    raise SystemExit(
        'NeoForge client smoke test found a startup/runtime failure: ' + match.group(0)
    )

startup_markers = (
    'Blockbuster (Fabric port) initialized',
    'Blockbuster (Fabric port) client initialized',
    'Blockbuster (NeoForge port) initialized',
    'Blockbuster (NeoForge port) client initialized',
)

if not any(marker in output for marker in startup_markers):
    raise SystemExit('Blockbuster did not reach its NeoForge common/client initialization path.')

if status not in (0, 124, 130):
    raise SystemExit(f'runClient exited unexpectedly with status {status}')

print(f'NeoForge client launch smoke test passed (status {status}).')
