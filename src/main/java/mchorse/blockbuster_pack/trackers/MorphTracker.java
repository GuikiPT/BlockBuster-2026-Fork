package mchorse.blockbuster_pack.trackers;

import java.util.function.BooleanSupplier;

import mchorse.aperture.camera.CameraExporter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * The default tracker (port of Blockbuster 2.7.2's {@code MorphTracker},
 * roadmap P166). Feeds two consumers: the minema video-capture "track this
 * label" hook and Aperture's {@code CameraExporter} tracking-packet exporter.
 * Serialises a single {@code CombineTracking} byte (written unconditionally).
 *
 * <p><b>Seams (batch-4 integration).</b> The legacy class embedded the minema
 * ({@code @Optional.Method(modid = Minema.MODID)}) and Aperture
 * ({@code @Optional.Method(modid = Aperture.MOD_ID)}) integrations as inner
 * tracker classes, instantiated in {@link #init()} only when the respective mod
 * was loaded. Those integrations do not exist yet in the port:</p>
 * <ul>
 *   <li>the Aperture {@code CameraExporter} / {@code GuiMinemaPanel} tracking
 *       exporter lands in <b>S15</b>;</li>
 *   <li>the minema-replacement video-capture {@code doTrack} hook lands in
 *       <b>S18</b>.</li>
 * </ul>
 * <p>Each installs a real {@link ITrackerHook} into {@link #minemaHook} /
 * {@link #apertureHook}. When unset (headless, or before those stages) the
 * calls no-op, exactly like the legacy {@code Loader.isModLoaded} guard. The
 * OptiFine shadow-pass skip becomes the {@link #shadowPass} predicate
 * (installed by the S6 render layer); null → treat as "not a shadow pass",
 * matching {@code ReflectionUtils.isOptifineShadowPass()} returning false when
 * OptiFine is absent.</p>
 *
 * <p><b>batch-O (P202.1).</b> The Aperture receiving end landed, so the legacy
 * {@code ApertureTracker} body is back verbatim behind {@link #apertureHook}
 * (installed by {@code mchorse.blockbuster.client.video.TrackingHooks}), and the
 * per-instance {@link CameraExporter.TrackingPacket} field is a field again —
 * exactly like 1.12.2, including the reset → drop-the-packet-and-skip-a-frame
 * handshake, which sits <b>before</b> the shadow-pass check just as it did in
 * legacy (so a reset is consumed even during an OptiFine shadow pass).</p>
 *
 * <p><b>S22 P240 — {@link #minemaHook} stays uninstalled, deliberately.</b>
 * The stage file expected P234's video pipeline to become its consumer; the
 * code says otherwise. {@code minemaHook}'s legacy body was
 * {@code MinemaAPI.doTrack(tracker.name)} — a call into <i>Minema's own</i>
 * tracking backend ({@code info.ata4.minecraft.minema}), a third-party mod
 * that does not exist on 1.20.4 and whose recorder the port replaced with
 * {@code MinemaBackend}. That backend writes video frames; it has no tracking
 * side-channel, because in the port the tracking output <b>is</b> Aperture's
 * {@link CameraExporter} — which {@link #apertureHook} already feeds every
 * render. Installing a second hook onto the same exporter would double-track.
 * A null {@code minemaHook} is therefore exact 1.12.2-without-Minema parity,
 * which the stage overview explicitly classes as parity rather than a gap.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/trackers/MorphTracker.java
 */
public class MorphTracker extends BaseTracker
{
    /**
     * Render-time tracking consumer seam. Installed by S15 (Aperture) / S18
     * (minema). No-op when unset.
     */
    public interface ITrackerHook
    {
        void track(MorphTracker tracker);
    }

    /**
     * Minema's own tracking side-channel. <b>Intentionally never installed</b>
     * — see the class javadoc's S22 P240 note. Kept because the field is the
     * shape of the legacy two-backend dispatch and third parties may set it.
     */
    public static ITrackerHook minemaHook;

    /** Aperture {@code CameraExporter} tracking-packet hook (S15). Null → no-op. */
    public static ITrackerHook apertureHook;

    /**
     * OptiFine shadow-pass predicate (S6). When it returns true the Aperture
     * tracking is skipped for that render. Null → never a shadow pass.
     */
    public static BooleanSupplier shadowPass;

    /**
     * The exporter packet this tracker feeds, created lazily by the Aperture
     * hook and dropped when the exporter flags it reset (legacy field).
     */
    private CameraExporter.TrackingPacket trackingPacket = null;

    /* for aperture tracking */
    private boolean combineTracking;

    public CameraExporter.TrackingPacket getTrackingPacket()
    {
        return this.trackingPacket;
    }

    public void setTrackingPacket(CameraExporter.TrackingPacket trackingPacket)
    {
        this.trackingPacket = trackingPacket;
    }

    public void setCombineTracking(boolean combineTracking)
    {
        this.combineTracking = combineTracking;
    }

    public boolean getCombineTracking()
    {
        return this.combineTracking;
    }

    @Override
    public void track(LivingEntity target, double x, double y, double z, float entityYaw, float partialTicks)
    {
        /* minema tracking */
        if (minemaHook != null)
        {
            minemaHook.track(this);
        }

        /* aperture tracking */
        if (this.trackingPacket != null && this.trackingPacket.isReset())
        {
            this.trackingPacket = null;

            return;
        }

        /* (skipped during the OptiFine shadow pass) */
        if (apertureHook != null && (shadowPass == null || !shadowPass.getAsBoolean()))
        {
            apertureHook.track(this);
        }
    }

    @Override
    public boolean canMerge(BaseTracker tracker)
    {
        if (tracker instanceof MorphTracker)
        {
            MorphTracker apTracker = (MorphTracker) tracker;
            this.combineTracking = apTracker.combineTracking;

            return super.canMerge(tracker);
        }

        return false;
    }

    @Override
    public void copy(BaseTracker tracker)
    {
        if (tracker instanceof MorphTracker)
        {
            MorphTracker trackerAperture = (MorphTracker) tracker;

            this.combineTracking = trackerAperture.combineTracking;
        }

        super.copy(tracker);
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = false;

        if (obj instanceof MorphTracker)
        {
            MorphTracker morph = (MorphTracker) obj;

            result = super.equals(obj) && this.combineTracking == morph.combineTracking;
        }

        return result;
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        this.combineTracking = tag.getBoolean("CombineTracking");
    }

    @Override
    public NbtCompound toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        tag.putBoolean("CombineTracking", this.combineTracking);

        return tag;
    }
}
