package mchorse.blockbuster.events;

import java.util.function.Consumer;
import java.util.function.Predicate;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.item.ItemGun;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.blockbuster.utils.NBTUtils;
import mchorse.metamorph.api.MorphHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;

/**
 * The per-player tick loop and the gun's interaction-prevention gates
 * (roadmap P247).
 *
 * <p>1:1 behavioural port of 1.12.2
 * {@code mchorse/blockbuster/events/PlayerHandler.java}, which Forge fired as a
 * {@code PlayerTickEvent} <b>on both sides, once per player, in two phases</b>:</p>
 *
 * <table>
 * <tr><th>Legacy</th><th>Port</th></tr>
 * <tr><td>{@code PlayerTickEvent} START (both sides) — the four
 *         {@link ItemGun} tick statics on the held mainhand gun</td>
 *     <td>{@link #startTick(PlayerEntity)} from
 *         {@link ServerTickEvents#START_SERVER_TICK} over the player list
 *         (here) and from {@code ClientTickEvents.START_CLIENT_TICK} for
 *         {@code mc.player} through {@link #startTick(PlayerEntity, boolean)}
 *         (BlockbusterClient)</td></tr>
 * <tr><td>{@code PlayerTickEvent} END, {@code world.isRemote} +
 *         {@code mc.player == event.player} — {@code updateClient()}</td>
 *     <td>{@link #endTickClient(PlayerEntity, boolean)} from
 *         {@code ClientTickEvents.END_CLIENT_TICK}</td></tr>
 * <tr><td>{@code PlayerTickEvent} END, server — the 100-tick
 *         {@code StructureMorph.checkStructures()} poll</td>
 *     <td><b>Already covered</b> by {@code Blockbuster.registerServerEvents}'
 *         {@code END_SERVER_TICK → StructureMorph.checkStructures()} (P162).
 *         Not re-wired here; see the class note below.</td></tr>
 * <tr><td>{@code LivingAttackEvent} / four {@code PlayerInteractEvent}s at
 *         {@code EventPriority.HIGHEST}</td>
 *     <td>{@link #register()}'s five Fabric interaction callbacks, registered
 *         first in {@code onInitialize} (Fabric runs listeners in registration
 *         order, which is how the port emulates HIGHEST)</td></tr>
 * </table>
 *
 * <h2>Why this class is in {@code src/main} and not {@code src/client}</h2>
 * <p>Legacy registered it from {@code CommonProxy}, so the gun state pump and
 * the interaction gates run on a dedicated server too — a client-only class
 * would silently drop both in multiplayer, and the gun reload countdown is
 * <b>driven by the server</b> ({@code ItemGun.reload} is only ever reached from
 * {@code ServerHandlerGunReloading}). The client-only half of legacy's
 * {@code @SideOnly(Side.CLIENT) updateClient()} therefore becomes the five
 * {@link Runnable} duty seams below, installed by {@code BlockbusterClient}
 * (they stay no-ops on a dedicated server, exactly like the {@code @SideOnly}
 * method body did).</p>
 *
 * <h2>The 100-tick structure poll</h2>
 * <p>Legacy's server END branch polled {@code StructureMorph.checkStructures()}
 * every 100 <i>player</i> ticks off a single shared counter — so with two
 * players online it polled twice as often. The port already polls it every
 * server tick from P162, which is a superset; adding a second, slower poll here
 * would only duplicate work. The legacy field is kept as {@link #timer} for
 * diff-ability and is deliberately unused.</p>
 *
 * <h2>Pause parity (S22 P267)</h2>
 * <p>Forge's client {@code PlayerTickEvent} is posted from
 * {@code EntityPlayer.onUpdate}, which the client only reaches through
 * {@code WorldClient.updateEntities()} — and {@code Minecraft.runTick} guards
 * that call with {@code if (!this.isGamePaused)}
 * ({@code forgeSrc-1.12.2-14.23.5.2799-sources.jar},
 * {@code Minecraft.java:1960-1975}). <b>None of the bodies below ran while
 * 1.12.2 was paused</b>: the gun state machine stood still, the two TEISR
 * caches stopped aging, the skins folder was not rescanned, Snowstorm emitters
 * froze (like every vanilla particle), GIF skins stopped animating, and the
 * morph loop's squid-air countdown and transition fade held their value.
 * Fabric's {@code ClientTickEvents} fire at {@code MinecraftClient.tick()}'s
 * RETURN and {@code render} calls {@code tick()} unconditionally, so the port
 * has to say so: {@link #startTick(PlayerEntity, boolean)} and
 * {@link #endTickClient(PlayerEntity, boolean)} are the gated entry points the
 * client initializer uses. The server pump is untouched — the integrated server
 * is paused by its own loop, and a dedicated server never pauses at all.</p>
 *
 * <h2>Legacy quirks kept</h2>
 * <ul>
 *   <li>START runs the four gun statics in legacy's order —
 *       {@code decreaseReload}, {@code decreaseTime}, {@code checkGunState},
 *       {@code checkGunReload} — and <b>only</b> for a mainhand
 *       {@link ItemGun} (an offhand gun is inert).</li>
 *   <li>The END client body is gated on the local player, so nothing in it
 *       runs on the main menu / while disconnected (GIF animation included).</li>
 *   <li>The interaction gates read the <b>mainhand</b> stack, ignore non-gun
 *       and prop-less stacks, and cancel with the legacy "fail" result.</li>
 * </ul>
 *
 * <h2>Item-pickup suppression during scene playback (P119.3, batch U-G)</h2>
 * <p>Legacy's other half of this class was an ASM transform, not an event
 * subscriber: {@code InventoryPlayerTransformer} wrapped the single
 * {@code this.add(-1, stack)} call inside
 * {@code InventoryPlayer.addItemStackToInventory(ItemStack)} with
 * {@link #beforeItemStackAdd(PlayerInventory)} /
 * {@link #afterItemStackAdd(PlayerInventory)}. That maps onto
 * {@code PlayerInventory.insertStack(ItemStack)} — whose 1.20.4 body is the
 * byte-for-byte same {@code this.insertStack(-1, stack)} one-liner — through
 * {@code mchorse.blockbuster.mixin.PlayerInventoryPickupMixin}. Closed
 * 2026-07-26; before that {@code Scene.getTargetPlaybackPlayers()} had zero
 * callers and a puppeteered player vacuumed up items dropped during a take.</p>
 *
 * <p>The second legacy transformer ({@code EntityItemTransformer} →
 * {@code beforePlayerItemPickUp}) is <b>deliberately not ported</b>: its 2.7.2
 * body is empty, and its ASM anchor was a local introduced by Forge's
 * {@code EntityItemPickupEvent} patch which does not exist in vanilla 1.20.4.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/events/PlayerHandler.java}</p>
 */
