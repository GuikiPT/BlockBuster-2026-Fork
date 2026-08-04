package mchorse.mclib.client.gui.framework.elements;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.StencilPickFramebuffer;
import mchorse.mclib.client.render.RenderingUtilsClient;
import mchorse.mclib.utils.MathUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.vecmath.Matrix3d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

/**
 * Model renderer GUI element
 *
 * This base class can be used for full screen model viewer.
 *
 * <p>Port of McLib 2.4.3's {@code GuiModelRenderer} (roadmap P42). All
 * orbit/pan/flight camera math is verbatim (javax.vecmath is bundled — the
 * plan's JOML swap wasn't needed; JOML only appears at the GL boundary).
 * 1.20.4 mappings:</p>
 *
 * <ul>
 * <li>{@code gluPerspective}/{@code GlStateManager.ortho} dance →
 * {@code RenderSystem.backupProjectionMatrix()} + perspective
 * {@code setProjectionMatrix(..., VertexSorter.BY_DISTANCE)} +
 * {@code restoreProjectionMatrix()} (BBS-validated). The legacy return-ortho
 * {@code (0..w, h..0, 1000, 3000000)} isn't re-created — the vanilla GUI
 * projection owns the GUI z-conventions on 1.20.4.</li>
 * <li>model-view: push/loadIdentity on {@code RenderSystem.getModelViewStack()}
 * + pitch/yaw rotation + {@code -temp} translation, popped afterwards;
 * {@link #cameraMatrix} is computed in software (same Rx*Ry*T product legacy
 * read back from GL), headless-testable.</li>
 * <li>the dummy entity is created through the {@link #dummyEntityFactory}
 * seam — S4's morph editors install the DummyEntity factory; until then
 * {@link #getEntity()} is null and {@link #setupEntity()} no-ops.</li>
 * <li>{@link #drawUserModel(GuiContext)} stays the abstract model-drawing
 * seam (S4/S6 fill it); {@link #drawForStencil}/{@link #getStencilValue} are
 * the picking seam — {@link #tryPicking} keeps the legacy stencil readback,
 * redirected into the offscreen depth24-stencil8
 * {@link StencilPickFramebuffer} because vanilla 1.20.4's main framebuffer has
 * no stencil attachment (Forge's 1.12.2 one did).</li>
 * </ul>
 */
public abstract class GuiModelRenderer extends GuiElement
{
    private static boolean rendering;

    /**
     * Shared offscreen stencil target for {@link #tryPicking} — vanilla 1.20.4's
     * main framebuffer has no stencil attachment (Forge's did), so the pick pass
     * needs its own. One instance for every model renderer: only one pick runs
     * at a time and it is fully cleared per pass.
     */
    private static final StencilPickFramebuffer stencilBuffer = new StencilPickFramebuffer();

    /**
     * Viewport key/fill diffuse lights, in <b>model space</b> (BBS
     * {@code UIModelRenderer}'s pair): both tilted up, one from the far side,
     * one from the camera's side, so the model reads as top-lit from any orbit
     * angle.
     *
     * <p>BBS feeds these through
     * {@code RenderSystem.setupLevelDiffuseLighting(l0, l1, camera.view)}
     * because it multiplies the camera into the <b>per-draw</b>
     * {@link MatrixStack} — normal matrix included — so its buffer normals are
     * view-space and the lights have to be rotated into the same space. Here
     * the camera lives in the global {@code ModelViewMat} instead (see
     * {@link #drawModel}) and every draw opens a fresh identity stack, so the
     * normals stay model-space and the lights are used unrotated. Both are the
     * same fixed-in-world shading: the view matrix is a rotation, so
     * {@code dot(V*l, V*n) == dot(l, n)}.</p>
     */
    private static final org.joml.Vector3f LIGHT_0 = new org.joml.Vector3f(0F, 0.85F, -1F).normalize();
    private static final org.joml.Vector3f LIGHT_1 = new org.joml.Vector3f(0F, 0.85F, 1F).normalize();

