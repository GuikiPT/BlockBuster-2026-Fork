package mchorse.metamorph.network.common.survival;

/**
 * Select an acquired morph by index (roadmap P55). An out-of-range index (e.g.
 * {@code -1} from the demorph keybind) is a demorph — the server handler
 * interprets it, this payload just carries the raw index.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/survival/PacketSelectMorph.java</p>
 */
public class PacketSelectMorph extends PacketIndex
{
    public PacketSelectMorph()
    {
        super();
    }

    public PacketSelectMorph(int index)
    {
        super(index);
    }
}
