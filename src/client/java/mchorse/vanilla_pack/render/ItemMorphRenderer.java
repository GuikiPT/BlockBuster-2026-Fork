package mchorse.vanilla_pack.render;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.GuiInventoryElement;
import mchorse.mclib.client.render.McLibRenderLayers;
import mchorse.metamorph.client.render.IMorphRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import mchorse.vanilla_pack.morphs.ItemMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import org.joml.Quaternionf;

/**
 * Client render body for {@link ItemMorph} (roadmap P54/P53.2).
 *
 * <p>The morph's {@code Transform} field is a <b>frozen 9-key disk contract</b>
 * (see {@code ItemMorph}'s NBT notes); the map from those strings to yarn's
 * {@link ModelTransformationMode} lives here, and legacy's fallback — an unknown
 * or empty key means {@code NONE} — is preserved, so a file written by a future
 * Metamorph with a tenth key still loads and renders rather than throwing.</p>
 *
 * <p><b>Camera transform, and where it goes.</b> Legacy resolved the baked model
 * itself, ran it through {@code ForgeHooksClient.handleCameraTransforms} when
 * the transform was not {@code NONE} — which multiplies the transform onto the
 * current matrix and hands back the perspective-substituted model — and only
 * <i>then</i> applied the floating animation, finishing with the
 * transform-less {@code RenderItem.renderItem(stack, model)}. So the legacy
 * composition is {@code translate(pos) · CT · bob · spin · translate(-0.5) ·
 * draw}: the bob is expressed in the transformed frame and the spin turns about
 * the transformed Y axis.</p>
 *
 * <p>1.20.4 has no transform-less item draw to aim at — every {@code
 * ItemRenderer.renderItem} overload applies {@code
 * model.getTransformation().getTransformation(mode)} itself, right before the
 * same {@code translate(-0.5)}. Passing {@code NONE} to suppress it is not
 * neutral: the mode also selects the trident/spyglass flat-model substitution,
 * the builtin-vs-baked branch and the GUI compass-glint multiplier. So the
 * transform is instead <b>conjugated</b> — {@code CT · animation · CT⁻¹} is
 * applied here and {@code renderItem} runs with the real mode, its own {@code
 * CT} cancelling the {@code CT⁻¹} to leave exactly the legacy product while
 * every vanilla special case still fires. {@link #applyInverse} is analytic
 * (scale⁻¹ → conjugate quaternion → negated translation, the reverse of {@code
 * Transformation.apply}), so no matrix is numerically inverted and the {@link
 * MatrixStack} normal matrix stays correct.</p>
 *
 * <p>The extrusion path needs no conjugation: it draws its own mesh, so the
 * transform is simply applied before the animation as legacy did.</p>
 *
 * <p><b>Texture extrusion.</b> The {@code Texture} field replaces the item model
 * with a 2D-image extrusion — {@link ItemExtruder} builds it,
 * {@link CachedExtrusion} holds it. A texture that cannot be read still draws
 * nothing, which is exactly what legacy did when {@code extrude} returned
 * null, rather than falling back to the item model and drawing the wrong
 * thing.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/morphs/ItemMorph.java (render/renderOnScreen/getStackForRender/getTransformType)
 */
public class ItemMorphRenderer implements IMorphRenderer<ItemMorph>
{
    /**
     * Legacy {@code ItemMorph.getTransformTypes()} — the frozen key set, in
     * declaration order. Legacy held a Guava {@code BiMap} because its editor
     * needed the reverse lookup too; the ported editor reads the key list
     * instead, so a plain map plus {@link #transformName} covers both.
     */
    private static final Map<String, ModelTransformationMode> TRANSFORM_TYPES = buildTransformTypes();

    private static Map<String, ModelTransformationMode> buildTransformTypes()
    {
        Map<String, ModelTransformationMode> types = new HashMap<String, ModelTransformationMode>();

        types.put("none", ModelTransformationMode.NONE);
        types.put("third_person_left_hand", ModelTransformationMode.THIRD_PERSON_LEFT_HAND);
        types.put("third_person_right_hand", ModelTransformationMode.THIRD_PERSON_RIGHT_HAND);
        types.put("first_person_left_hand", ModelTransformationMode.FIRST_PERSON_LEFT_HAND);
        types.put("first_person_right_hand", ModelTransformationMode.FIRST_PERSON_RIGHT_HAND);
        types.put("head", ModelTransformationMode.HEAD);
        types.put("gui", ModelTransformationMode.GUI);
        types.put("ground", ModelTransformationMode.GROUND);
        types.put("fixed", ModelTransformationMode.FIXED);

        return Collections.unmodifiableMap(types);
    }

    public static Map<String, ModelTransformationMode> getTransformTypes()
    {
        return TRANSFORM_TYPES;
    }