public class PlayerHandler
{
    /** No-op duty, the value every client seam holds on a dedicated server. */
    private static final Runnable NOOP = () -> {};

    /** The single handler instance (legacy {@code new PlayerHandler()}). */
    public static final PlayerHandler INSTANCE = new PlayerHandler();

    /* The three legacy Function<GunProps, Boolean> selectors, verbatim (legacy
     * kept them as instance fields; static is equivalent for a singleton and
     * lets the gate be exercised without one). */

    /** Legacy {@code leftHandler}. */
    public static final Predicate<GunProps> leftHandler = (props) -> props.preventLeftClick;

    /** Legacy {@code rightHandler}. */
    public static final Predicate<GunProps> rightHandler = (props) -> props.preventRightClick;

    /** Legacy {@code attackHandler}. */
    public static final Predicate<GunProps> attackHandler = (props) -> props.preventEntityAttack;

    /* ------------------------------------------------------------------ *
     * updateClient() duty seams — legacy @SideOnly(Side.CLIENT) body,     *
     * in legacy order. Installed by BlockbusterClient.installClientDuties. *
     * ------------------------------------------------------------------ */

    /** 1. Model-block TEISR cache aging ({@code TileEntityModelItemStackRenderer.models}). */
    public static Runnable modelCachePump = NOOP;

    /** 2. Gun TEISR cache aging + {@code props.update()} ({@code TileEntityGunItemStackRenderer.models}). */
    public static Runnable gunCachePump = NOOP;

    /** 3. The 30-tick skins-folder rescan body ({@code SkinHandler.checkSkinsFolder()}). */
    public static Runnable skinsRescan = NOOP;

    /** 4. Snowstorm emitter simulation ({@code RenderingHandler.updateEmitters()}). */
    public static Runnable emitterPump = NOOP;

    /** 5. The GIF animation clock ({@code GifTexture.updateTick()}). */
    public static Runnable gifPump = NOOP;

