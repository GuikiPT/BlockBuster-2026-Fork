from pathlib import Path

ROOT = Path('src/client/java')

for file in ROOT.rglob('*.java'):
    lines = file.read_text().splitlines(True)
    changed = False

    for index, line in enumerate(lines[:-1]):
        if not line.rstrip().endswith(');'):
            continue

        next_index = index + 1

        while next_index < len(lines) and not lines[next_index].strip():
            next_index += 1

        if next_index < len(lines) and lines[next_index].lstrip().startswith('.'):
            newline = '\n' if line.endswith('\n') else ''
            lines[index] = line.rstrip('\n').rstrip()[:-1] + newline
            changed = True

    if changed:
        file.write_text(''.join(lines))

print('Normalized semicolons inside fluent Java call chains.')
