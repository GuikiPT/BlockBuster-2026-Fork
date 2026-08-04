package mchorse.blockbuster.client.model.parsing;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.ModelUtils;
import mchorse.mclib.utils.resources.RLUtils;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.util.Identifier;

/**
 * Model exporter (roadmap P74).
 *
 * <p>Port of 1.12.2's {@code mchorse.blockbuster.client.model.parsing.ModelExporter}:
 * turns a vanilla (or modded) mob renderer's model into a Blockbuster JSON model,
 * which the model editor then hand-corrects. Backs {@code /model export
 * <entity_name> [entity_tag]}.</p>
 *
 * <h2>What legacy did, and what it maps to</h2>
 *
 * <p>1.12.2 models were {@code ModelBase} subclasses holding {@code ModelRenderer}
 * fields, each carrying a {@code cubeList} of {@code ModelBox}es; the exporter
 * reflected over the model's inherited fields to find the renderers, eliminated the
 * ones that were children of others, and emitted one Blockbuster limb per box. On
 * 1.20.4 a model is a tree of {@link ModelPart}s with a named {@code children} map
 * and a {@code cuboids} list, so the shape survives almost unchanged — the
 * reflection walk stays (it is still the only way to get <i>names</i> for the tree
 * <i>roots</i>, which no accessor exposes), the {@code children} map supplies names
 * for everything below them, and {@code ModelPart.Cuboid} replaces
 * {@code ModelBox}.</p>
 *
 * <p>Deviations, all documented in place below:</p>
 *
 * <ol>
 *   <li><b>Posing without rendering.</b> Legacy baked the pose by actually drawing
 *       the entity off-screen ({@code render.doRender(entity, 0, -420, 0, 0, 0)}).
 *       This port calls {@code animateModel} + {@code setAngles} directly with
 *       synthetic zero state, per the S5 plan — the same two calls a real render
 *       makes, minus the GL, so it also works headlessly.</li>
 *   <li><b>Texture size is solved, not read.</b> A baked {@link ModelPart} has no
 *       {@code textureWidth}/{@code textureHeight} (the dimensions live on the
 *       {@code TexturedModelData} that built it and are dropped), so
 *       {@link #solveTextureSize} recovers them from the cuboids' own UV spans.
 *       This subsumes legacy's {@code ModelIronGolem} workaround: there is no
 *       longer a second, lying {@code ModelBase.textureWidth} to disagree with.</li>
 *   <li><b>Child elimination is transitive.</b> Legacy only checked direct
 *       {@code childModels} membership, which was enough when a 1.12.2 model's
 *       fields were siblings. 1.20.4 model classes routinely hold a field for a
 *       deep descendant ({@code root.getChild("body").getChild("head")}), and a
 *       direct-only check would emit that subtree twice — once through the root's
 *       recursion and once as its own root.</li>
 *   <li><b>Mirroring is per-box and inferred.</b> 1.12.2 read
 *       {@code ModelRenderer.mirror}; 1.20.4 bakes mirroring into cuboid vertices,
 *       so {@link #isMirrored} reads it back off the geometry.</li>
 *   <li><b>The default texture is asked for directly.</b> Legacy hunted down
 *       {@code Render}'s protected "entity → ResourceLocation" method by
 *       reflection; {@code EntityRenderer.getTexture(T)} is public here.</li>
 * </ol>
 *
 * <p>Everything else is 1:1, including the quirks: <b>standing, sleeping and flying
 * are the same snapshot</b> (legacy saved all three from one render without
 * re-posing in between, so only {@code sneaking} actually differs), only a box that
 * is <i>first</i> in its part becomes the named anchor that later boxes and child
 * parts parent onto, pose translations negate all three axes and additionally
 * rebase root limbs against y = 24, and rotations are converted radians → degrees.</p>
 *
 * <p>The extraction half is exposed as statics over plain {@link ModelPart}s so it
 * is testable without a live entity; the instance half only supplies the entity
 * state.</p>
 */
public class ModelExporter
{
    /** Legacy's fallback texture size, used when no cuboid can supply one. */
    public static final int[] DEFAULT_TEXTURE = new int[] {64, 32};

