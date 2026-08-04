package mchorse.aperture.camera;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import mchorse.aperture.Aperture;
import mchorse.aperture.camera.data.Position;
import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.MatrixUtils;

import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Exporter for camera, entity and morph tracking data (S18 <b>P202.1</b>) —
 * the compositing JSON that external tools (Chryfi's Blender import script)
 * consume.
 *
 * <p>Port of Aperture 1.8.2's {@code mchorse.aperture.camera.CameraExporter}
 * (author: Christian F. / Chryfi), kept under its legacy package/class name for
 * diff-ability against {@code .tools/legacy-src}. The <b>JSON contract is
 * frozen</b>: key names, key order, the deliberate {@code motionblur_fps}
 * duplication of {@code fps}, the hardcoded {@code dynamic_fov: false}, the
 * {@code required_import_version} of {@value #REQUIRED_IMPORT_VERSION}, the
 * {@code "frame"} start-offset written only on the first element of each
 * entity/morph array, and the {@code name.1}/{@code name.2} duplicate-name
 * dedupe all transfer verbatim.</p>
 *
 * <h2>Modern-API substitutions (decided in {@code plan/S18-video-capture.md})</h2>
 * <ul>
 * <li><b>Event seams.</b> Legacy registered
 * {@code MinemaEventbus.cameraBUS}/{@code endRenderBUS} listeners inside
 * {@link #start(PositionSource)}. Minema is dead on 1.20.4, so
 * {@link #addCameraFrame()} and {@link #frameEnd()} are called directly by the
 * P199–P202 capture stack's per-frame hooks; both early-return when not
 * {@link #isTracking() building}, which is exactly what "the listener is only
 * registered during a session" meant.</li>
 * <li><b>Camera position.</b> {@code GL_MODELVIEW_MATRIX} does not exist on core
 * profile. The camera world position is read straight off the 1.20.4
 * {@code Camera} the S15 runner drives ({@link #cameraPosition}); the legacy
 * math shape is preserved as documentation and as an executable reference in
 * {@link #legacyCameraPosition(Matrix4d, double, double, double)}, which
 * {@code CameraPositionEquivalenceTest} asserts is numerically identical.</li>
 * <li><b>Timing.</b> {@code Minema.instance.getConfig()} becomes
 * {@link CaptureInfo}, supplied by the recorder from its {@code VideoParams}.
 * The Minema VR cube-face gate ({@code TimerModifier.getCubeFace() !=
 * CubeFace.FRONT}) is dropped — there is no VR cube capture in the port, so the
 * condition is constant-false and its term vanishes from
 * {@link #skipFrame()}.</li>
 * <li><b>Entity resolution.</b> {@link EntitySource} is the client-side seam
 * that runs the ported {@code mchorse.aperture.utils.EntitySelector} against the
 * client world and snapshots prev/current position + body yaw; the
 * interpolation itself stays here, on {@link Interpolations#lerp(double, double,
 * double)}, exactly like legacy.</li>
 * <li><b>Morph transform.</b> Legacy's no-arg {@code MatrixUtils.getTransformation()}
 * read GL state; the port's renderer publishes the decomposed
 * {@link MatrixUtils.Transformation} into {@link #currentTransformation} right
 * before invoking the tracker (same value, supplied instead of scraped).</li>
 * <li><b>Dropped:</b> the Minema-native {@code MinemaAPI.doTrack(name)}
 * side-channel that {@code MorphTracker.MinemaTracker} also fed. It was a second
 * tracking backend inside Minema itself with no port equivalent; this JSON
 * exporter is the surviving contract (documented divergence).</li>
 * </ul>
 *
 * <h2>Divergences worth knowing</h2>
 * <ul>
 * <li>Legacy held resolved entities in a {@link java.util.HashSet}, so
 * {@code entity_tracking} key order followed identity hashes — unstable between
 * runs. The port keeps a {@link LinkedHashSet} in selector-match order so the
 * output is reproducible (and golden-comparable). No key/value changes.</li>
 * <li>Morph transform components are {@code float}-derived here
 * ({@link MatrixUtils.Transformation} stores {@code Matrix4f}) where legacy
 * decomposed {@code Matrix4d}; the printed doubles therefore carry float
 * precision. The goldens are the port's own.</li>
 * <li>{@link #track(TrackingPacket)} null-guards its argument (legacy would
 * NPE). Total-reader rule.</li>
 * </ul>
 *
 * <p>This class lives in the <b>main</b> source set — not
 * {@code src/client} as the plan sketch assumed — because
 * {@code mchorse.blockbuster_pack.trackers.MorphTracker} holds a
 * {@link TrackingPacket} field and is itself common code (a morph/NBT class
 * loaded on both sides). Everything client-only sits behind the seams above,
 * which {@code mchorse.blockbuster.client.video.TrackingHooks} installs.</p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/CameraExporter.java
 */
public class CameraExporter
{
    /** Frozen importer contract version written into the header. */
    public static final int REQUIRED_IMPORT_VERSION = 160;

    /* --------------------------------------------------------------------- */
    /* Seams                                                                 */
    /* --------------------------------------------------------------------- */

    /**
     * The camera angle source — legacy took the whole {@code CameraRunner};
     * {@code mchorse.aperture.camera.CameraRunner} (client) implements this so
     * {@code trackingExporter.start(editor.getRunner())} still compiles verbatim.
     */
    public interface PositionSource
    {
        Position getPosition();
    }

    /** Per-frame entity snapshot for one tracked entity (see {@link EntitySource}). */
    public static final class TrackedEntity
    {
        public final String uuid;
        public final String name;
        public final double lastTickX;
        public final double lastTickY;
        public final double lastTickZ;
        public final double x;
        public final double y;
        public final double z;
        /** Whether the entity is a {@code LivingEntity} (⇒ {@code body_rotation}). */
        public final boolean living;
        public final double prevBodyYaw;
        public final double bodyYaw;

        public TrackedEntity(String uuid, String name, double lastTickX, double lastTickY, double lastTickZ,
            double x, double y, double z, boolean living, double prevBodyYaw, double bodyYaw)
        {
            this.uuid = uuid;
            this.name = name;
            this.lastTickX = lastTickX;
            this.lastTickY = lastTickY;
            this.lastTickZ = lastTickZ;
            this.x = x;
            this.y = y;
            this.z = z;
            this.living = living;
            this.prevBodyYaw = prevBodyYaw;
            this.bodyYaw = bodyYaw;
        }

        /** Convenience for a still, non-living entity (tests/fixtures). */
        public TrackedEntity(String uuid, String name, double x, double y, double z)
        {
            this(uuid, name, x, y, z, x, y, z, false, 0, 0);
        }
    }

    /**
     * Resolves one normalized selector token (already {@code @…}-wrapped by
     * {@link #parseSelectors(String)}) into snapshots. Implementations may throw
     * — {@link #tryFindingEntity()} swallows, matching legacy's
     * {@code catch (Exception e) { }}.
     */
    public interface EntitySource
    {
        Collection<TrackedEntity> find(String token) throws Exception;
    }

    /**
     * The recorder's capture settings — the port's stand-in for
     * {@code Minema.instance.getConfig()}. {@code motionBlurFrames} is legacy's
     * {@code Math.round(getFrameRate()) / (int) frameRate}, i.e.
     * {@code 2^motionBlurLevel}.
     */
    public static final class CaptureInfo
    {
        public static final CaptureInfo DEFAULT = new CaptureInfo(60, 1920, 1080, 1, 1);

        public final int fps;
        public final int width;
        public final int height;
        public final int heldFrames;
        public final int motionBlurFrames;

        public CaptureInfo(int fps, int width, int height, int heldFrames, int motionBlurFrames)
        {
            this.fps = fps;
            this.width = width;
            this.height = height;
            this.heldFrames = Math.max(1, heldFrames);
            this.motionBlurFrames = Math.max(1, motionBlurFrames);
        }
    }

    /** Recorder capture settings, sampled once at {@link #start(PositionSource)}. */
    public static Supplier<CaptureInfo> captureInfo = () -> CaptureInfo.DEFAULT;

    /**
     * The camera's world position for the frame being tracked (1.20.4
     * {@code Camera#getPos}). {@code null} ⇒ the frame is skipped and
     * {@code trackedCamera} stays false, so morphs skip too.
     */
    public static Supplier<Vector3d> cameraPosition = () -> null;

    /** Render partial ticks ({@code MinecraftClient#getTickDelta}). */
    public static Supplier<Float> partialTicks = () -> 0F;

    /** Client-world selector matching. Default resolves nothing (headless). */
    public static EntitySource entitySource = (token) -> Collections.emptyList();

    /**
     * The decomposed model-view transformation of the morph currently
     * rendering, published by the tracker renderer immediately around its
     * {@code tracker.track(...)} call (legacy read GL state instead).
     */
    public static MatrixUtils.Transformation currentTransformation;

    /**
     * Where {@link #exportTrackingData(String)} writes. Legacy used Minema's
     * capture dir; the port writes next to the video output (P201 export path).
     */
    public static Supplier<Path> exportDir = () -> null;

    /* --------------------------------------------------------------------- */
    /* State (1:1 with legacy)                                               */
    /* --------------------------------------------------------------------- */

    private JsonObject wrapper = new JsonObject();
    private JsonArray cameraData = new JsonArray();
    private JsonObject entityData = new JsonObject();
    private JsonObject morphData = new JsonObject();

    private Map<String, TrackingPacket> registeredTrackingPackets = new HashMap<String, TrackingPacket>();

    /**
     * 1 to 1 relationship. Key is the name of the entity (indexed if duplicate),
     * value is the entity's UUID string.
     */
    private BiMap<String, String> entityNameRemaps = HashBiMap.create();

    private double[] trackingInitialPos = {0, 0, 0};

    /**
     * Resolved entities for the current frame. {@link LinkedHashSet} (legacy:
     * {@code HashSet}) so {@code entity_tracking} key order is reproducible.
     */
    private Set<TrackedEntity> entities = new LinkedHashSet<TrackedEntity>();

    private PositionSource runner;

    /* run variables */
    private int frame;
    /** held-frame counter, cycles {@code 1..heldFrames} (Minema's held frames) */
    private int heldframes;
    private double motionblurFrames;
    private boolean building = false;
    private boolean trackedCamera = false;

    /** Settings sampled at {@link #start(PositionSource)} (legacy read config live). */
    private CaptureInfo info = CaptureInfo.DEFAULT;

    /* user defined variables */
    private boolean relativeOrigin = false;
    private String selector; //for entities

    public void setOriginX(double x)
    {
        this.trackingInitialPos[0] = x;
    }

    public void setOriginY(double y)
    {
        this.trackingInitialPos[1] = y;
    }

    public void setOriginZ(double z)
    {
        this.trackingInitialPos[2] = z;
    }

    public double getOriginX()
    {
        return this.trackingInitialPos[0];
    }

    public double getOriginY()
    {
        return this.trackingInitialPos[1];
    }

    public double getOriginZ()
    {
        return this.trackingInitialPos[2];
    }

    /**
     * Sets the entity selector String and then tries to find these entities.
     */
    public void setEntitiesSelector(String selector)
    {
        this.selector = selector;
    }

    public String getEntitiesSelector()
    {
        return this.selector;
    }

    public int getFrame()
    {
        return (int) Math.floor(this.frame / this.motionblurFrames);
    }

    public boolean isTracking()
    {
        return this.building;
    }

    public boolean isRelativeOrigin()
    {
        return this.relativeOrigin;
    }

    public void setRelativeOrigin(boolean relativeOrigin)
    {
        this.relativeOrigin = relativeOrigin;
    }

    /** The exported document so far, in the compact form goldens compare. */
    public String toJson()
    {
        return this.wrapper.toString();
    }

    /** Camera frames captured so far (test/inspection helper). */
    public int getCameraFrameCount()
    {
        return this.cameraData.size();
    }

    /**
     * reset the class variables to default values - standby for next export process
     */
    public void reset()
    {
        this.wrapper = new JsonObject();
        this.cameraData = new JsonArray();
        this.entityData = new JsonObject();
        this.morphData = new JsonObject();

        this.registeredTrackingPackets.forEach((key, value) ->
        {
            value.reset(); //let the morph know, to delete this tracker
        });

        this.registeredTrackingPackets.clear(); //avoid memory leak
        this.entityNameRemaps.clear();
        this.entities.clear();

        if (!this.relativeOrigin)
        {
            this.trackingInitialPos[0] = 0;
            this.trackingInitialPos[1] = 0;
            this.trackingInitialPos[2] = 0;
        }

        this.building = false;
        this.frame = 0;
        this.motionblurFrames = 0;
        this.heldframes = 0;
        this.runner = null;
        this.trackedCamera = false;
    }

    /**
     * Adds the tracking packet to this class.
     *
     * <p>Legacy ends in an unconditional {@code return true}; the
     * {@code MorphTracker} caller nevertheless guards on the return value. Both
     * halves are kept verbatim — do not invent a false path.</p>
     *
     * @return true if the tracker was added
     */
    public boolean addTracker(TrackingPacket tracker)
    {
        if (!tracker.combiningMorphs)
        {
            this.registerTracker(tracker);
        }
        else
        {
            /* Sentinel: a never-tracked name can never satisfy the contiguity
             * check below (getFrame() - 1 == getFrame() - 100). */
            int endFrame = this.getFrame() - 100;

            /* if the tracker has been tracked already, check the end frame */
            if (this.registeredTrackingPackets.containsKey(tracker.name))
            {
                JsonArray frames = this.morphData.getAsJsonArray(tracker.name);

                if (frames.size() > 0)
                {
                    int startFrame = frames.get(0).getAsJsonObject().get("frame").getAsInt();

                    endFrame = startFrame + frames.size() - 1;
                }
            }

            if (this.getFrame() - 1 == endFrame)
            {
                tracker.trackingData = this.morphData.getAsJsonArray(tracker.name);
            }
            else
            {
                /* if it cant append the tracking data or if it was not yet tracked */
                this.registerTracker(tracker);
            }
        }

        return true;
    }

    /**
     * Checks for duplicate names and composes unique index names if duplicates
     * are present. Adds the tracking name and the tracker to the map of
     * registered trackers and to the JSON object for the tracking export.
     */
    private void registerTracker(TrackingPacket tracker)
    {
        tracker.name = this.checkDuplicateName(tracker.name, this.registeredTrackingPackets);

        this.morphData.add(tracker.name, tracker.trackingData);
        this.registeredTrackingPackets.put(tracker.name, tracker);
    }

    /**
     * This method checks the Map for duplicate keys. In case the String already
     * exists as a key, it composes a unique indexed name: {@code name.number}.
     *
     * @return either an indexed name, like {@code "name.1"} if it found one
     *         duplicate, or the original name if it didn't find a duplicate
     */
    private String checkDuplicateName(String name, Map<String, ?> map)
    {
        int counter = 0;

        while (map.containsKey(name + ((counter == 0) ? "" : "." + counter)))
        {
            counter++;
        }

        return name + ((counter == 0) ? "" : "." + counter);
    }

    /**
     * Begin an export session. Legacy also registered the two Minema event-bus
     * listeners here; the port's recorder calls {@link #addCameraFrame()} /
     * {@link #frameEnd()} directly (both no-op while not building).
     */
    public void start(PositionSource runner)
    {
        if (this.building)
        {
            return;
        }

        CaptureInfo captured = captureInfo == null ? null : captureInfo.get();

        this.info = captured == null ? CaptureInfo.DEFAULT : captured;

        this.wrapper.add("information", this.getHeaderInformation());

        this.wrapper.add("camera_tracking", this.cameraData);

        this.wrapper.add("entity_tracking", this.entityData);

        this.wrapper.add("morph_tracking", this.morphData);

        this.runner = runner;
        this.building = true;
        this.motionblurFrames = this.info.motionBlurFrames;
    }

    /**
     * Checks if the current frame should be skipped.
     *
     * @return true if this is not building, or the held-frame counter has not
     *         reached the configured held frames yet, or this render is a
     *         motion-blur sub-frame
     */
    public boolean skipFrame()
    {
        int motionblur = (int) this.motionblurFrames;

        /* Guard the modulo: motionblurFrames is 0 while not building (legacy
         * could only reach this method from a live Minema session). */
        boolean ignoreMotionblurFrame = motionblur <= 0 || this.frame % motionblur != 0;

        return !this.building || this.heldframes < this.info.heldFrames || ignoreMotionblurFrame;
    }

    /**
     * Execute this method at the end of a frame. It updates the frame counter
     * and tracks entities.
     */
    public void frameEnd()
    {
        if (!this.building)
        {
            return;
        }

        this.addEntitiesData(partialTicks.get());

        this.frame = (this.heldframes >= this.info.heldFrames) ? this.frame + 1 : this.frame;
    }

    /**
     * Update the heldframes counter. Do this at the beginning of a frame.
     * A frame should be rendered at the last heldframe.
     */
    private void updateHeldFrames()
    {
        int minemaHF = this.info.heldFrames;

        this.heldframes = (this.heldframes < minemaHF) ? this.heldframes + 1 : 1;
    }

    public JsonObject getHeaderInformation()
    {
        JsonObject information = new JsonObject();
        JsonArray resolution = new JsonArray();

        resolution.add(this.info.width);
        resolution.add(this.info.height);

        information.add("fps", new JsonPrimitive(this.info.fps));

        /* Deliberate legacy duplication: Minema's own getFrameRate() (the
         * motion-blur-multiplied rate) is commented out in the legacy source in
         * favour of the plain frame rate. Import scripts depend on it. */
        information.add("motionblur_fps", new JsonPrimitive(this.info.fps));

        /* Legacy: OptifineHelper.dynamicFov() is commented out — hardcoded
         * false. P218.1 ports that method (client-side, backed by vanilla's
         * FOV-effects scale), but the call stays commented out here: import
         * scripts read this field and legacy always wrote false. */
        information.add("dynamic_fov", new JsonPrimitive(false));
        information.add("resolution", resolution);
        information.add("held_frames", new JsonPrimitive(this.info.heldFrames));
        information.add("required_import_version", new JsonPrimitive(REQUIRED_IMPORT_VERSION));

        return information;
    }

    /**
     * Append the current frame's transform to a morph's tracker. Called by
     * {@code mchorse.blockbuster_pack.trackers.MorphTracker} during the morph's
     * render.
     */
    public void track(TrackingPacket tracker)
    {
        if (tracker == null || this.skipFrame())
        {
            return;
        }

        /* only track morph if the camera was tracked first */
        if (!this.trackedCamera)
        {
            return;
        }

        MatrixUtils.Transformation transformation = currentTransformation;

        if (transformation == null)
        {
            /* No published model-view for this render (legacy scraped GL state,
             * which always existed). Total-reader: skip, never crash. */
            return;
        }

        Vector3d pos = new Vector3d();
        Vector3d scale = new Vector3d();

        pos.x = transformation.translation.m03;
        pos.y = transformation.translation.m13;
        pos.z = transformation.translation.m23;

        scale.x = transformation.scale.m00;
        scale.y = transformation.scale.m11;
        scale.z = transformation.scale.m22;

        JsonObject frame = new JsonObject();
        JsonArray positionData = new JsonArray();
        JsonArray rotationData = new JsonArray();
        JsonArray scaleData = new JsonArray();

        /* position data */
        positionData.add(pos.x - this.trackingInitialPos[0]);
        positionData.add(pos.y - this.trackingInitialPos[1]);
        positionData.add(pos.z - this.trackingInitialPos[2]);

        /* rotation matrix data */
        JsonArray jsonRotX = new JsonArray();
        jsonRotX.add((double) transformation.rotation.m00);
        jsonRotX.add((double) transformation.rotation.m01);
        jsonRotX.add((double) transformation.rotation.m02);

        JsonArray jsonRotY = new JsonArray();
        jsonRotY.add((double) transformation.rotation.m10);
        jsonRotY.add((double) transformation.rotation.m11);
        jsonRotY.add((double) transformation.rotation.m12);

        JsonArray jsonRotZ = new JsonArray();
        jsonRotZ.add((double) transformation.rotation.m20);
        jsonRotZ.add((double) transformation.rotation.m21);
        jsonRotZ.add((double) transformation.rotation.m22);

        rotationData.add(jsonRotX);
        rotationData.add(jsonRotY);
        rotationData.add(jsonRotZ);

        /* scale data */
        scaleData.add(scale.x);
        scaleData.add(scale.y);
        scaleData.add(scale.z);

        /* start offset — only on the first element of the (possibly
         * re-registered) array, mirroring the entity-array convention */
        if (tracker.trackingData.size() == 0)
        {
            frame.addProperty("frame", this.getFrame());
        }

        frame.add("position", positionData);
        frame.add("rotation", rotationData);
        frame.add("scale", scaleData);

        tracker.trackingData.add(frame);
    }

    private void addEntitiesData(float partialTick)
    {
        if (this.skipFrame())
        {
            return;
        }

        /* update entities */
        this.tryFindingEntity();

        for (TrackedEntity entity : this.entities)
        {
            JsonArray frameArray = this.addEntityTracker(entity);

            double x = Interpolations.lerp(entity.lastTickX, entity.x, partialTick);
            double y = Interpolations.lerp(entity.lastTickY, entity.y, partialTick);
            double z = Interpolations.lerp(entity.lastTickZ, entity.z, partialTick);

            JsonObject frame = new JsonObject();
            JsonArray positionData = new JsonArray();
            JsonArray angleData = new JsonArray();

            positionData.add(x - this.trackingInitialPos[0]);
            positionData.add(y - this.trackingInitialPos[1]);
            positionData.add(z - this.trackingInitialPos[2]);

            if (frameArray.size() == 0)
            {
                frame.addProperty("frame", this.getFrame());
            }

            frame.add("position", positionData);

            if (entity.living)
            {
                double bodyYaw = entity.prevBodyYaw + (entity.bodyYaw - entity.prevBodyYaw) * partialTick;

                /* Legacy wrote a bare int 0 for the X and Z components. */
                angleData.add(0);
                angleData.add(bodyYaw);
                angleData.add(0);
                frame.add("body_rotation", angleData);
            }

            frameArray.add(frame);
        }
    }

    /**
     * Searches whether the entity has been tracked already.
     *
     * @return the JSON array of the already tracked entity, or a new array
     *         registered under a unique name
     */
    private JsonArray addEntityTracker(TrackedEntity entity)
    {
        String name = this.entityNameRemaps.inverse().get(entity.uuid);
        JsonArray frameArray;

        if (name != null)
        {
            frameArray = this.entityData.getAsJsonArray(name);
        }
        else
        {
            /* The entity has not been tracked yet */

            name = this.checkDuplicateName(entity.name, this.entityNameRemaps);
            frameArray = new JsonArray();

            /* add the entity to the json structure and to the name remaps */
            this.entityData.add(name, frameArray);
            this.entityNameRemaps.put(name, entity.uuid);
        }

        return frameArray;
    }

    /**
     * Capture one camera frame. Legacy's {@code MinemaEventbus.cameraBUS}
     * listener; the recorder calls it once per render, right after the camera
     * matrices are final.
     */
    public void addCameraFrame()
    {
        if (!this.building)
        {
            /* Legacy only ever ran this while a session was live (listener
             * registered in start()); the explicit guard reproduces that and
             * keeps the held-frame counter from drifting between sessions. */
            return;
        }

        this.updateHeldFrames();

        if (this.skipFrame())
        {
            return;
        }

        Vector3d pos = cameraPosition == null ? null : cameraPosition.get();

        if (pos == null || this.runner == null)
        {
            /* No camera / runner state — skip, and leave trackedCamera false so
             * morph tracking skips this frame too (total-reader rule). */
            return;
        }

        this.trackedCamera = true;

        if (this.frame == 0)
        {
            if (!this.relativeOrigin)
            {
                this.trackingInitialPos[0] = pos.x;
                this.trackingInitialPos[1] = pos.y;
                this.trackingInitialPos[2] = pos.z;
            }
        }

        JsonObject frame = new JsonObject();
        JsonArray positionData = new JsonArray();
        JsonArray angleData = new JsonArray();

        positionData.add(pos.x - this.trackingInitialPos[0]);
        positionData.add(pos.y - this.trackingInitialPos[1]);
        positionData.add(pos.z - this.trackingInitialPos[2]);

        Position position = this.runner.getPosition();

        /* Wire contract: [fov, roll, yaw, pitch] */
        angleData.add(position.angle.fov);
        angleData.add(position.angle.roll);
        angleData.add(position.angle.yaw);
        angleData.add(position.angle.pitch);

        frame.add("position", positionData);
        frame.add("angle", angleData);

        this.cameraData.add(frame);
    }

    /**
     * Write the tracking JSON. Legacy wrote {@code wrapper.toString()} (compact
     * Gson) as UTF-8 into Minema's capture dir; the port writes into
     * {@link #exportDir} (the recorder's export path), same {@code <name>.json}
     * naming.
     */
    public void exportTrackingData(String filename)
    {
        Path dir = exportDir == null ? null : exportDir.get();

        if (dir == null)
        {
            Aperture.LOGGER.warn("No tracking export directory configured; dropping " + filename);

            return;
        }

        try
        {
            Files.createDirectories(dir);

            BufferedWriter file = Files.newBufferedWriter(dir.resolve(filename), StandardCharsets.UTF_8);

            file.write(this.wrapper.toString());
            file.close();

            Aperture.LOGGER.info("Successfully created the tracking data file.");
        }
        catch (IOException e)
        {
            Aperture.LOGGER.error("An error occurred during writing the tracking data file.", e);
        }
    }

    /**
     * Try finding entities based on the entity selector.
     *
     * <p>Legacy split the field on {@code " - "}, wrapped bare names as
     * {@code @e[name=…]} and swallowed every selector exception. All three
     * transfer; the parsing half is {@link #parseSelectors(String)} so it is
     * unit-testable without a world.</p>
     */
    public void tryFindingEntity()
    {
        this.entities.clear();

        if (this.selector == null || this.selector.isEmpty())
        {
            return;
        }

        /* Dedupe by UUID across tokens, insertion-ordered. */
        Map<String, TrackedEntity> found = new LinkedHashMap<String, TrackedEntity>();

        for (String token : parseSelectors(this.selector))
        {
            try
            {
                Collection<TrackedEntity> matched = entitySource.find(token);

                if (matched != null)
                {
                    for (TrackedEntity entity : matched)
                    {
                        if (entity != null)
                        {
                            found.putIfAbsent(entity.uuid, entity);
                        }
                    }
                }
            }
            catch (Exception e)
            {
                /* legacy: catch (Exception e) { } — malformed selectors are
                 * silently ignored */
            }
        }

        this.entities.addAll(found.values());
    }

    /**
     * Split a tracking selector field into normalized selector tokens: legacy
     * {@code selector.split(" - ")}, then {@code "@e[name=" + s + "]"} for any
     * token that does not already contain an {@code @}.
     */
    public static List<String> parseSelectors(String selector)
    {
        List<String> tokens = new ArrayList<String>();

        if (selector == null || selector.isEmpty())
        {
            return tokens;
        }

        for (String token : selector.split(" - "))
        {
            tokens.add(token.contains("@") ? token : "@e[name=" + token + "]");
        }

        return tokens;
    }

    /**
     * The legacy camera-position extraction, kept executable as documentation
     * and as the reference the equivalence test compares against.
     *
     * <p>1.12.2 read the GL model-view (which, at the camera hook, maps
     * render-entity-relative world space to camera space), right-multiplied it
     * by the translation of the negated interpolated render-entity position —
     * turning it into an absolute world→camera matrix — inverted it, and took
     * the translation column, which is the camera's world position. On core
     * profile there is no {@code GL_MODELVIEW_MATRIX}, and the same value is
     * simply {@code Camera#getPos()}; the JSON values, not the extraction
     * route, are the contract.</p>
     *
     * @param modelview the (row-major, already transposed) model-view matrix
     * @param renderX   interpolated render-entity X
     * @param renderY   interpolated render-entity Y
     * @param renderZ   interpolated render-entity Z
     */
    public static Vector3d legacyCameraPosition(Matrix4d modelview, double renderX, double renderY, double renderZ)
    {
        Matrix4d translation = new Matrix4d();
        Matrix4d work = new Matrix4d(modelview);

        translation.setIdentity();
        translation.setTranslation(new Vector3d(-renderX, -renderY, -renderZ));

        work.mul(translation);
        work.invert();

        return new Vector3d(work.m03, work.m13, work.m23);
    }

    /**
     * Tracking packet — one morph's growing frame array plus the reset-flag
     * handshake the morph polls ({@code MorphTracker} nulls its packet when
     * {@link #isReset()} goes true).
     */
    public static class TrackingPacket
    {
        private String name;
        private JsonArray trackingData = new JsonArray();
        private boolean reset = false;
        private boolean combiningMorphs;

        public TrackingPacket(String name, boolean combiningMorphs)
        {
            this.name = name;
            this.combiningMorphs = combiningMorphs;
        }

        public boolean isReset()
        {
            return this.reset;
        }

        public String getName()
        {
            return this.name;
        }

        public boolean isCombiningMorphs()
        {
            return this.combiningMorphs;
        }

        /** Frames captured so far (test/inspection helper). */
        public int size()
        {
            return this.trackingData.size();
        }

        private void reset()
        {
            this.trackingData = new JsonArray();
            this.reset = true; //morph should check for this value to delete the packet
        }
    }
}
