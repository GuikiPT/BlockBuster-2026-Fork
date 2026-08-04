package mchorse.metamorph.client.render;

import mchorse.metamorph.api.morphs.EntityMorphLimbs;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.render.entity.model.QuadrupedEntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The limb-name list an entity morph offers to the body-part editor
 * (roadmap P59.2).
 *
 * <p>Legacy {@code EntityMorph.setupLimbs} reflected over the {@code ModelBase}
 * subclass's <b>fields</b> of type {@code ModelRenderer} and used the Java field
 * names ({@code bipedHead}, {@code leg1}, …) as limb keys. That approach cannot
 * be ported literally: 1.20.4 field names are obfuscated in a production jar, so
 * reflection would yield {@code field_3398} in game and {@code head} in a dev
 * run — the exact kind of environment-dependent key a saved body part must never
 * hold.</p>
 *
 * <p>What is stable in 1.20.4 is the {@link ModelPart} child <b>tree</b>: the
 * keys of {@link ModelPart#children} are the literal strings the model's
 * {@code TexturedModelData} builder used ({@code "head"}, {@code "right_arm"},
 * {@code "right_hind_leg"}, …), and those survive obfuscation. So the names come
 * from a depth-first walk of that tree.</p>
 *
 * <p>One gap has to be filled by a table: a model's <i>root</i> parts are handed
 * out by {@link AnimalModel#getHeadParts()}/{@code getBodyParts()} as bare
 * {@link ModelPart}s with no name attached (the named root they were built from
 * is not retained). For the two base models legacy's reflection could actually
 * populate — {@link BipedEntityModel} and {@link QuadrupedEntityModel} — the
 * names are known constants, so they are added explicitly, in the same order the
 * legacy field declarations had. Every other model contributes whatever its
 * children are named, and a model type exposing no roots at all contributes
 * nothing (the editor then shows an empty limb list, which is what legacy showed
 * for a model whose fields it could not read).</p>
 *
 * <p>These are the same yarn names {@link EntityMorphLimbs} translates old
 * saved {@code Limb} strings <i>into</i>, so an imported 1.12 body part and a
 * freshly picked one address the same limb.</p>
 *
 * <p>Note that attaching body parts to those limbs while rendering an entity
 * morph is still P80.2 ({@code setupBodyPart}/{@code setupLimbs} on the render
 * side); this class only feeds the editor's picker.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/morphs/EntityMorph.java ({@code setupLimbs})
 */
public final class EntityMorphLimbNames
{
    private EntityMorphLimbNames()
    {}

    /** Legacy {@code ModelBiped}'s {@code ModelRenderer} fields, in order. */
    public static final String[] BIPED = {
        EntityModelPartNames.HEAD,
        EntityModelPartNames.HAT,
        EntityModelPartNames.BODY,
        EntityModelPartNames.RIGHT_ARM,
        EntityModelPartNames.LEFT_ARM,
        EntityModelPartNames.RIGHT_LEG,
        EntityModelPartNames.LEFT_LEG
    };

    /** Legacy {@code ModelQuadruped}'s fields ({@code head, body, leg1..leg4}). */
    public static final String[] QUADRUPED = {
        EntityModelPartNames.HEAD,
        EntityModelPartNames.BODY,
        EntityModelPartNames.RIGHT_HIND_LEG,
        EntityModelPartNames.LEFT_HIND_LEG,
        EntityModelPartNames.RIGHT_FRONT_LEG,
        EntityModelPartNames.LEFT_FRONT_LEG
    };

    /**
     * Every limb name the given model offers, root names first (when the model
     * type makes them knowable) then the child tree, depth first. Insertion
     * ordered and duplicate free; the editor sorts it for display.
     */
    public static Collection<String> names(EntityModel<?> model)
    {
        if (model == null)
        {
            return Collections.emptyList();
        }

        Set<String> names = new LinkedHashSet<String>();

        if (model instanceof BipedEntityModel)
        {
            Collections.addAll(names, BIPED);
        }
        else if (model instanceof QuadrupedEntityModel)
        {
            Collections.addAll(names, QUADRUPED);
        }

        /* EntityMorphArms.parts is already the full depth-first walk (P54's
         * stand-in for ModelBase.boxList), so naming each part's children names
         * every named part in the model exactly once. */
        for (ModelPart part : EntityMorphArms.parts(model))
        {
            names.addAll(part.children.keySet());
        }

        return names;
    }

    /**
     * Add the names of every descendant of {@code part}, depth first — the same
     * naming rule applied to a bare {@link ModelPart} tree, for callers that
     * hold a root rather than a model. The part itself is not named from here:
     * only a parent knows its children's keys.
     */
    public static void collect(ModelPart part, Set<String> names)
    {
        if (part == null)
        {
            return;
        }

        for (Map.Entry<String, ModelPart> entry : part.children.entrySet())
        {
            names.add(entry.getKey());
            collect(entry.getValue(), names);
        }
    }
}