    private LivingEntity entity;
    private LivingEntityRenderer<LivingEntity, EntityModel<LivingEntity>> render;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ModelExporter(LivingEntity entity, LivingEntityRenderer render)
    {
        this.entity = entity;
        this.render = (LivingEntityRenderer<LivingEntity, EntityModel<LivingEntity>>) render;
    }

    private EntityModel<LivingEntity> getModel()
    {
        return this.render.getModel();
    }

    /**
     * Main method of this class.
     *
     * <p>Exports a {@link Model} from the renderer's model and the entity's state,
     * then resolves the renderer's texture as the model's default.</p>
     */
    public Model exportModel(String name)
    {
        Model data = exportModel(name, this.getModel(), this.entity);

        this.setDefaultTexture(data);

        return data;
    }

    public String exportJSON(String name)
    {
        return ModelUtils.toJson(this.exportModel(name));
    }

    /**
     * The renderer-free half of the export, in legacy's order: pose once, read the
     * geometry, pose again and snapshot standing/sleeping/flying, then sneak and
     * snapshot that.
     *
     * <p>The renderer only ever supplied two things — the model and the default
     * texture — so everything except the texture is expressible over the model and
     * the entity alone, which is also what makes the whole sequence (and its
     * four-pose quirk) reachable headlessly.</p>
     */
    public static Model exportModel(String name, EntityModel<LivingEntity> model, LivingEntity entity)
    {
        Model data = new Model();

        pose(model, entity, false);

        List<NamedPart> roots = modelParts(model);

        data.name = name;
        data.texture = solveTextureSize(roots);

        Map<String, ModelPart> limbs = generateLimbs(data, roots);

        /* Save standing, sleeping and flying poses. Legacy re-posed once here and
         * then wrote all three from that single snapshot without touching the
         * model in between — so the three poses come out identical. Kept: the
         * exported model is a starting point the user edits in the model editor,
         * and "all poses start from standing" is the behaviour they know. */
        pose(model, entity, false);
        savePose("standing", data, limbs, entity.getWidth(), entity.getHeight());
        savePose("sleeping", data, limbs, entity.getWidth(), entity.getHeight());
        savePose("flying", data, limbs, entity.getWidth(), entity.getHeight());

        /* Save sneaking pose */
        setSneaking(entity);
        pose(model, entity, true);
        savePose("sneaking", data, limbs, entity.getWidth(), entity.getHeight());

        return data;
    }

    /**
     * Bake the model's pose from the entity's current state.
     *
     * <p>The 1.20.4 stand-in for legacy's {@code doRender(entity, 0, -420, 0, 0, 0)}:
     * that call's only relevant effect was the {@code setLivingAnimations} +
     * {@code setRotationAngles} pair it made on the way to the GL, with all of the
     * limb-swing/head-yaw arguments left at zero because the entity was freshly
     * constructed and never ticked. Those two calls are {@code animateModel} and
     * {@code setAngles} here, so the pose is identical without a framebuffer.</p>
     *
     * @param sneaking mirrors legacy's {@code ModelBiped.isSneak = true} bracket.
     *                 1.20.4's {@link BipedEntityModel} reads its own
     *                 {@code sneaking} field inside {@code setAngles} (it does not
     *                 re-derive it from the entity), so setting the field is both
     *                 necessary and sufficient for biped models; non-biped models
     *                 read the entity, which {@link #setSneaking} has already
     *                 crouched.
     */
    public static void pose(EntityModel<LivingEntity> model, LivingEntity entity, boolean sneaking)
    {
        model.handSwingProgress = 0;
        model.riding = false;
        model.child = entity.isBaby();

        if (model instanceof BipedEntityModel)
        {
            ((BipedEntityModel<?>) model).sneaking = sneaking;
        }

        model.animateModel(entity, 0, 0, 0);
        model.setAngles(entity, 0, 0, 0, 0, 0);
    }

