from pathlib import Path
import os
import re
import subprocess

root = Path.cwd()
log_path = root / 'build' / 'neoforge-client-smoke.log'
log_path.parent.mkdir(parents=True, exist_ok=True)
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

fatal = re.compile(
    r'MixinApplyError|InvalidMixinException|MixinTransformerError|'
    r'Could not execute entrypoint|Failed to create mod instance|'
    r'Exception in thread "Render thread"|ModLoadingException|'
    r'java\.lang\.NoSuchMethodError|java\.lang\.NoSuchFieldError',
    re.IGNORECASE,
)

if fatal.search(output):
    raise SystemExit('NeoForge client smoke test found a startup/runtime failure.')

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
