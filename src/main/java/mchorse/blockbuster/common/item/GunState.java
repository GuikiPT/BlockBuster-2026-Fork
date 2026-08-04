package mchorse.blockbuster.common.item;

/**
 * Gun state machine states.
 *
 * <p>Legacy source: this enum is declared inside
 * {@code mchorse.blockbuster.common.item.ItemGun} in Blockbuster 2.7.2. The
 * P193 branch lifts it into its own top-level type so {@link
 * mchorse.blockbuster.common.GunProps} can persist the {@code "State"} tag
 * without depending on the (parallel-branch) {@code ItemGun} behaviour port
 * (P194).</p>
 *
 * <p><b>Ordinal contract (format-bearing).</b> {@code GunProps.toNBT} writes
 * {@code tag.setInteger("State", state.ordinal())} and {@code fromNBT} reads it
 * back through {@code values()[ordinal]}. The declaration order below is a
 * disk/wire contract — reordering it silently corrupts every stored gun stack.
 * Do not reorder: {@code READY_TO_SHOOT}=0, {@code RELOADING}=1,
 * {@code NEED_TO_BE_RELOAD}=2.</p>
 */
public enum GunState
{
    READY_TO_SHOOT,
    RELOADING,
    NEED_TO_BE_RELOAD
}
