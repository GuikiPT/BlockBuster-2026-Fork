package mchorse.blockbuster.client;

import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.utils.EntityUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;

/**
 * S22 <b>P238</b> — limb roll (and the two client bits of frame application
 * that travel with it).
 *
 * <h2>What was dark</h2>
 * <p>Roll was captured and stored the whole way through — {@code
 * Frame.fromPlayerClient} records {@code CameraHandler.getRoll()},
 * {@code Frame.toNBT} writes the {@code "Roll"} tag, {@code Frame.apply} writes
 * {@code EntityActor.roll}, and {@code CameraHandler.rollGetter} /
 * {@code rollPartialGetter} <i>are</i> installed by {@code ApertureClient}. The
 * two ends were missing:</p>
 * <ul>
 *   <li>{@code EntityUtils.getRoll} returned a hard-coded {@code 0F}, so the
 *       stored value was never read back;</li>
 *   <li>{@code PoseContext.roll} — which {@code ModelCustom.setRotationAngles}
 *       applies to every limb flagged {@code roll} — had <b>no writer
 *       anywhere</b>.</li>
 * </ul>
 * <p>Net effect: limbs flagged {@code roll} in a custom model stayed upright
 * during recorded / camera-rolled playback. Both ends are now real
 * ({@code EntityUtils.getRoll} carries the legacy body,
 * {@code PoseContexts.fromEntity} writes {@code context.roll}); this class
 * installs the one thing those bodies cannot reach from the main source set —
 * the client player — plus the two {@code Record} client-frame behaviours that
 * were left as TODOs next to it.</p>
 *
 * <h2>The seams</h2>
 * <ul>
 *   <li>{@link EntityUtils#clientPlayer} — legacy compared against
 *       {@code Minecraft.getMinecraft().player} in both
 *       {@code EntityUtils.getRoll} and {@code Record.applyFrameClient}. With
 *       it installed, playing a record back on <b>yourself</b> drives Aperture's
 *       camera roll (legacy {@code applyFrameClient} →
 *       {@code CameraHandler.setRoll(prevRoll, roll)}), which is what made the
 *       local player's own model roll follow the recording.</li>
 *   <li>{@link Record#clientSneakInput} — legacy
 *       {@code Record.applyClientMovement} pushed the recorded sneak state into
 *       {@code EntityPlayerSP.movementInput.sneak}, not just
 *       {@code setSneaking}. Yarn's equivalent is
 *       {@code ClientPlayerEntity.input.sneaking}. Without it a self-playback
 *       un-sneaks on the very next input poll.</li>
 * </ul>
 *
 * <p>One {@code install()}, one line in {@code BlockbusterClient} (S22
 * shared-file protocol).</p>
 *
 * Legacy sources:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/utils/EntityUtils.java (getRoll),
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/recording/data/Record.java
 * (applyClientMovement / applyFrameClient),
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/client/model/ModelCustom.java:443
 */
public final class LimbRollWiring
{
    private LimbRollWiring()
    {}

    /** Install the P238 seams. Idempotent (plain field assignment). */
    public static void install()
    {
        EntityUtils.clientPlayer = LimbRollWiring::clientPlayer;
        Record.clientSneakInput = LimbRollWiring::applySneakInput;
    }

    /** Legacy {@code Minecraft.getMinecraft().player}. */
    public static LivingEntity clientPlayer()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc == null ? null : mc.player;
    }

    /**
     * Legacy {@code ((EntityPlayerSP) actor).movementInput.sneak = sneaking} —
     * only the local player has an {@code Input}, every other entity is a
     * no-op (legacy's {@code instanceof EntityPlayerSP} guard).
     */
    public static void applySneakInput(LivingEntity actor, boolean sneaking)
    {
        if (actor instanceof ClientPlayerEntity player && player.input != null)
        {
            player.input.sneaking = sneaking;
        }
    }
}
