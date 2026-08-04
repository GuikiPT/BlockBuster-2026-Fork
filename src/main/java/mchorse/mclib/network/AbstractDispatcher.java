package mchorse.mclib.network;

import io.netty.buffer.Unpooled;
import mchorse.mclib.network.chunked.ChunkedTransport;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Full port of McLib 2.4.3's AbstractDispatcher (roadmap P23) on the Fabric
 * 1.20.4 {@code Identifier} + {@code PacketByteBuf} networking APIs (the
 * pre-1.20.5 era — not CustomPayload records).
 *
 * <p>Key mapping decisions (see plan/S02-mclib-networking.md):</p>
 * <ul>
 * <li>1.12's implicit discriminator bytes ({@code nextPacketID++} in
 * registration order) become one stable snake-cased {@code Identifier} per
 * packet class ({@code blockbuster:modify_actor} style). The registration
 * order is still a frozen contract, enforced by the {@link ChannelLedger}
 * golden test (P23.1). Both-side registrations of one class collapse to a
 * single Identifier with a receiver per side.</li>
 * <li>Client-only API ({@code ClientPlayNetworking}) is never touched here:
 * CLIENT-side registrations are recorded and wired later by the client-source
 * {@code ClientDispatcherHooks} (invoked from the ClientModInitializer, which
 * runs after common init), and {@code sendToServer} routes through the
 * {@link #clientSender} seam that same hook installs. Handler classes for the
 * CLIENT side are referenced <b>by name</b> and instantiated lazily, because
 * they live in the split client source set.</li>
 * <li>Packets are decoded ({@code fromBytes}) on the netty thread — the
 * Fabric receiver's buffer is released when the callback returns — and the
 * handler base classes hop {@code run(...)} to the game thread (P28).</li>
 * <li>Payloads over {@link ChunkedTransport#CHUNK_SIZE} transparently ride
 * the chunked transport (P25) on the reserved {@code <modid>:chunk}
 * channel.</li>
 * </ul>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/AbstractDispatcher.java</p>
 *
 * @author Ernio (Ernest Sadowski)
 */
public abstract class AbstractDispatcher
{
    protected static final Logger LOGGER = LoggerFactory.getLogger("mclib");

    /** Every constructed dispatcher, for connection-scoped state cleanup (P27/P25). */
    private static final List<AbstractDispatcher> INSTANCES = new CopyOnWriteArrayList<>();

    /**
     * The running server, captured via {@code ServerLifecycleEvents} in the
     * mod entrypoint (legacy Forge got this from {@code sendToAll}'s wrapper).
     */
    private static volatile MinecraftServer server;

    /**
     * Client→server sender installed by the client-source
     * {@code ClientDispatcherHooks} ({@code ClientPlayNetworking::send});
     * stays null on a dedicated server.
     */
    private static volatile BiConsumer<Identifier, PacketByteBuf> clientSender;

    private final String modID;
    private final Identifier chunkChannel;
    private final List<Registration> registrations = new ArrayList<>();
    private final Map<Class<? extends IMessage>, Identifier> channelsByClass = new LinkedHashMap<>();
    private final Map<Identifier, Registration> serverReceivers = new LinkedHashMap<>();
    private final Map<Identifier, Registration> clientReceivers = new LinkedHashMap<>();
    private final AtomicInteger transferIds = new AtomicInteger();
    private final Map<UUID, ChunkedTransport.Reassembler> serverReassemblers = new ConcurrentHashMap<>();
    private final ChunkedTransport.Reassembler clientReassembler = new ChunkedTransport.Reassembler();
    private boolean chunkWired;

    /** Placeholder reassembly key for headless loopback tests (null player). */
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public AbstractDispatcher(String modID)
    {
        this.modID = modID;
        this.chunkChannel = new Identifier(modID, "chunk");

        INSTANCES.add(this);
    }

    /**
     * Here you supposed to register packets to handlers
     */
    public abstract void register();

    /* Static lifecycle wiring (called from mod entrypoints) */

    public static void setServer(MinecraftServer runningServer)
    {
        server = runningServer;
    }

    public static MinecraftServer getServer()
    {
        return server;
    }

    public static void setClientSender(BiConsumer<Identifier, PacketByteBuf> sender)
    {
        clientSender = sender;
    }

    /** Server-side per-player chunk state cleanup (ServerPlayConnectionEvents.DISCONNECT). */
    public static void onPlayerDisconnect(UUID player)
    {
        for (AbstractDispatcher dispatcher : INSTANCES)
        {
            dispatcher.serverReassemblers.remove(player);
        }
    }

    /** Client-side chunk state cleanup, invoked from P27's one disconnect hook. */
    public static void resetAllClientState()
    {
        for (AbstractDispatcher dispatcher : INSTANCES)
        {
            dispatcher.clientReassembler.reset();
        }
    }

    /* Registration */

    /**
     * Register given message with given message handler on a given side.
     * This overload serves SERVER-side (main-source-set) handlers and keeps
     * the exact legacy call-site shape.
     */
    public <REQ extends IMessage> void register(Class<REQ> message, Class<? extends AbstractMessageHandler<? super REQ>> handler, Side side)
    {
        this.registerInternal(message, handler.getName(), side);
    }

    /**
     * Registration by handler class <b>name</b> — required for CLIENT-side
     * handlers, which live in the split client source set and cannot be
     * referenced as class literals from common code. The handler is
     * instantiated lazily (client env or tests only).
     */
    public <REQ extends IMessage> void register(Class<REQ> message, String handlerClassName, Side side)
    {
        this.registerInternal(message, handlerClassName, side);
    }

    private void registerInternal(Class<? extends IMessage> message, String handlerClassName, Side side)
    {
        this.ensureChunkWired();

        Identifier channel = this.channelsByClass.computeIfAbsent(message, clazz -> new Identifier(this.modID, channelName(clazz)));
        Registration registration = new Registration(message, handlerClassName, side, channel);

        this.registrations.add(registration);

        Map<Identifier, Registration> receivers = side == Side.SERVER ? this.serverReceivers : this.clientReceivers;

        if (receivers.putIfAbsent(channel, registration) != null)
        {
            LOGGER.warn("Dispatcher {}: duplicate {}-side registration for {} — keeping the first handler", this.modID, side, channel);

            return;
        }

        if (side == Side.SERVER)
        {
            /* fail fast on the server side: bad handler = boot-time error, like Forge */
            registration.handler();
            this.wireRegisterServerReceiver(channel);
        }
    }

    private void ensureChunkWired()
    {
        if (!this.chunkWired)
        {
            this.chunkWired = true;
            this.wireRegisterServerReceiver(this.chunkChannel);
        }
    }

    /**
     * Snake-cased channel name from the legacy packet class name:
     * {@code PacketModifyActor -> modify_actor}. This mapping IS the P23.1
     * ledger convention; the golden test asserts it against
     * {@link ChannelLedger}.
     */
    public static String channelName(Class<?> packet)
    {
        String name = packet.getSimpleName();

        if (name.startsWith("Packet"))
        {
            name = name.substring("Packet".length());
        }

        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /* Introspection (ledger test, client hooks) */

    public String getModID()
    {
        return this.modID;
    }

    public Identifier getChunkChannel()
    {
        return this.chunkChannel;
    }

    public List<Registration> getRegistrations()
    {
        return Collections.unmodifiableList(this.registrations);
    }

    /** Unique CLIENT-side channels, for ClientDispatcherHooks wiring. */
    public List<Identifier> getClientChannels()
    {
        return new ArrayList<>(this.clientReceivers.keySet());
    }

    /* Send helpers (legacy targeting semantics) */

    /**
     * Send message to players who are tracking given entity.
     *
     * <p>{@code PlayerLookup.tracking(entity)} does not include the entity
     * itself when it is a player — 1.12.2's
     * {@code EntityTracker.getTrackingPlayers} had the same semantics, so
     * parity holds.</p>
     */
    public void sendToTracked(Entity entity, IMessage message)
    {
        for (ServerPlayerEntity player : PlayerLookup.tracking(entity))
        {
            this.sendTo(message, player);
        }
    }

    /**
     * Send message to given player
     */
    public void sendTo(IMessage message, ServerPlayerEntity player)
    {
        this.sendTo(message, player, null);
    }

    /**
     * Send message to given player, running {@code onSent} after the final
     * frame (single packet or last chunk) has been handed to the wire —
     * the P25 completion-callback hook.
     */
    public void sendTo(IMessage message, ServerPlayerEntity player, Runnable onSent)
    {
        Identifier channel = this.channelFor(message);
        PacketByteBuf buf = this.encode(message);

        if (buf.readableBytes() > ChunkedTransport.CHUNK_SIZE)
        {
            for (byte[] frame : ChunkedTransport.split(this.transferIds.getAndIncrement(), this.innerPayload(channel, buf)))
            {
                this.wireSendToPlayer(this.chunkChannel, wrapFrame(frame), player);
            }
        }
        else
        {
            this.wireSendToPlayer(channel, buf, player);
        }

        if (onSent != null)
        {
            onSent.run();
        }
    }

    /**
     * Send message to all players
     */
    public void sendToAll(IMessage message)
    {
        MinecraftServer running = server;

        if (running == null)
        {
            LOGGER.warn("Dispatcher {}: sendToAll({}) with no running server — dropped", this.modID, message.getClass().getSimpleName());

            return;
        }

        for (ServerPlayerEntity player : PlayerLookup.all(running))
        {
            this.sendTo(message, player);
        }
    }

    /**
     * Send message to all players around the given point
     */
    public void sendToAllAround(IMessage message, TargetPoint point)
    {
        MinecraftServer running = server;

        if (running == null)
        {
            LOGGER.warn("Dispatcher {}: sendToAllAround({}) with no running server — dropped", this.modID, message.getClass().getSimpleName());

            return;
        }

        ServerWorld world = running.getWorld(point.dimension);

        if (world == null)
        {
            LOGGER.warn("Dispatcher {}: sendToAllAround({}) — unknown dimension {}", this.modID, message.getClass().getSimpleName(), point.dimension);

            return;
        }

        for (ServerPlayerEntity player : PlayerLookup.around(world, new Vec3d(point.x, point.y, point.z), point.range))
        {
            this.sendTo(message, player);
        }
    }

    /**
     * Send message to the server
     */
    public void sendToServer(IMessage message)
    {
        this.sendToServer(message, null);
    }

    /** {@code sendToServer} with the P25 completion-callback hook. */
    public void sendToServer(IMessage message, Runnable onSent)
    {
        Identifier channel = this.channelFor(message);
        PacketByteBuf buf = this.encode(message);

        if (buf.readableBytes() > ChunkedTransport.CHUNK_SIZE)
        {
            for (byte[] frame : ChunkedTransport.split(this.transferIds.getAndIncrement(), this.innerPayload(channel, buf)))
            {
                this.wireSendToServer(this.chunkChannel, wrapFrame(frame));
            }
        }
        else
        {
            this.wireSendToServer(channel, buf);
        }

        if (onSent != null)
        {
            onSent.run();
        }
    }

    /* Receive path — public so Fabric receivers, ClientDispatcherHooks and
     * headless loopback tests all funnel through the same code */

    /**
     * Server-side receive: decode on the calling (netty) thread, hand to the
     * handler (which hops to the game thread), send any reply back.
     */
    public void receiveServer(Identifier channel, PacketByteBuf buf, ServerPlayerEntity player)
    {
        if (channel.equals(this.chunkChannel))
        {
            UUID key = player != null ? player.getUuid() : NIL_UUID;
            ChunkedTransport.Reassembler reassembler = this.serverReassemblers.computeIfAbsent(key, uuid -> new ChunkedTransport.Reassembler());
            byte[] payload = reassembler.receive(buf);

            if (payload != null)
            {
                PacketByteBuf inner = new PacketByteBuf(Unpooled.wrappedBuffer(payload));

                this.receiveServer(new Identifier(inner.readString()), inner, player);
            }

            return;
        }

        Registration registration = this.serverReceivers.get(channel);

        if (registration == null)
        {
            LOGGER.warn("Dispatcher {}: no server receiver for {} — dropped", this.modID, channel);

            return;
        }

        IMessage message = this.createPacket(registration);

        if (message == null)
        {
            return;
        }

        message.fromBytes(buf);

        IMessage reply = registration.handler().handleServerMessage(player, message);

        if (reply != null)
        {
            this.sendTo(reply, player);
        }
    }

    /**
     * Client-side receive (invoked by ClientDispatcherHooks' registered
     * receivers, or directly by tests).
     */
    public void receiveClient(Identifier channel, PacketByteBuf buf)
    {
        if (channel.equals(this.chunkChannel))
        {
            byte[] payload = this.clientReassembler.receive(buf);

            if (payload != null)
            {
                PacketByteBuf inner = new PacketByteBuf(Unpooled.wrappedBuffer(payload));

                this.receiveClient(new Identifier(inner.readString()), inner);
            }

            return;
        }

        Registration registration = this.clientReceivers.get(channel);

        if (registration == null)
        {
            LOGGER.warn("Dispatcher {}: no client receiver for {} — dropped", this.modID, channel);

            return;
        }

        IMessage message = this.createPacket(registration);

        if (message == null)
        {
            return;
        }

        message.fromBytes(buf);

        IMessage reply = registration.handler().handleClientMessage(message);

        if (reply != null)
        {
            this.sendToServer(reply);
        }
    }

    /* Wire seams — the ONLY places touching the actual Fabric send/receive
     * registries; loopback test dispatchers override these */

    protected void wireSendToPlayer(Identifier channel, PacketByteBuf buf, ServerPlayerEntity player)
    {
        ServerPlayNetworking.send(player, channel, buf);
    }

    protected void wireSendToServer(Identifier channel, PacketByteBuf buf)
    {
        BiConsumer<Identifier, PacketByteBuf> sender = clientSender;

        if (sender != null)
        {
            sender.accept(channel, buf);
        }
        else
        {
            LOGGER.warn("Dispatcher {}: sendToServer({}) with no client sender installed (dedicated server or headless) — dropped", this.modID, channel);
        }
    }

    protected void wireRegisterServerReceiver(Identifier channel)
    {
        ServerPlayNetworking.registerGlobalReceiver(channel, (mcServer, player, handler, buf, responseSender) -> this.receiveServer(channel, buf, player));
    }

    /* Internals */

    private Identifier channelFor(IMessage message)
    {
        Identifier channel = this.channelsByClass.get(message.getClass());

        if (channel == null)
        {
            throw new IllegalStateException("Packet " + message.getClass().getName() + " was never registered on dispatcher " + this.modID);
        }

        return channel;
    }

    private PacketByteBuf encode(IMessage message)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        message.toBytes(buf);

        return buf;
    }

    /** Chunked-inner layout: target Identifier string + the packet's own bytes. */
    private byte[] innerPayload(Identifier channel, PacketByteBuf encoded)
    {
        PacketByteBuf inner = PacketByteBufs.create();

        inner.writeString(channel.toString());
        inner.writeBytes(encoded);

        byte[] payload = new byte[inner.readableBytes()];

        inner.readBytes(payload);

        return payload;
    }

    private static PacketByteBuf wrapFrame(byte[] frame)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBytes(frame);

        return buf;
    }

    private IMessage createPacket(Registration registration)
    {
        try
        {
            return registration.packetClass().getDeclaredConstructor().newInstance();
        }
        catch (ReflectiveOperationException e)
        {
            LOGGER.error("Dispatcher {}: cannot instantiate packet {} — dropped", this.modID, registration.packetClass().getName(), e);

            return null;
        }
    }

    /**
     * One {@code register(...)} call: packet class + handler (by name, lazily
     * instantiated for the CLIENT side) + receiving side + collapsed channel
     * Identifier. The ordered list of these is the modern form of 1.12's
     * discriminator sequence.
     */
    public static final class Registration
    {
        private final Class<? extends IMessage> packetClass;
        private final String handlerClassName;
        private final Side side;
        private final Identifier channel;
        private AbstractMessageHandler<IMessage> handler;

        private Registration(Class<? extends IMessage> packetClass, String handlerClassName, Side side, Identifier channel)
        {
            this.packetClass = packetClass;
            this.handlerClassName = handlerClassName;
            this.side = side;
            this.channel = channel;
        }

        public Class<? extends IMessage> packetClass()
        {
            return this.packetClass;
        }

        public String handlerClassName()
        {
            return this.handlerClassName;
        }

        public Side side()
        {
            return this.side;
        }

        public Identifier channel()
        {
            return this.channel;
        }

        /**
         * The single handler instance (legacy Forge also
         * reflection-instantiated one handler per registration).
         */
        @SuppressWarnings("unchecked")
        public synchronized AbstractMessageHandler<IMessage> handler()
        {
            if (this.handler == null)
            {
                try
                {
                    this.handler = (AbstractMessageHandler<IMessage>) Class.forName(this.handlerClassName).getDeclaredConstructor().newInstance();
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException("Cannot instantiate message handler " + this.handlerClassName, e);
                }
            }

            return this.handler;
        }
    }
}
