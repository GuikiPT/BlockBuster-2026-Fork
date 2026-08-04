package mchorse.aperture.camera;

import mchorse.aperture.camera.data.InterpolationType;
import mchorse.aperture.camera.data.Position;
import mchorse.aperture.camera.fixtures.AbstractFixture;
import mchorse.aperture.camera.fixtures.CircularFixture;
import mchorse.aperture.camera.fixtures.DollyFixture;
import mchorse.aperture.camera.fixtures.KeyframeFixture;
import mchorse.aperture.camera.fixtures.ManualFixture;
import mchorse.aperture.camera.fixtures.PathFixture;
import mchorse.mclib.utils.Color;

import javax.vecmath.Vector2d;
import java.util.ArrayList;
import java.util.List;

/**
 * P178.1 — the pure geometry half of the in-world camera profile renderer.
 *
 * <p>Legacy {@code CameraRenderer.onLastRender} interleaved profile evaluation
 * with fixed-function GL calls ({@code Tessellator} + {@code GlStateManager}).
 * On 1.20.4 the drawing must go through {@code MatrixStack} +
 * {@code VertexConsumerProvider}, so the port splits the phase in two:</p>
 *
 * <ul>
 * <li><b>this class</b> — walks a {@link CameraProfile} and produces the exact
 * vertex/card/point lists legacy would have pushed into the tessellator, in
 * legacy emission order. Completely headless, hence golden-testable.</li>
 * <li>{@link CameraRenderer#onLastRender} — consumes the lists and emits them
 * through the modern render layers.</li>
 * </ul>
 *
 * <p><b>The line list is a raw {@code GL_LINES} vertex stream</b>, not a list of
 * segments: consecutive pairs form one line and a trailing odd vertex is
 * dropped, exactly like GL. That matters because
 * {@link #circular(CameraProfile, AbstractFixture, long)} emits one vertex per
 * step (plus one leading vertex), which under {@code GL_LINES} yields the
 * legacy <em>dashed</em> arc rather than a continuous one — keeping the raw
 * stream keeps that quirk load-bearing instead of accidentally "fixing" it.</p>
 *
 * <p>Legacy source:
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/CameraRenderer.java}
 * ({@code onLastRender}, {@code drawFixture}, {@code drawPathFixture},
 * {@code drawCircularFixture}, {@code drawCard}, {@code drawPathPoint}).</p>
 */
public class ProfileRenderGeometry
{
    /**
     * Legacy {@code final int p = 15} — line sub-segments emitted per path
     * point (or per 5-tick step for keyframe/manual fixtures). Copy literally:
     * the segment count is "the look".
     */
    public static final int SEGMENTS_PER_POINT = 15;

    /**
     * Legacy {@code int size = (int) (duration / 5)} — non-path fixtures
     * (keyframe, manual) sample one path point every 5 ticks.
     */
    public static final int TICKS_PER_STEP = 5;

    /**
     * Legacy {@code if (distX + distY + distZ >= 0.5)} — a second card is
     * drawn at the fixture's end position only when it is at least this far
     * (Manhattan distance) from the start position.
     */
    public static final double CARD_SPLIT_DISTANCE = 0.5;

    /** Legacy {@code for (int i = 0; i < circles; i += 5)} */
    public static final int CIRCULAR_STEP = 5;

    /** Legacy {@code float b = (i + 3) / circles * duration} */
    public static final int CIRCULAR_DASH = 3;

    /** Legacy {@code Math.min(circles.get(), 360)} */
    public static final float CIRCULAR_MAX = 360;

    /** Legacy {@code GL11.glPointSize(10)} — black anchor outline, in pixels */
    public static final int ANCHOR_OUTER_PIXELS = 10;

    /** Legacy {@code GL11.glPointSize(8)} — white anchor core, in pixels */
    public static final int ANCHOR_INNER_PIXELS = 8;

    /** Legacy {@code GL11.glLineWidth(4)} */
    public static final float LINE_WIDTH = 4;

