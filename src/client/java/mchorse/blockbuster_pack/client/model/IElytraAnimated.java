package mchorse.blockbuster_pack.client.model;

/**
 * Elytra smoothing state carrier (roadmap P76).
 *
 * <p>Legacy {@code ModelElytra} smoothed the wing rotation over frames by lerping
 * {@code rotateElytraX/Y/Z} toward the target angle at {@code 0.1} per frame — the
 * vanilla {@code AbstractClientPlayer} carried those fields, and Blockbuster's
 * {@code EntityActor} mirrored them so an actor's elytra animates identically.</p>
 *
 * <p>{@code EntityActor} implements this in S8; {@link ModelElytra} reads it via
 * {@code instanceof} so P76 stays buildable/testable before the actor lands.</p>
 */
public interface IElytraAnimated
{
    float getRotateElytraX();

    float getRotateElytraY();

    float getRotateElytraZ();

    void setRotateElytraX(float value);

    void setRotateElytraY(float value);

    void setRotateElytraZ(float value);
}