    private static Vector3d vec = new Vector3d();
    private static Matrix3d mat = new Matrix3d();
    protected Matrix4d cameraMatrix = new Matrix4d();

    /**
     * P42→S4 seam: factory for the dummy pose entity (legacy
     * {@code new DummyEntity(mc.world)}). Null until S4 installs it —
     * headless trees and pre-S4 code get a null {@link #entity}.
     */
    public static Function<MinecraftClient, LivingEntity> dummyEntityFactory;

    protected LivingEntity entity;
    protected BlockState block = Blocks.GRASS_BLOCK.getDefaultState();

    protected int timer;
    protected boolean dragging;
    protected boolean position;
    protected Vector3f temp = new Vector3f();

    public float fov = 70.0F;
    public float scale;
    public float yaw;
    public float pitch;
    public Vector3f pos = new Vector3f();
    public boolean flight;

    public boolean hideModel;
    public boolean fullScreen;

    public Consumer<GuiContext> beforeRender;
    public Consumer<GuiContext> afterRender;

    public boolean customEntity;
    public float entityPitch;
    public float entityYawHead;
    public float entityYawBody;
    public int entityTicksExisted;

    protected float lastX;
    protected float lastY;

    /* --------------------------------------------------------------------- */
    /* Panning                                                               */
    /* --------------------------------------------------------------------- */

    /**
     * The camera's rotation, {@code Rx(pitch) * Ry(yaw)} — the same product
     * {@link #drawModel} pushes onto the model-view stack, minus the
     * {@code -temp} translation. Refreshed by {@link #setupPosition}.
     */
    protected final Matrix4f view = new Matrix4f();

    /**
     * The perspective {@link #setupViewport} last rendered with. The pan ray is
     * unprojected through this exact matrix, so grabbing stays pinned to what
     * is on screen even when the viewport's aspect is off (the {@code rx}/{@code ry}
     * ceilings there can disagree by a step at some GUI scales).
     */
    protected final Matrix4f projection = new Matrix4f();

    /**
     * Camera state latched when a position drag starts (BBS's
     * {@code cachedCamera}/{@code cachedPos}). The drag is solved against the
     * frame it began on, so the grabbed point cannot drift as {@link #pos}
     * moves underneath it.
     */
    private final Matrix4f cachedView = new Matrix4f();
    private final Matrix4f cachedProjection = new Matrix4f();
    private Vector3f cachedPos = new Vector3f();

    /** Camera position at drag start, relative to the orbit pivot ({@code temp - pos}). */
    private Vector3d cachedEye = new Vector3d();

    /** Drag plane normal: the camera's forward axis, through the orbit pivot. */
    private Vector3d plane = new Vector3d();

    /** Where the cursor first pierced {@link #plane}, and whether it did at all. */
    private Vector3d grab = new Vector3d();
    private boolean grabbed;

    /* Picking */
    protected boolean tryPicking;
    protected Consumer<String> callback;

    private long tick;

    public static boolean isRendering()
    {
        return rendering;
    }

    public static void disableRenderingFlag()
    {
        rendering = false;
    }

    public GuiModelRenderer(MinecraftClient mc)
    {
        super(mc);

        this.entity = dummyEntityFactory == null ? null : dummyEntityFactory.apply(mc);

        if (this.entity != null)
        {
            this.entity.setYaw(0F);
            this.entity.prevYaw = 0F;
            this.entity.setPitch(0F);
            this.entity.prevPitch = 0F;
            this.entity.headYaw = this.entity.prevHeadYaw = 0F;
            this.entity.bodyYaw = this.entity.prevBodyYaw = 0F;
            this.entity.setOnGround(true);
        }

        this.reset();

        /* Seeded so a drag started before the first draw unprojects through a
         * real perspective rather than inverting an identity. setupViewport
         * overwrites it with the true aspect every frame. */
        this.updateProjection(1F);
    }

    public Matrix4d getCameraMatrix()
    {
        return new Matrix4d(this.cameraMatrix);
    }

    public GuiModelRenderer picker(Consumer<String> callback)
    {
        this.callback = callback;

        return this;
    }

