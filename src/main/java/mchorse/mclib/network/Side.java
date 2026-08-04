package mchorse.mclib.network;

/**
 * Bundled replacement for Forge's {@code net.minecraftforge.fml.relauncher.Side}
 * as used by dispatcher registration call sites (roadmap P23). Only the two
 * values legacy {@code AbstractDispatcher.register(...)} consumed; the side
 * denotes the <b>receiving</b> side of the packet, exactly like 1.12.2.
 */
public enum Side
{
    CLIENT,
    SERVER;

    public boolean isClient()
    {
        return this == CLIENT;
    }

    public boolean isServer()
    {
        return this == SERVER;
    }
}
