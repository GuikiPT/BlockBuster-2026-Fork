package mchorse.blockbuster.utils;

import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.capabilities.recording.IRecording;
import mchorse.blockbuster.capabilities.recording.Recording;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.mclib.utils.Interpolations;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Partial port of legacy {@code utils/EntityUtils} — the record-player
 * attachment half needed by the recording engine (P109–P111). The raytracing
 * helpers arrive with the action batches that use them.
 *
 * <p>Legacy attaches the {@link RecordPlayer} to actors via the
 * {@code EntityActor.playback} field and to players via the {@link IRecording}
 * capability (P112). Non-actor, non-player living entities are never attached
 * (mirrors legacy — returns null / no-op).</p>
 */
public class EntityUtils
{
    /**
     * Legacy {@code EntityUtils.sendStatusMessage} — sends the message to the
     * <b>action bar</b> (the overlay slot), not the chat log. In 1.12.2 this was
     * {@code player.sendStatusMessage(message, true)}; the yarn 1.20.4
     * equivalent is {@code sendMessage(text, overlay=true)}.
     */
    public static void sendStatusMessage(ServerPlayerEntity player, Text message)
    {
        player.sendMessage(message, true);
    }

    /**
     * Select the {@code model.json} pose key for an entity's state (roadmap P82,
     * legacy {@code EntityUtils.poseForEntity}).
     *
     * <p>The priority order is <b>load-bearing and user-visible</b>: riding beats
     * flying beats sneaking. The returned strings ({@code "riding"},
     * {@code "flying"}, {@code "sneaking"}, {@code "standing"}) are exact
     * model.json pose-map keys, so a riding-and-sneaking entity resolves to
     * {@code "riding"}.</p>
     */
    public static String poseForEntity(LivingEntity entity)
    {
        return poseForEntity(entity.hasVehicle(), entity.isFallFlying(), entity.isSneaking());
    }

    /**
     * Pure-flag form of {@link #poseForEntity(LivingEntity)} — headless-testable
     * and the single source of the priority ordering.
     */
    public static String poseForEntity(boolean riding, boolean flying, boolean sneaking)
    {
        if (riding)
        {
            return "riding";
        }

        if (flying)
        {
            return "flying";
        }

        if (sneaking)
        {
            return "sneaking";
        }

        return "standing";
    }

    /**
     * The local (client) player, or {@code null} on a server / headless.
     *
     * <p>S22 P238 seam: legacy {@link #getRoll} compared the entity against
     * {@code Minecraft.getMinecraft().player} directly, and {@code Record}'s
     * client frame application did the same. This class is main-source, so the
     * client entrypoint installs the lookup
     * ({@code mchorse.blockbuster.client.LimbRollWiring}). The {@code null}
     * default makes every "is this the local player" test false, which is the
     * correct server-side answer.</p>
     */
    public static Supplier<LivingEntity> clientPlayer = () -> null;

    /**
     * Roll (in degrees) applied to a limb marked {@code roll} (roadmap P82,
     * legacy {@code EntityUtils.getRoll}).
     *
     * <p>Verbatim legacy body (S22 P238): an {@link EntityActor} interpolates
     * its recorded {@code prevRoll → roll} pair, the local player reads
     * Aperture's camera roll through {@link mchorse.blockbuster.aperture
     * .CameraHandler#getRoll(float)}, everything else is upright.</p>
     *
     * <p>Legacy took a plain {@code Entity}; every call site passes a
     * {@code LivingEntity} (the pose pass only runs for living entities), so the
     * port narrows the parameter. Behaviour is unchanged.</p>
     */
    public static float getRoll(LivingEntity entity, float partialTicks)
    {
        if (entity instanceof EntityActor actor)
        {
            return Interpolations.lerp(actor.prevRoll, actor.roll, partialTicks);
        }
        else if (entity != null && entity == clientPlayer.get())
        {
            return CameraHandler.getRoll(partialTicks);
        }

        return 0F;
    }

    /**
     * Legacy {@code EntityUtils.entityByUUID(World, UUID)} — "Get entity by
     * UUID in the server world. Looked up on minecraft forge forum, I don't
     * remember where's exactly...".
     *
     * <p>1.12.2 linearly scanned {@code world.loadedEntityList}. 1.20.4 keeps a
     * UUID index behind {@link ServerWorld#getEntity(UUID)}, so the scan is
     * replaced by that lookup (same result, O(1)).</p>
     *
     * <p><b>1.20.4 deviation:</b> there is no generic entity iteration on a
     * plain {@link World}, so a non-{@link ServerWorld} (i.e. the client world)
     * returns {@code null} instead of scanning. Every caller is playback code
     * that only ever runs server-side. A null world or a null/zero
     * {@code target} also returns {@code null} rather than throwing.</p>
     */
    public static Entity entityByUUID(World world, UUID target)
    {
        if (target == null || !(world instanceof ServerWorld serverWorld))
        {
            return null;
        }

        return serverWorld.getEntity(target);
    }

    public static RecordPlayer getRecordPlayer(LivingEntity entity)
    {
        if (entity instanceof EntityActor actor)
        {
            return actor.playback;
        }
        else if (entity instanceof PlayerEntity player)
        {
            IRecording record = Recording.get(player);

            return record == null ? null : record.getRecordPlayer();
        }

        return null;
    }

    public static void setRecordPlayer(LivingEntity entity, RecordPlayer player)
    {
        if (entity instanceof EntityActor actor)
        {
            actor.playback = player;
        }
        else if (entity instanceof PlayerEntity playerEntity)
        {
            IRecording record = Recording.get(playerEntity);

            if (record != null)
            {
                record.setRecordPlayer(player);
            }
        }
    }
}
