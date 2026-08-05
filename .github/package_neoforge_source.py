from pathlib import Path
import zipfile

root = Path.cwd()
output = root / 'build' / 'distributions' / 'BlockBuster-2.7.2-NeoForge-1.21.1-source.zip'
output.parent.mkdir(parents=True, exist_ok=True)

excluded_roots = {'.git', '.gradle', 'build', 'run', '.idea', '.github'}

with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root)
        if not path.is_file():
            continue
        if relative.parts and relative.parts[0] in excluded_roots:
            continue
        if path.suffix == '.iml':
            continue
        archive.write(path, relative.as_posix())

print(output)
