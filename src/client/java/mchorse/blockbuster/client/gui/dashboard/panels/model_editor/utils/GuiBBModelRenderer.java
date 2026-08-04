package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.blaze3d.systems.RenderSystem;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.formats.obj.ShapeKey;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.render.RenderCustomModel;
import mchorse.blockbuster.client.render.layer.LayerHeldItem;
import mchorse.blockbuster_pack.client.gui.ILimbSelector;
import mchorse.mclib.client.Draw;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.input.GuiTransformations;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.utils.DummyEntity;
import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;

/**
 * Blockbuster model viewport (roadmap P84) + the limb-pick render seam
 * (roadmap P158 blocker).
 *
 * <p>The reusable in-GUI viewport that renders a {@link ModelCustom} into the
 * S3 GUI framework off the world renderer, with McLib's orbit/pan/fly camera
 * ({@link GuiModelRenderer}, P42) and Ctrl+click stencil limb-picking. This is
 * the "viewport + picking" the S12 model editor and the S4 morph editors embed.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/.../model_editor/utils/GuiBBModelRenderer.java}.</p>
 *
 * <p><b>Parity decisions.</b></p>
 * <ul>
 * <li><b>Model draw</b> mirrors legacy {@code drawUserModel}: scale by
 * {@code model.scale[*]}, then the {@code -1 * getScale()} X/Y flip, the
 * {@code -1.501} Y drop and {@code 180° + entityYawBody} rotation, so a legacy
 * model.json sits on the ground plane identically. The animated pose apply
 * ({@code setLivingAnimations}/{@code setRotationAngles}, P76/P80) is not yet
 * landed; until then the current {@link #pose} is applied per limb
 * ({@link ModelCustom#applyLimbPose}) and the parentless limbs are drawn.</li>
 * <li><b>Picking</b>: {@link #drawForStencil} delegates to
 * {@link ModelCustom#renderForStencil} (limb index+1 tagging);
 * {@link #getStencilValue} maps the read-back stencil value through
 * {@link ModelCustom#getStencilLimbName} — {@code limbs[value - 1].limb.name},
 * value 0 → null. GL readback is S20 acceptance; the mapping logic is
 * headless-tested.</li>
 * <li><b>Held items</b>: {@link #toggleItems()} flips the {@link DummyEntity}
 * diamond/golden sword defaults, exactly as legacy.</li>
 * </ul>
 *
 * <p><b>The limb-pick render seam (P158 blocker).</b> Legacy resolves a click
 * to a limb in two halves, both reproduced here:</p>
 * <ol>
 * <li><b>click → name.</b> {@link #mouseClicked(GuiContext)} re-arms
 * {@code tryPicking} when a drag started with Ctrl held (the base class already
 * does this — legacy repeated it in the subclass and the repetition is kept,
 * it is idempotent), {@link #mouseReleased(GuiContext)} disarms it, and
 * {@link #tryPicking(GuiContext)} — invoked from {@link #drawUserModel} right
 * after the model transform is established, exactly where legacy called it —
 * reads back the stencil pixel and hands the limb name to the
 * {@code picker(Consumer&lt;String&gt;)} callback. {@link #pickLimbs(Supplier)}
 * installs the legacy callback body verbatim: route the name to the currently
 * shown panel when it is an {@link ILimbSelector}.</li>
 * <li><b>name → highlight.</b> The consumer panel assigns {@link #limb} (legacy
 * {@code editor.bbRenderer.limb = morph.model.limbs.get(name)}; the
 * {@link #highlightLimb(String)} convenience does the same lookup), and this
 * class draws the highlight on top of the model with depth testing off:
 * a translucent {@code (0, 0.5, 1, 0.2)} box for box limbs
 * ({@link #renderLimbHighlight}), the limb silhouette for OBJ/VOX limbs
 * ({@link #renderObjHighlight}), the origin axis when {@link #origin}
 * ({@link #drawAxis}), the model hitbox when {@link #aabb}
 * ({@link #renderAABB}) and the anchor point when {@link #anchorPreview} is
 * set.</li>
 * </ol>
 *
 * <p><b>Load-bearing legacy quirks kept verbatim:</b></p>
 * <ul>
 * <li>The highlight box is only drawn when {@code model.limbs.length > 1} — a
 * single-limb model shows only the origin axis (and only when {@link #origin}).</li>
 * <li>{@code postRender} is applied <b>without</b> a matching pop inside the
 * highlight block, so {@link #renderAnchorPreview} runs in <b>limb-local</b>
 * space in the box / OBJ branches but in <b>model</b> space for a single-limb
 * model with {@link #origin} off. That asymmetry is legacy behaviour.</li>
 * <li>The highlight box is inflated by {@code 0.1F / 16} on every axis and then
 * shrunk/grown by the limb's {@code sizeOffset} with the <b>mixed signs</b>
 * legacy used ({@code minX + o, minY - o, minZ - o, maxX - o, maxY + o, maxZ + o}
 * — note X is offset the opposite way from Y/Z because X was mirrored).</li>
 * <li>{@link #renderAABB} is drawn <b>outside</b> the model transform (camera
 * space), using {@code pose.size} directly.</li>
 * </ul>
 *
 * <p><b>Documented 1.20.4 deviations</b> (GL1 → core profile):</p>
 * <ul>
 * <li>{@code renderObjHighlight} legacy rendered the limb into the stencil
 * buffer and then painted a full-screen quad through it, giving a flat
 * {@code (0, 0.5, 1, 0.2)} silhouette. The default framebuffer has no stencil
 * attachment under the core profile, so the same silhouette is produced by
 * re-drawing the limb's own geometry (children detached, exactly as legacy
 * nulled {@code childModels}) in that colour. Two known differences: overlapping
 * triangles of one limb blend twice where legacy's stencil mask blended once,
 * and the entity {@link RenderLayer} the geometry goes through depth-tests, so
 * the silhouette is occluded by geometry in front of it where legacy's
 * full-screen masked quad was not. The depth-test state that layer leaves behind
 * is reset so the origin axis / anchor marker drawn after it keep legacy's
 * always-on-top behaviour.</li>
 * <li>{@code Draw.point} used {@code GL_POINTS} with {@code glPointSize};
 * 1.20.4's {@link VertexFormat.DrawMode} has no point mode, so the axis origin
 * marker is drawn as two nested cubes (black outer, white inner) keeping the
 * legacy 12:10 size ratio.</li>
 * <li>{@code GuiAnchorModal} (an S12 model-editor tab, not ported) supplied the
 * anchor preview vector. {@link #anchorPreview} is the same value as a plain
 * {@code float[3]} seam so the render math could be ported and tested; S12 sets
 * it from its vector element.</li>
 * </ul>
 */
