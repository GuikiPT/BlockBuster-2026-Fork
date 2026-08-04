from pathlib import Path
import os
import re
import subprocess

root = Path.cwd()
log_path = root / 'build' / 'client-smoke.log'
log_path.parent.mkdir(parents=True, exist_ok=True)
command = ['xvfb-run', '-a', './gradlew', 'runClient', '--no-daemon']

try:
    completed = subprocess.run(
        command,
        cwd=root,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        timeout=90,
        env=os.environ.copy(),
    )
    output = completed.stdout
    status = completed.returncode
except subprocess.TimeoutExpired as error:
    output = error.stdout or ''
    if isinstance(output, bytes):
        output = output.decode(errors='replace')
    status = 124

log_path.write_text(output)
print(output)

fatal = re.compile(
    r'MixinApplyError|InvalidMixinException|MixinTransformerError|'
    r'Could not execute entrypoint|Exception in thread "Render thread"|'
    r'java\.lang\.NoSuchMethodError|java\.lang\.NoSuchFieldError',
    re.IGNORECASE,
)

if fatal.search(output):
    raise SystemExit('Client smoke test found a startup/runtime failure.')

if 'Loading Minecraft 1.21.1' not in output:
    raise SystemExit('Minecraft 1.21.1 did not reach Fabric Loader startup.')

if status not in (0, 124, 130):
    raise SystemExit(f'runClient exited unexpectedly with status {status}')

print(f'Client launch smoke test passed (status {status}).')