    /**
     * The per-player morph loop. Legacy Metamorph's {@code MorphHandler}
     * subscribed {@code PlayerTickEvent} Phase.END on <b>both</b> sides; the
     * port's {@link MorphHandler#register()} only drives the server list, so
     * this seam is what runs it client-side (squid-air countdown,
     * {@code IMorphing.getAnimation()} transition fade, every ability's
     * {@code update}, and — through {@code Morphing.update} →
     * {@code morph.update} — every morph's own {@code Animation.progress}).
     * Seam so the loop is observable headlessly.
     */
    public static Consumer<PlayerEntity> morphTick = MorphHandler::onPlayerTick;

    /**
     * The four {@link ItemGun} tick statics, in legacy order. Seam so the
     * duty ordering is observable headlessly; production value is
     * {@link ItemGun#tickHeldGun(ItemStack, PlayerEntity)}.
     */
    public static GunPump gunPump = ItemGun::tickHeldGun;

    /** The gun state pump signature (a {@code BiConsumer} with a name). */
    public interface GunPump
    {
        void pump(ItemStack stack, PlayerEntity player);
    }

    /**
     * Legacy {@code PlayerHandler.timer} — the server-side 100-tick
     * {@code StructureMorph.checkStructures()} counter. Kept for diff-ability;
     * the poll itself lives on the P162 server tick (see the class doc).
     */
    private int timer;

    /** Legacy {@code PlayerHandler.skinsTimer} — the 30-tick skins rescan. */
    private int skinsTimer;

    /**
     * Common registration (legacy {@code CommonProxy}'s
     * {@code MinecraftForge.EVENT_BUS.register(new PlayerHandler())}).
     *
     * <p>Call this <b>first</b> in {@code onInitialize}: Fabric dispatches
     * interaction listeners in registration order and stops at the first
     * non-{@code PASS}, which is how the port emulates legacy's
     * {@code EventPriority.HIGHEST} — the gun's prevention gates must get to
     * veto before the recording {@code ActionHandler} logs the interaction.</p>
     */
    public static void register()
    {
        /* Legacy onLivingAttack — GunProps.preventEntityAttack. */
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
            prevented(player, attackHandler) ? ActionResult.FAIL : ActionResult.PASS);

