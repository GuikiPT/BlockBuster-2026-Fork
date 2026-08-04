package mchorse.mclib.network.mclib.client;

import mchorse.mclib.network.mclib.common.PacketBoolean;

/**
 * A special handler just for PacketBoolean to allow for efficient transport of just 5 bytes in total.
 *
 * <p>The legacy "packets cannot be transported via inheritance to handlers
 * ... TODO check in port" is resolved in the port: Fabric routes per
 * Identifier, but the separate class stays (see {@code PacketBoolean}).</p>
 */
public class ClientHandlerBoolean extends AbstractClientHandlerAnswer<PacketBoolean>
{
}
