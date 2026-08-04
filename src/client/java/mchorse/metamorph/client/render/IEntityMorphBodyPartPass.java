package mchorse.metamorph.client.render;

import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.LivingEntity;

/**
 * The body-part pass seam of {@link EntityMorphRenderer} (roadmap P80.2).
 *
 * <p>Legacy {@code EntityMorph.setupBodyPart()}: right before a disguise is
 * drawn, point the mob renderer's body-part layer at this morph; the layer then
 * fires inside the vanilla render. Split out as a seam because arming it touches
 * a live {@code EntityRenderDispatcher} and a vanilla renderer's feature list —
 * neither of which exists headlessly — while the pass's decisions (which limb,
 * which part, restore-on-exit) are worth testing without a game.</p>
 *
 * <p>The default is {@link #NOOP}, which is what a dedicated server and the test
 * suite see. {@link EntityMorphBodyPartPass#install()} replaces it at client
 * init; {@code EntityMorphBodyPartPassTest} pins that it did.</p>
 */
public interface IEntityMorphBodyPartPass
{
    /**
     * Arm the pass for one draw of {@code morph} on {@code dummy}.
     *
     * @return an opaque token to hand back to {@link #disarm(Object)}, or null
     *         when there was nothing to arm (no parts, no living renderer)
     */
    Object arm(EntityRenderDispatcher dispatcher, LivingEntity dummy, EntityMorph morph);

    /** Undo {@link #arm}; null-tolerant, and safe to call twice. */
    void disarm(Object armed);

    /**
     * "No body-part pass installed" — every disguise draws without its attached
     * parts, exactly the P80.2 behaviour this phase replaces.
     */
    IEntityMorphBodyPartPass NOOP = new IEntityMorphBodyPartPass()
    {
        @Override
        public Object arm(EntityRenderDispatcher dispatcher, LivingEntity dummy, EntityMorph morph)
        {
            return null;
        }

        @Override
        public void disarm(Object armed)
        {}
    };
}
