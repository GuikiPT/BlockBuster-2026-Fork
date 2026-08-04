package mchorse.metamorph.client.render;

import mchorse.metamorph.Metamorph;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.QuadrupedEntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limb <i>name</i> → limb <i>{@link ModelPart}</i> resolution for the entity
 * morph body-part pass (roadmap P80.2).
 *
 * <p>{@link EntityMorphLimbNames} answers "which limbs does this model offer",
 * for the editor's picker. This answers the render-time question the picker's
 * answer implies: <b>where is the limb called {@code X}</b>. It returns a
 * <i>path</i> — root first, target last — because a {@link ModelPart}'s
 * transform is only meaningful under its ancestors'. Legacy needed no such thing:
 * 1.12's {@code ModelBase} kept every limb as a flat field and its
 * {@code postRender} applied one part's transform, which was the whole chain
 * because vanilla 1.12 models were flat.</p>
 *
 * <p><b>Two lookup sources, in this order.</b></p>
 * <ol>
 *   <li>The model's <i>named roots</i>. {@link AnimalModel} hands its roots out
 *       as bare parts with the names discarded, so for the two base models
 *       legacy's reflection could populate the names come from the declared
 *       fields — {@link BipedEntityModel}'s seven public ones and
 *       {@link QuadrupedEntityModel}'s six (access-widened). These are the names
 *       {@code EntityMorphLimbs} translates old {@code Limb} strings <i>into</i>,
 *       so this is the lookup an imported 1.12 body part lands in.</li>
 *   <li>Failing that, a depth-first walk of the {@link ModelPart#children} tree
 *       under every root, whose keys are the literal strings the model's
 *       {@code TexturedModelData} builder used. That covers every other model —
 *       a creeper's {@code head}, a ghast's tentacles — and is where a limb
 *       picked in the editor on a modern client comes from.</li>
 * </ol>
 *
 * <p><b>Not found is not an error.</b> {@link #path} returns null and the caller
 * skips the part, which is legacy's own behaviour ("no point to render here since
 * if a limb wasn't found then it wouldn't be transformed correctly"). It is
 * logged once per model-class/name pair, because a silently detached body part is
 * precisely the failure P227 exists to make visible.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/morphs/EntityMorph.java ({@code setupLimbs}, {@code renderBodyParts})
 */
public final class EntityMorphLimbParts
{
    /** One-shot "no such limb on this model" warning keys. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private EntityMorphLimbParts()
    {}

    /**
     * The chain of parts to apply, outermost first, to reach the limb named
     * {@code limb} on {@code model} — or null when the model has no such limb.
     */
    public static List<ModelPart> path(EntityModel<?> model, String limb)
    {
        if (model == null || limb == null || limb.isEmpty())
        {
            return null;
        }

        ModelPart root = namedRoots(model).get(limb);

        if (root != null)
        {
            return Collections.singletonList(root);
        }

        for (ModelPart part : roots(model))
        {
            List<ModelPart> path = descend(part, limb);

            if (path != null)
            {
                return path;
            }
        }

        warn(model, limb);

        return null;
    }

    /**
     * The named roots of the two vanilla base models — the yarn counterparts of
     * every {@code ModelRenderer} field legacy's reflection found on
     * {@code ModelBiped} / {@code ModelQuadruped}, in the legacy declaration
     * order. Insertion ordered so it mirrors {@link EntityMorphLimbNames#BIPED} /
     * {@link EntityMorphLimbNames#QUADRUPED} exactly. Any other model type has no
     * addressable roots (its roots are anonymous), same as
     * {@link EntityMorphLimbNames}.
     */
    public static Map<String, ModelPart> namedRoots(EntityModel<?> model)
    {
        Map<String, ModelPart> roots = new LinkedHashMap<String, ModelPart>();

        if (model instanceof BipedEntityModel)
        {
            BipedEntityModel<?> biped = (BipedEntityModel<?>) model;
            ModelPart[] parts = {biped.head, biped.hat, biped.body, biped.rightArm, biped.leftArm, biped.rightLeg, biped.leftLeg};

            put(roots, EntityMorphLimbNames.BIPED, parts);
        }
        else if (model instanceof QuadrupedEntityModel)
        {
            QuadrupedEntityModel<?> quadruped = (QuadrupedEntityModel<?>) model;
            ModelPart[] parts = {quadruped.head, quadruped.body, quadruped.rightHindLeg, quadruped.leftHindLeg, quadruped.rightFrontLeg, quadruped.leftFrontLeg};

            put(roots, EntityMorphLimbNames.QUADRUPED, parts);
        }

        return roots;
    }

    private static void put(Map<String, ModelPart> roots, String[] names, ModelPart[] parts)
    {
        for (int i = 0; i < names.length; i++)
        {
            if (parts[i] != null)
            {
                roots.put(names[i], parts[i]);
            }
        }
    }

    /**
     * The model's root parts, in the order the model type exposes them — the
     * same set {@link EntityMorphArms#parts} walks, kept unflattened here so a
     * child's ancestors stay recoverable.
     */
    public static List<ModelPart> roots(EntityModel<?> model)
    {
        List<ModelPart> roots = new ArrayList<ModelPart>();

        if (model instanceof AnimalModel)
        {
            AnimalModel<?> animal = (AnimalModel<?>) model;

            for (ModelPart part : animal.getHeadParts())
            {
                roots.add(part);
            }

            for (ModelPart part : animal.getBodyParts())
            {
                roots.add(part);
            }
        }
        else if (model instanceof SinglePartEntityModel)
        {
            roots.add(((SinglePartEntityModel<?>) model).getPart());
        }

        return roots;
    }

    /**
     * Depth-first search under {@code part} for a child keyed {@code limb},
     * returning the whole chain from {@code part} down to it.
     */
    private static List<ModelPart> descend(ModelPart part, String limb)
    {
        if (part == null)
        {
            return null;
        }

        for (Map.Entry<String, ModelPart> entry : part.children.entrySet())
        {
            if (entry.getKey().equals(limb))
            {
                List<ModelPart> path = new ArrayList<ModelPart>();

                path.add(part);
                path.add(entry.getValue());

                return path;
            }

            List<ModelPart> deeper = descend(entry.getValue(), limb);

            if (deeper != null)
            {
                deeper.add(0, part);

                return deeper;
            }
        }

        return null;
    }

    private static void warn(EntityModel<?> model, String limb)
    {
        String key = model.getClass().getName() + "#" + limb;

        if (WARNED.add(key))
        {
            Metamorph.LOGGER.warn("P80.2 body part is attached to limb \"{}\", which {} does not have — the part is not drawn", limb, model.getClass().getSimpleName());
        }
    }
}
