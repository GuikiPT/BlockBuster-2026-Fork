from pathlib import Path
import re

path = Path('src/main/java/mchorse/blockbuster/recording/scene/fake/FakeClientConnection.java')
text = path.read_text()
text, count = re.subn(
    r'(?m)^    @Override\n(?=    public void setInitialPacketListener\()',
    '',
    text,
)

if count != 1:
    raise RuntimeError(f'Expected one obsolete setInitialPacketListener override, removed {count}')

path.write_text(text)
