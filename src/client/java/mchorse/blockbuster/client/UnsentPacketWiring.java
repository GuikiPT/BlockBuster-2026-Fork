package mchorse.blockbuster.client;

import mchorse.blockbuster.events.GunShootHandler;
import mchorse.blockbuster.recording.capturing.FrameHandler;

/**
 * S22 P244 — installer for the two client-tick handlers whose absence left
 * registered packets with no production send site.
 *
 * <p>The P244 audit found three registered-but-unsent packets. Two of them are
 * sent from client-tick handlers that 1.12.2 had and the port never wrote:</p>
 *
 * <table>
 *   <tr><th>Packet</th><th>Legacy sender</th><th>Ported to</th></tr>
 *   <tr><td>{@code PacketGunReloading}</td>
 *       <td>{@code events/GunShootHandler.handleReloading}</td>
 *       <td>{@link GunShootHandler}</td></tr>
 *   <tr><td>{@code PacketDamageControlCheck}</td>
 *       <td>{@code recording/capturing/FrameHandler.onPlayerTick}</td>
 *       <td>{@link FrameHandler}</td></tr>
 * </table>
 *
 * <p>The third, {@code aperture:PacketCameraReset}, has no sender in Aperture
 * 1.8.2 either — see {@code plan/inbox/batchS-M.md} for the decision record. Its
 * registration is kept, so nothing here touches it.</p>
 *
 * <p>Both handlers also carry legacy behaviour beyond their packet:
 * {@code GunShootHandler} additionally sends {@code PacketGunInteract} (the
 * client half of firing a gun, whose only prior send site was the server's own
 * echo) and blocks the conflicting vanilla left click; {@code FrameHandler}
 * additionally drives the client-side {@code Mode.FRAMES} recorder that
 * {@code ClientHandlerPlayerRecording} uploads.</p>
 */
public class UnsentPacketWiring
{
    private static boolean installed;

    public static void install()
    {
        if (installed)
        {
            return;
        }

        installed = true;

        GunShootHandler.install();
        FrameHandler.install();
    }

    /** Test seam — whether {@link #install()} has run in this JVM. */
    public static boolean isInstalled()
    {
        return installed;
    }
}
