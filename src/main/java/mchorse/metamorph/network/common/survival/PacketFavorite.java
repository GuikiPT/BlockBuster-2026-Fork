package mchorse.metamorph.network.common.survival;

/**
 * Favorite/unfavorite a morph by index (roadmap P55). Bidirectional: the server
 * echoes it back on success so the client GUI state stays server-confirmed.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/survival/PacketFavorite.java</p>
 */
public class PacketFavorite extends PacketIndex
{
    public PacketFavorite()
    {
        super();
    }

    public PacketFavorite(int index)
    {
        super(index);
    }
}
