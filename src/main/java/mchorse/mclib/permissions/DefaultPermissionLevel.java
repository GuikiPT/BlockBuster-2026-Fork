package mchorse.mclib.permissions;

/**
 * Shim of Forge 1.12.2's
 * {@code net.minecraftforge.server.permission.DefaultPermissionLevel}
 * (minimal P21 subset landed with S2/P26 — {@code PermissionCategory}
 * serializes this enum's <b>ordinals</b> over the wire, so names AND order
 * must match Forge exactly: ALL, OP, NONE).
 */
public enum DefaultPermissionLevel
{
    ALL,
    OP,
    NONE;
}