    /**
     * Legacy {@code getTransformType()}: empty/null/unknown → {@code NONE}.
     */
    public static ModelTransformationMode transformType(String transform)
    {
        if (transform == null || transform.isEmpty())
        {
            return ModelTransformationMode.NONE;
        }

        ModelTransformationMode mode = TRANSFORM_TYPES.get(transform);

        return mode == null ? ModelTransformationMode.NONE : mode;
    }

    /** Reverse of {@link #transformType}, for the editor's dropdown label. */
    public static String transformName(ModelTransformationMode mode)
    {
        for (Map.Entry<String, ModelTransformationMode> entry : TRANSFORM_TYPES.entrySet())
        {
            if (entry.getValue() == mode)
            {
                return entry.getKey();
            }
        }

        return "none";
    }

    /**
     * Legacy {@code getStackForRender}: an equipment-sourced morph reads the
     * rendered entity's slot, <b>except</b> inside a model-renderer preview,
     * where there is no meaningful wearer and legacy substituted the client
     * player so the preview shows the editor's own gear.
     */
    public static ItemStack stackForRender(ItemMorph morph, LivingEntity entity)
    {
        if (morph.itemFromEquipment)
        {
            if (GuiModelRenderer.isRendering())
            {
                MinecraftClient mc = MinecraftClient.getInstance();

                entity = mc == null ? entity : mc.player;
            }

            return entity == null ? ItemStack.EMPTY : entity.getEquippedStack(morph.equipmentSlot);
        }

        return morph.stack;
    }

    /**
     * Legacy's floating animation: a sine bob on Y and a constant spin on Y,
     * both driven off the entity's own age so two morphed players are not in
     * lockstep. Pure, so the curve is pinned by a test.
     */
    public static float bobbing(long worldTime, float partialTicks)
    {
        return MathHelper.sin((worldTime + partialTicks) / 10F) * 0.1F + 0.1F;
    }

    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(ItemMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || entity == null || context.matrices == null || context.consumers == null)
        {
            return;
        }

        ItemRenderer items = mc.getItemRenderer();

        if (items == null)
        {
            return;
        }

        ItemStack stack = stackForRender(morph, entity);
        MatrixStack matrices = context.matrices;
        int light = VanillaPackMorphRenderers.lightOf(morph.lighting, context.light);
        ModelTransformationMode mode = transformType(morph.transform);
        int seed = entity.getId() + mode.ordinal();

        /* Legacy resolved the model and applied the camera transform above the
         * texture branch, so both paths get it — and both get it *before* the
         * animation. */
        Transformation transform = cameraTransformation(items, stack, entity, mode, seed);

        matrices.push();

