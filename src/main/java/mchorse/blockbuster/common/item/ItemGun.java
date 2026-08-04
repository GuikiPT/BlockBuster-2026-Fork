package mchorse.blockbuster.common.item;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.common.entity.EntityGunProjectile;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.guns.PacketGunInfo;
import mchorse.blockbuster.network.common.guns.PacketGunShot;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.ShootGunAction;
import mchorse.blockbuster.utils.NBTUtils;
import mchorse.blockbuster_pack.morphs.SequencerMorph;
import mchorse.mclib.utils.Interpolation;
import mchorse.mclib.utils.OpHelper;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * BB gun item (P194 — full fire/reload/durability state machine over P193's
 * {@link GunProps}).
 *
 * <p>Faithful port of 1.12.2 {@code common/item/ItemGun.java}. The pure shooting
 * math ({@link #getRandom}, {@link #rotate}, {@link #setThrowableHeading}) is
 * kept as static helpers so it can be exercised headlessly; the per-tick server
 * state machine ({@link #decreaseTime}/{@link #decreaseReload}/{@link
 * #checkGunState}/{@link #checkGunReload}) and the shoot path mirror the legacy
 * ordering (including its quirks — see the S17 plan).</p>
 *
 * <h2>Port deltas / seams</h2>
 * <ul>
 *   <li>{@code GunState} was lifted to a top-level type in P193; this class uses
 *       it.</li>
 *   <li>The sequencer projectile-selection branch was SEAM'd while
 *       {@code SequencerMorph} (S14 Blockbuster morph pack) was missing; it is
 *       restored now that P160 has landed.</li>
 *   <li>{@code EntityActor.EntityFakePlayer} shooters are unwrapped to their
 *       backing actor for the shot broadcast id, exactly as legacy did.</li>
 *   <li>The re-equip-animation cache migration into
 *       {@code TileEntityGunItemStackRenderer.models} is a P197 client concern;
 *       {@link #allowNbtUpdateAnimation} here ports only the morph-equality
 *       predicate.</li>
 *   <li>Commands run through {@link CommandSink}, a seam over
 *       {@code CommandManager.executeWithPrefix} (injectable for tests).</li>
 * </ul>
 */
public class ItemGun extends Item implements IGunItem
{
    /* Legacy lang-key parity: the converted lang JSONs keep 1.12.2's keys
     * 1:1 (LangConversionTest), so point the translation key at the legacy id. */
    @Override
    public String getTranslationKey()
    {
        return "item.blockbuster.gun.name";
    }

    /**
     * Command-execution seam (legacy
     * {@code player.getServer().commandManager.executeCommand(sender, cmd)}).
     * Production installs the real {@code CommandManager} path; tests inject a
     * capturing sink.
     */
    public interface CommandSink
    {
        void execute(Object sender, String command);
    }

    /**
     * Client-installed seam (P197): migrate the cached gun render entry from an
     * old stack key to a new one when a re-equip keeps the same models. The
     * render cache is client-only, so {@code BlockbusterClient} wires this to
     * {@code TileEntityGunItemStackRenderer.migrate}; null on the server.
     */
    public static BiConsumer<ItemStack, ItemStack> cacheMigrator;

    public static CommandSink commandSink = (sender, command) ->
    {
        if (command == null || command.isEmpty())
        {
            return;
        }

        MinecraftServer server = null;
        ServerCommandSource source = null;

        if (sender instanceof ServerPlayerEntity)
        {
            server = ((ServerPlayerEntity) sender).getServer();
            source = ((ServerPlayerEntity) sender).getCommandSource();
        }
        else if (sender instanceof Entity)
        {
            server = ((Entity) sender).getServer();
            source = ((Entity) sender).getCommandSource();
        }

        if (server != null && source != null)
        {
            server.getCommandManager().executeWithPrefix(source, command);
        }
    };

    /**
     * Legacy {@code getRandom(a, b)}: reversed-argument formula
     * {@code Math.random() * (a - b) + b} — still spans the range but with the
     * arguments swapped from the conventional form. Preserved verbatim.
     */
    public static float getRandom(float a, float b)
    {
        return (float) Math.random() * (a - b) + b;
    }

    /**
     * Which morph bullet {@code i} of a burst actually carries.
     *
     * <p>Legacy inlined this in the shoot loop: with {@code props.sequencer} set
     * and a {@link SequencerMorph} projectile, each bullet picks one of the
     * sequencer's entries — {@code getRandom()} when {@code props.random}, else
     * cycling with the bullet index. Anything else fires the base morph. The
     * empty-entry-list guard is ours; legacy's {@code i % seq.morphs.size()}
     * divided by zero.</p>
     */
    public static AbstractMorph pickProjectileMorph(GunProps props, AbstractMorph base, int i)
    {
        if (props.sequencer && base instanceof SequencerMorph)
        {
            SequencerMorph seq = (SequencerMorph) base;

            if (!seq.morphs.isEmpty())
            {
                return props.random ? seq.getRandom() : seq.get(i % seq.morphs.size());
            }
        }

        return base;
    }

    public ItemGun()
    {
        super(new Item.Settings().maxCount(1));
    }

    @Override
    public UseAction getUseAction(ItemStack stack)
    {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack)
    {
        return 0;
    }

    /* ---------------------------------------------------------------------
     * Per-tick state machine (driven from PlayerHandler, START phase)
     * --------------------------------------------------------------------- */

    /**
     * The four per-tick statics on a held gun, in legacy
     * {@code PlayerHandler.onPlayerTick} (Phase.START) order.
     *
     * <p>This is the single production entry point for the pump — legacy ran
     * this exact sequence on <b>both</b> sides, so both
     * {@code ServerTickEvents.START_SERVER_TICK} (over the player list) and
     * {@code ClientTickEvents.START_CLIENT_TICK} (local player) come through
     * here. Until roadmap P247 nothing called any of the four and a reload
     * parked the gun in {@link GunState#RELOADING} forever.</p>
     *
     * <p>The caller has already established that {@code stack} is a gun; the
     * order below is load-bearing (the reload countdown must clear before
     * {@code checkGunReload} can start another one in the same tick).</p>
     */
    public static void tickHeldGun(ItemStack stack, PlayerEntity player)
    {
        decreaseReload(stack, player);
        decreaseTime(stack, player);
        checkGunState(stack, player);
        checkGunReload(stack, player);
    }

    public static void decreaseTime(ItemStack stack, PlayerEntity player)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return;
        }

        if (props.storedShotDelay > 0)
        {
            props.storedShotDelay = Math.max(props.storedShotDelay - 1, 0);

            NBTUtils.saveGunProps(stack, props.toNBT());

            syncToSelfAndTracked(player, props);
        }
    }

    private void resetTime(ItemStack stack, PlayerEntity player)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return;
        }

        props.storedShotDelay = props.shotDelay;

        NBTUtils.saveGunProps(stack, props.toNBT());
    }

    public static void decreaseReload(ItemStack stack, PlayerEntity player)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return;
        }

        if (props.state == GunState.RELOADING)
        {
            props.storedReloadingTime = props.storedReloadingTime - 1;

            if (props.storedReloadingTime <= 0)
            {
                props.storedReloadingTime = 0;
                props.state = GunState.READY_TO_SHOOT;
            }

            NBTUtils.saveGunProps(stack, props.toNBT());

            syncToSelfAndTracked(player, props);
        }
    }

    public static void checkGunState(ItemStack stack, PlayerEntity player)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return;
        }

        if (props.storedAmmo <= 0 && props.useReloading && props.state == GunState.READY_TO_SHOOT)
        {
            props.state = GunState.NEED_TO_BE_RELOAD;
        }

        /* Legacy: early-return before save/sync when there is no ammo stack —
         * fixes the gun name flashing on screen when shooting without ammo. */
        if (props.ammoStack.isEmpty())
        {
            return;
        }

        /* Legacy saved into the *live* mainhand stack, not the passed one.
         * `player == null` is a headless-test tolerance only (P247's pump
         * test); production always ticks a real player's held gun. */
        NBTUtils.saveGunProps(player == null ? stack : player.getMainHandStack(), props.toNBT());

        syncToSelfAndTracked(player, props);
    }

    public static void checkGunReload(ItemStack stack, PlayerEntity player)
    {
        if (player != null && !player.getWorld().isClient)
        {
            GunProps props = NBTUtils.getGunProps(stack);

            if (props != null && props.state == GunState.NEED_TO_BE_RELOAD && props.storedShotDelay == 0)
            {
                ItemGun gun = (ItemGun) stack.getItem();

                gun.reload(player, stack);
            }
        }
    }

    private static void syncToSelfAndTracked(PlayerEntity player, GunProps props)
    {
        /* Operand order flipped against legacy (`!isRemote && instanceof`):
         * a ServerPlayerEntity always carries a server world, so the test is
         * equivalent, and short-circuiting on the instanceof keeps the pump
         * runnable with a null/world-less player in headless tests. */
        if (player instanceof ServerPlayerEntity && !player.getWorld().isClient)
        {
            Dispatcher.sendTo(new PacketGunInfo(props.toNBT(), player.getId()), (ServerPlayerEntity) player);
            Dispatcher.sendToTracked(player, new PacketGunInfo(props.toNBT(), player.getId()));
        }
    }

    /* ---------------------------------------------------------------------
     * Durability / ammo
     * --------------------------------------------------------------------- */

    public void decreaseDurability(GunProps props, ItemStack stack, PlayerEntity player)
    {
        if (props == null)
        {
            return;
        }

        if (props.durability != 0)
        {
            int val = props.storedDurability - 1;

            if (val <= 0)
            {
                commandSink.execute(player, props.destroyCommand);

                player.getMainHandStack().setCount(0);
            }

            props.storedDurability = val;

            if (NBTUtils.saveGunProps(stack, props.toNBT()))
            {
                synchronize(player, props.toNBT());
                syncToSelfAndTracked(player, props);
            }
        }
    }

    private void synchronize(PlayerEntity player, NbtCompound tag)
    {
        if (!(player instanceof ServerPlayerEntity) || !OpHelper.isPlayerOp((ServerPlayerEntity) player))
        {
            return;
        }

        ItemStack stack = player.getMainHandStack();

        if (NBTUtils.saveGunProps(stack, tag))
        {
            PacketGunInfo packet = new PacketGunInfo(tag, player.getId());

            Dispatcher.sendTo(packet, (ServerPlayerEntity) player);
            Dispatcher.sendToTracked(player, packet);
        }
    }

    private boolean consumeInnerAmmo(ItemStack stack, PlayerEntity player)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return false;
        }

        int ammo = props.storedAmmo;

        if (ammo <= 0)
        {
            if (props.useReloading)
            {
                return false;
            }
            else
            {
                props.storedAmmo = props.ammo;

                NBTUtils.saveGunProps(player.getMainHandStack(), props.toNBT());

                if (!player.getAbilities().creativeMode && !props.ammoStack.isEmpty())
                {
                    return this.consumeAmmoStack(player, props.ammoStack, props.ammoStack.getCount()) >= 0;
                }
                else
                {
                    return true;
                }
            }
        }

        this.consumeAmmo(stack, player);

        return true;
    }

    private void consumeAmmo(ItemStack stack, PlayerEntity player)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return;
        }

        props.storedAmmo -= 1;

        NBTUtils.saveGunProps(player.getMainHandStack(), props.toNBT());
    }

    /**
     * Legacy {@code consumeAmmoStack} — 1.12.2's
     * {@code inventory.clearMatchingItems(item, -1, count, tag)}: remove up to
     * {@code count} items matching the ammo's {@code Item} + full NBT, returning
     * the number removed. The pre-flattening {@code -1} metadata wildcard is
     * moot post-flattening (documented delta).
     */
    public int consumeAmmoStack(PlayerEntity player, ItemStack ammo, int count)
    {
        return clearMatchingItems(player, ammo, count);
    }

    /** NBT-exact inventory removal (see {@link #consumeAmmoStack}). */
    public static int clearMatchingItems(PlayerEntity player, ItemStack ammo, int count)
    {
        int removed = 0;
        List<ItemStack> main = player.getInventory().main;

        for (int i = 0; i < main.size() && removed < count; i++)
        {
            ItemStack stack = main.get(i);

            if (stack.isEmpty() || stack.getItem() != ammo.getItem())
            {
                continue;
            }

            if (!Objects.equals(stack.getNbt(), ammo.getNbt()))
            {
                continue;
            }

            int take = Math.min(stack.getCount(), count - removed);

            stack.decrement(take);
            removed += take;
        }

        return removed;
    }

    public void reload(PlayerEntity player, ItemStack stack)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return;
        }

        int count;

        if (!player.getAbilities().creativeMode && !props.ammoStack.isEmpty())
        {
            count = this.consumeAmmoStack(player, props.ammoStack, props.ammo - props.storedAmmo);
        }
        else
        {
            count = props.ammo - props.storedAmmo;
        }

        if (count > 0)
        {
            props.state = GunState.RELOADING;
            props.storedAmmo += count;
            props.storedReloadingTime = props.reloadingTime;

            commandSink.execute(player, props.reloadCommand);

            NBTUtils.saveGunProps(stack, props.toNBT());
            syncToSelfAndTracked(player, props);
        }
    }

    /* ---------------------------------------------------------------------
     * Shooting
     * --------------------------------------------------------------------- */

    public void shootIt(ItemStack stack, PlayerEntity player, World world)
    {
        this.resetTime(stack, player);

        GunProps props = NBTUtils.getGunProps(stack);

        if (world.isClient && props != null)
        {
            if (props.staticRecoil)
            {
                player.setPitch(player.getPitch() + Interpolation.QUINT_IN.interpolate(player.prevPitch, player.prevPitch + props.recoilXMin, 1F) - player.prevPitch);
                player.setYaw(player.getYaw() + Interpolation.QUINT_IN.interpolate(player.prevYaw, player.prevYaw + props.recoilYMin, 1F) - player.prevYaw);
            }
            else
            {
                player.setPitch(player.getPitch() + Interpolation.SINE_IN.interpolate(player.prevPitch, player.prevPitch + getRandom(props.recoilXMin, props.recoilXMax), 1F) - player.prevPitch);
                player.setYaw(player.getYaw() + Interpolation.SINE_IN.interpolate(player.prevYaw, player.prevYaw + getRandom(props.recoilYMin, props.recoilYMax), 1F) - player.prevYaw);
            }

            if (props.launch)
            {
                float pitch = player.getPitch() + (float) ((Math.random() - 0.5) * props.scatterY);
                float yaw = player.getYaw() + (float) ((Math.random() - 0.5) * props.scatterX);

                player.setVelocity(setThrowableHeading(pitch, yaw, 0, props.speed));
            }
        }

        this.shoot(stack, props, player, world);
    }

    public boolean shoot(ItemStack stack, GunProps props, PlayerEntity player, World world)
    {
        if (props == null)
        {
            return false;
        }

        if (props.launch)
        {
            float pitch = player.getPitch() + (float) ((Math.random() - 0.5) * props.scatterY);
            float yaw = player.getYaw() + (float) ((Math.random() - 0.5) * props.scatterX);

            player.setVelocity(setThrowableHeading(pitch, yaw, 0, props.speed));

            commandSink.execute(player, props.fireCommand);
        }
        else
        {
            if (!this.consumeInnerAmmo(stack, player))
            {
                return false;
            }

            EntityGunProjectile last = null;
            AbstractMorph base = MorphManager.INSTANCE.morphFromNBT(props.projectileMorph);

            for (int i = 0; i < Math.max(props.projectiles, 1); i++)
            {
                AbstractMorph morph = MorphUtils.copy(pickProjectileMorph(props, base, i));

                EntityGunProjectile projectile = new EntityGunProjectile(world, props, morph);

                float pitch = player.getPitch() + (float) ((Math.random() - 0.5) * props.scatterY);
                float yaw = player.getYaw() + (float) ((Math.random() - 0.5) * props.scatterX);
                double x = player.getX();
                double y = player.getEyeY();
                double z = player.getZ();
                float[] vector = rotate(props.shootingOffsetX, props.shootingOffsetY, props.shootingOffsetZ, player.getYaw(), player.getPitch());

                x += vector[0];
                y += vector[1];
                z += vector[2];

                projectile.setPosition(x, y, z);
                projectile.shootFrom(player);
                projectile.setVelocity(player, pitch, yaw, 0, props.speed, 0);
                projectile.setInitialMotion();

                if (props.projectiles > 0 && !world.isClient)
                {
                    world.spawnEntity(projectile);
                }

                last = projectile;
            }

            if (last != null)
            {
                commandSink.execute(last, props.fireCommand);
            }
        }

        if (!world.isClient)
        {
            /* Legacy: player instanceof EntityFakePlayer ? fakePlayer.actor :
             * player — the shot is broadcast under the actor's entity id, so
             * tracking clients play the recoil on the actor, not on an entity
             * they have never been sent. */
            Entity entity = player instanceof EntityActor.EntityFakePlayer fake && fake.actor != null ? fake.actor : player;
            int id = entity.getId();

            if (player instanceof ServerPlayerEntity)
            {
                Dispatcher.sendTo(new PacketGunShot(id), (ServerPlayerEntity) player);
            }

            Dispatcher.sendToTracked(entity, new PacketGunShot(id));

            List<Action> events = CommonProxy.manager.getActions(player);

            if (events != null)
            {
                events.add(new ShootGunAction(stack.writeNbt(new NbtCompound())));
            }

            this.decreaseDurability(NBTUtils.getGunProps(stack), stack, player);

            if (player instanceof ServerPlayerEntity)
            {
                GunProps p = NBTUtils.getGunProps(stack);

                NBTUtils.saveGunProps(stack, p.toNBT());
                Dispatcher.sendTo(new PacketGunInfo(p.toNBT(), entity.getId()), (ServerPlayerEntity) player);
                Dispatcher.sendToTracked(player, new PacketGunInfo(p.toNBT(), entity.getId()));
            }
        }

        return true;
    }

    /**
     * Legacy {@code setThrowableHeading(entity, pitch, yaw, pitchOffset,
     * velocity)} — normalize the look vector and scale by velocity, returning
     * the resulting motion (kept pure/testable; the caller assigns it).
     */
    public static Vec3d setThrowableHeading(float rotationPitch, float rotationYaw, float pitchOffset, float velocity)
    {
        float f = -MathHelper.sin(rotationYaw * 0.017453292F) * MathHelper.cos(rotationPitch * 0.017453292F);
        float f1 = -MathHelper.sin((rotationPitch + pitchOffset) * 0.017453292F);
        float f2 = MathHelper.cos(rotationYaw * 0.017453292F) * MathHelper.cos(rotationPitch * 0.017453292F);

        double x = f;
        double y = f1;
        double z = f2;
        double distance = Math.sqrt(x * x + y * y + z * z);

        return new Vec3d(x / distance * velocity, y / distance * velocity, z / distance * velocity);
    }

    /**
     * Legacy {@code rotate(Vector3f offset, yaw, pitch)} using
     * {@code javax.vecmath} matrices:
     * {@code a.rotY((180 - yaw)/180*PI)}, {@code b.rotX(-pitch/180*PI)},
     * {@code a.mul(b)}, {@code a.transform(vector)}. Reproduced with explicit
     * float matrix math so the numeric result is byte-identical to vecmath.
     */
    public static float[] rotate(float vx, float vy, float vz, float yaw, float pitch)
    {
        float ay = (180F - yaw) / 180F * (float) Math.PI;
        float ax = -pitch / 180F * (float) Math.PI;

        float cy = (float) Math.cos(ay);
        float sy = (float) Math.sin(ay);
        float cx = (float) Math.cos(ax);
        float sx = (float) Math.sin(ax);

        /* rotY(ay) = [[cy,0,sy],[0,1,0],[-sy,0,cy]] */
        float[][] a = {
            {cy, 0F, sy},
            {0F, 1F, 0F},
            {-sy, 0F, cy}
        };
        /* rotX(ax) = [[1,0,0],[0,cx,-sx],[0,sx,cx]] */
        float[][] b = {
            {1F, 0F, 0F},
            {0F, cx, -sx},
            {0F, sx, cx}
        };

        /* a = a * b */
        float[][] m = new float[3][3];

        for (int r = 0; r < 3; r++)
        {
            for (int c = 0; c < 3; c++)
            {
                m[r][c] = a[r][0] * b[0][c] + a[r][1] * b[1][c] + a[r][2] * b[2][c];
            }
        }

        /* transform(vector) = m * vector */
        float rx = m[0][0] * vx + m[0][1] * vy + m[0][2] * vz;
        float ry = m[1][0] * vx + m[1][1] * vy + m[1][2] * vz;
        float rz = m[2][0] * vx + m[2][1] * vy + m[2][2] * vz;

        return new float[] {rx, ry, rz};
    }

    /* ---------------------------------------------------------------------
     * Melee
     * --------------------------------------------------------------------- */

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return super.postHit(stack, target, attacker);
        }

        if (!props.meleeCommand.isEmpty() && attacker instanceof ServerPlayerEntity)
        {
            commandSink.execute(attacker, props.meleeCommand);
        }

        /* Armor/invulnerability-bypassing melee (that is the feature). */
        target.setHealth(target.getHealth() - props.meleeDamage);

        return false;
    }

    /* ---------------------------------------------------------------------
     * Durability bar (legacy showDurabilityBar / getDurabilityForDisplay /
     * getRGBDurabilityForDisplay)
     * --------------------------------------------------------------------- */

    @Override
    public boolean isItemBarVisible(ItemStack stack)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props != null)
        {
            return props.state == GunState.RELOADING || props.ammo > 1 || props.durability > 0;
        }

        return super.isItemBarVisible(stack);
    }

    @Override
    public int getItemBarStep(ItemStack stack)
    {
        /* Legacy ratio is "how full" (0..1); the vanilla bar draws 0..13 as
         * remaining, so step = round(13 * (1 - ratio)). */
        return Math.round(13F * (1F - (float) getDurabilityRatio(stack)));
    }

    /** Legacy {@code getDurabilityForDisplay} ratio (0..1). */
    public static double getDurabilityRatio(ItemStack stack)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props != null)
        {
            if (props.state != GunState.READY_TO_SHOOT)
            {
                if (props.state == GunState.RELOADING && props.reloadingTime > 0)
                {
                    return (double) props.storedReloadingTime / props.reloadingTime;
                }
                else
                {
                    return 1.0;
                }
            }
            else if (props.ammo > 1)
            {
                return 1.0 - ((double) props.storedAmmo / props.ammo);
            }
            else if (props.durability > 0)
            {
                return 1.0 - ((double) props.storedDurability / props.durability);
            }
        }

        return 0.0;
    }

    @Override
    public int getItemBarColor(ItemStack stack)
    {
        GunProps props = NBTUtils.getGunProps(stack);

        if (props != null)
        {
            if (props.state != GunState.READY_TO_SHOOT)
            {
                /* Legacy 0xFFFF0000 → 24-bit 0xFF0000 (the alpha byte is dropped
                 * on 1.20.4's item-bar colour path — documented delta). */
                return 0xFF0000;
            }
            else if (props.ammo > 1)
            {
                return 0x2FC0FF;
            }
        }

        return super.getItemBarColor(stack);
    }

    /* ---------------------------------------------------------------------
     * Re-equip animation suppression (Fabric FabricItem hook)
     * --------------------------------------------------------------------- */

    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack)
    {
        if (newStack.getItem() instanceof ItemGun && oldStack.getItem() instanceof ItemGun)
        {
            GunProps oldProps = NBTUtils.getGunProps(oldStack);
            GunProps newProps = NBTUtils.getGunProps(newStack);

            boolean isSameModel = Objects.equals(oldProps.defaultMorph, newProps.defaultMorph)
                && Objects.equals(oldProps.firingMorph, newProps.firingMorph)
                && Objects.equals(oldProps.crosshairMorph, newProps.crosshairMorph)
                && Objects.equals(oldProps.handsMorph, newProps.handsMorph)
                && Objects.equals(oldProps.reloadMorph, newProps.reloadMorph)
                && Objects.equals(oldProps.zoomOverlayMorph, newProps.zoomOverlayMorph);

            /* When the models are identical, legacy also migrates the cached
             * GunEntry from the old stack key to the new one to keep animation
             * state alive across the re-equip. The client render cache is
             * client-only, so the migration runs through a client-installed
             * seam (P197). */
            if (isSameModel && cacheMigrator != null)
            {
                cacheMigrator.accept(oldStack, newStack);
            }

            return !isSameModel;
        }

        return true;
    }
}
