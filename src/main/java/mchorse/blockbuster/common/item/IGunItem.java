package mchorse.blockbuster.common.item;

/**
 * Marker interface identifying an item as a Blockbuster gun.
 *
 * <p>Legacy {@code NBTUtils.saveGunProps}/{@code getGunProps} test the item
 * with {@code stack.getItem() instanceof ItemGun}. The concrete {@code ItemGun}
 * behaviour port lands in a parallel branch (P194), so P193 gates the
 * {@code "Gun"} stack-tag contract on this marker instead. The real
 * {@code ItemGun} must implement {@link IGunItem} when it lands.</p>
 */
public interface IGunItem
{
}