        /* Legacy onPlayerInteract(LeftClickBlock) — GunProps.preventLeftClick. */
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
            prevented(player, leftHandler) ? ActionResult.FAIL : ActionResult.PASS);

        /* Legacy onPlayerInteract(EntityInteract) — GunProps.preventRightClick. */
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
            prevented(player, rightHandler) ? ActionResult.FAIL : ActionResult.PASS);

        /* Legacy onPlayerInteract(RightClickBlock) — GunProps.preventRightClick. */
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
            prevented(player, rightHandler) ? ActionResult.FAIL : ActionResult.PASS);

        /* Legacy onPlayerInteract(RightClickItem) — GunProps.preventRightClick.
         * TypedActionResult carries the stack back, so hand it the untouched
         * one (legacy only set the cancellation result). */
        UseItemCallback.EVENT.register((player, world, hand) ->
            prevented(player, rightHandler)
                ? TypedActionResult.fail(player.getStackInHand(hand))
                : TypedActionResult.pass(player.getStackInHand(hand)));

        /* Legacy PlayerTickEvent START, server side: the four gun tick statics
         * per online player. Forge fired PlayerTickEvent per player; iterating
         * the player list off START_SERVER_TICK is the same set at the same
         * point in the tick. */
        ServerTickEvents.START_SERVER_TICK.register(server ->
        {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
            {
                INSTANCE.startTick(player);
            }
        });
    }

    /**
     * Legacy {@code handle(player, event, handler)}: read the mainhand stack,
     * bail unless it is a gun with props, and report whether the given
     * prevention flag vetoes the interaction.
     *
     * <p>Pure over {@link GunProps} + the flag selector, so the gate is
     * headlessly testable (the callbacks above are all one-liners over it).</p>
     */
    public static boolean prevented(PlayerEntity player, Predicate<GunProps> handler)
    {
        return player != null && prevented(player.getMainHandStack(), handler);
    }

    /** The stack-taking half of {@link #prevented(PlayerEntity, Predicate)}. */
    public static boolean prevented(ItemStack stack, Predicate<GunProps> handler)
    {
        if (stack == null || !(stack.getItem() instanceof ItemGun))
        {
            return false;
        }

        GunProps props = NBTUtils.getGunProps(stack);

        return props != null && handler.test(props);
    }

    /* ------------------------------------------------------------------ *
     * PlayerTickEvent Phase.START                                          *
     * ------------------------------------------------------------------ */

    /**
     * Legacy START phase: pump the gun state machine on the held mainhand gun.
     * Runs on both sides — the server one owns the authoritative countdown and
     * the {@code PacketGunInfo} sync, the client one is the local prediction.
     */
    public void startTick(PlayerEntity player)
    {
        if (player == null)
        {
            return;
        }

        this.startTick(player.getMainHandStack(), player);
    }

    /**
     * The <b>client</b> START entry point (P267): legacy's client
     * {@code PlayerTickEvent} could not fire while the game was paused, so the
     * gun state machine froze with the world. Without this the local prediction
     * kept draining {@code storedShotDelay}/the reload countdown behind the
     * escape menu while the (also paused) server's authoritative copy stood
     * still, and the two only resynced on the next {@code PacketGunInfo}.
     *
     * <p>Separate from {@link #startTick(PlayerEntity)} rather than a parameter
     * on it, because the server pump must stay ungated.</p>
     *
     * @param paused {@code MinecraftClient.isPaused()}.
     */
    public void startTick(PlayerEntity player, boolean paused)
    {
        if (paused)
        {
            return;
        }

        this.startTick(player);
    }

    /**
     * The stack-taking half, split out so the mainhand gate is testable without
     * a live player inventory.
     */
    public void startTick(ItemStack stack, PlayerEntity player)
    {
        if (stack != null && stack.getItem() instanceof ItemGun)
        {
            gunPump.pump(stack, player);
        }
    }

    /* ------------------------------------------------------------------ *
     * PlayerTickEvent Phase.END, client side                               *
     * ------------------------------------------------------------------ */

    /**
     * Legacy END phase for the local player: {@code updateClient()} followed by
     * the (Metamorph-owned, lower Forge priority) morph loop.
     *
     * <p>Legacy reached {@code updateClient} only when
     * {@code Minecraft.getMinecraft().player == event.player}; the caller
     * ({@code BlockbusterClient}) passes {@code mc.player}, so the null check
     * here <b>is</b> that gate — with no player in world nothing in the body
     * runs, which is why GIFs freeze on the main menu on 1.12.2 too.</p>
     */
    public void endTickClient(PlayerEntity player)
    {
        if (player == null)
        {
            return;
        }

        this.updateClient();

        /* Metamorph's own PlayerTickEvent END subscriber, which legacy ran on
         * both sides at default priority (i.e. after this class's HIGHEST). */
        morphTick.accept(player);
    }

    /**
     * The <b>client</b> END entry point (P267) — the pause gate for all six
     * duties above.
     *
     * <p>The gate wraps the whole body because legacy skipped the whole event,
     * and every duty inside it is an accumulator whose state would otherwise
     * drift by exactly the pause duration: the two TEISR caches decrement an
     * eviction timer <i>and</i> call {@code update()} on the cached model/gun
     * props, {@link #skinsTimer} counts to 30, {@code updateEmitters()} steps
     * every Bedrock emitter's simulation, {@code GifTexture.updateTick()} is the
     * GIF clock, and {@code capability.update(player)} runs the squid-air
     * countdown and the morph transition fade. Freezing them behind the escape
     * menu is the 1.12.2 behaviour, and it is also what vanilla does with its
     * own particles ({@code ClientWorld.tickEntities} sits inside
     * {@code MinecraftClient.tick}'s {@code !paused} branch).</p>
     *
     * @param paused {@code MinecraftClient.isPaused()}.
     */
    public void endTickClient(PlayerEntity player, boolean paused)
    {
        if (paused)
        {
            return;
        }

        this.endTickClient(player);
    }

    /**
     * The <b>other</b> half of legacy's client {@code PlayerTickEvent} END:
     * every player in the client world that is not {@code local} (P290).
     *
     * <p>{@link #endTickClient(PlayerEntity)} is the {@code mc.player ==
     * event.player} half — legacy really did gate {@code updateClient()} that
     * way. It does <b>not</b> gate {@link #morphTick}: Forge posted
     * {@code PlayerTickEvent} from {@code EntityPlayer.onUpdate}, which
     * {@code WorldClient.updateEntities()} calls for every
     * {@code EntityOtherPlayerMP} too, so Metamorph's {@code onPlayerTick} ran
     * client-side for <b>all</b> players. The port drove it only for
     * {@code mc.player}, which left every other player's {@code Morphing}
     * frozen on your screen — and frozen is worse than late, because
     * {@code Morphing.setMorph} arms {@code animation = 20} and only
     * {@code Morphing.update} counts it back down. Stuck at 20 with no
     * previous morph, {@code MorphRenderer.shouldReplace}'s
     * {@code previousMorph == null && animation > 10} arm rejects the draw
     * forever: a morphed remote player rendered as the vanilla player, and
     * neither the transition nor the morph's own {@code Animation} ever
     * advanced — "morph animation is off no matter what option".</p>
     *
     * <p>Deliberately a second entry point rather than a loop inside
     * {@link #endTickClient(PlayerEntity)}: the five {@code updateClient}
     * duties are global-per-tick, not per-player, and running them once per
     * player would age every cache N times a tick.</p>
     *
     * @param local the local player, already ticked by
     *        {@link #endTickClient(PlayerEntity)} — skipped here so it is not
     *        ticked twice. Null is legal (no local player yet).
     * @param players every player in the client world; null/empty is legal.
     */
    public void endTickClientOtherPlayers(PlayerEntity local, Iterable<? extends PlayerEntity> players)
    {
        if (players == null)
        {
            return;
        }

        for (PlayerEntity player : players)
        {
            if (player != null && player != local)
            {
                morphTick.accept(player);
            }
        }
    }

    /**
     * Pause-gated {@link #endTickClientOtherPlayers(PlayerEntity, Iterable)} —
     * same gate, and for the same reason, as
     * {@link #endTickClient(PlayerEntity, boolean)}.
     *
     * @param paused {@code MinecraftClient.isPaused()}.
     */
    public void endTickClientOtherPlayers(PlayerEntity local, Iterable<? extends PlayerEntity> players, boolean paused)
    {
        if (paused)
        {
            return;
        }

        this.endTickClientOtherPlayers(local, players);
    }

    /**
     * Legacy {@code @SideOnly(Side.CLIENT) updateClient()} — the five per-tick
     * client duties, in legacy order. The bodies are seams (see the class doc);
     * the <b>order and the 30-tick skins counter live here</b>, which is the
     * point of the consolidation: before P247 these were four independent
     * {@code END_CLIENT_TICK} registrations whose relative order was an
     * accident of the client initializer's line numbers.
     */
    public void updateClient()
    {
        /* 1. model blocks item update */
        modelCachePump.run();

        /* 2. gun itemstack update */
        gunCachePump.run();

        /* 3. skins folder rescan, every 30 ticks */
        if (this.skinsTimer++ >= 30)
        {
            skinsRescan.run();
            this.skinsTimer = 0;
        }

        /* 4. Snowstorm emitters */
        emitterPump.run();

        /* 5. GIF animation clock */
        gifPump.run();
    }

    /** Test seam: the skins counter's current value. */
    public int getSkinsTimer()
    {
        return this.skinsTimer;
    }

    /** Test seam: reset the per-instance counters. */
    public void resetTimers()
    {
        this.skinsTimer = 0;
        this.timer = 0;
    }

    /* ------------------------------------------------------------------ *
     * Item-pickup suppression during scene playback (P119.3)              *
     *                                                                     *
     * Legacy: mchorse/blockbuster/events/PlayerHandler.java:50 (the        *
     * snapshot), :87-99, :105-117, :134-142, :149-152, driven by           *
     * mchorse/blockbuster/core/transformers/InventoryPlayerTransformer.    *
     * ------------------------------------------------------------------ */

    /** The 36 main-inventory slots legacy snapshotted (and only those). */
    public static final int MAIN_INVENTORY_SIZE = 36;

    /**
     * Legacy {@code PlayerHandler.mainInventoryBefore} — <b>one shared static
     * 36-slot snapshot for the whole game</b>, rewritten on every single
     * {@code insertStack} on any player's inventory.
     *
     * <p><b>Quirk, deliberately preserved:</b> because it is shared and not
     * per-player, two players inserting in the same tick race — the second
     * snapshot overwrites the first, so a suppressed player restored after an
     * intervening insert would be restored from the <i>other</i> player's
     * inventory. 1.12.2 behaves exactly this way; the port keeps it (pinned by
     * {@code PlayerHandlerPickupTest}). It is unobservable for the single-scene
     * single-target case the feature exists for.</p>
     *
     * <p><b>Quirk 2:</b> armor and offhand are <b>not</b> snapshotted, so an
     * item that lands in an armor slot is not suppressed.</p>
     */
    private static final DefaultedList<ItemStack> mainInventoryBefore =
        DefaultedList.ofSize(MAIN_INVENTORY_SIZE, ItemStack.EMPTY);

    /**
     * Legacy {@code beforeItemStackAdd(InventoryPlayer)} — called from the
     * {@code PlayerInventory.insertStack(ItemStack)} mixin at HEAD. Copies the
     * 36 main slots into {@link #mainInventoryBefore}.
     *
     * <p>The {@code .copy()} is load-bearing: {@code insertStack} mutates the
     * existing stacks in place when it merges into a partial stack, so a
     * shallow reference snapshot would compare a stack against itself and
     * suppress nothing.</p>
     */
    public static void beforeItemStackAdd(PlayerInventory inventory)
    {
        if (inventory == null)
        {
            return;
        }

        snapshot(inventory.main, mainInventoryBefore);
    }

    /**
     * Legacy {@code afterItemStackAdd(InventoryPlayer)} — called from the same
     * mixin at RETURN. A single delegation, exactly like 1.12.2.
     */
    public static void afterItemStackAdd(PlayerInventory inventory)
    {
        preventItemPickUpScenePlayback(inventory);
    }

    /**
     * The pure snapshot half of {@link #beforeItemStackAdd(PlayerInventory)}.
     *
     * <p>Total: legacy indexed {@code before} with the live list's length and
     * would have thrown on a mismatch; the port copies the overlap.</p>
     */
    public static void snapshot(DefaultedList<ItemStack> main, DefaultedList<ItemStack> before)
    {
        int size = Math.min(main.size(), before.size());

        for (int i = 0; i < size; i++)
        {
            before.set(i, main.get(i).copy());
        }
    }

    /**
     * Legacy {@code preventItemPickUpScenePlayback(InventoryPlayer)}: is this
     * inventory owned by a player some live scene has chosen for first-person
     * playback? If so, undo whatever the insert just did.
     *
     * <p><b>Deviation (equivalent):</b> legacy walked every scene's
     * {@code getTargetPlaybackPlayers()} looking for a player whose
     * {@code inventory} field <i>is</i> this one. The port asks the same
     * question from the other end — {@code inventory.player} (public final in
     * yarn) and {@link #isSuppressed(PlayerEntity)} — which is the same set for
     * every reachable case (a target player's {@code getInventory()} always
     * returns its own inventory) and keeps the scene-membership rule as one
     * testable predicate instead of two copies of the walk. The
     * identity check is retained as the legacy guard.</p>
     */
    private static void preventItemPickUpScenePlayback(PlayerInventory inventory)
    {
        if (inventory == null)
        {
            return;
        }

        PlayerEntity player = inventory.player;

        if (player != null && player.getInventory() == inventory && isSuppressed(player))
        {
            preventItemPickUp(player);
        }
    }

    /**
     * The suppression rule: <b>reference-identity membership in some live
     * scene's target-playback player list</b>. Not a class check — actors
     * ({@code EntityActor}) are not players and can never reach the insert
     * seam, and scene fake players never enter {@code targetPlayers} either.
     * Legacy had <b>no config key</b> for this; the transform was registered
     * unconditionally and scene membership is the only gate.
     */
    public static boolean isSuppressed(PlayerEntity player)
    {
        if (player == null)
        {
            return false;
        }

        for (Scene scene : CommonProxy.scenes.getScenes().values())
        {
            if (scene.isPlayerTargetPlayback(player))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Legacy {@code preventItemPickUp(EntityPlayer)} — reset the main
     * inventory to what it was before the item was added.
     *
     * <p>This <b>absorbs</b> the item: it is neither kept nor re-dropped into
     * the world. Parity, not a bug.</p>
     */
    public static void preventItemPickUp(PlayerEntity player)
    {
        if (player == null)
        {
            return;
        }

        preventItemPickUp(player.getInventory().main, mainInventoryBefore);
    }

    /**
     * The pure 36-slot diff-and-restore. Any slot that is not
     * {@link ItemStack#areEqual} (yarn's {@code areItemStacksEqual}: same
     * count <i>and</i> same item+NBT) to its snapshot is written back.
     */
    public static void preventItemPickUp(DefaultedList<ItemStack> main, DefaultedList<ItemStack> before)
    {
        int size = Math.min(main.size(), before.size());

        for (int i = 0; i < size; i++)
        {
            ItemStack itemStackNow = main.get(i);
            ItemStack itemStackBefore = before.get(i);

            if (!ItemStack.areEqual(itemStackNow, itemStackBefore))
            {
                main.set(i, itemStackBefore);
            }
        }
    }

    /** Test seam: the shared snapshot (the quirk under test). */
    public static DefaultedList<ItemStack> getMainInventoryBefore()
    {
        return mainInventoryBefore;
    }
}
