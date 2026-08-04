package mchorse.metamorph.network.common.survival;

/**
 * Remove an acquired morph by index (roadmap P55). Echoed back by the server on
 * success.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/survival/PacketRemoveMorph.java</p>
 */
public class PacketRemoveMorph extends PacketIndex
{
    public PacketRemoveMorph()
    {
        super();
    }

    public PacketRemoveMorph(int index)
    {
        super(index);
    }
}
