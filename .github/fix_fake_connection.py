from pathlib import Path

path = Path('src/main/java/mchorse/blockbuster/recording/scene/fake/FakeClientConnection.java')
text = path.read_text()
text = text.replace('    @Override\n    public void setInitialPacketListener(PacketListener packetListener)\n',
                    '    public void setInitialPacketListener(PacketListener packetListener)\n')
path.write_text(text)
