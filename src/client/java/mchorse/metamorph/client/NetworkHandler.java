package mchorse.metamorph.client;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphSettings;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.ForeignMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * Custom payload network handler — roadmap <b>P55.1</b>, landed in S21 as a
 * post-parity integration (see {@code plan/S21-optional-compat.md} scope item
 * 6 and {@code plan/inbox/batchO-E.md}).
 *
 * <p>This handler lets a <b>server-side plugin</b> (Bukkit/Spigot/Paper, a
 * proxy, a datapack-driven command chain — anything that can put a custom
 * payload on the wire) morph and demorph players purely on the receiving
 * client, without the mod being installed server-side. It is a port of
 * Metamorph 1.4's {@code mchorse.metamorph.client.NetworkHandler} +
 * {@code MorphRunnable}.</p>
 *
 * <h2>The wire grammar</h2>
 *
 * <p>Channel (clientbound, server → client):
 * <b>{@code metamorph:plugin_morph}</b>. Payload: a bare, length-prefix-free
 * UTF-8 byte blob — the whole remainder of the custom-payload packet — which is
 * {@code trim()}ed and then split on the single ASCII space {@code ' '}:</p>
 *
 * <pre>
 *   &lt;username&gt;                            -- 1 token   : demorph that player
 *   &lt;username&gt; &lt;morphName&gt;                -- 2 tokens  : morph, no extra NBT
 *   &lt;username&gt; &lt;morphName&gt; &lt;snbt…&gt;        -- 3+ tokens : morph with NBT
 * </pre>
 *
 * <ul>
 *   <li><b>{@code username}</b> — the exact (case-sensitive) profile name of
 *   <i>any</i> player in the receiving client's world; not necessarily the
 *   player the payload was sent to. See the security note below.</li>
 *   <li><b>{@code morphName}</b> — the Metamorph morph id, i.e. the value of a
 *   morph's {@code Name} tag ({@code blockbuster.fred}, {@code minecraft:pig},
 *   {@code metamorph.Block}, …). It is injected into the parsed compound
 *   <b>after</b> parsing, so a {@code Name} inside the SNBT is always
 *   overridden by this token.</li>
 *   <li><b>{@code snbt}</b> — tokens 3..n re-joined with single spaces and
 *   parsed by {@link StringNbtReader#parse(String)} (1.12.2 used
 *   {@code JsonToNBT.getTagFromJson}, the same SNBT dialect). The split/re-join
 *   is <b>lossless for interior spaces</b> — {@code String.split(" ")} keeps the
 *   empty tokens a run of spaces produces, and the re-join puts one space back
 *   per token boundary — so a quoted string like {@code "Big  Fred"} survives
 *   with both its spaces. Only whitespace at the very ends of the payload is
 *   lost (to {@code trim()}), and tabs/newlines are never split on at all.</li>
 * </ul>
 *
 * <p><b>Token counting is literal</b>: because empty tokens count, a payload
 * with a double space between the username and the morph name
 * ({@code "Steve  test"}) is <i>three</i> tokens — morph name {@code ""} and
 * NBT {@code "test"} — which fails to parse and is dropped. Send exactly one
 * space between fields.</p>
 *
 * <p>Examples (raw payload bytes, UTF-8):</p>
 *
 * <pre>
 *   Steve
 *   Steve blockbuster.fred
 *   Steve minecraft:pig {EntityData:{id:"minecraft:pig",Saddle:1b}}
 *   Steve blockbuster.fred {Skin:"blockbuster:skins/fred/red.png", Scale:2.0f}
 * </pre>
 *
 * <p>A Bukkit plugin sends this with
 * {@code player.sendPluginMessage(plugin, "metamorph:plugin_morph", bytes)}
 * after {@code getMessenger().registerOutgoingPluginChannel(...)}; the byte
 * array is the payload verbatim — <b>no</b> {@code DataOutputStream.writeUTF}
 * length prefix, or the prefix bytes end up inside the username token.</p>
 *
 * <h2>Semantics</h2>
 *
 * <ul>
 *   <li>Applied <b>client-side only</b>, on the receiving client, through the
 *   P52 morphing component ({@code force = true} — no acquired-morph or
 *   creative check).</li>
 *   <li>Every applied morph is forced to {@link MorphSettings#DEFAULT}
 *   ({@code hostile=false, hands=true, updates=false}): a plugin morph grants
 *   <b>no abilities, actions or attacks</b>. Legacy passed the shared static
 *   instance, not a copy; kept verbatim.</li>
 *   <li>Unknown player name → silent no-op (legacy behaviour: the payload can
 *   legitimately arrive before the player is in render distance).</li>
 *   <li>Unknown/blacklisted morph name, unparsable SNBT, oversized or
 *   undecodable payload → logged warning + no-op. Never a crash, never a
 *   disconnect: this is untrusted external input.</li>
 * </ul>
 *
 * <h2>Deliberate deviations from 1.12.2</h2>
 *
 * <ol>
 *   <li><b>Channel id.</b> Legacy used the bare string {@code "Metamorph"},
 *   which is unrepresentable here: {@link Identifier}'s grammar is
 *   {@code [a-z0-9_.-]} (the uppercase {@code M} is illegal) <i>and</i> Bukkit's
 *   {@code Messenger.validateChannel} has required namespaced lowercase
 *   channels since 1.13, so no modern plugin could send on it either. The
 *   namespace-plus-path id chosen for the port is {@code metamorph:plugin_morph}
 *   — <b>not</b> {@code metamorph:morph}, which the port already burns twice:
 *   it is the dispatcher channel of {@code PacketMorph} (P55) and the entity
 *   type id of the morph ghost. A second global receiver on that identifier
 *   would be silently dropped by Fabric and the plaintext payload would reach
 *   {@code ClientHandlerMorph} instead. No 2.7.2-era plugin works unchanged
 *   regardless of the id; every plugin must be re-pointed at this one.</li>
 *   <li><b>Byte count.</b> Legacy read {@code buffer.capacity()} bytes; the
 *   Fabric buffer is a slice of a shared network buffer whose capacity is
 *   unrelated to the payload, so this reads {@link PacketByteBuf#readableBytes()}.</li>
 *   <li><b>Foreign morphs.</b> {@code MorphManager.morphFromNBT} no longer
 *   returns {@code null} for an unclaimed {@code Name} — since P220 it returns
 *   an inert {@link ForeignMorph} that preserves the compound for storage
 *   round-trips. Applying one here would replace the player's morph with
 *   nothing, so this handler rejects it and keeps legacy's "unknown morph =
 *   no-op" contract.</li>
 *   <li><b>Silence.</b> Legacy swallowed SNBT parse failures with an empty
 *   {@code catch} and printed stack traces for payload failures; both become
 *   one-line warnings on the {@code Metamorph} logger (the project's
 *   total-reader rule).</li>
 * </ol>
 *
 * <h2>Security note (parity, deliberately kept)</h2>
 *
 * <p>The payload names an <b>arbitrary player</b>, not the receiver: any server
 * the client connects to can change how that client renders any other player it
 * can see, at will, with no permission check. That is exactly what 1.12.2 did —
 * silently — and it is what makes the feature useful (a plugin morphs everyone's
 * view of one player with one broadcast). It is <b>purely cosmetic and purely
 * client-side</b>: nothing here touches the server's idea of the player, no
 * ability/attack/action is granted ({@link MorphSettings#DEFAULT} is forced),
 * and the effect dies with the connection.</p>
 *
 * <p>Legacy source:
 * .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/NetworkHandler.java</p>
 */
public class NetworkHandler
{
    /**
     * The plugin channel. See the deviation note above for why the legacy
     * {@code "Metamorph"} name and the obvious {@code metamorph:morph} are both
     * unavailable.
     */
    public static final Identifier CHANNEL = new Identifier(Metamorph.MOD_ID, "plugin_morph");

    /**
     * Hard payload ceiling. This channel is clientbound, so vanilla's own cap
     * is 1,048,576 bytes (the serverbound cap is 32,767); the packet never
     * reaches us above that, and this guard makes the bound explicit and
     * testable for loopback/proxy senders that bypass the vanilla codec.
     */
    public static final int MAX_PAYLOAD_BYTES = 1048576;

    /**
     * Registers the client receiver. Called from
     * {@link MetamorphClient#init()}; idempotent registration is Fabric's
     * problem (a second call is rejected with a warning by the API itself).
     */
    public static void register()
    {
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, responseSender) ->
        {
            /* Decode on the netty thread — the buffer is released when this
             * callback returns — then hop to the game thread, exactly like
             * legacy's Minecraft.addScheduledTask(new MorphRunnable(args)). */
            String[] args = readPayload(buf);

            if (args == null)
            {
                return;
            }

            client.execute(new MorphRunnable(args));
        });
    }

    /**
     * Total reader: the raw payload bytes → the space-split argument array, or
     * {@code null} when the payload is unusable (logged). Never throws.
     */
    public static String[] readPayload(PacketByteBuf buf)
    {
        try
        {
            int length = buf.readableBytes();

            if (length > MAX_PAYLOAD_BYTES)
            {
                Metamorph.LOGGER.warn("{}: dropping a {} byte payload (limit {})", CHANNEL, length, MAX_PAYLOAD_BYTES);

                return null;
            }

            byte[] array = new byte[length];

            buf.readBytes(array);

            /* Legacy: new String(array, "UTF-8").trim().split(" ") — malformed
             * UTF-8 decodes to U+FFFD rather than throwing, and split(" ")
             * keeps empty tokens from runs of spaces, both of which are the
             * legacy behaviour and are relied upon by the arg-count branches. */
            return new String(array, StandardCharsets.UTF_8).trim().split(" ");
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("{}: malformed payload — ignoring", CHANNEL, e);

            return null;
        }
    }

    /**
     * The pure half of {@link MorphRunnable}: argument array → what to do, or
     * {@code null} for "nothing" (logged). The morph, when there is one, is
     * fully built and already forced to {@link MorphSettings#DEFAULT}.
     */
    public static Command parse(String[] args)
    {
        if (args == null || args.length == 0)
        {
            return null;
        }

        String target = args[0];

        if (args.length >= 3)
        {
            /* NBT re-joined from args[2..]: the payload is space-split, so an
             * SNBT compound containing spaces arrives as several tokens. */
            StringBuilder data = new StringBuilder(args[2]);

            for (int i = 3; i < args.length; i++)
            {
                data.append(" ").append(args[i]);
            }

            NbtCompound tag;

            try
            {
                tag = StringNbtReader.parse(data.toString());
            }
            catch (Exception e)
            {
                Metamorph.LOGGER.warn("{}: unparsable morph NBT '{}' — ignoring", CHANNEL, data);

                return null;
            }

            tag.putString("Name", args[1]);

            return command(target, tag);
        }
        else if (args.length == 2)
        {
            NbtCompound tag = new NbtCompound();

            tag.putString("Name", args[1]);

            return command(target, tag);
        }

        return new Command(target, null);
    }

    /**
     * Applies a parsed command to a resolved player through the P52 morphing
     * component. Forced, like legacy — no acquired-morph or creative gate.
     */
    public static void apply(Command command, PlayerEntity player)
    {
        if (command == null || player == null)
        {
            return;
        }

        IMorphing morphing = Morphing.get(player);

        if (morphing == null)
        {
            return;
        }

        morphing.setCurrentMorph(command.morph, player, true);
    }

    /**
     * Legacy {@code World.getPlayerEntityByName}, which yarn 1.20.4 does not
     * have: an exact, case-sensitive scan of the world's player list (same as
     * {@code EntityMorph.resolveOwner}'s username arm). Generic so it is
     * testable without a constructible {@code PlayerEntity}.
     */
    public static <T> T findPlayer(List<? extends T> players, Function<T, String> name, String target)
    {
        if (players == null || target == null)
        {
            return null;
        }

        for (T player : players)
        {
            if (target.equals(name.apply(player)))
            {
                return player;
            }
        }

        return null;
    }

    /** Morph construction + the two rejections that keep legacy's no-op. */
    private static Command command(String target, NbtCompound tag)
    {
        AbstractMorph morph = MorphManager.INSTANCE.morphFromNBT(tag);

        if (morph == null)
        {
            /* blacklisted, or no factory registered at all */
            Metamorph.LOGGER.warn("{}: morph '{}' is unavailable (blacklisted or unregistered) — ignoring", CHANNEL, tag.getString("Name"));

            return null;
        }

        if (morph instanceof ForeignMorph)
        {
            /* P220's placeholder exists to survive load/save round trips of
             * stored data; applying it live would silently blank the player. */
            Metamorph.LOGGER.warn("{}: no factory claims morph '{}' — ignoring", CHANNEL, tag.getString("Name"));

            return null;
        }

        /* No fancy stuff or actions */
        morph.forceSettings(MorphSettings.DEFAULT);

        return new Command(target, morph);
    }

    /**
     * One decoded payload: the targeted player's name plus the morph to give
     * them, or {@code null} for the 1-token demorph form.
     */
    public static class Command
    {
        public final String player;
        public final AbstractMorph morph;

        public Command(String player, AbstractMorph morph)
        {
            this.player = player;
            this.morph = morph;
        }

        public boolean isDemorph()
        {
            return this.morph == null;
        }
    }

    /**
     * Morph runnable
     *
     * This class is responsible for morphing a player into a morph or
     * demorphing him based on the arguments passed in the payload packet.
     */
    public static class MorphRunnable implements Runnable
    {
        public String[] args;

        public MorphRunnable(String[] args)
        {
            this.args = args;
        }

        @Override
        public void run()
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld world = mc == null ? null : mc.world;

            if (world == null)
            {
                return;
            }

            /* Legacy looked the player up first and returned before parsing
             * anything — an unknown name is not a warning, it is the normal
             * case for a broadcast that names someone out of render range. */
            PlayerEntity player = findPlayer(world.getPlayers(), p -> p.getName().getString(), this.args[0]);

            if (player == null)
            {
                return;
            }

            apply(parse(this.args), player);
        }
    }
}