        try
        {
            matrices.translate(x, y, z);

            transform.apply(false, matrices);

            if (morph.animation)
            {
                matrices.translate(0F, bobbing(entity.getWorld().getTime(), partialTicks), 0F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + partialTicks) * 2F));
            }

            if (morph.texture != null)
            {
                CachedExtrusion extrusion = ItemExtruder.extrude(morph.texture.toIdentifier());

                if (extrusion != null)
                {
                    extrusion.emit(context.consumers.getBuffer(McLibRenderLayers.extrusion(extrusion.texture, false)),
                        matrices.peek(), light, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);
                }

                return;
            }

            if (stack.isEmpty())
            {
                return;
            }

            /* The other half of the conjugation: renderItem re-applies the very
             * transform this undoes, so the product is the legacy one. */
            applyInverse(matrices, transform);

            items.renderItem(entity, stack, mode, false, matrices, context.consumers, entity.getWorld(),
                light, OverlayTexture.DEFAULT_UV, seed);
        }
        finally
        {
            matrices.pop();
        }
    }

    /**
     * The camera transform {@code ItemRenderer.renderItem} will apply for
     * {@code mode} — read off the same baked model vanilla will read it off,
     * which is what makes the {@link #applyInverse} conjugation cancel exactly.
     *
     * <p>vanilla's inner {@code renderItem} swaps the <i>flat</i> inventory
     * model back in for the three "not held" modes before reading the
     * transformation, so a trident or spyglass item morph set to {@code gui},
     * {@code ground} or {@code fixed} resolves through that swap here too. The
     * two model ids are private in {@code ItemRenderer}; they are rebuilt rather
     * than access-widened, since they are plain vanilla resource names.</p>
     *
     * @return the transformation, or {@link Transformation#IDENTITY} when there
     *         is nothing to apply ({@code NONE}, or a degenerate scale that
     *         {@link #applyInverse} could not undo).
     */
    private static Transformation cameraTransformation(ItemRenderer items, ItemStack stack, LivingEntity entity, ModelTransformationMode mode, int seed)
    {
        if (mode == ModelTransformationMode.NONE)
        {
            return Transformation.IDENTITY;
        }

        BakedModel model = items.getModel(stack, entity.getWorld(), entity, seed);

        if (mode == ModelTransformationMode.GUI || mode == ModelTransformationMode.GROUND || mode == ModelTransformationMode.FIXED)
        {
            BakedModelManager models = items.getModels().getModelManager();

            if (stack.isOf(Items.TRIDENT))
            {
                model = models.getModel(ModelIdentifier.ofVanilla("trident", "inventory"));
            }
            else if (stack.isOf(Items.SPYGLASS))
            {
                model = models.getModel(ModelIdentifier.ofVanilla("spyglass", "inventory"));
            }
        }

        if (model == null)
        {
            return Transformation.IDENTITY;
        }

        Transformation transform = model.getTransformation().getTransformation(mode);

        return invertible(transform) ? transform : Transformation.IDENTITY;
    }

    /**
     * Whether {@link #applyInverse} can undo this transform. A model that
     * declares a zero on any scale axis collapses the frame and has no inverse;
     * dropping the whole conjugation there (rather than dividing by zero and
     * emitting NaN vertices) costs only the legacy bob/spin ordering on a model
     * that draws nothing along that axis anyway.
     */
    public static boolean invertible(Transformation transform)
    {
        return Math.abs(transform.scale.x()) > 1.0E-6F
            && Math.abs(transform.scale.y()) > 1.0E-6F
            && Math.abs(transform.scale.z()) > 1.0E-6F;
    }

    /**
     * The exact inverse of {@code Transformation.apply(false, matrices)}, which
     * is {@code translate(t) · rotateXYZ(r) · scale(s)} — so the inverse is
     * {@code scale(1/s) · rotateXYZ(r)⁻¹ · translate(-t)}, in that order. Done
     * with {@link MatrixStack} ops rather than a numeric matrix inversion so the
     * stack's normal matrix is maintained the same way the forward pass
     * maintains it.
     *
     * <p>{@code leftHanded} is not a parameter: both legacy and this port only
     * ever apply the right-handed form.</p>
     */
    public static void applyInverse(MatrixStack matrices, Transformation transform)
    {
        if (transform == Transformation.IDENTITY)
        {
            return;
        }

        matrices.scale(1F / transform.scale.x(), 1F / transform.scale.y(), 1F / transform.scale.z());

        matrices.multiply(new Quaternionf().rotationXYZ(
            transform.rotation.x() * DEG_TO_RAD,
            transform.rotation.y() * DEG_TO_RAD,
            transform.rotation.z() * DEG_TO_RAD).conjugate());

        matrices.translate(-transform.translation.x(), -transform.translation.y(), -transform.translation.z());
    }

    /** {@code Transformation.apply}'s degrees→radians constant, verbatim. */
    private static final float DEG_TO_RAD = 0.017453292F;

    /* --------------------------------------------------------------------- */
    /* On screen (GUI)                                                       */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy drew the GUI preview at {@code scale / 16} with the stack's own
     * 16×16 GUI render centred on {@code (-8, -8)}, offset 12 px up so the item
     * sits where a mob's body would in the same cell.
     *
     * <p>The extrusion takes the other branch: no camera transform (legacy did
     * not apply one on screen), and a {@code (s, -s, s)} scale at the
     * <b>undivided</b> cell scale — legacy wrote {@code scale * 16F} against the
     * already-divided local, which cancels back out. The mirrored Y goes through
     * {@link MatrixStack#scale} rather than {@code multiplyPositionMatrix}
     * because all three magnitudes match, which is the case vanilla handles by
     * flipping the normal matrix's sign — and the extrusion's normals are real
     * geometry, unlike the flat quads elsewhere in this port.</p>
     */
    @Override
    public void renderOnScreen(ItemMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        DrawContext dc = GuiDraw.getDrawContext();

        if (dc == null)
        {
            return;
        }

        MatrixStack matrices = dc.getMatrices();
        float cell = scale;

        scale = scale / 16F;

        matrices.push();

        try
        {
            matrices.translate(x, y - 12, 0);

            if (morph.texture != null)
            {
                CachedExtrusion extrusion = ItemExtruder.extrude(morph.texture.toIdentifier());

                if (extrusion != null)
                {
                    VertexConsumerProvider.Immediate consumers = dc.getVertexConsumers();

                    matrices.scale(cell, -cell, cell);

                    extrusion.emit(consumers.getBuffer(McLibRenderLayers.extrusion(extrusion.texture, false)),
                        matrices.peek(), MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);

                    consumers.draw();
                }

                return;
            }

            matrices.scale(scale, scale, scale);

            /* z 0, not the default 200 — the legacy call passed its zLevel
             * explicitly, and the ported drawItemStack keeps that offset. */
            GuiInventoryElement.drawItemStack(stackForRender(morph, player), -8, -8, 0, null);
        }
        finally
        {
            matrices.pop();
        }
    }
}
