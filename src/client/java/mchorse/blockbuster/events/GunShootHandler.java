package mchorse.blockbuster.events;

import mchorse.blockbuster.client.KeyboardHandler;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.item.GunState;
import mchorse.blockbuster.common.item.ItemGun;
import mchorse.blockbuster.mixin.client.KeyBindingKeyMapAccessor;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.guns.PacketGunInteract;
import mchorse.blockbuster.network.common.guns.PacketGunReloading;
import mchorse.blockbuster.utils.NBTUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * Client gun input (roadmap P194 sender half, wired by S22 P241/P244).
 *
 * <p>1:1 port of 1.12.2 {@code events/GunShootHandler} — the client-tick
 * handler that turns the two gun keybinds into packets. Until P244 the class
 * did not exist at all, which is why {@link PacketGunReloading} had no
 * production send site (and {@link PacketGunInteract}'s only send site was the
 * server's own echo): <b>a gun could not be fired or reloaded from the
 * client.</b></p>
 *
 * <p>Legacy shape kept exactly:</p>
 * <ul>
 *   <li>{@code ClientTickEvent} END, highest priority → {@link
 *       ClientTickEvents#END_CLIENT_TICK} (Fabric has no priorities; this
 *       handler only reads keybinds and sends packets, so ordering against
 *       other END listeners is not observable);</li>
 *   <li>everything is gated on the <b>mainhand</b> stack being an
 *       {@link ItemGun} — offhand guns are inert, as on 1.12.2;</li>
 *   <li>{@link #handleShootKey} is edge-triggered through {@code canBeShotPress}
 *       and only re-arms while the key stays held when the gun's
 *       {@code shootWhenHeld} is set (auto-fire);</li>
 *   <li>{@link #handleReloading} is edge-triggered through
 *       {@code canBeReloaded} and requires state {@link GunState#READY_TO_SHOOT};</li>
 *   <li>{@link #blockLeftClick} forces the vanilla attack cooldown to
 *       {@code 10000} while the shoot bind conflicts with the attack bind, so
 *       the same physical click cannot also punch/mine. Legacy did this by
 *       reflecting {@code Minecraft.leftClickCounter}; the port widens
 *       {@code MinecraftClient.attackCooldown}, which is the same counter with
 *       the same reset-on-release semantics.</li>
 * </ul>
 *
 * <p>Note that {@code KeyBinding.isPressed()} — not {@code wasPressed()} — is
 * the analogue of legacy {@code isKeyDown()}: this handler needs the held state,
 * and it does its own edge detection exactly like legacy did.</p>
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/events/GunShootHandler.java</p>
 */
public class GunShootHandler
{
    /** Legacy magic number for the blocked vanilla left click. */
    public static final int BLOCKED_ATTACK_COOLDOWN = 10000;

    private static GunShootHandler instance;

    private boolean canBeShotPress = true;
    private boolean canBeReloaded = true;

    /**
     * Install the client-tick handler (idempotent). One call site:
     * {@code mchorse.blockbuster.client.UnsentPacketWiring#install()}.
     */
    public static void install()
    {
        if (instance != null)
        {
            return;
        }

        instance = new GunShootHandler();

        ClientTickEvents.END_CLIENT_TICK.register(instance::onTick);
    }

    /** The installed handler, or {@code null} before {@link #install()}. */
    public static GunShootHandler get()
    {
        return instance;
    }

    public void onTick(MinecraftClient mc)
    {
        if (mc == null || mc.player == null)
        {
            return;
        }

        /* Unconditional, and before the gun gate: the slot has to belong to the
         * attack bind whether or not a gun is held, or punching and mining stay
         * dead everywhere. */
        yieldAttackKeySlot(mc);

        ItemStack stack = mc.player.getMainHandStack();

        if (stack.getItem() instanceof ItemGun)
        {
            this.blockLeftClick(mc);
            this.handleShootKey(mc, stack);
            this.handleReloading(mc, stack);
        }
    }

    private void handleShootKey(MinecraftClient mc, ItemStack stack)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return;
        }

        if (shootDown(mc))
        {
            if (this.canBeShotPress && props.storedShotDelay == 0 && props.state == GunState.READY_TO_SHOOT)
            {
                Dispatcher.sendToServer(new PacketGunInteract(stack, mc.player.getId()));

                this.canBeShotPress = false;

                return;
            }

            if (props.storedShotDelay == 0 && props.shootWhenHeld)
            {
                this.canBeShotPress = true;
            }
        }
        else
        {
            this.canBeShotPress = true;
        }
    }

    private void handleReloading(MinecraftClient mc, ItemStack stack)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return;
        }

        if (down(KeyboardHandler.gunReload) && this.canBeReloaded && props.state == GunState.READY_TO_SHOOT)
        {
            Dispatcher.sendToServer(new PacketGunReloading(stack, mc.player.getId()));

            this.canBeReloaded = false;
        }
        else
        {
            this.canBeReloaded = true;
        }
    }

    private void blockLeftClick(MinecraftClient mc)
    {
        if (conflicts(KeyboardHandler.gunShoot, mc.options == null ? null : mc.options.attackKey))
        {
            mc.attackCooldown = BLOCKED_ATTACK_COOLDOWN;
        }
    }

    /**
     * Legacy {@code KeyboardHandler.gunShoot.conflicts(gameSettings.keyBindAttack)}.
     * Extracted (and null-tolerant) so the decision is testable headlessly.
     *
     * <p>{@link KeyBinding#equals(KeyBinding)} is the bound-key overload, not
     * {@code Object.equals} — two distinct bindings on the same key are equal
     * here, which is exactly the question being asked.</p>
     */
    public static boolean conflicts(KeyBinding shoot, KeyBinding attack)
    {
        return shoot != null && attack != null && shoot.equals(attack);
    }

    /**
     * Give vanilla's attack bind back its entry in
     * {@code KeyBinding.KEY_TO_BINDINGS} when the shoot bind has taken it.
     *
     * <p>That map holds <b>one binding per key</b> and is the only route a
     * mouse button has to a binding ({@code Mouse.onMouseButton} calls nothing
     * but {@code KeyBinding.setKeyPressed} / {@code onKeyPressed}, both of which
     * resolve through it). {@code updateKeysByCode} rebuilds it by iterating
     * {@code KEYS_BY_ID}, so with {@code gunShoot} and {@code attackKey} both on
     * mouse left, whichever lands last owns the button and the other is never
     * pressed at all — which is why shooting appeared to "overshadow" hitting.
     * Note the loser is starved <i>globally</i>, not only while a gun is held.
     *
     * <p>1.12.2 had no such starvation, so both binds answered and legacy could
     * express "the gun ate this click" purely through {@link #blockLeftClick}.
     * Restoring that means the two binds must not compete for the slot at all,
     * and vanilla's has to be the one holding it: the attack bind needs both the
     * held state <i>and</i> the press count ({@code MinecraftClient.doAttack} is
     * driven by {@code wasPressed()}, whose counter has no public setter),
     * whereas {@link #handleShootKey} only ever needs the held state and does
     * its own edge detection through {@code canBeShotPress}. So vanilla keeps
     * the binding and the gun reads the held state off it — see
     * {@link #shootDown}.
     *
     * <p>Runs every client tick because the map is rebuilt whenever options load
     * or a bind changes in the controls screen; one tick of catch-up costs a
     * hash lookup and a reference compare.</p>
     */
    private static void yieldAttackKeySlot(MinecraftClient mc)
    {
        KeyBinding shoot = KeyboardHandler.gunShoot;
        KeyBinding attack = mc.options == null ? null : mc.options.attackKey;

        if (!conflicts(shoot, attack))
        {
            return;
        }

        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(attack);

        /* Both unbound also compares equal above; that shared slot is inert
         * (nothing ever presses the unknown key), so leave it alone. */
        if (key == null || key.equals(InputUtil.UNKNOWN_KEY))
        {
            return;
        }

        Map<InputUtil.Key, KeyBinding> bindings = KeyBindingKeyMapAccessor.getKeyToBindings();

        if (bindings.get(key) == shoot)
        {
            bindings.put(key, attack);
        }
    }

    /**
     * Held state of the shoot bind: read off the attack bind while the two share
     * a key, since {@link #yieldAttackKeySlot} has deliberately left that one
     * holding the button. Same physical key either way, so the OR just covers
     * the tick before the slot is reasserted.
     */
    private static boolean shootDown(MinecraftClient mc)
    {
        KeyBinding shoot = KeyboardHandler.gunShoot;
        KeyBinding attack = mc.options == null ? null : mc.options.attackKey;

        return conflicts(shoot, attack) ? down(shoot) || down(attack) : down(shoot);
    }

    /** Legacy {@code isKeyDown()}; null-tolerant for pre-{@code register()} state. */
    private static boolean down(KeyBinding binding)
    {
        return binding != null && binding.isPressed();
    }
}
