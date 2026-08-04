from pathlib import Path
import re

ROOT = Path('src/client/java')

# The old multiline form ended with a line containing only `.next();`. After
# removing that obsolete VertexConsumer method, the preceding final attribute
# call must terminate the Java statement itself.
for file in ROOT.rglob('*.java'):
    text = file.read_text()
    updated = re.sub(
        r'(?m)^(\s*\.(?:color|normal|light|texture|overlay)\([^\n;]+\))\s*$',
        r'\1;',
        text,
    )

    if updated != text:
        file.write_text(updated)

print('Restored terminators for migrated multiline vertex chains.')
