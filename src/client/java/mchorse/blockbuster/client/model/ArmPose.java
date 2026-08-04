package mchorse.blockbuster.client.model;

/**
 * Arm posture (roadmap P82).
 *
 * <p>Port of the four {@code net.minecraft.client.model.ModelBiped.ArmPose}
 * values that Blockbuster 2.7.2's {@code ModelCustom.setHands} produced and
 * {@code setRotationAngles} consumed. 1.20.4's vanilla {@code BipedEntityModel
 * .ArmPose} is a richer, non-matching enum (it drives vanilla arm layers, not
 * our custom limb graph), so we keep our own four-value enum with the exact
 * legacy semantics rather than mapping onto the vanilla one.</p>
 *
 * <ul>
 *   <li>{@link #EMPTY} — no held item / non-living entity;</li>
 *   <li>{@link #ITEM} — a held item with no special use action;</li>
 *   <li>{@link #BLOCK} — an item being actively used with
 *       {@code UseAction.BLOCK} (shield-style block);</li>
 *   <li>{@link #BOW_AND_ARROW} — a bow/gun aiming pose (main hand only).</li>
 * </ul>
 */
public enum ArmPose
{
    EMPTY, ITEM, BLOCK, BOW_AND_ARROW;
}
