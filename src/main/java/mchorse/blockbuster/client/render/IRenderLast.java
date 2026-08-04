package mchorse.blockbuster.client.render;

import javax.vecmath.Vector3d;

/**
 * Marker for anything that opts out of the normal render pass and is instead
 * drawn, back-to-front, in the sorted tail pass (roadmap P80.3).
 *
 * <p>Verbatim port of 1.12.2
 * {@code blockbuster-1.12/.../client/render/IRenderLast.java} — one method,
 * returning the position the tail pass depth-sorts on. Implemented by
 * {@link mchorse.blockbuster.common.entity.EntityActor} and
 * {@link mchorse.blockbuster.common.tileentity.TileEntityModel}, the two things
 * whose GUI exposes a "render last" toggle.</p>
 *
 * <h2>Why this lives in {@code src/main}</h2>
 * Its two implementors are common-side classes ({@code EntityActor} is an
 * entity, {@code TileEntityModel} a block entity), so a client-source-set
 * interface could not be implemented by either. The legacy package name is
 * kept — {@code mchorse.blockbuster.client.render} — for diff-ability against
 * the 1.12.2 tree, and the interface deliberately mentions no Minecraft type at
 * all, so the sort is testable against a plain test double.
 *
 * <h2>Deliberate deviation: {@code partialTicks} is a parameter</h2>
 * Legacy's signature is zero-arg and {@code EntityActor.getRenderLastPos}
 * reaches for {@code Minecraft.getMinecraft().getRenderPartialTicks()}
 * <i>inside</i> the method — the only way it could get render-time partial ticks
 * from a comparator that passed none. The port passes them in instead. The
 * semantics are identical (the sort key is the <b>render</b> position, not the
 * tick position); what changes is that a common-side class no longer has to
 * reach into a client-only singleton, and that the arithmetic becomes a pure
 * function of fields, which is what makes it headlessly assertable.
 */
public interface IRenderLast
{
    /**
     * @param partialTicks render-frame partial ticks
     * @return the position used to depth sort
     */
    Vector3d getRenderLastPos(float partialTicks);
}