    /** Legacy {@code drawCard}: {@code float factor = 0.5F} */
    public static final float CARD_SIZE = 0.5F;

    /** Legacy {@code drawPathPoint}: {@code float factor = 0.1F} */
    public static final float POINT_SIZE = 0.1F;

    /**
     * One {@code builder.pos(...).color(...)} call from the legacy
     * {@code GL_LINES} stream.
     */
    public static class LineVertex
    {
        public final double x;
        public final double y;
        public final double z;
        public final float r;
        public final float g;
        public final float b;
        public final float a;

        public LineVertex(double x, double y, double z, float r, float g, float b, float a)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
    }

    /**
     * The circular-interpolation centre marker of a path fixture (legacy: a
     * black {@code GL_POINTS} dot of size 10 with a white size-8 dot on top).
     */
    public static class Anchor
    {
        public final double x;
        public final double y;
        public final double z;

        public Anchor(double x, double y, double z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * A numbered fixture card billboard (legacy {@code drawCard}).
     */
    public static class Card
    {
        public final double x;
        public final double y;
        public final double z;
        public final float r;
        public final float g;
        public final float b;
        public final int index;
        public final long duration;

        public Card(double x, double y, double z, float r, float g, float b, int index, long duration)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.g = g;
            this.b = b;
            this.index = index;
            this.duration = duration;
        }
    }

    /**
     * A small numbered path-point billboard (legacy {@code drawPathPoint};
     * always drawn white — the legacy method took a colour and ignored it,
     * issuing {@code GlStateManager.color(1, 1, 1)}).
     */
    public static class PathPoint
    {
        public final double x;
        public final double y;
        public final double z;
        public final int index;

        public PathPoint(double x, double y, double z, int index)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.index = index;
        }
    }

    public final List<LineVertex> lines = new ArrayList<LineVertex>();
    public final List<Anchor> anchors = new ArrayList<Anchor>();
    public final List<Card> cards = new ArrayList<Card>();
    public final List<PathPoint> points = new ArrayList<PathPoint>();

    /* Legacy reused two mutable Position instances and one Color across the
     * whole pass — keep that, the fixtures write into them in place. */
    private final Position prev = new Position(0, 0, 0, 0, 0);
    private final Position next = new Position(0, 0, 0, 0, 0);
    private final Color color = new Color();

    public static ProfileRenderGeometry build(CameraProfile profile)
    {
        ProfileRenderGeometry geometry = new ProfileRenderGeometry();

        geometry.generate(profile);

        return geometry;
    }

    /**
     * Legacy {@code onLastRender} body (minus the GL state juggling and the
     * {@code profileRender}/runner gates, which live in
     * {@link CameraRenderer#shouldRenderProfile}).
     */
    public void generate(CameraProfile profile)
    {
        if (profile == null)
        {
            return;
        }

        for (int i = 0; i < profile.fixtures.size(); i++)
        {
            AbstractFixture fixture = profile.fixtures.get(i);

            if (fixture instanceof PathFixture)
            {
                ((PathFixture) fixture).disableSpeed();
            }

            fixture.applyFixture(0L, 0.0F, profile, this.prev);
            fixture.applyFixture(fixture.getDuration(), 0.0F, profile, this.next);

            long duration = fixture.getDuration();

            double distX = Math.abs(this.next.point.x - this.prev.point.x);
            double distY = Math.abs(this.next.point.y - this.prev.point.y);
            double distZ = Math.abs(this.next.point.z - this.prev.point.z);

            this.color.set(fixture.color.get(), false);

            if (this.color.getRGBColor() == 0)
            {
                Color registry = registryColor(fixture);

                if (registry != null)
                {
                    this.color.copy(registry);
                }
            }

            if (distX + distY + distZ >= CARD_SPLIT_DISTANCE)
            {
                this.card(i, duration, this.next);
            }

            this.card(i, duration, this.prev);
            this.fixture(profile, fixture, duration);

            if (fixture instanceof PathFixture)
            {
                ((PathFixture) fixture).reenableSpeed();
            }
        }
    }

