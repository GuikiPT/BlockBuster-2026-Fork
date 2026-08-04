package mchorse.blockbuster.common;

import mchorse.mclib.permissions.PermissionCategory;

/**
 * Port of Blockbuster 2.7.2's {@code BlockbusterPermissions} (P21) — holders
 * for Blockbuster's own two permission leaves, assigned during permission
 * registration ({@code Blockbuster.onPermissionRegister}) exactly like
 * 1.12.2: {@code blockbuster.model_block.edit} and
 * {@code blockbuster.scenes.open}.
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/common/BlockbusterPermissions.java</p>
 */
public class BlockbusterPermissions
{
    public static PermissionCategory editModelBlock;
    public static PermissionCategory openScene;
}
