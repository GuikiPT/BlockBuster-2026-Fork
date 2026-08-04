package mchorse.blockbuster.client.particles.components.appearance;

/**
 * Camera facing mode
 *
 * <p>Ported verbatim from the 1.12.2 legacy engine
 * ({@code mchorse.blockbuster.client.particles.components.appearance.CameraFacing}).
 * The eleven string ids are a JSON disk contract; {@code fromString} falls back to
 * {@link #ROTATE_XYZ} on any unknown id (total reader). The {@code isLookAt} /
 * {@code isDirection} flags gate parsing of the {@code direction} JSON block in
 * {@link BedrockComponentAppearanceBillboard}. The {@code direction_*} and
 * {@code lookat_direction} modes are Blockbuster additions beyond the original
 * five-mode Bedrock implementation.</p>
 */
public enum CameraFacing
{
    ROTATE_XYZ("rotate_xyz"), ROTATE_Y("rotate_y"),
    LOOKAT_XYZ("lookat_xyz", true, false), LOOKAT_Y("lookat_y", true, false), LOOKAT_DIRECTION("lookat_direction", true, true),
    DIRECTION_X("direction_x", false, true), DIRECTION_Y("direction_y", false, true), DIRECTION_Z("direction_z", false, true),
    EMITTER_XY("emitter_transform_xy"), EMITTER_XZ("emitter_transform_xz"), EMITTER_YZ("emitter_transform_yz");

    public final String id;
    public final boolean isLookAt;
    public final boolean isDirection;

    public static CameraFacing fromString(String string)
    {
        for (CameraFacing facing : values())
        {
            if (facing.id.equals(string))
            {
                return facing;
            }
        }

        return ROTATE_XYZ;
    }

    private CameraFacing(String id, boolean isLookAt, boolean isDirection)
    {
        this.id = id;
        this.isLookAt = isLookAt;
        this.isDirection = isDirection;
    }

    private CameraFacing(String id)
    {
        this(id, false, false);
    }
}
