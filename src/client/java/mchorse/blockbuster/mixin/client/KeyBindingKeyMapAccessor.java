package mchorse.blockbuster.mixin.client;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Accessor over {@code KeyBinding.KEY_TO_BINDINGS} ({@code Map<InputUtil.Key,
 * KeyBinding>}, verified with javap against the loom-cache named jar).
 *
 * <p>The map is what turns a physical key event into a pressed binding —
 * {@code KeyBinding.setKeyPressed} and {@code onKeyPressed} both resolve
 * through it, and {@code Mouse.onMouseButton} has no other route to a binding.
 * It holds <b>one binding per key</b>: {@code updateKeysByCode} clears it and
 * re-puts every registered binding under its bound key, so when two bindings
 * share a key the last one written wins and the other never sees a press at
 * all. Which one wins is decided by {@code KEYS_BY_ID}'s {@link
 * java.util.HashMap} iteration order — stable for a given set of bindings, but
 * not something a mod can choose.</p>
 *
 * <p>Blockbuster's gun-shoot bind defaults to mouse left, the same key as
 * vanilla's attack bind, and legacy 1.12.2 expected both to answer. Reasserting
 * the vanilla binding's ownership is the only part that needs the map — see
 * {@code GunShootHandler.yieldAttackKeySlot}, the single consumer.</p>
 */
@Mixin(KeyBinding.class)
public interface KeyBindingKeyMapAccessor
{
    @Accessor("KEY_TO_BINDINGS")
    static Map<InputUtil.Key, KeyBinding> getKeyToBindings()
    {
        throw new AssertionError();
    }
}
