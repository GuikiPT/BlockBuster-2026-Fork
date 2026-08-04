package mchorse.blockbuster.common;

import org.jetbrains.annotations.Nullable;

import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Oriented bounding box (roadmap P75.2).
 *
 * <p>Port of 1.12.2's {@code mchorse.blockbuster.common.OrientedBB} by
 * Christian F. (Chryfi). Field names, units and the corner/edge layout are
 * legacy's, so this file diffs against
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/common/OrientedBB.java}.</p>
 *
 * <h2>What it is for</h2>
 *
 * <p>In 2.7.2 the OBB has exactly <b>one</b> consumer: the F3 debug wireframe.
 * A model author adds boxes to a limb in the model editor (they land in
 * {@code model.json} as the per-limb {@code orientedBBs} array, parsed by
 * {@link mchorse.blockbuster.api.json.ModelLimbAdapter}), {@code CustomMorph}
 * clones the blueprint boxes into its {@code orientedBBlimbs} map, and the limb
 * renderer refreshes them every frame so the wireframe follows the animated
 * limb. The collision/raycast use the class doc promises ("this should be
 * called at least once somewhere before the collision is tested") <b>does not
 * exist in 2.7.2</b> — no legacy call site tests an OBB against anything; the
 * gun raycast never reaches here. So the wireframe is the whole feature, and
 * the debug view is how an author checks a box before the hit detection that
 * would use it (S17) exists.</p>
 *
 * <h2>Spaces (the thing that is easy to get wrong)</h2>
 *
 * <p>{@link #center} is the entity's <b>absolute world</b> position (lerped by
 * partial ticks, written once per frame by {@code ModelCustom}) and
 * {@link #offset} is the limb's position <b>relative to the entity root</b>,
 * because {@code updateObbs} decomposes {@code MatrixUtils.matrix⁻¹ · limbMatrix}
 * and {@code MatrixUtils.matrix} is captured at the entity root
 * ({@code RenderCustomModel}'s {@code renderLivingAt} tail, before any
 * rotation). Their sum — the corners — is therefore absolute world space, which
 * is why the draw subtracts the camera/view origin. Legacy did the identical
 * thing with {@code builder.setTranslation(-playerX, -playerY, -playerZ)}.</p>
 *
 * <h2>Deliberate differences from 1.12.2</h2>
 *
 * <ul>
 *   <li>The GL drawing methods ({@code render(RenderWorldLastEvent)} and
 *       {@code renderAxes}) are <b>not</b> here: this class lives in the common
 *       source set. Their <i>geometry</i> is — {@link #wireframeVertices()} and
 *       {@link #axesVertices(double)} produce the exact vertex streams legacy
 *       pushed through the tessellator, and
 *       {@code mchorse.blockbuster.client.render.WorldDebugRenderer} turns them
 *       into lines. Splitting it this way is also what makes the geometry
 *       headlessly testable.</li>
 *   <li>{@code public static Matrix4f modelView} is dropped. It was a scratch
 *       buffer for {@code MatrixUtils.readModelView(...)}, i.e. for reading
 *       {@code GL_MODELVIEW_MATRIX}; on 1.20.4 the model view is the
 *       {@code MatrixStack} argument and there is nothing to scratch into.</li>
 *   <li>{@code RenderingHandler.obbsToRender.add(this)} becomes the
 *       {@link #debugSink} seam — a client-only collection cannot be named from
 *       the common source set. Installed by {@code WorldDebugRenderer.install()}
 *       and {@code null} on a dedicated server, where the registration is a
 *       no-op exactly as it should be.</li>
 *   <li>{@link Corner} is public (legacy: a private inner class) so the corner
 *       math can be asserted from a test. Nothing else changed about it.</li>
 * </ul>
 *
 * <p>Legacy quirk kept: <b>every</b> {@code OrientedBB} registers itself for
 * drawing on construction ({@link #setup}) as well as on every
 * {@link #buildCorners()}. A model reload therefore flashes its freshly parsed
 * blueprint boxes at the world origin for the one frame in which they were
 * built (their {@code center} is still zero). The set is cleared per frame, so
 * that is all it ever was.</p>
 *
 * <p>The learning sources legacy credits for the OBB concept:
 * {@code http://www.cie.bgu.tum.de/publications/bachelorthesis/2014_Engeser.pdf},
 * {@code https://www.sciencedirect.com/topics/computer-science/oriented-bounding-box}.</p>
 *
 * @author Christian F. (known as Chryfi) — original 1.12.2 implementation
 */
public class OrientedBB
{
    /**
     * Legacy {@code RenderingHandler.obbsToRender.add(this)}, as a seam.
     *
     * <p>Installed by {@code WorldDebugRenderer.install()} (client initializer)
     * and left {@code null} on a server, where nothing draws. Registration is
     * "this box wants to be drawn this frame"; the sink is a per-frame register
     * that the world-debug tail drains and clears.</p>
     */
    public static Consumer<OrientedBB> debugSink;

    /** local basis vector x */
    private Vector3d w = new Vector3d(1, 0, 0);
    /** local basis vector y */
    private Vector3d u = new Vector3d(0, 1, 0);
    /** local basis vector z */
    private Vector3d v = new Vector3d(0, 0, 1);

    /** global anchor point - mostly for rendering anchorpoints */
    private Vector3d anchorPoint = new Vector3d();

    /** scale factor determined by modelView and other scaling factors */
    public Matrix3d scale = new Matrix3d();

    public Matrix3d rotation = new Matrix3d();

    /** initial rotation defined at the beginning of model creation (degrees) */
    public double[] rotation0 = {0, 0, 0};

    /** global center point (not the anchor) */
    public Vector3d center = new Vector3d();

    /** half-width */
    public double hw;
    /** half-height */
    public double hu;
    /** half-depth */
    public double hv;

    /**
     * corners - starting from maxXYZ (1,1,1) going clockwise same thing for
     * bottom - starting at maxXminYmaxZ
     */
    public Corner[] corners = new Corner[8];

    /** offset from limb (calculated through modelview) */
    public Vector3d limbOffset = new Vector3d();

    /** anchor of the obb - for initial rotation */
    public Vector3d anchorOffset = new Vector3d();

    /** offset from main entity */
    public Vector3d offset = new Vector3d();

    public OrientedBB(@Nullable Vector3d center, @Nullable double[] rotation0, float width, float height, float depth)
    {
        if (center == null)
        {
            center = new Vector3d();
        }

        if (rotation0 == null)
        {
            rotation0 = new double[3];
        }

        this.setup(rotation0, width, height, depth);
        this.center.set(center);
    }

    public OrientedBB()
    {
        double[] rotation0 = new double[3];

        this.rotation.setIdentity();
        this.setup(rotation0, 0, 0, 0);
        this.buildCorners();
    }

    public void setup(double[] rotation0, float width, float height, float depth)
    {
        this.center = new Vector3d();
        this.hw = Math.abs(width) / 2;
        this.hu = Math.abs(height) / 2;
        this.hv = Math.abs(depth) / 2;
        this.rotation0 = rotation0;

        register(this);

        this.rotation.setIdentity();
        this.scale.setIdentity();
    }

    /** Legacy's {@code RenderingHandler.obbsToRender.add(this)}, null-safe. */
    private static void register(OrientedBB obb)
    {
        Consumer<OrientedBB> sink = debugSink;

        if (sink != null)
        {
            sink.accept(obb);
        }
    }

    /** The point the axis cross is drawn at — {@code center + offset}. */
    public Vector3d getAnchorPoint()
    {
        return this.anchorPoint;
    }

    /**
     * This method calculates all the corners of the OBB according to rotation,
     * anchor and other transformation. The corners are saved inside the
     * attribute corners.
     */
    public void buildCorners()
    {
        register(this);

        Vector3d width = new Vector3d(this.w);
        Vector3d height = new Vector3d(this.u);
        Vector3d depth = new Vector3d(this.v);
        Matrix3d rotation0 = anglesToMatrix(this.rotation0[0], this.rotation0[1], this.rotation0[2]);

        width.scale(this.hw);
        height.scale(this.hu);
        depth.scale(this.hv);

        Vector3d limbOffset0 = new Vector3d(this.limbOffset);

        Vector3d anchorOffset0 = new Vector3d(this.anchorOffset);

        Vector3d offset0 = new Vector3d(this.offset);

        Matrix3d rotscale = new Matrix3d(this.scale);

        rotscale.mul(this.rotation);
        rotscale.mul(rotation0);

        this.rotation.transform(limbOffset0);
        this.scale.transform(limbOffset0);

        rotscale.transform(anchorOffset0); // not entirely sure if that is correct - testing later in gui
        rotscale.transform(width);
        rotscale.transform(height);
        rotscale.transform(depth);

        Vector3d center = new Vector3d(this.center);

        center.add(offset0);
        this.anchorPoint.set(center);
        center.add(anchorOffset0);
        center.add(limbOffset0);

        /* calculate the corners */
        Vector3d pos = new Vector3d(center);
        pos.add(width);
        pos.add(height);
        pos.add(depth);

        Corner maxXYZ = new Corner(pos);
        this.corners[0] = maxXYZ;

        pos.set(center);
        pos.sub(width);
        pos.add(height);
        pos.add(depth);

        Corner minXmaxYZ = new Corner(pos);
        this.corners[1] = minXmaxYZ;

        pos.set(center);
        pos.sub(width);
        pos.add(height);
        pos.sub(depth);

        Corner minXmaxYminZ = new Corner(pos);
        this.corners[2] = minXmaxYminZ;

        pos.set(center);
        pos.add(width);
        pos.add(height);
        pos.sub(depth);

        Corner maxXYminZ = new Corner(pos);
        this.corners[3] = maxXYminZ;

        pos.set(center);
        pos.add(width);
        pos.sub(height);
        pos.add(depth);

        Corner maxXminYmaxZ = new Corner(pos);
        this.corners[4] = maxXminYmaxZ;

        pos.set(center);
        pos.sub(width);
        pos.sub(height);
        pos.add(depth);

        Corner minXYmaxZ = new Corner(pos);
        this.corners[5] = minXYmaxZ;

        pos.set(center);
        pos.sub(width);
        pos.sub(height);
        pos.sub(depth);

        Corner minXYZ = new Corner(pos);
        this.corners[6] = minXYZ;

        pos.set(center);
        pos.add(width);
        pos.sub(height);
        pos.sub(depth);

        Corner maxXminYZ = new Corner(pos);
        this.corners[7] = maxXminYZ;

        /* connect the corners */
        maxXYZ.connect(maxXYminZ);
        maxXYZ.connect(minXmaxYZ);
        maxXYZ.connect(maxXminYmaxZ);

        minXmaxYminZ.connect(maxXYminZ);
        minXmaxYminZ.connect(minXYZ);
        minXmaxYminZ.connect(minXmaxYZ);

        minXYmaxZ.connect(maxXminYmaxZ);
        minXYmaxZ.connect(minXYZ);
        minXYmaxZ.connect(minXmaxYZ);

        maxXminYZ.connect(maxXYminZ);
        maxXminYZ.connect(minXYZ);
        maxXminYZ.connect(maxXminYmaxZ);
    }

    /**
     * The {@code GL_LINES} vertex stream legacy's {@code render(...)} pushed:
     * consecutive pairs form one edge.
     *
     * <p>Legacy walked the four "start" corners {@code 0, 2, 5, 7} — the
     * diagonal half of the cube, so between them their three connections each
     * cover all 12 edges exactly once — and emitted {@code start, end} per
     * connection. 24 vertices, 12 lines, no duplicates. An OBB whose corners
     * were never built yields an empty stream instead of throwing.</p>
     */
    public List<Vector3d> wireframeVertices()
    {
        List<Vector3d> vertices = new ArrayList<Vector3d>();

        for (int index : new int[] {0, 2, 5, 7})
        {
            Corner start = this.corners[index];

            if (start == null)
            {
                return new ArrayList<Vector3d>();
            }

            for (Corner end : start.connections)
            {
                vertices.add(new Vector3d(start.position));
                vertices.add(new Vector3d(end.position));
            }
        }

        return vertices;
    }

    /**
     * The three axis lines legacy's {@code renderAxes(translation, color,
     * this.anchorPoint, 4, true, false)} drew: centred on {@link #anchorPoint},
     * rotated by {@code rotation · rotation0} (legacy's {@code rotate = true};
     * note the {@code scale} matrix is deliberately <i>not</i> applied), each
     * one a full segment from {@code center - axis} to {@code center + axis}.
     *
     * @param length half-length in Minecraft pixels; legacy passes 4 and
     *               divides by 32 ("1 &lt;=&gt; 1 pixel => divide by 16 and by 2
     *               as it's half length")
     */
    public List<Vector3d> axesVertices(double length)
    {
        List<Vector3d> vertices = new ArrayList<Vector3d>();

        Matrix3d rotation0 = anglesToMatrix(this.rotation0[0], this.rotation0[1], this.rotation0[2]);

        length /= 32; // 1 <=> 1 pixel => divide by 16 and by 2 as it's half length

        Vector3d axisX1 = new Vector3d(length, 0, 0);
        Vector3d axisX2 = new Vector3d(0, length, 0);
        Vector3d axisX3 = new Vector3d(0, 0, length);

        this.rotation.transform(axisX1);
        rotation0.transform(axisX1);

        this.rotation.transform(axisX2);
        rotation0.transform(axisX2);

        this.rotation.transform(axisX3);
        rotation0.transform(axisX3);

        Vector3d center0 = this.anchorPoint;

        for (Vector3d axis : new Vector3d[] {axisX1, axisX2, axisX3})
        {
            vertices.add(new Vector3d(center0.x + axis.x, center0.y + axis.y, center0.z + axis.z));
            vertices.add(new Vector3d(center0.x - axis.x, center0.y - axis.y, center0.z - axis.z));
        }

        return vertices;
    }

    /**
     * This method converts the given angles into one single 3x3 rotation
     * matrix. The rotation mode is XYZ.
     *
     * @param angleX rotation around X
     * @param angleY rotation around Y (Minecraft height axis)
     * @param angleZ rotation around Z
     * @return the complete rotation Matrix3d
     */
    public static Matrix3d anglesToMatrix(double angleX, double angleY, double angleZ)
    {
        double radX = Math.toRadians(angleX);
        double radY = Math.toRadians(angleY);
        double radZ = Math.toRadians(angleZ);
        Matrix3d rotation = new Matrix3d();
        Matrix3d rot = new Matrix3d();

        rotation.setIdentity();
        rot.rotX(radX);
        rotation.mul(rot);
        rot.rotY(radY);
        rotation.mul(rot);
        rot.rotZ(radZ);
        rotation.mul(rot);

        return rotation;
    }

    @Override
    public OrientedBB clone()
    {
        OrientedBB d = new OrientedBB();

        d.hu = this.hu;
        d.hw = this.hw;
        d.hv = this.hv;
        d.anchorOffset.set(this.anchorOffset);
        d.offset.set(this.offset);
        d.center.set(this.center);
        d.limbOffset.set(this.limbOffset);
        d.rotation.set(this.rotation);
        /* Legacy shares the array rather than copying it — kept. */
        d.rotation0 = this.rotation0;
        d.scale.set(this.scale);

        return d;
    }

    @Override
    public String toString()
    {
        return "OBB - center: " + this.center;
    }

    /**
     * One of the eight corners. Legacy's private inner class, widened to public
     * so the corner math is assertable from a headless test.
     */
    public static class Corner
    {
        /** global position (could it be also local???) */
        public Vector3d position;

        /**
         * List of corners that should be connected with this corner. Corners
         * inside this list should also have a connection to this corner.
         */
        private List<Corner> connections;

        public Corner(Vector3d pos)
        {
            this.position = new Vector3d(pos);
            this.connections = new ArrayList<Corner>();
        }

        public List<Corner> getConnections()
        {
            return this.connections;
        }

        /**
         * This method connects the given corner with this corner. It adds given
         * corner to this connection list and this corner to given corner's
         * connection list.
         *
         * @return true if connection was established. False means that no
         *         connection was made as both lists contain already the corners
         */
        public boolean connect(Corner corner)
        {
            if (!corner.connections.contains(this) && !this.connections.contains(corner))
            {
                corner.connections.add(this);
                this.connections.add(corner);

                return true;
            }
            else if (!corner.connections.contains(this))
            {
                corner.connections.add(this);

                return true;
            }
            else if (!this.connections.contains(corner))
            {
                this.connections.add(corner);

                return true;
            }

            return false;
        }

        /**
         * This method removes the given corner and this corner from both
         * connection lists.
         *
         * @return false if both connection lists don't have the corners.
         */
        public boolean disconnect(Corner corner)
        {
            if (corner.connections.contains(this) && this.connections.contains(corner))
            {
                corner.connections.remove(this);
                this.connections.remove(corner);

                return true;
            }
            else if (corner.connections.contains(this))
            {
                corner.connections.remove(this);

                return true;
            }
            else if (this.connections.contains(corner))
            {
                this.connections.remove(corner);

                return true;
            }

            return false;
        }
    }
}
