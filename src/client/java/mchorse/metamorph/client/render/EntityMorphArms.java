package mchorse.metamorph.client.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.QuadrupedEntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The "which two limbs are this mob's hands" search (roadmap P54).
 *
 * <p>Port of Metamorph's {@code EntityMorph.setupHands}, which a morphed player's
 * first-person view needs: the disguise's own arm is drawn in place of the
 * player's, and on an arbitrary mob model there is no such thing as a labelled
 * arm. Legacy had three branches and this keeps all three, including the third
 * one's own description of itself — "for anything else, pretty bad algorithm".</p>
 *
 * <ol>
 *   <li>{@code ModelBiped} → its two arm fields;</li>
 *   <li>{@code ModelQuadruped} → {@code leg2} / {@code leg3}, which in yarn's
 *       naming are the <b>left hind</b> and <b>right front</b> legs (legacy's
 *       four legs were numbered right-hind, left-hind, right-front, left-front,
 *       so the pair it picked is diagonal — preserved, not corrected);</li>
 *   <li>anything else → sort every box by {@code rotationPointX} and take the
 *       extreme one from each end: leftmost for the left hand, rightmost for the
 *       right.</li>
 * </ol>
 *
 * <p><b>What the third branch had to be rebuilt from.</b> 1.12.2 models kept a
 * flat {@code boxList} of every {@code ModelRenderer} ever added to them; on
 * 1.20.4 a model is a tree of {@link ModelPart}s with no flat view and no public
 * root. The equivalent is a depth-first walk from whatever roots the model type
 * exposes ({@link AnimalModel}'s head + body parts, {@link SinglePartEntityModel}'s
 * single root), which yields the same set for every vanilla model — they are all
 * reachable from those roots. A model type exposing neither yields no parts, and
 * the caller then treats the morph as hand-less, which is the same thing legacy
 * did when its sort found an empty list.</p>
 *
 * <p><b>The comparator is legacy's, oddly written and correct.</b> It casts the
 * float pivot difference to {@code int} after rounding it <i>away</i> from zero
 * ({@code ceil} when positive, {@code floor} when negative), which is a long way
 * round to a plain comparison — but it does order by pivot, and only an exact
 * tie yields 0, where the stable sort then falls back to list order. It is kept
 * verbatim rather than replaced by {@code Float.compare} because the two agree
 * everywhere it matters and the legacy form is the one to diff against.</p>
 */
public final class EntityMorphArms
{
    private EntityMorphArms()
    {
    }

    /** The pair, in legacy's field order. Either component may be null. */
    public static final class Arms
    {
        public final ModelPart left;
        public final ModelPart right;

        public Arms(ModelPart left, ModelPart right)
        {
            this.left = left;
            this.right = right;
        }

        /** Legacy's guard: a null on either side means "no hand to draw". */
        public boolean complete()
        {
            return this.left != null && this.right != null;
        }
    }

    public static Arms find(EntityModel<?> model)
    {
        if (model instanceof BipedEntityModel)
        {
            BipedEntityModel<?> biped = (BipedEntityModel<?>) model;

            return new Arms(biped.leftArm, biped.rightArm);
        }

        if (model instanceof QuadrupedEntityModel)
        {
            QuadrupedEntityModel<?> quadruped = (QuadrupedEntityModel<?>) model;

            return new Arms(quadruped.leftHindLeg, quadruped.rightFrontLeg);
        }

        return byPivot(parts(model));
    }

    /**
     * Legacy's fallback: the same list sorted two ways, taking the head of each.
     * Written as two sorts rather than one min/max pair so a tie resolves the way
     * legacy's did — a stable sort leaves equal-pivot boxes in list order, and
     * both sorts then answer with the first of them.
     */
    public static Arms byPivot(List<ModelPart> parts)
    {
        if (parts == null || parts.isEmpty())
        {
            return new Arms(null, null);
        }

        List<ModelPart> left = new ArrayList<ModelPart>(parts);
        List<ModelPart> right = new ArrayList<ModelPart>(parts);

        left.sort(new Comparator<ModelPart>()
        {
            @Override
            public int compare(ModelPart a, ModelPart b)
            {
                return truncate(a.pivotX - b.pivotX);
            }
        });

        right.sort(new Comparator<ModelPart>()
        {
            @Override
            public int compare(ModelPart a, ModelPart b)
            {
                return truncate(b.pivotX - a.pivotX);
            }
        });

        return new Arms(left.get(0), right.get(0));
    }

    /**
     * Legacy's comparator body: {@code (int) (d < 0 ? floor(d) : ceil(d))} —
     * rounded away from zero, so every nonzero difference survives the int cast
     * and only an exact tie compares equal. See the class note.
     */
    public static int truncate(float delta)
    {
        return (int) (delta < 0 ? Math.floor(delta) : Math.ceil(delta));
    }

    /**
     * Every {@link ModelPart} reachable from the model, depth first — the
     * 1.20.4 stand-in for {@code ModelBase.boxList}.
     */
    public static List<ModelPart> parts(EntityModel<?> model)
    {
        List<ModelPart> parts = new ArrayList<ModelPart>();

        if (model instanceof AnimalModel)
        {
            AnimalModel<?> animal = (AnimalModel<?>) model;

            for (ModelPart part : animal.getHeadParts())
            {
                collect(part, parts);
            }

            for (ModelPart part : animal.getBodyParts())
            {
                collect(part, parts);
            }
        }
        else if (model instanceof SinglePartEntityModel)
        {
            collect(((SinglePartEntityModel<?>) model).getPart(), parts);
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
}