public class GuiBBModelRenderer extends GuiModelRenderer
{
    public Map<String, ResourceLocation> materials;
    public Identifier texture;

    /**
     * The McLib form of {@link #texture}, assigned alongside it by
     * {@code GuiModelEditorPanel.setSkin}. The extruded-layer cache keys on this
     * type, so the preview has to carry it or {@code is3D} limbs cannot be
     * voxelised here (see {@link RenderCustomModel#lastSkin}).
     */
    public ResourceLocation skin;

    public ModelCustom model;

    public boolean items;

    /* --------------------------------------------------------------------- */
    /* Preview animation (roadmap P137)                                      */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code swinging}: run the walk cycle. Driven by the model editor's
     * "running" toolbar toggle.
     */
    public boolean swinging;

    private float swing;
    private float swingAmount;

    /**
     * Legacy {@code swipe}: countdown of the one-shot arm swing, ticked down in
     * {@link #update()} and mapped to {@code swingProgress}.
     *
     * <p><b>Legacy quirk.</b> It starts at {@code 0}, not {@code -1}, and the
     * "no swipe" test is {@code swipe == -1} — so a freshly opened viewport
     * plays one frame of a fully-extended swing before {@link #update()} walks
     * it to {@code -1}. Kept verbatim.</p>
     */
    private int swipe;

    /** Legacy {@code swipe()}: arm the 6-tick one-shot arm swing. */
    public void swipe()
    {
        this.swipe = 6;
    }

    /**
     * Legacy {@code update()}: tick the swipe countdown and the walk cycle.
     * {@code swing} advances by {@code 0.75} per tick with amplitude {@code 1}
     * while swinging, and resets to zero the moment it stops.
     */
    @Override
    protected void update()
    {
        super.update();

        if (this.swipe > -1)
        {
            this.swipe--;
        }

        if (this.swinging)
        {
            this.swing += 0.75F;
            this.swingAmount = 1.0F;
        }
        else
        {
            this.swing = 0.0F;
            this.swingAmount = 0.0F;
        }
    }

    /* --------------------------------------------------------------------- */
    /* Limb-pick render seam (P158 blocker)                                  */
    /* --------------------------------------------------------------------- */

    /**
     * The currently selected/highlighted limb. Legacy consumers
     * ({@code GuiPosePanel}, {@code GuiCustomBodyPartEditor},
     * {@code GuiModelEditorPanel}) assign this field directly; keep it public.
     */
    public ModelLimb limb;

    /** Draw the selected limb's origin axis (legacy {@code origin}). */
    public boolean origin;

    /** Draw the model's hitbox from the current pose (legacy {@code aabb}). */
    public boolean aabb;

    /**
     * Legacy {@code looking}: when false the preview stops following the mouse
     * with its head and uses the {@code customEntity} head angles instead.
     * Consumed by the animated-pose apply (P76/P80) which is not landed yet;
     * the field exists so editors ({@code GuiCustomMorph} sets it to false) can
     * be ported 1:1 now.
     */
    public boolean looking = true;

    /**
     * Anchor preview position in normalized limb-bounds space (x, y, z ∈ 0..1),
     * or null for none. Legacy held a {@code GuiAnchorModal} and read
     * {@code anchorPreview.vector.a/b/c.value}; see the class javadoc.
     */
    public float[] anchorPreview;

    private ModelPose pose;
    private List<ShapeKey> shapes;

    /** Onion-skin ghost poses (S12 pose editor supplies them; null → none). */
    private List<ModelPose> onionSkins;

    /** Model-view matrix captured at the model's origin (legacy read it from GL). */
    private Matrix4d modelMatrix = new Matrix4d();

    /**
     * The current frame's partial ticks. Legacy read {@code context.partialTicks}
     * inline in {@code drawUserModel}; the pose pass is split across a few
     * methods here, so the frame's value is latched instead of threaded.
     *
     * <p>Protected because subclasses extend the draw ({@code GuiCustomMorph
     * .GuiModelRendererBodyPart} appends the P80 body-part layer) and need the
     * same frame value the pose pass used.</p>
     */
    protected float partialTicks;

    public GuiBBModelRenderer(MinecraftClient mc)
    {
        super(mc);

        this.modelMatrix.setIdentity();
    }

    public void setModel(ModelCustom model, Identifier texture)
    {
        this.model = model;
        this.texture = texture;
        /* Cleared rather than left alone: an Identifier carries no McLib skin to
         * pair with, and a stale one would extrude is3D limbs from the previous
         * model's image. Callers that have the McLib location assign both
         * fields directly. */
        this.skin = null;
    }

    public void setPose(ModelPose pose)
    {
        this.setPose(pose, pose == null ? null : pose.shapes);
    }

    public void setPose(ModelPose pose, List<ShapeKey> shapes)
    {
        this.pose = pose;
        this.shapes = shapes;
    }

    /**
     * The pose currently fed to the preview (the highlight and hitbox draws
     * read it).
     */
    public ModelPose getPose()
    {
        return this.pose;
    }

    /**
     * Set the onion-skin ghost poses (roadmap P84 onion-skin hook). The S12
     * pose editor feeds a secondary list of poses rendered as reduced-alpha
     * ghosts before the main model. Null / empty clears them.
     */
    public void setOnionSkins(List<ModelPose> onionSkins)
    {
        this.onionSkins = onionSkins;
    }

    public void toggleItems()
    {
        this.items = !this.items;

        if (this.entity instanceof DummyEntity)
        {
            ((DummyEntity) this.entity).toggleItems(this.items);
        }
    }

    /* --------------------------------------------------------------------- */
    /* Picking                                                               */
    /* --------------------------------------------------------------------- */

    /**
     * Install the legacy limb picker: on a Ctrl+click the resolved limb name is
     * routed into the delegate supplied by {@code delegate} when that delegate
     * implements {@link ILimbSelector}.
     *
     * <p>Port of {@code GuiCustomMorph.createMorphRenderer}'s callback:</p>
     * <pre>
     * this.bbRenderer.picker((limb) -&gt;
     * {
     *     if (this.view.delegate instanceof ILimbSelector)
     *     {
     *         ((ILimbSelector) this.view.delegate).setLimb(limb);
     *     }
     * });
     * </pre>
     *
     * <p>The delegate is resolved lazily (per pick), exactly like the legacy
     * lambda read {@code this.view.delegate} at call time — the shown panel
     * changes while the same renderer stays mounted.</p>
     */
    public GuiBBModelRenderer pickLimbs(Supplier<Object> delegate)
    {
        this.picker((limb) -> ILimbSelector.select(delegate == null ? null : delegate.get(), limb));

        return this;
    }

    /**
     * Convenience for the consumer half of the seam: resolve a limb name
     * through the current model's blueprint and highlight it. Equivalent to the
     * legacy {@code editor.bbRenderer.limb = morph.model.limbs.get(name)}
     * assignment; an unknown name (or no model) clears the highlight.
     */
    public void highlightLimb(String name)
    {
        this.limb = this.model == null || this.model.model == null || name == null
            ? null
            : this.model.model.limbs.get(name);
    }

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        boolean result = super.mouseClicked(context);

        /* Legacy repeated the base class' Ctrl check in the subclass; it is
         * idempotent (the base already armed picking) and is kept verbatim. */
        if (this.dragging && GuiUtils.isCtrlKeyDown())
        {
            this.tryPicking = true;
            this.dragging = false;
        }

        return result;
    }

    @Override
    public void mouseReleased(GuiContext context)
    {
        super.mouseReleased(context);

        this.tryPicking = false;
    }

    /* --------------------------------------------------------------------- */
    /* Transform                                                             */
    /* --------------------------------------------------------------------- */

    /**
     * Extra uniform scale applied to the model (legacy {@code getScale()}).
     * Base viewport renders at 1; {@code GuiCustomMorph.GuiModelRendererBodyPart}
     * overrides it with the morph's {@code scale}.
     */
    protected float getScale()
    {
        return 1;
    }

    /**
     * The model-view matrix at the model's origin — legacy read it back from GL
     * right after the model transform stack was pushed
     * ({@code MatrixUtils.readModelViewDouble()}). Computed in software here so
     * it is available headless.
     */
    public Matrix4d getModelMatrix()
    {
        return new Matrix4d(this.modelMatrix);
    }

    /**
     * Apply the legacy model transform stack, in legacy order:
     * {@code scale(model.scale)}, {@code scale(-s, -s, s)},
     * {@code translate(0, -1.501, 0)}, {@code rotateY(180 + entityYawBody)}.
     */
    protected void applyModelTransform(MatrixStack matrices)
    {
        /* Total: a hand-edited model.json can carry a short "scale" array.
         * Model.fillInMissing normalises on parse; normalise again here so any
         * programmatically built blueprint cannot AIOOBE out of the render. */
        float[] scale = Model.normalize(this.model.model.scale, 1F, 1F, 1F);
        float s = this.getScale();

        matrices.scale(scale[0], scale[1], scale[2]);
        matrices.scale(-1F * s, -1F * s, 1F * s);
        matrices.translate(0F, -1.501F, 0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 + (this.customEntity ? this.entityYawBody : 0)));
    }

    /**
     * Software twin of {@link #applyModelTransform(MatrixStack)} composed onto
     * the camera matrix — the value legacy captured from the GL model-view.
     */
    protected Matrix4d computeModelMatrix()
    {
        Matrix4d result = new Matrix4d(this.cameraMatrix);

        if (this.model == null || this.model.model == null)
        {
            return result;
        }

        /* Total: a hand-edited model.json can carry a short "scale" array.
         * Model.fillInMissing normalises on parse; normalise again here so any
         * programmatically built blueprint cannot AIOOBE out of the render. */
        float[] scale = Model.normalize(this.model.model.scale, 1F, 1F, 1F);
        float s = this.getScale();
        Matrix4d op = new Matrix4d();

        op.setIdentity();
        op.m00 = scale[0];
        op.m11 = scale[1];
        op.m22 = scale[2];
        result.mul(op);

        op.setIdentity();
        op.m00 = -s;
        op.m11 = -s;
        op.m22 = s;
        result.mul(op);

        op.setIdentity();
        op.setTranslation(new Vector3d(0, -1.501F, 0));
        result.mul(op);

        op.rotY(Math.toRadians(180 + (this.customEntity ? this.entityYawBody : 0)));
        result.mul(op);

        return result;
    }

    /* --------------------------------------------------------------------- */
    /* Draw                                                                  */
    /* --------------------------------------------------------------------- */

    @Override
    protected void drawUserModel(GuiContext context)
    {
        if (this.model == null || this.model.model == null)
        {
            return;
        }

        this.partialTicks = context.partialTicks;

        MatrixStack matrices = new MatrixStack();

        this.applyModelTransform(matrices);
        this.modelMatrix = this.computeModelMatrix();

        /* Legacy bound the skin here, before the model draw, so that a nested
         * item/model-block render inside the model could restore it. It is also
         * what an is3D limb extrudes from — hence both forms. */
        if (this.texture != null)
        {
            RenderCustomModel.bindLastTexture(this.skin, this.texture);
        }

        /* Arm the stencil-picking pass (populates the read-back pixel the base
         * class maps through getStencilValue) before the visible draw — legacy
         * called tryPicking here, between the transform and the model draw. */
        this.tryPicking(context);

        this.renderOnionSkins(matrices);
        this.renderModel(matrices);

        if (this.items)
        {
            this.renderHeldItems(matrices);
        }

        /* Render highlighting things on top. Legacy re-ran updateModel here so
         * the highlight sits on the posed limbs, then pushed one matrix around
         * the whole block with depth/texture/lighting disabled. */
        this.applyPose(this.pose);

        this.setDepthTest(false);
        matrices.push();

        if (this.limb != null && this.model.limbs != null)
        {
            ModelCustomRenderer targetLimb = this.model.get(this.limb.name);

            if (targetLimb != null)
            {
                if (this.model.limbs.length > 1)
                {
                    if (targetLimb.getClass() != ModelCustomRenderer.class)
                    {
                        this.renderObjHighlight(matrices, targetLimb);
                    }
                    else
                    {
                        targetLimb.postRender(matrices, 1F / 16F);
                        this.renderLimbHighlight(matrices, this.limb);
                    }
                }
                else
                {
                    if (this.origin)
                    {
                        targetLimb.postRender(matrices, 1F / 16F);
                        this.drawAxis(matrices, targetLimb, 0.25F);
                    }
                }

                this.renderAnchorPreview(matrices, targetLimb);
            }
        }

        matrices.pop();
        this.setDepthTest(true);

        if (this.aabb)
        {
            this.renderAABB();
        }
    }

    /**
     * Legacy's {@code GlStateManager.disableDepth()} / {@code enableDepth()}
     * bracket around the highlight block.
     *
     * <p>Guarded: {@link RenderSystem} asserts it is called on the render
     * thread and throws otherwise, which off-thread (headless probes) would
     * abort the whole {@link #drawUserModel} — the rest of this class is
     * deliberately no-op-without-GL, and the highlight branch selection is part
     * of the spec ({@code limbs.length > 1}), so it has to stay drivable.</p>
     */
    private void setDepthTest(boolean enabled)
    {
        try
        {
            if (enabled)
            {
                RenderSystem.enableDepthTest();
            }
            else
            {
                RenderSystem.disableDepthTest();
            }
        }
        catch (Exception e)
        {
            /* No render thread / GL context — nothing to toggle. */
        }
    }

    /**
     * Render the onion-skin ghosts (roadmap P84). Each secondary pose is drawn
     * at reduced alpha before the main model. GL-guarded / total.
     */
    protected void renderOnionSkins(MatrixStack matrices)
    {
        if (this.onionSkins == null || this.onionSkins.isEmpty())
        {
            return;
        }

        for (ModelPose ghost : this.onionSkins)
        {
            this.renderPose(matrices, ghost, 0.3F);
        }
    }

    protected void renderModel(MatrixStack matrices)
    {
        this.renderPose(matrices, this.pose, 1F);
    }

    private void renderPose(MatrixStack matrices, ModelPose pose, float alpha)
    {
        Identifier texture = this.skin();

        if (texture == null || this.mc == null)
        {
            return;
        }

        try
        {
            this.applyPose(pose);

            VertexConsumerProvider.Immediate immediate = this.mc.getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer consumer = immediate.getBuffer(RenderLayer.getEntityTranslucent(texture));

            /* S7/P91.1: OBJ material textures and VOX palettes are picked
             * per material group from the frame's provider, so the editor
             * preview has to be a render frame like every other model draw —
             * otherwise every group falls back to the model's own skin and a
             * multi-material OBJ previews untextured (legacy bound them). */
            MorphRenderContext.push(matrices, immediate,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0F);

            try
            {
                this.model.render(matrices, consumer, 1F, 1F, 1F, alpha,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            }
            finally
            {
                MorphRenderContext.pop();
            }

            immediate.draw();
        }
        catch (Exception e)
        {
            /* Total: a bad model / missing texture never crashes the GUI. */
        }
    }

    /**
     * The render layer the ctrl-click stencil pass draws through (roadmap
     * <b>P286</b>) — it <b>must discard fully transparent texels</b>.
     *
     * <p><b>The bug this closes.</b> Ctrl-clicking an invisible part of a model
     * still selected it. 1.12.2 got the skip for free from the fixed-function
     * alpha test: {@code ModelCustomRenderer.render} set
     * {@code GlStateManager.alphaFunc(GL_GREATER, 0)} for the whole model draw
     * and mclib's {@code drawModel} had {@code GL_ALPHA_TEST} enabled, so a
     * fragment whose texel alpha was 0 was discarded before it could write a
     * stencil value. 1.20.4 core profile has no fixed-function alpha test, and
     * the port drew the stencil pass through {@link RenderLayer#getEntitySolid},
     * whose {@code rendertype_entity_solid} fragment shader has <b>no
     * {@code discard}</b> — so every rasterised fragment, alpha-0 included,
     * wrote {@code GL_REPLACE} into the stencil buffer and became pickable.</p>
     *
     * <p><b>Deliberate deviation, in the threshold only.</b> Legacy discarded at
     * {@code alpha == 0} exactly ({@code GL_GREATER, 0}); vanilla's cutout
     * shader discards at {@code alpha < 0.1}, i.e. everything under 26/255. So
     * texels with alpha 1–25 were pickable in 2.7.2 and are not here. That band
     * is invisible to the eye, which is precisely the thing the user asked to
     * stop being able to click, and matching legacy exactly would cost a custom
     * shader for a band nobody can see. {@code NoCull} matches the visible
     * draw's {@code disableCull}, so both passes rasterise the same faces —
     * picking geometry that disagrees with the geometry on screen would be a
     * worse bug than the one being fixed.</p>
     *
     * <p>Static and package-visible so the layer choice is headless-testable;
     * the enclosing draw needs a GL context.</p>
     */
    static RenderLayer stencilLayer(Identifier texture)
    {
        return RenderLayer.getEntityCutoutNoCull(texture);
    }

    @Override
    protected void drawForStencil(GuiContext context)
    {
        if (this.model == null || this.model.model == null)
        {
            return;
        }

        try
        {
            /* Apply the current pose so the stencil geometry matches the visible
             * render (legacy applied the pose via updateModel before tryPicking);
             * otherwise a picked pixel would map to an un-posed limb position. */
            this.applyPose(this.pose);

            MatrixStack matrices = new MatrixStack();

            this.applyModelTransform(matrices);

            VertexConsumerProvider.Immediate immediate = this.mc.getBufferBuilders().getEntityVertexConsumers();
            RenderLayer layer = stencilLayer(this.texture == null ? this.blankTexture() : this.texture);

            /* glStencilFunc is a draw-time call while vertex emission is
             * deferred, so each limb's geometry must be flushed before the next
             * limb's stencil index is set — otherwise every limb rasterises
             * under the last index and picking always resolves to one limb. */
            this.model.renderForStencil(matrices, () ->
            {
                immediate.draw();

                return immediate.getBuffer(layer);
            }, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);

            immediate.draw();
        }
        catch (Exception e)
        {
            /* No GL context / bad model — picking yields nothing this frame. */
        }
    }

    /**
     * Push the model's per-frame material / shape / pose state and run the pose
     * + animation pass over every limb — legacy {@code updateModel}. Shared by
     * the visible ({@link #renderPose}) and stencil ({@link #drawForStencil})
     * draws so both use identically posed limbs.
     *
     * <p>Legacy called {@code setLivingAnimations} then
     * {@code setRotationAngles(limbSwing, swingAmount, ticks, headYaw,
     * headPitch, factor, entity)}. Here that is one
     * {@link ModelCustom#setRotationAngles(mchorse.blockbuster.client.model.PoseContext)}
     * over the {@link #poseContext()} snapshot (P82 made every entity/world read
     * of that pass explicit); {@code setLivingAnimations}' only body was the cape
     * simulation, which is a {@code PoseContext} input now and stays off in the
     * editor because the dummy has no cape state.</p>
     *
     * <p>{@code setRotationAngles} itself calls {@code applyLimbPose} per limb,
     * so the static pose is applied by the same call — but only when the model
     * has a pose. A null pose falls back to the plain per-limb apply, which is
     * what the base viewport did before the animation pass landed.</p>
     */
    private void applyPose(ModelPose pose)
    {
        this.model.materials = this.materials;
        this.model.shapes = this.shapes;
        this.model.pose = pose;

        if (pose == null || this.model.limbs == null)
        {
            return;
        }

        this.model.setRotationAngles(this.poseContext());
    }

    /**
     * The per-frame animation inputs, assembled exactly as legacy's
     * {@code drawUserModel} assembled its {@code setRotationAngles} arguments.
     *
     * <ul>
     * <li>{@code limbSwing} is {@code swing + partialTicks} while the walk
     * cycle runs and a hard {@code 0} otherwise (legacy did not let the last
     * partial tick bleed through);</li>
     * <li>the head follows the <b>mouse</b> ({@code yaw - entityYawBody},
     * {@code -pitch}) unless {@link #looking} is off, in which case it takes the
     * custom entity's own head angles (and {@code 0} with no custom entity);</li>
     * <li>{@code swingProgress} is the swipe countdown mapped through
     * {@code clamp(1 - (swipe - partial) / 6, 0, 1)}, with the legacy
     * {@code swipe == -1} "no swipe" test — see {@link #swipe}.</li>
     * </ul>
     */
    protected PoseContext poseContext()
    {
        PoseContext context = new PoseContext();

        float partial = this.partialTicks;
        float headYaw = this.yaw - (this.customEntity ? this.entityYawBody : 0);
        float headPitch = -this.pitch;

        if (!this.looking)
        {
            headYaw = this.customEntity ? this.entityYawHead - this.entityYawBody : 0;
            headPitch = this.customEntity ? this.entityPitch : 0;
        }

        context.limbSwing = this.swinging ? this.swing + partial : 0;
        context.limbSwingAmount = this.swingAmount;
        context.ageInTicks = this.customEntity ? this.entityTicksExisted : this.timer;
        context.netHeadYaw = headYaw;
        context.headPitch = headPitch;
        context.swingProgress = this.swipe == -1 ? 0 : MathHelper.clamp(1.0F - (this.swipe - 1.0F * partial) / 6.0F, 0.0F, 1.0F);

        return context;
    }

    /**
     * Draw the dummy entity's held items over the model — legacy's static
     * {@code renderItems}, gated on {@link #items}. Total: no client, no
     * entity, or a throwing item render never takes the viewport down.
     */
    protected void renderHeldItems(MatrixStack matrices)
    {
        if (this.mc == null || this.entity == null || this.model == null)
        {
            return;
        }

        try
        {
            VertexConsumerProvider.Immediate immediate = this.mc.getBufferBuilders().getEntityVertexConsumers();
            PoseContext context = this.poseContext();

            matrices.push();

            new LayerHeldItem().doRenderLayer(matrices, immediate,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, this.entity, this.model,
                context.limbSwing, context.limbSwingAmount, context.ageInTicks,
                context.netHeadYaw, context.headPitch, 1F / 16F);

            immediate.draw();
            matrices.pop();
        }
        catch (Exception e)
        {
            /* Total: the held-item preview never crashes the editor. */
        }
    }

    /**
     * The stand-in texture the stencil pass binds when the model has no skin.
     *
     * <p>Must name a resource that actually <b>ships</b>: 1.20.4 answers a
     * missing texture with one {@code Failed to load texture} log line, a
     * {@code GL_INVALID_OPERATION} per bind, and an incomplete sampler — no
     * exception. This used to be {@code blockbuster:textures/gui/blank.png},
     * which the port never shipped (and 1.12.2 never had); it is now McLib's
     * bundled {@code mclib:textures/pixel.png}, the same placeholder
     * {@code RLUtils} hands out. Pinned by {@code TextureReferenceExistsTest}.</p>
     */
    private Identifier blankTexture()
    {
        return new Identifier("mclib", "textures/pixel.png");
    }

    /**
     * The skin the preview draws with: the picked texture, or the blank
     * placeholder when the model carries its texture in its own materials
     * (see {@link RenderCustomModel#skinOrBlank}).
     *
     * <p>An OBJ/MTL or VOX model has no skin of its own — its texture is bound
     * per material group — so gating the preview draw on {@link #texture}
     * rendered nothing at all for them.</p>
     */
    private Identifier skin()
    {
        return RenderCustomModel.skinOrBlank(this.texture, this.model == null ? null : this.model.model);
    }

    @Override
    protected String getStencilValue(int value)
    {
        return this.model == null ? null : this.model.getStencilLimbName(value);
    }

    /* --------------------------------------------------------------------- */
    /* Highlight geometry                                                    */
    /* --------------------------------------------------------------------- */

    /**
     * The translucent selection box drawn around a box limb. The math is legacy
     * verbatim — see the class javadoc for the mixed-sign {@code sizeOffset}
     * quirk and the {@code 0.1F} inflation.
     */
    protected void renderLimbHighlight(MatrixStack matrices, ModelLimb limb)
    {
        float[] b = limbHighlightBounds(limb);

        this.drawCube(matrices, b[0], b[1], b[2], b[3], b[4], b[5], 0F, 0.5F, 1F, 0.2F);

        if (this.origin)
        {
            this.drawAxis(matrices, this.model.get(limb.name), 0.25F);
        }
    }

    /**
     * The selection box corners for a box limb, in limb-local space:
     * {@code {minX, minY, minZ, maxX, maxY, maxZ}}. Legacy math verbatim,
     * extracted so it is headless-testable:
     *
     * <ul>
     * <li>the box starts at {@code 0..size * 1/16} per axis;</li>
     * <li>it is shifted by the limb's {@code anchor} fraction and inflated by
     * {@code 0.1F / 16} on both ends so it just clears the limb's own faces;</li>
     * <li><b>X is then negated</b> ({@code minX *= -1; maxX *= -1}) — so the
     * returned {@code minX} is numerically the larger value whenever the box has
     * width, exactly as legacy fed it to {@code Draw.cube};</li>
     * <li>{@code sizeOffset} is finally applied with legacy's <b>mixed signs</b>:
     * {@code +o} on minX, {@code -o} on minY/minZ, {@code -o} on maxX,
     * {@code +o} on maxY/maxZ — i.e. X grows in the opposite direction because
     * it was mirrored.</li>
     * </ul>
     */
    public static float[] limbHighlightBounds(ModelLimb limb)
    {
        float f = 1F / 16F;
        float w = limb.size[0] * f;
        float h = limb.size[1] * f;
        float d = limb.size[2] * f;
        float o = limb.sizeOffset * f;

        float minX = 0;
        float minY = 0;
        float minZ = 0;
        float maxX = w;
        float maxY = h;
        float maxZ = d;

        minX -= w * limb.anchor[0] + 0.1F * f;
        maxX -= w * limb.anchor[0] - 0.1F * f;
        minY -= h * limb.anchor[1] + 0.1F * f;
        maxY -= h * limb.anchor[1] - 0.1F * f;
        minZ -= d * limb.anchor[2] + 0.1F * f;
        maxZ -= d * limb.anchor[2] - 0.1F * f;

        minX *= -1;
        maxX *= -1;

        return new float[] {minX + o, minY - o, minZ - o, maxX - o, maxY + o, maxZ + o};
    }

    /**
     * The OBJ/VOX limb silhouette highlight. See the class javadoc for the
     * core-profile replacement of the legacy stencil-masked full-screen quad.
     *
     * <p>The trailing {@code postRender} is deliberately <b>not</b> popped —
     * legacy left the matrix at the limb's local space so
     * {@link #renderAnchorPreview} that follows shares it.</p>
     */
    protected void renderObjHighlight(MatrixStack matrices, ModelCustomRenderer renderer)
    {
        float f = 1F / 16F;

        matrices.push();

        if (renderer.parent != null)
        {
            renderer.parent.postRender(matrices, f);
        }

        /* Legacy detached the children so only this limb's own geometry was
         * masked into the stencil; same here for the silhouette draw. */
        List<ModelCustomRenderer> children = new ArrayList<ModelCustomRenderer>(renderer.childModels);

        renderer.childModels.clear();

        try
        {
            Identifier texture = this.skin();

            if (this.mc != null && texture != null)
            {
                VertexConsumerProvider.Immediate immediate = this.mc.getBufferBuilders().getEntityVertexConsumers();

                renderer.render(matrices, immediate.getBuffer(RenderLayer.getEntityTranslucent(texture)), f,
                    0F, 0.5F, 1F, 0.2F, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);

                immediate.draw();
            }
        }
        catch (Exception e)
        {
            /* Total: the highlight never crashes the editor. */
        }
        finally
        {
            renderer.childModels.clear();
            renderer.childModels.addAll(children);

            /* RenderLayer's depth-test phase re-enables depth testing when the
             * batch ends. Legacy kept depth OFF for the whole highlight block,
             * so the origin axis and the anchor marker that follow this call
             * must not start being occluded by the model. */
            this.setDepthTest(false);
        }

        matrices.pop();

        renderer.postRender(matrices, f);

        if (this.origin)
        {
            this.drawAxis(matrices, renderer, 0.25F);
        }
    }

    /**
     * The RGB origin axis of a limb. When the global transform orientation is
     * {@code GLOBAL}, the limb's rotation and scale are reverted first so the
     * axis stays world-aligned (legacy
     * {@code RenderingUtils.glRevertRotationScale(rotation, scale, XYZ)}).
     */
    protected void drawAxis(MatrixStack matrices, ModelCustomRenderer target, float length)
    {
        matrices.push();

        if (target != null && GuiTransformations.GuiStaticTransformOrientation.getOrientation() == GuiTransformations.TransformOrientation.GLOBAL)
        {
            this.revertRotationScale(matrices, target);
        }

        this.drawAxisLines(matrices, length);

        matrices.pop();
    }

    /**
     * Invert the limb's scale and its XYZ rotation, in the legacy order:
     * {@code scale(1/sx, 1/sy, 1/sz)} then {@code rotate(-x, X)},
     * {@code rotate(-y, Y)}, {@code rotate(-z, Z)}. Zero scale components map
     * to zero (legacy divided guarded by {@code != 0}).
     */
    protected void revertRotationScale(MatrixStack matrices, ModelCustomRenderer target)
    {
        float invSx = target.scaleX != 0 ? 1F / target.scaleX : 0;
        float invSy = target.scaleY != 0 ? 1F / target.scaleY : 0;
        float invSz = target.scaleZ != 0 ? 1F / target.scaleZ : 0;

        matrices.scale(invSx, invSy, invSz);

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) -Math.toDegrees(target.rotateAngleX)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) -Math.toDegrees(target.rotateAngleY)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) -Math.toDegrees(target.rotateAngleZ)));
    }

    /**
     * Port of McLib's {@code Draw.axis}: a black 5px underlay, the RGB 3px axis
     * on top, then the origin marker.
     */
    protected void drawAxisLines(MatrixStack matrices, float length)
    {
        if (this.mc == null)
        {
            return;
        }

        Draw.axis(matrices, length);
    }

    /**
     * Origin marker: legacy drew a 12px black point with a 10px white point on
     * top. Core profile has no point draw mode, so two nested cubes keep the
     * 12:10 ratio.
     */
    protected void drawPoint(MatrixStack matrices)
    {
        if (this.mc == null)
        {
            return;
        }

        Draw.point(matrices);
    }

    /**
     * Port of McLib's {@code Draw.cube} — a filled quad cube in the legacy face
     * order (top, bottom, left, right, front, back) and vertex winding, emitted
     * through the {@link VertexFormats#POSITION_COLOR} shader (blend on, so the
     * legacy {@code enableAlpha}/{@code enableBlend} bracket is preserved).
     */
    protected void drawCube(MatrixStack matrices, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float red, float green, float blue, float alpha)
    {
        if (this.mc == null)
        {
            return;
        }

        Draw.cube(matrices, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
    }

    /**
     * Render the model's hitbox from the current pose. Legacy drew it in camera
     * space (outside the model transform push) with
     * {@code RenderGlobal.drawBoundingBox}; the modern equivalent is the debug
     * line {@link RenderLayer}. Note the legacy Z extents reuse
     * {@code size[0]} — the hitbox is square in plan, by design.
     */
    protected void renderAABB()
    {
        ModelPose current = this.pose;

        if (current == null || this.mc == null)
        {
            return;
        }

        try
        {
            /* Inside the trap: hitboxBounds indexes pose.size[0..1], and a
             * hand-edited model.json can still reach here through a code path
             * that bypassed Model.fillInMissing's array normalisation. */
            float[] b = hitboxBounds(current);

            VertexConsumerProvider.Immediate immediate = this.mc.getBufferBuilders().getEntityVertexConsumers();

            WorldRenderer.drawBox(new MatrixStack(), immediate.getBuffer(RenderLayer.getLines()),
                b[0], b[1], b[2], b[3], b[4], b[5], 1.0F, 1.0F, 1.0F, 1.0F);

            immediate.draw();
        }
        catch (Exception e)
        {
            /* No GL context — the hitbox simply doesn't render. */
        }
    }

    /**
     * The pose hitbox corners {@code {minX, minY, minZ, maxX, maxY, maxZ}}.
     * Legacy quirk preserved: the <b>Z</b> extents are derived from
     * {@code size[0]} (the width), never {@code size[2]} — the hitbox is square
     * in plan, matching the vanilla entity bounding box the pose describes.
     */
    public static float[] hitboxBounds(ModelPose pose)
    {
        return new float[] {
            -pose.size[0] / 2.0F,
            0.0F,
            -pose.size[0] / 2.0F,
            pose.size[0] / 2.0F,
            pose.size[1],
            pose.size[0] / 2.0F
        };
    }

    /**
     * The anchor point preview: a marker at the anchor position interpolated
     * inside the limb's mesh bounds, offset by the limb's origin. Legacy math
     * verbatim, including the {@code translate(-x, -y, z)} sign asymmetry (X/Y
     * are mirrored by the model transform, Z is not).
     */
    protected void renderAnchorPreview(MatrixStack matrices, ModelCustomRenderer renderer)
    {
        if (this.anchorPreview == null || this.anchorPreview.length < 3
            || this.limb == null || this.limb.origin == null || this.limb.origin.length < 3
            || renderer.min == null || renderer.max == null)
        {
            return;
        }

        float[] t = anchorTranslation(renderer, this.limb, this.anchorPreview);

        matrices.push();
        matrices.translate(t[0], t[1], t[2]);

        this.drawPoint(matrices);

        matrices.pop();
    }

    /**
     * The translation applied before drawing the anchor marker, legacy verbatim:
     * the anchor fraction is lerped inside the limb's mesh bounds
     * ({@code min}/{@code max}), the limb {@code origin} is subtracted, and the
     * result is emitted as {@code (-x, -y, +z)} — X and Y are negated because
     * the model transform mirrors them, Z is not.
     */
    public static float[] anchorTranslation(ModelCustomRenderer renderer, ModelLimb limb, float[] anchor)
    {
        float dx = renderer.max.x - renderer.min.x;
        float dy = renderer.max.y - renderer.min.y;
        float dz = renderer.max.z - renderer.min.z;

        float x = renderer.min.x + Interpolations.lerp(0, dx, anchor[0]) - limb.origin[0];
        float y = renderer.min.y + Interpolations.lerp(0, dy, anchor[1]) - limb.origin[1];
        float z = renderer.min.z + Interpolations.lerp(0, dz, anchor[2]) - limb.origin[2];

        return new float[] {-x, -y, z};
    }
}