    /**
     * Tries to set default texture.
     *
     * <p>Legacy scanned {@code Render}'s declared methods for the protected one
     * taking an {@code Entity} and returning a {@code ResourceLocation}; that method
     * is {@code EntityRenderer.getTexture} and it is public in 1.20.4, so the
     * reflection is gone. It is still wrapped, because a renderer is free to derive
     * the texture from state a never-ticked entity does not have.</p>
     */
    private void setDefaultTexture(Model data)
    {
        try
        {
            Identifier texture = this.render.getTexture(this.entity);

            if (texture != null)
            {
                data.defaultTexture = RLUtils.create(texture.getNamespace(), texture.getPath());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Set entity sneaking.
     *
     * <p>Legacy's tameable branch is kept verbatim (a sitting dog is the "sneaking"
     * pose of a dog). The plain branch additionally forces the crouching
     * {@link EntityPose}: 1.12.2's {@code isSneaking()} <i>was</i> the flag, whereas
     * 1.20.4's {@code isInSneakingPose()} — what a model reads when it consults the
     * entity rather than its own {@code sneaking} field — is derived from the pose,
     * and the pose is only recomputed in {@code LivingEntity.tick}, which an entity
     * built for export never runs. It is set only for an entity that has a world,
     * because assigning it recalculates the entity's dimensions.</p>
     */
    public static void setSneaking(LivingEntity entity)
    {
        if (entity instanceof TameableEntity)
        {
            ((TameableEntity) entity).setSitting(true);
        }
        else
        {
            entity.setSneaking(true);

            if (entity.getWorld() != null)
            {
                entity.setPose(EntityPose.CROUCHING);
            }
        }
    }

    /* Headless extraction half — statics over plain ModelParts */

    /**
     * A tree root together with the name it was found under. Legacy had no
     * equivalent: 1.12.2 named limbs from {@code ModelBox.boxName} (which vanilla
     * models almost never set, so its exports were mostly {@code limb_0},
     * {@code limb_1}, …). 1.20.4 does better — a part's name is its key in the
     * parent's {@code children} map, and for the roots it is the model field's name.
     */
    public static final class NamedPart
    {
        public final String name;
        public final ModelPart part;

        public NamedPart(String name, ModelPart part)
        {
            this.name = name;
            this.part = part;
        }
    }

    /**
     * Save pose transformations for every limb — legacy's {@code savePose} with the
     * entity's dimensions passed in rather than read off a live entity.
     */
    public static void savePose(String poseName, Model data, Map<String, ModelPart> limbs, float width, float height)
    {
        ModelPose pose = new ModelPose();

        pose.size = new float[] {width, height, width};

        for (Map.Entry<String, ModelPart> entry : limbs.entrySet())
        {
            String key = entry.getKey();
            ModelPart part = entry.getValue();
            ModelTransform transform = new ModelTransform();

            float PI = (float) Math.PI;

            float rx = part.pitch * 180 / PI;
            float ry = part.yaw * 180 / PI;
            float rz = part.roll * 180 / PI;

            float x = part.pivotX;
            float y = part.pivotY;
            float z = part.pivotZ;

            if (data.limbs.get(key).parent.isEmpty())
            {
                x *= -1;
                y = -(y - 24);
                z *= -1;
            }
            else
            {
                x *= -1;
                y *= -1;
                z *= -1;
            }

            transform.rotate = new float[] {rx, ry, rz};
            transform.translate = new float[] {x, y, z};

            pose.limbs.put(key, transform);
        }

        data.poses.put(poseName, pose);
    }

    /**
     * Generate limbs from the given model roots, filling {@code data.limbs} and
     * returning the "which part backs which limb" map that {@link #savePose} needs
     * (only anchor limbs — the first box of each part — appear in it, as in legacy).
     */
    public static Map<String, ModelPart> generateLimbs(Model data, List<NamedPart> roots)
    {
        return generateLimbs(data, roots, data.texture);
    }

    public static Map<String, ModelPart> generateLimbs(Model data, List<NamedPart> roots, int[] texture)
    {
        Map<String, ModelPart> limbs = new HashMap<String, ModelPart>();
        int[] limbId = new int[1];

        for (NamedPart root : roots)
        {
            generateLimbs(limbs, root.part, root.name, data, texture, "", limbId);
        }

        return limbs;
    }

    /**
     * Recursive method for generating limbs.
     *
     * <p>Legacy's body, with {@code cubeList} → {@code cuboids} and
     * {@code childModels} → the named {@code children} map. The two quirks that
     * matter are preserved: a part's <i>first</i> box carries the mirror flag and the
     * incoming parent and becomes {@code firstName}, which every later box of the
     * part and every child part parents onto; and a part with <b>no</b> boxes leaves
     * {@code firstName} empty, so its children come out as roots.</p>
     */
    private static void generateLimbs(Map<String, ModelPart> limbs, ModelPart part, String partName, Model data, int[] texture, String parentName, int[] limbId)
    {
        int j = 0;
        String firstName = "";

        for (ModelPart.Cuboid box : part.cuboids)
        {
            ModelLimb limb = new ModelLimb();
            String name = limbName(data, partName, j, limbId[0]);

            if (j == 0)
            {
                limb.mirror = isMirrored(box);
                limb.parent = parentName;
                firstName = name;
                limbs.put(name, part);
            }
            else
            {
                limb.parent = firstName;
            }

            limb.size = getModelSize(box);
            limb.texture = getModelOffset(box, texture);
            limb.anchor = getAnchor(box, limb.size);

            data.limbs.put(name, limb);

            limbId[0]++;
            j++;
        }

        for (Map.Entry<String, ModelPart> child : part.children.entrySet())
        {
            generateLimbs(limbs, child.getValue(), child.getKey(), data, texture, firstName, limbId);
        }
    }

    /**
     * Name box {@code j} of a part.
     *
     * <p>Legacy: {@code boxName}, or {@code "limb_" + limbId} when it was absent —
     * which for vanilla models was nearly always. Here the part name stands in for
     * {@code boxName}, suffixed for a part's second and later boxes, and legacy's
     * {@code limb_<id>} numbering survives as the unnamed/collision fallback (two
     * parts at different depths may legitimately both be called {@code head}).</p>
     */
    private static String limbName(Model data, String partName, int j, int limbId)
    {
        String name = partName.isEmpty()
            ? "limb_" + limbId
            : (j == 0 ? partName : partName + "_" + j);

        if (!data.limbs.containsKey(name))
        {
            return name;
        }

        name = "limb_" + limbId;

        for (int i = 2; data.limbs.containsKey(name); i++)
        {
            name = "limb_" + limbId + "_" + i;
        }

        return name;
    }

    /**
     * Compute model size based on the box — legacy's {@code posX2 - posX1} triple.
     * {@code Cuboid} stores {@code minX}/{@code maxX} <b>before</b> dilation is
     * applied, so this is the authored box size, as in 1.12.2.
     */
    public static int[] getModelSize(ModelPart.Cuboid box)
    {
        int w = (int) (box.maxX - box.minX);
        int h = (int) (box.maxY - box.minY);
        int d = (int) (box.maxZ - box.minZ);

        return new int[] {w, h, d};
    }

    /**
     * Get the texture offset of a box.
     *
     * <p>Legacy tried {@code ModelBase.getTextureOffset(boxName)} first and fell
     * back to reflecting {@code ModelBox}'s private {@code TexturedQuad[]} and
     * scaling the minimum vertex UV by the renderer's texture size. 1.20.4 has no
     * texture-offset table at all, so only the fallback remains — and it is exact:
     * {@code Cuboid} lays its first (west) quad's left edge at the box's {@code u}
     * and its top edge at its {@code v}, both divided by the texture size, so the
     * minimum UV times that size is the offset back.</p>
     */
    public static int[] getModelOffset(ModelPart.Cuboid box, int[] texture)
    {
        float[] bounds = uvBounds(box);

        if (bounds == null)
        {
            return new int[] {0, 0};
        }

        /* Legacy truncated. UVs are exact here — every one of them is an integer
         * over a power-of-two texture size — so truncation and rounding agree, and
         * the legacy form is the one to diff against. */
        return new int[] {(int) (bounds[0] * texture[0]), (int) (bounds[1] * texture[1])};
    }

    /**
     * Compute anchor based on the box — legacy's {@code -posX1 / size} triple.
     */
    public static float[] getAnchor(ModelPart.Cuboid box, int[] size)
    {
        float w = size[0] != 0 ? -box.minX / size[0] : 0;
        float h = size[1] != 0 ? -box.minY / size[1] : 0;
        float d = size[2] != 0 ? -box.minZ / size[2] : 0;

        return new float[] {w, h, d};
    }

    /**
     * Whether a box's texture is mirrored.
     *
     * <p>1.12.2 asked the renderer ({@code ModelRenderer.mirror}); 1.20.4 bakes
     * mirroring into the cuboid by swapping the two X extremes <i>before</i> building
     * its vertices, so it reads back off the geometry: on a non-mirrored box the
     * first two vertices of any quad that spans X run from the high X to the low one,
     * and mirroring reverses that. Quads that do not span X (the two side faces) are
     * skipped, which is also why a box built without those spanning faces answers
     * {@code false} — the same answer as a model that never mirrored anything.</p>
     */
    public static boolean isMirrored(ModelPart.Cuboid box)
    {
        for (ModelPart.Quad quad : box.sides)
        {
            if (quad == null || quad.vertices.length < 2)
            {
                continue;
            }

            float a = quad.vertices[0].pos.x();
            float b = quad.vertices[1].pos.x();

            if (a != b)
            {
                return a < b;
            }
        }

        return false;
    }

    /**
     * Recover the model's texture size from its boxes' UVs.
     *
     * <p>The one piece of legacy state 1.20.4 simply does not keep: 1.12.2 read
     * {@code ModelBase.textureWidth/Height} and then overrode it with the largest
     * {@code ModelRenderer.textureWidth/Height} (the {@code ModelIronGolem}
     * workaround, because models set the size on their renderers and left the
     * model's own fields lying). A baked {@link ModelPart} carries neither: the
     * dimensions belong to the {@code TexturedModelData} that created it and are
     * consumed while normalising the UVs.</p>
     *
     * <p>But that normalisation is exactly what makes them recoverable. A cuboid's
     * six faces tile a UV region whose width is {@code 2·(sizeX + sizeZ)} pixels and
     * whose height is {@code sizeY + sizeZ}, and the stored UVs are those pixel
     * counts divided by the texture size — so dividing the known pixel span by the
     * observed normalised span gives the size back, exactly. Only fully six-sided
     * boxes are used (a box missing its west or south face does not reach the edges
     * of its own region); the largest answer wins, which is the same
     * "trust the biggest" rule legacy applied across renderers.</p>
     */
    public static int[] solveTextureSize(List<NamedPart> roots)
    {
        List<ModelPart> parts = new ArrayList<ModelPart>();

        for (NamedPart root : roots)
        {
            collect(root.part, parts);
        }

        return solveTextureSizeForParts(parts);
    }

    public static int[] solveTextureSizeForParts(Collection<ModelPart> parts)
    {
        int width = 0;
        int height = 0;

        for (ModelPart part : parts)
        {
            for (ModelPart.Cuboid box : part.cuboids)
            {
                if (box.sides.length != 6)
                {
                    continue;
                }

                float[] bounds = uvBounds(box);

                if (bounds == null)
                {
                    continue;
                }

                float spanU = bounds[2] - bounds[0];
                float spanV = bounds[3] - bounds[1];

                float pixelsU = 2 * ((box.maxX - box.minX) + (box.maxZ - box.minZ));
                float pixelsV = (box.maxY - box.minY) + (box.maxZ - box.minZ);

                if (spanU > 0 && pixelsU > 0)
                {
                    width = Math.max(width, Math.round(pixelsU / spanU));
                }

                if (spanV > 0 && pixelsV > 0)
                {
                    height = Math.max(height, Math.round(pixelsV / spanV));
                }
            }
        }

        if (width <= 0 || height <= 0)
        {
            return new int[] {DEFAULT_TEXTURE[0], DEFAULT_TEXTURE[1]};
        }

        return new int[] {width, height};
    }

    /**
     * {@code {minU, minV, maxU, maxV}} over every vertex of every face, or
     * {@code null} for a box with no faces at all.
     */
    private static float[] uvBounds(ModelPart.Cuboid box)
    {
        float minU = Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        boolean any = false;

        for (ModelPart.Quad quad : box.sides)
        {
            if (quad == null)
            {
                continue;
            }

            for (ModelPart.Vertex vertex : quad.vertices)
            {
                minU = Math.min(vertex.u, minU);
                minV = Math.min(vertex.v, minV);
                maxU = Math.max(vertex.u, maxU);
                maxV = Math.max(vertex.v, maxV);
                any = true;
            }
        }

        return any ? new float[] {minU, minV, maxU, maxV} : null;
    }

    /**
     * Get all named tree roots the given model has.
     *
     * <p>Legacy's {@code getModelRenderers}: walk every inherited field of type
     * {@code ModelRenderer}/{@code ModelRenderer[]}, dedup by identity, then drop
     * the ones that are children of another. Kept, because a model field's name is
     * the only name a <i>root</i> has (the {@code children} map names everything
     * below it, and the accessors that expose roots — {@code AnimalModel}'s head and
     * body parts, {@code SinglePartEntityModel}'s part — expose no names at all).</p>
     *
     * <p>The child check is transitive here, not direct-only as in legacy: 1.12.2
     * fields were siblings under a flat model, while 1.20.4 models routinely keep a
     * field for a deep descendant of another field, and a direct-only check would
     * walk that subtree twice.</p>
     *
     * <p>If the model exposes no {@link ModelPart} fields at all, this falls back to
     * the accessor roots with generated names, so an obfuscated or synthesised model
     * still exports something rather than nothing.</p>
     */
    public static List<NamedPart> modelParts(EntityModel<?> model)
    {
        List<NamedPart> parts = new ArrayList<NamedPart>();
        Set<ModelPart> seen = new HashSet<ModelPart>();

        for (Field field : getInheritedFields(model.getClass()))
        {
            Class<?> type = field.getType();
            boolean single = ModelPart.class.equals(type);
            boolean array = ModelPart[].class.equals(type);

            if (!single && !array)
            {
                continue;
            }

            try
            {
                field.setAccessible(true);

                if (single)
                {
                    add(parts, seen, field.getName(), (ModelPart) field.get(model));
                }
                else
                {
                    ModelPart[] more = (ModelPart[]) field.get(model);

                    for (int i = 0; more != null && i < more.length; i++)
                    {
                        add(parts, seen, field.getName() + "_" + i, more[i]);
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();

                continue;
            }
        }

        if (parts.isEmpty())
        {
            for (ModelPart part : accessorParts(model))
            {
                add(parts, seen, "limb_" + parts.size(), part);
            }
        }

        /* Eliminate any parts reachable from another part */
        List<NamedPart> roots = new ArrayList<NamedPart>();

        for (NamedPart child : parts)
        {
            boolean isChild = false;

            for (NamedPart parent : parts)
            {
                if (parent.part != child.part && contains(parent.part, child.part))
                {
                    isChild = true;

                    break;
                }
            }

            if (!isChild)
            {
                roots.add(child);
            }
        }

        return roots;
    }

    private static void add(List<NamedPart> parts, Set<ModelPart> seen, String name, ModelPart part)
    {
        if (part != null && seen.add(part))
        {
            parts.add(new NamedPart(name, part));
        }
    }

    /** Whether {@code needle} sits anywhere below {@code part}. */
    private static boolean contains(ModelPart part, ModelPart needle)
    {
        for (ModelPart child : part.children.values())
        {
            if (child == needle || contains(child, needle))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The nameless roots a model type exposes through its own API — the same set
     * {@code EntityMorphArms} walks for the hand search.
     */
    private static List<ModelPart> accessorParts(EntityModel<?> model)
    {
        List<ModelPart> parts = new ArrayList<ModelPart>();

        if (model instanceof AnimalModel)
        {
            AnimalModel<?> animal = (AnimalModel<?>) model;

            for (ModelPart part : animal.getHeadParts())
            {
                parts.add(part);
            }

            for (ModelPart part : animal.getBodyParts())
            {
                parts.add(part);
            }
        }
        else if (model instanceof SinglePartEntityModel)
        {
            parts.add(((SinglePartEntityModel<?>) model).getPart());
        }

        return parts;
    }

    private static void collect(ModelPart part, List<ModelPart> out)
    {
        if (part == null || out.contains(part))
        {
            return;
        }

        out.add(part);

        for (ModelPart child : part.children.values())
        {
            collect(child, out);
        }
    }

    /**
     * From StackOverflow
     */
    public static List<Field> getInheritedFields(Class<?> type)
    {
        List<Field> fields = new ArrayList<Field>();

        for (Class<?> c = type; c != null; c = c.getSuperclass())
        {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }

        return fields;
    }
}
