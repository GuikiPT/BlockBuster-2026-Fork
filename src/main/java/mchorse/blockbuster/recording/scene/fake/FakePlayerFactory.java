package mchorse.blockbuster.recording.scene.fake;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.capabilities.recording.IRecording;
import mchorse.blockbuster.capabilities.recording.Recording;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.client.option.ChatVisibility;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Arm;
import net.minecraft.util.Uuids;

/**
 * Builds scene fake players — the single-class replacement for the five legacy
 * netty-stub classes ({@code fake/FakeContext}, {@code FakeChannel},
 * {@code FakeProtocol}, {@code FakeFMLAttribute}, {@code FakeConfig}) plus the
 * hand-rolled {@code CPacketClientSettings} / {@code NetHandlerPlayServer} dance
 * in {@code Scene.collectActors}.
 *
 * <p>Legacy behavior preserved:</p>
 * <ul>
 *   <li>deterministic profile {@code new GameProfile(new UUID(0, actorIndex),
 *       name.isEmpty() ? "Player" : name)} (fake UUIDs are observable to other
 *       mods/scoreboards, so the collision-prone {@code UUID(0, index)} scheme
 *       is kept verbatim);</li>
 *   <li>a {@code PlayerMorph}'s own profile overrides the generated one;</li>
 *   <li>skin layers forced on via the client-options {@code playerModelParts}
 *       mask {@code 127} — the whole reason for the legacy client-settings hack;</li>
 *   <li>marked as a fake player through the {@link IRecording} component
 *       ({@code Recording.get(player).setFakePlayer(true)}).</li>
 * </ul>
 *
 * <p>On 1.20.1 the netty stubs collapse into {@link FakeClientConnection}/
 * {@link FakePlayerNetworkHandler}, but the mask still travels the legacy route:
 * a {@link ClientSettingsC2SPacket} handed straight to
 * {@code ServerPlayerEntity.setClientSettings} — the same packet type legacy
 * hand-built as {@code CPacketClientSettings}, minus the byte-buffer forgery.</p>
 */
public class FakePlayerFactory
{
    /** Skin-layer bitmask with every model part enabled (legacy wrote 127). */
    public static final int ALL_MODEL_PARTS = 127;

    /**
     * Construct a fake {@link ServerPlayerEntity} for the given replay.
     *
     * @param world      the scene's server world.
     * @param replay     the replay descriptor (name + morph → profile).
     * @param actorIndex {@code Scene.actorsCount} at construction time — feeds
     *                   the deterministic UUID.
     * @return the fake player, or {@code null} if it could not be created (never
     *         throws — the caller falls back to a plain actor).
     */
    public static ServerPlayerEntity create(ServerWorld world, Replay replay, int actorIndex)
    {
        if (world == null || world.getServer() == null)
        {
            Blockbuster.LOGGER.warn("Cannot create a fake player for replay '" + (replay == null ? "?" : replay.id) + "': no server world");

            return null;
        }

        try
        {
            GameProfile profile = createProfile(replay, actorIndex);

            ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile);

            /* Attach a dead handler so nothing ever gets sent to a nonexistent
             * client. */
            player.networkHandler = new FakePlayerNetworkHandler(player);

            /* Skins layers don't show up by default; force the model-part mask
             * onto the tracked data (legacy did this via handleClientSettings). */
            player.setClientSettings(createClientSettings());

            IRecording recording = Recording.get(player);

            if (recording != null)
            {
                recording.setFakePlayer(true);
            }

            return player;
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to create a fake player for replay '" + (replay == null ? "?" : replay.id) + "'", e);

            return null;
        }
    }

    /**
     * Build the fake player's profile: deterministic {@code UUID(0, actorIndex)}
     * + replay name, unless the replay carries a player morph whose own profile
     * takes over (legacy {@code replay.morph instanceof PlayerMorph}).
     */
    public static GameProfile createProfile(Replay replay, int actorIndex)
    {
        String name = replay == null || replay.name.isEmpty() ? "Player" : replay.name;
        GameProfile profile = new GameProfile(new UUID(0, actorIndex), name);

        if (replay != null && replay.isPlayerMorph())
        {
            GameProfile fromMorph = readMorphProfile(replay.morph);

            if (fromMorph != null)
            {
                profile = fromMorph;
            }
        }

        return profile;
    }

    /**
     * Read a player disguise's own profile — legacy
     * {@code ((PlayerMorph) replay.morph).profile}, now
     * {@link EntityMorph#profile} on a morph named
     * {@link EntityMorph#PLAYER_ID}.
     *
     * <p><b>Deliberate deviation:</b> legacy assigned that field
     * unconditionally, so a player morph whose profile never resolved handed a
     * {@code null} profile to the fake player's constructor and threw. The
     * caller here keeps the deterministic {@code UUID(0, index)} profile when
     * this returns {@code null} — which is also why it reads the raw field
     * rather than {@code resolveProfile()}: the stand-in Steve profile would
     * name every unresolved actor "Steve".</p>
     */
    private static GameProfile readMorphProfile(AbstractMorph morph)
    {
        return morph instanceof EntityMorph entity && entity.isPlayer() ? entity.profile : null;
    }

    /**
     * Client settings mirroring the legacy {@code CPacketClientSettings} buffer:
     * language {@code "en_US"}, view distance {@code 10}, chat {@code FULL},
     * chat colors on, model-part mask {@code 127} (all layers), main arm
     * {@code RIGHT}. Text filtering off and server-listing on match the vanilla
     * defaults for the two fields 1.12.2 had no concept of.
     */
    public static ClientSettingsC2SPacket createClientSettings()
    {
        return new ClientSettingsC2SPacket("en_US", 10, ChatVisibility.FULL, true, ALL_MODEL_PARTS, Arm.RIGHT, false, true);
    }
}
