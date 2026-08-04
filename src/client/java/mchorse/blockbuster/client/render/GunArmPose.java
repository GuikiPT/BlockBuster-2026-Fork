package mchorse.blockbuster.client.render;

/**
 * Pure port of the arm-pose predicate from the legacy
 * {@code RenderingHandler.changePlayerHand} (P197): when the player holds a gun,
 * force the {@code BOW_AND_ARROW} arm pose either always (when
 * {@code alwaysArmsShootingPose}) or while the shoot key is held (when
 * {@code enableArmsShootingPose}).
 *
 * <p>The ASM {@code RenderPlayerTransformer} that appended
 * {@code changePlayerHand} before {@code setModelVisibilities}'s RETURN becomes
 * the {@code PlayerEntityRendererArmPoseMixin} on
 * {@code PlayerEntityRenderer.getArmPose}; this class holds the pure decision so
 * the flag/keystate matrix is headlessly testable.</p>
 *
 * Legacy source of truth:
 * {@code blockbuster-1.12/.../client/RenderingHandler.java} (changePlayerHand).
 */
public final class GunArmPose
{
    private GunArmPose()
    {}

    /**
     * Whether the shooting arm pose ({@code BOW_AND_ARROW}) should replace the
     * vanilla pose for a gun held in hand.
     *
     * @param alwaysArmsShootingPose {@code GunProps.alwaysArmsShootingPose}
     * @param enableArmsShootingPose {@code GunProps.enableArmsShootingPose}
     * @param shootKeyDown           whether {@code KeyboardHandler.gunShoot} is held
     */
    public static boolean shouldUseShootingPose(boolean alwaysArmsShootingPose, boolean enableArmsShootingPose, boolean shootKeyDown)
    {
        if (alwaysArmsShootingPose)
        {
            return true;
        }

        return enableArmsShootingPose && shootKeyDown;
    }
}