    public void setRotation(float yaw, float pitch)
    {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void setPosition(float x, float y, float z)
    {
        this.pos.set(x, y, z);
    }

    public void setScale(float scale)
    {
        this.scale = scale;
    }

    public LivingEntity getEntity()
    {
        return this.entity;
    }

    public void reset()
    {
        this.yaw = 0;
        this.pitch = 0;
        this.scale = 2;
        this.pos = new Vector3f(0, 1, 0);

        this.hideModel = false;
        this.fullScreen = false;

        this.beforeRender = null;
        this.afterRender = null;

        this.customEntity = false;
        this.entityPitch = 0F;
        this.entityYawHead = 0F;
        this.entityYawBody = 0F;
        this.entityTicksExisted = 0;
    }

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        if (super.mouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context) && (context.mouseButton == 0 || context.mouseButton == 2))
        {
            this.dragging = true;
            this.flight = false;
            this.position = GuiUtils.isShiftKeyDown() || context.mouseButton == 2;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            if (GuiUtils.isCtrlKeyDown())
            {
                this.tryPicking = true;
                this.dragging = false;
            }

            if (this.dragging && this.position)
            {
                this.startPositionDrag(context);
            }
        }

        return this.area.isInside(context);
    }

    @Override
    public boolean mouseScrolled(GuiContext context)
    {
        if (super.mouseScrolled(context))
        {
            return true;
        }

        if (this.area.isInside(context))
        {
            this.scale += Math.copySign(this.getZoomFactor(), context.mouseWheel);
            this.scale = MathUtils.clamp(this.scale, 0, 100);
        }

        return this.area.isInside(context);
    }

    protected float getZoomFactor()
    {
        if (this.scale < 1) return 0.05F;
        if (this.scale > 30) return 5F;
        if (this.scale > 10) return 1F;
        if (this.scale > 3) return 0.5F;

        return 0.1F;
    }

    @Override
    public void mouseReleased(GuiContext context)
    {
        this.dragging = false;
        this.tryPicking = false;

        if (this.flight)
        {
            this.flight = false;

            vec.set(0, 0, -this.scale);
            this.rotateVector(vec);

            this.pos.x -= vec.x;
            this.pos.y -= vec.y;
            this.pos.z -= vec.z;
        }

        super.mouseReleased(context);
    }

    @Override
    public boolean keyTyped(GuiContext context)
    {
        if (this.dragging && !this.position)
        {
            if (context.keyCode == LegacyKeyCodes.KEY_W || context.keyCode == LegacyKeyCodes.KEY_S || context.keyCode == LegacyKeyCodes.KEY_A ||
                    context.keyCode == LegacyKeyCodes.KEY_D || context.keyCode == LegacyKeyCodes.KEY_LSHIFT || context.keyCode == LegacyKeyCodes.KEY_SPACE)
            {
                if (!this.flight)
                {
                    this.flight = true;

                    vec.set(0, 0, -this.scale);
                    this.rotateVector(vec);

                    this.pos.x += vec.x;
                    this.pos.y += vec.y;
                    this.pos.z += vec.z;
                }

                return true;
            }
        }

        return super.keyTyped(context);
    }

    @Override
    public void draw(GuiContext context)
    {
        this.updateLogic(context);

        rendering = true;

        GuiDraw.scissor(this.area.x, this.area.y, this.area.w, this.area.h, context);
        this.drawModel(context);
        GuiDraw.unscissor(context);

        rendering = false;

        super.draw(context);

        this.updatePosition(context);
    }

    private void updateLogic(GuiContext context)
    {
        long i = context.tick - this.tick;

        if (i > 10)
        {
            i = 10;
        }

        while (i > 0)
        {
            this.update();
            i--;
        }

        this.tick = context.tick;
    }

    /**
     * Update logic
     */
    protected void update()
    {
        this.timer = this.mc != null && this.mc.player != null ? this.mc.player.age : this.timer + 1;

        if (this.entity != null)
        {
            this.entity.age = this.timer;
        }
    }

    /**
     * Draw currently edited model
     */
    private void drawModel(GuiContext context)
    {
        /* Pure part — always runs (headless-testable state) */
        this.setupPosition(context);
        this.cameraMatrix = this.computeCameraMatrix();
        this.setupEntity();

        /* GL part */
        if (GuiDraw.getDrawContext() == null || this.mc == null || this.mc.getWindow() == null)
        {
            return;
        }

        /* Flush batched GUI geometry before switching projections */
        context.drawContext.draw();

        this.setupViewport(context);

        MatrixStack modelView = RenderSystem.getModelViewStack();

        modelView.push();

        /* Everything between the viewport/projection switch and its restore must
         * be unwound even when a hook throws: drawGround, the beforeRender /
         * afterRender consumer seams and drawUserModel are all arbitrary caller
         * code, and one escape would otherwise leave the whole game rendering
         * into this element's viewport rect, with the perspective projection and
         * one extra entry on the model-view stack — unrecoverable in-session. */
        try
        {
            modelView.loadIdentity();
            modelView.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.pitch));
            modelView.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.yaw));
            modelView.translate(-this.temp.x, -this.temp.y, -this.temp.z);
            RenderSystem.applyModelViewMatrix();

            /* Enable rendering states (legacy enableStandardItemLighting /
             * alpha / rescale-normal block) */
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            GuiDraw.resetColor();

            /* Legacy enableStandardItemLighting's GL_LIGHT0/1 pair, ported as
             * the shader-light uniforms the 1.20.4 entity shaders read.
             *
             * Not DiffuseLighting.method_34742(): that is the preset for
             * vanilla's InventoryScreen.drawEntity, whose 180°-about-Z
             * quaternion goes through MatrixStack.multiply and so negates the
             * X/Y of every buffered normal — which is the only reason both of
             * its directions carry y = -1. This viewport buffers normals
             * upright, so that preset lit the underside of every model and
             * left the tops on bare ambient (0.4). */
            RenderSystem.setShaderLights(LIGHT_0, LIGHT_1);

            /* hideModel: legacy parked depthFunc at GL_NEVER around this block —
             * a fixed-function trick that no longer holds on 1.20.4, where every
             * RenderLayer's startDrawing re-arms LEQUAL as the buffered geometry
             * flushes, so the "hidden" model rasterized anyway (the immersive
             * editor's doubled model). The ground and the user model are simply
             * not drawn instead. The before/after hooks still run as a pair
             * (callers push/pop the global model-view in them), and the stencil
             * pick pass below keeps drawing regardless — picking must rasterize
             * (color-masked) even while the visible model is hidden, which is
             * exactly what legacy's LEQUAL restore inside the NEVER window was
             * for. Skipping is safe for render-driven morph side effects
             * (animation clocks): hideModel is only ever set when the world
             * render drew the same morph this frame (renderComplete), so those
             * side effects already ran. */
            if (!this.hideModel)
            {
                this.drawGround();
            }

            if (this.beforeRender != null)
            {
                this.beforeRender.accept(context);
            }

            if (!this.hideModel)
            {
                this.drawUserModel(context);
            }

            if (this.afterRender != null)
            {
                this.afterRender.accept(context);
            }
        }
        finally
        {
            /* Disable rendering states */
            RenderSystem.enableCull();
            RenderSystem.disableDepthTest();
            DiffuseLighting.enableGuiDepthLighting();

            GuiDraw.clearDepth();

            modelView.pop();
            RenderSystem.applyModelViewMatrix();

            /* Return back to the GUI (orthographic) projection */
            Window window = this.mc.getWindow();

            RenderSystem.viewport(0, 0, window.getFramebufferWidth(), window.getFramebufferHeight());
            RenderSystem.restoreProjectionMatrix();
        }
    }

    protected void updatePosition(GuiContext context)
    {
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.dragging)
        {
            if (this.position)
            {
                this.dragPosition(context);
            }
            else
            {
                this.yaw -= this.lastX - mouseX;
                this.pitch -= this.lastY - mouseY;
            }

            this.lastX = mouseX;
            this.lastY = mouseY;
        }

        if (this.dragging && !this.position)
        {
            float fps = this.mc != null ? Math.max(this.mc.getCurrentFps(), 1) : 60;
            float multiplier = 4F / fps;

            if (GuiUtils.isKeyDown(LegacyKeyCodes.KEY_LCONTROL))
            {
                multiplier *= 5;
            }
            else if (GuiUtils.isKeyDown(LegacyKeyCodes.KEY_LMENU))
            {
                multiplier *= 0.2F;
            }

            vec.set(0, 0, 0);

            if (GuiUtils.isKeyDown(LegacyKeyCodes.KEY_W))
            {
                vec.z++;
            }

            if (GuiUtils.isKeyDown(LegacyKeyCodes.KEY_S))
            {
                vec.z--;
            }

            if (GuiUtils.isKeyDown(LegacyKeyCodes.KEY_A))
            {
                vec.x++;
            }

            if (GuiUtils.isKeyDown(LegacyKeyCodes.KEY_D))
            {
                vec.x--;
            }

            mat.rotY((180 - this.yaw) / 180 * (float) Math.PI);
            mat.transform(vec);

            if (GuiUtils.isKeyDown(LegacyKeyCodes.KEY_SPACE))
            {
                vec.y++;
            }

            if (GuiUtils.isKeyDown(LegacyKeyCodes.KEY_LSHIFT))
            {
                vec.y--;
            }

            if (vec.length() > 0)
            {
                vec.normalize();
            }

            this.pos.x += vec.x * multiplier;
            this.pos.y += vec.y * multiplier;
            this.pos.z += vec.z * multiplier;
        }
    }

    /**
     * Latch everything the drag will be solved against: the camera as it stood
     * when the button went down, and the point under the cursor at that moment.
     *
     * <p>The plane is the camera's forward axis through the orbit pivot — the
     * screen-parallel plane the model sits on, so dragging moves {@link #pos}
     * exactly as far as the cursor travelled over it, at any zoom.</p>
     */
    protected void startPositionDrag(GuiContext context)
    {
        this.cachedPos.set(this.pos);
        this.cachedView.set(this.view);
        this.cachedProjection.set(this.projection);
        this.cachedEye.set(this.temp.x - this.pos.x, this.temp.y - this.pos.y, this.temp.z - this.pos.z);

        this.plane.set(0, 0, 1);
        this.rotateVector(this.plane);

        this.grabbed = this.calculateOnPlane(context, this.grab);
    }

    /**
     * Legacy McLib panned by {@code -(lastX - mouseX) / 60} scaled by
     * {@link #getZoomFactor}, so the model slid at a rate that only roughly
     * tracked the cursor and stepped whenever the zoom crossed a factor
     * boundary. This is BBS {@code UIModelRenderer}'s drag instead: re-pierce
     * the drag plane with the cursor ray and shift the pivot by however far the
     * pierce point moved, which pins the grabbed point under the cursor.
     */
    protected void dragPosition(GuiContext context)
    {
        if (!this.grabbed)
        {
            return;
        }

        Vector3d point = new Vector3d();

        if (this.calculateOnPlane(context, point))
        {
            this.pos.set(
                (float) (this.cachedPos.x - point.x + this.grab.x),
                (float) (this.cachedPos.y - point.y + this.grab.y),
                (float) (this.cachedPos.z - point.z + this.grab.z)
            );
        }
    }

    /**
     * Intersect the cursor ray with {@link #plane}, in pivot-relative world
     * space. BBS calls {@code Intersectiond.intersectLineSegmentPlane}; that
     * lives in {@code org.joml.Vector3d} and this class's vectors are
     * {@code javax.vecmath}, so the same solve is written out here.
     *
     * <p>BBS ignores the "no hit" return and lets the miss read as the origin,
     * which snaps the model across the viewport. Here a miss is reported so the
     * caller can leave {@link #pos} alone — reachable, because {@link #scale}
     * clamps down to {@code 0}, and a camera sitting on the pivot has no plane
     * to pierce.</p>
     *
     * @return whether the ray actually crossed the plane
     */
    protected boolean calculateOnPlane(GuiContext context, Vector3d result)
    {
        Vector3d direction = new Vector3d();

        this.getMouseDirection(context, direction);

        /* Segment long enough to always straddle the plane: the ray's forward
         * component is 1 by construction and the plane sits `scale` away. */
        direction.scale(this.scale * 2D);

        double denominator = this.plane.dot(direction);

        if (Math.abs(denominator) < 1.0E-9D)
        {
            return false;
        }

        double t = -this.plane.dot(this.cachedEye) / denominator;

        if (t < 0D || t > 1D)
        {
            return false;
        }

        result.scaleAdd(t, direction, this.cachedEye);

        return true;
    }

    /**
     * The cursor's world-space ray direction (BBS {@code CameraUtils
     * .getMouseDirection}): normalize the cursor into the viewport's NDC, then
     * push it back through the inverse of the latched {@code projection * view}.
     *
     * <p>The {@code w} divide vanilla unprojection would do is skipped, exactly
     * as BBS skips it — the cached view carries no translation, so the camera is
     * the origin of this space and dividing every component by the same {@code w}
     * would only rescale a vector that is already the direction.</p>
     */
    protected void getMouseDirection(GuiContext context, Vector3d result)
    {
        /* Must be the rect setupViewport projected through, or the ray lands
         * off-cursor — hence the same fullScreen split. */
        int vx = this.fullScreen ? 0 : this.area.x;
        int vy = this.fullScreen ? 0 : this.area.y;
        int vw = this.fullScreen ? context.screen.width : this.area.w;
        int vh = this.fullScreen ? context.screen.height : this.area.h;

        if (vw <= 0 || vh <= 0)
        {
            result.set(0, 0, 0);

            return;
        }

        float w2 = vw / 2F;
        float h2 = vh / 2F;
        float x = (context.mouseX - vx - w2) / w2;
        float y = (-(context.mouseY - vy) + h2) / h2;

        org.joml.Vector4f forward = new org.joml.Vector4f(x, y, 0F, 1F);

        new Matrix4f(this.cachedProjection).mul(this.cachedView).invert().transform(forward);

        result.set(forward.x, forward.y, forward.z);
    }

    protected void setupPosition(GuiContext context)
    {
        this.temp = new Vector3f(this.pos);

        this.view.identity()
            .rotateX((float) Math.toRadians(this.pitch))
            .rotateY((float) Math.toRadians(this.yaw));

        if (this.flight)
        {
            return;
        }

        vec.set(0, 0, -this.scale);
        this.rotateVector(vec);

        this.temp.x += vec.x;
        this.temp.y += vec.y;
        this.temp.z += vec.z;
    }

    private void rotateVector(Vector3d vec)
    {
        mat.rotX(this.pitch / 180 * (float) Math.PI);
        mat.transform(vec);
        mat.rotY((180 - this.yaw) / 180 * (float) Math.PI);
        mat.transform(vec);
    }

    /**
     * The model-view matrix legacy read back from GL after its
     * loadIdentity + rotate(pitch, X) + rotate(yaw, Y) + translate(-temp)
     * sequence — computed in software here ({@code Rx * Ry * T}), so the
     * camera matrix is available headless.
     */
    protected Matrix4d computeCameraMatrix()
    {
        Matrix4d result = new Matrix4d();
        Matrix4d op = new Matrix4d();

        result.setIdentity();
        op.rotX(Math.toRadians(this.pitch));
        result.mul(op);
        op.rotY(Math.toRadians(this.yaw));
        result.mul(op);
        op.setIdentity();
        op.setTranslation(new Vector3d(-this.temp.x, -this.temp.y, -this.temp.z));
        result.mul(op);

        return result;
    }

    protected void setupViewport(GuiContext context)
    {
        /* Changing projection mode to perspective. In order for this to
         * work, depth buffer must also be cleared. Thanks to Gegy for
         * pointing this out (depth buffer)! */
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

        Window window = this.mc.getWindow();
        int displayWidth = window.getFramebufferWidth();
        int displayHeight = window.getFramebufferHeight();

        float rx = (float) Math.ceil(displayWidth / (double) context.screen.width);
        float ry = (float) Math.ceil(displayHeight / (double) context.screen.height);

        int vx = this.fullScreen ? 0 : (int) (this.area.x * rx);
        int vy = this.fullScreen ? 0 : (int) (displayHeight - (this.area.y + this.area.h) * ry);
        int vw = this.fullScreen ? displayWidth : (int) (this.area.w * rx);
        int vh = this.fullScreen ? displayHeight : (int) (this.area.h * ry);

        RenderSystem.viewport(vx, vy, vw, vh);
        RenderSystem.backupProjectionMatrix();

        /* Kept in the field so startPositionDrag can unproject through the very
         * matrix this frame rendered with. setProjectionMatrix copies. */
        this.updateProjection((float) vw / (float) vh);

        RenderSystem.setProjectionMatrix(this.projection, VertexSorter.BY_DISTANCE);
    }

    protected void updateProjection(float aspect)
    {
        this.projection.setPerspective((float) Math.toRadians(this.fov), aspect, 0.05F, 1000F);
    }

    protected void setupEntity()
    {
        if (this.entity == null)
        {
            return;
        }

        if (this.customEntity)
        {
            this.entity.prevPitch = this.entityPitch;
            this.entity.setPitch(this.entityPitch);
            this.entity.prevHeadYaw = this.entity.headYaw = this.entityYawHead;
            this.entity.prevBodyYaw = this.entity.bodyYaw = this.entityYawBody;
            this.entity.age = this.entityTicksExisted;
        }
        else
        {
            this.entity.prevPitch = 0;
            this.entity.setPitch(0);
            this.entity.prevHeadYaw = this.entity.headYaw = 0;
            this.entity.prevBodyYaw = this.entity.bodyYaw = 0;
        }
    }

    /**
     * Draw your model here
     */
    protected abstract void drawUserModel(GuiContext context);

    /**
     * IMPORTANT: this method should be called manually by the subclass right
     * after rendering the model
     */
    protected void tryPicking(GuiContext context)
    {
        if (!this.tryPicking)
        {
            return;
        }

        if (GuiDraw.getDrawContext() == null || this.mc == null || this.mc.getWindow() == null)
        {
            this.tryPicking = false;

            return;
        }

        Window window = this.mc.getWindow();
        int displayWidth = window.getFramebufferWidth();
        int displayHeight = window.getFramebufferHeight();

        float rx = (float) Math.ceil(displayWidth / (double) context.screen.width);
        float ry = (float) Math.ceil(displayHeight / (double) context.screen.height);

        int x = (int) (context.mouseX * rx);
        int y = (int) (displayHeight - (context.mouseY) * ry);

        /* 1.12.2 read the stencil straight off Forge's stencil-enabled main
         * framebuffer. Vanilla 1.20.4 has no stencil attachment there, so the
         * pass is redirected into an offscreen depth24-stencil8 FBO of the same
         * size — same coordinates, same clear/draw/read sequence. */
        int previous = -1;
        boolean offscreen = stencilBuffer.setup(displayWidth, displayHeight);

        if (offscreen)
        {
            previous = stencilBuffer.bindAndClear();
            offscreen = previous >= 0;
        }

        if (!offscreen)
        {
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }

        int value = -1;

        try
        {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);

            /* Legacy bracketed this draw with depthFunc(LEQUAL)/depthFunc(NEVER)
             * when hideModel was on — restoring rasterization inside the NEVER
             * window so the pick could still write stencil. hideModel no longer
             * parks depthFunc (see draw()); the pick pass just draws. */
            GL11.glColorMask(false, false, false, false);

            try
            {
                this.drawForStencil(context);
            }
            finally
            {
                GL11.glColorMask(true, true, true, true);
            }

            if (offscreen)
            {
                value = stencilBuffer.readStencil(x, y);
            }
            else
            {
                ByteBuffer buffer = ByteBuffer.allocateDirect(1);

                GL11.glReadPixels(x, y, 1, 1, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, buffer);
                buffer.rewind();

                /* Unsigned: McLib read a signed byte, so models with 128+ limbs
                 * reported a negative value and their picks were dropped. */
                value = buffer.get() & 0xFF;
            }
        }
        finally
        {
            GL11.glDisable(GL11.GL_STENCIL_TEST);

            if (offscreen)
            {
                stencilBuffer.unbind(previous);
            }

            this.tryPicking = false;
        }

        if (this.callback != null && value > 0)
        {
            this.callback.accept(this.getStencilValue(value));
        }
    }

    /**
     * Here you should draw your own things into stencil
     */
    protected void drawForStencil(GuiContext context)
    {}

    protected String getStencilValue(int value)
    {
        return null;
    }

    /**
     * Render block of grass under the model (which signify where
     * located the ground below the model)
     */
    protected void drawGround()
    {
        if (McLib.enableGridRendering.get())
        {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.lineWidth(3);

            try
            {
                buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

                for (int x = 0; x <= 10; x++)
                {
                    if (x == 0)
                    {
                        buffer.vertex(x - 5, 0, -5).color(0F, 0F, 1F, 0.75F).next();
                        buffer.vertex(x - 5, 0, 5).color(0F, 0F, 1F, 0.75F).next();
                    }
                    else
                    {
                        buffer.vertex(x - 5, 0, -5).color(0.25F, 0.25F, 0.25F, 0.75F).next();
                        buffer.vertex(x - 5, 0, 5).color(0.25F, 0.25F, 0.25F, 0.75F).next();
                    }
                }

                for (int x = 0; x <= 10; x++)
                {
                    if (x == 10)
                    {
                        buffer.vertex(-5, 0, x - 5).color(1F, 0F, 0F, 0.75F).next();
                        buffer.vertex(5, 0, x - 5).color(1F, 0F, 0F, 0.75F).next();
                    }
                    else
                    {
                        buffer.vertex(-5, 0, x - 5).color(0.25F, 0.25F, 0.25F, 0.75F).next();
                        buffer.vertex(5, 0, x - 5).color(0.25F, 0.25F, 0.25F, 0.75F).next();
                    }
                }

                tessellator.draw();
            }
            finally
            {
                /* Never leave the process-wide tessellator buffer building — a
                 * throw here would make every later GuiDraw call fail with
                 * "Already building!" for the rest of the session. */
                if (buffer.isBuilding())
                {
                    buffer.clear();
                }

                RenderSystem.lineWidth(1);
            }
        }
        else
        {
            try
            {
                MatrixStack stack = new MatrixStack();

                applyGroundBlockTransform(stack);

                VertexConsumerProvider.Immediate immediate = this.mc.getBufferBuilders().getEntityVertexConsumers();

                this.mc.getBlockRenderManager().renderBlockAsEntity(this.block, stack, immediate, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
                immediate.draw();
            }
            catch (Exception e)
            {
                /* No baked models outside a resource-loaded client — the
                 * ground block simply doesn't render */
            }
        }
    }

    /**
     * Legacy {@code GuiModelRenderer.renderGround}'s block branch, op for op,
     * plus the quarter-turn {@code renderBlockAsEntity} no longer applies for
     * itself (roadmap P289 — see
     * {@link RenderingUtilsClient#blockBrightnessQuarterTurn}).
     *
     * <p>Composed, the block spans {@code [-0.5, 0.5] × [-1, 0] × [-0.5, 0.5]}:
     * a single block directly under the model's origin, centred. Without the
     * compensation the leftover {@code -90°} pushed it to
     * {@code [-1.5, -0.5]} on X — the same one-axis, one-block error as the
     * block morph, in a different subsystem, because the same legacy op list was
     * copied.</p>
     */
    public static void applyGroundBlockTransform(MatrixStack stack)
    {
        stack.translate(0, -0.5F, 0);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F));
        stack.translate(-0.5F, -0.5F, 0.5F);
        RenderingUtilsClient.blockBrightnessQuarterTurn(stack);
    }
}