    /**
     * Legacy {@code FixtureRegistry.CLIENT.get(fixture.getClass()).color}.
     *
     * <p>Port addition (totality rule): the legacy lookup NPEs for a fixture
     * type whose client info was never registered; here a missing entry leaves
     * the fixture's own (zero) colour in place, which renders black instead of
     * crashing the world render. Headless tests also run without
     * {@code registerClient} having been called.</p>
     */
    private static Color registryColor(AbstractFixture fixture)
    {
        FixtureRegistry.FixtureInfo info = FixtureRegistry.CLIENT.get(fixture.getClass());

        return info == null ? null : info.color;
    }

    private void card(int index, long duration, Position position)
    {
        this.cards.add(new Card(position.point.x, position.point.y, position.point.z, this.color.r, this.color.g, this.color.b, index, duration));
    }

    private void line(Position position)
    {
        this.lines.add(new LineVertex(position.point.x, position.point.y, position.point.z, this.color.r, this.color.g, this.color.b, 1F));
    }

    /** Legacy {@code drawFixture} dispatch */
    private void fixture(CameraProfile profile, AbstractFixture fixture, long duration)
    {
        if (fixture instanceof PathFixture || fixture instanceof KeyframeFixture || fixture instanceof DollyFixture || fixture instanceof ManualFixture)
        {
            this.path(profile, fixture, duration);
        }
        else if (fixture instanceof CircularFixture)
        {
            this.circular(profile, fixture, duration);
        }
    }

    /** Legacy {@code drawPathFixture} */
    private void path(CameraProfile profile, AbstractFixture fixture, long duration)
    {
        int size = (int) (duration / TICKS_PER_STEP);
        PathFixture path = null;

        if (fixture instanceof PathFixture)
        {
            path = (PathFixture) fixture;
            size = path.size();
        }

        final int p = SEGMENTS_PER_POINT;

        if (fixture instanceof DollyFixture)
        {
            /* Dolly is a straight line: start and end only */
            this.line(this.prev);
            this.line(this.next);
        }
        else
        {
            for (int i = 0; i < size; i++)
            {
                for (int j = 0; j < p; j++)
                {
                    fixture.applyFixture((long) ((float) (j + i * p) / (float) (size * p) * duration), 0, profile, this.prev);
                    fixture.applyFixture((long) ((float) (j + i * p + 1) / (float) (size * p) * duration), 0, profile, this.next);

                    this.line(this.prev);
                    this.line(this.next);
                }
            }
        }

        if (path != null && path.interpolation.get() == InterpolationType.CIRCULAR && path.size() > 0)
        {
            Vector2d center = path.getCenter();
            double y = 0;

            for (int i = 0; i < path.size(); i++)
            {
                y += path.points.get(i).point.y;
            }

            y /= path.size();

            this.anchors.add(new Anchor(center.x, y, center.y));
        }

        if (path != null)
        {
            for (int i = 1; i < path.size() - 1; i++)
            {
                fixture.applyFixture(path.getTickForPoint(i), 0, profile, this.prev);

                this.points.add(new PathPoint(this.prev.point.x, this.prev.point.y, this.prev.point.z, i));
            }
        }
    }

    /**
     * Legacy {@code drawCircularFixture}. One leading vertex plus one vertex
     * per step: under {@code GL_LINES} that pairs up into dashes, which is the
     * legacy look.
     */
    private void circular(CameraProfile profile, AbstractFixture fixture, long duration)
    {
        float circles = Math.min(((CircularFixture) fixture).circles.get(), CIRCULAR_MAX);

        for (int i = 0; i < circles; i += CIRCULAR_STEP)
        {
            float a = i / circles * duration;
            float b = (i + CIRCULAR_DASH) / circles * duration;

            fixture.applyFixture((long) a, a - (int) a, profile, this.prev);
            fixture.applyFixture((long) b, b - (int) b, profile, this.next);

            if (i == 0)
            {
                this.line(this.prev);
            }

            this.line(this.next);
        }
    }
}
