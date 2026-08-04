package mchorse.blockbuster.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiPoseTransformations;
import mchorse.blockbuster.client.render.item.TileEntityGunItemStackRenderer;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.entity.EntityGunProjectile;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.guns.PacketGunInfo;
import mchorse.blockbuster.utils.NBTUtils;
import mchorse.blockbuster.utils.mclib.BBIcons;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.GuiPanelBase;
import mchorse.mclib.client.gui.framework.elements.GuiScrollElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiSlotElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.ScrollDirection;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.MathUtils;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.creative.GuiCreativeMorphsMenu;
import mchorse.metamorph.client.gui.creative.GuiMorphRenderer;
import mchorse.metamorph.client.gui.creative.GuiNestedEdit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Gun editor screen (roadmap P198).
 *
 * <p>Full 1:1 port of Blockbuster 2.7.2's {@code mchorse.blockbuster.client.gui
 * .GuiGun} (932 lines) — the End-key editor opened over a held gun item. Seven
 * tabs registered on {@link GuiGunPanels}: fire settings, projectile settings,
 * two cross-labeled "Pro options" pages, impact settings, gun+projectile
 * transforms, and the first-person gun transform. Every field editor, the nine
 * morph slots (one shared {@link GuiCreativeMorphsMenu}), the arms/bullet
 * preview renderers, Tab-cycling and the close-time
 * {@link PacketGunInfo}(tag, 0) sync are preserved verbatim, including the
 * load-bearing legacy quirks:</p>
 *
 * <ul>
 * <li><b>Cross-labeled aim panels</b>: the panel holding recoil/zoom/ammo
 * ({@link #aimOptionsSecond}) is registered with the lang key
 * {@code aim_options} ("Pro options"); the commands/prevention panel
 * ({@link #aimOptions}) with {@code aim_options_second} ("Pro options (page
 * 2)"). The swap is intentional 1.12.2 UI parity.</li>
 * <li><b>Tab cycles backward by default; Shift+Tab forward</b> ({@code ? 1 :
 * -1} + a clicked-button indirection).</li>
 * <li>The generic {@code blockbuster.gui.director.enabled} ("Enabled") label is
 * reused on the {@code sequencer}/{@code vanish}/{@code bounce}/{@code sticks}
 * toggles.</li>
 * <li>{@code closeScreen} reads the durability <i>widget</i>
 * ({@code (int) this.durability.value}) into {@code storedDurability} — editing
 * max durability also refills current durability on save.</li>
 * </ul>
 *
 * <h2>Port adapter: NBT morph/transform slots</h2>
 *
 * <p>The landed {@link GunProps} (P193) keeps its morph and transform slots as
 * raw {@link NbtCompound} rather than the {@code AbstractMorph}/
 * {@code ModelTransform} objects 1.12.2 held. This screen bridges that at the
 * boundary: it parses each slot into a live {@link AbstractMorph} /
 * {@link ModelTransform} for editing/preview on construction, mutates those in
 * place through the editors, and serializes them back into the props NBT slots
 * (every morph pick, and every frame for transforms, so the in-hand render
 * cache previews edits live exactly as legacy's shared-object editing did). The
 * working-morph copies ({@code props.setCurrent}/etc.) still receive the
 * {@link AbstractMorph} directly, matching legacy.</p>
 */
public class GuiGun extends GuiBase
{
    public GunProps props;
    public int index;

    public GuiPanelBase<GuiElement> panel;

    /* Morphs configuration */
    public GuiCreativeMorphsMenu morphs;

    /* Live morph slots (parsed from the props NBT slots; written back on pick).
     * Legacy stored these on GunProps directly as AbstractMorph. */
    private AbstractMorph defaultMorphSlot;
    private AbstractMorph firingMorphSlot;
    private AbstractMorph projectileMorphSlot;
    private AbstractMorph impactMorphSlot;
    private AbstractMorph handsMorphSlot;
    private AbstractMorph zoomOverlayMorphSlot;
    private AbstractMorph inventoryMorphSlot;
    private AbstractMorph reloadMorphSlot;
    private AbstractMorph crosshairMorphSlot;

    /* Live transforms (parsed from the props NBT slots; synced back each frame) */
    private ModelTransform gunTransform;
    private ModelTransform gunTransformFirstPerson;
    private ModelTransform projectileTransform;

    /* Gun options */
    public GuiElement gunOptions;
    public GuiNestedEdit pickDefault;
    public GuiNestedEdit pickFiring;
    public GuiTextElement fireCommand;
    public GuiTrackpadElement delay;
    public GuiTrackpadElement projectiles;
    public GuiTrackpadElement scatterX;
    public GuiTrackpadElement scatterY;
    public GuiToggleElement launch;
    public GuiToggleElement useTarget;
    public GuiSlotElement ammoStack;

    /* Projectile options */
    public GuiElement projectileOptions;
    public GuiNestedEdit pickProjectile;
    public GuiTextElement tickCommand;
    public GuiTrackpadElement ticking;
    public GuiTrackpadElement lifeSpan;
    public GuiToggleElement yaw;
    public GuiToggleElement pitch;
    public GuiToggleElement sequencer;
    public GuiToggleElement random;
    public GuiTrackpadElement hitboxX;
    public GuiTrackpadElement hitboxY;
    public GuiTrackpadElement speed;
    public GuiTrackpadElement friction;
    public GuiTrackpadElement gravity;
    public GuiTrackpadElement fadeIn;
    public GuiTrackpadElement fadeOut;

    /* Evanechecssss' options */
    public GuiElement aimOptions;
    public GuiToggleElement staticRecoil;
    public GuiTrackpadElement recoilXMin;
    public GuiTrackpadElement recoilXMax;
    public GuiTrackpadElement recoilYMin;
    public GuiTrackpadElement recoilYMax;

    public GuiToggleElement enableArmsShootingPose;
    public GuiToggleElement alwaysArmsShootingPose;

    public GuiTrackpadElement shootOffsetX;
    public GuiTrackpadElement shootOffsetY;
    public GuiTrackpadElement shootOffsetZ;

    public GuiNestedEdit pickCrosshairMorph;
    public GuiNestedEdit pickInventoryMorph;
    public GuiNestedEdit pickHandsMorph;
    public GuiNestedEdit pickReloadMorph;
    public GuiNestedEdit pickZoomOverlayMorph;

    public GuiToggleElement hideCrosshairOnZoom;
    public GuiToggleElement useInventoryMorph;
    public GuiToggleElement hideHandsOnZoom;
    public GuiToggleElement useZoomOverlayMorph;

    public GuiTrackpadElement zoomFactor;
    public GuiTrackpadElement ammo;
    public GuiToggleElement useReloading;
    public GuiTrackpadElement reloadingTime;
    public GuiToggleElement shootWhenHeld;
    public GuiTrackpadElement shotDelay;

    /* Evanechecssss's options (page 2) */
    public GuiElement aimOptionsSecond;
    public GuiTextElement destroyCommand;
    public GuiTextElement meleeCommand;
    public GuiTextElement reloadCommand;
    public GuiTextElement zoomOnCommand;
    public GuiTextElement zoomOffCommand;

    public GuiTrackpadElement meleeDamage;
    public GuiTrackpadElement mouseZoom;
    public GuiTrackpadElement durability;
    public GuiToggleElement preventLeftClick;
    public GuiToggleElement preventRightClick;
    public GuiToggleElement preventEntityAttack;

    /* Impact options */
    public GuiElement impactOptions;
    public GuiNestedEdit pickImpact;
    public GuiTextElement impactCommand;
    public GuiTextElement impactEntityCommand;
    public GuiTrackpadElement impactDelay;
    public GuiToggleElement vanish;
    public GuiToggleElement bounce;
    public GuiToggleElement sticks;
    public GuiTrackpadElement hits;
    public GuiTrackpadElement damage;
    public GuiTrackpadElement knockbackHorizontal;
    public GuiTrackpadElement knockbackVertical;
    public GuiTrackpadElement bounceFactor;
    public GuiTextElement vanishCommand;
    public GuiTrackpadElement vanishDelay;
    public GuiTrackpadElement penetration;
    public GuiToggleElement ignoreBlocks;
    public GuiToggleElement ignoreEntities;

    /* Transforms */
    public GuiElement transformOptions;
    public GuiPoseTransformations gun;
    public GuiPoseTransformations projectile;
    public GuiMorphRenderer arms;
    public GuiProjectileModelRenderer bullet;

    /* First person transform */
    public GuiElement transformFirstPersonOptions;
    public GuiPoseTransformations gunFirstPerson;

    public GuiGun(ItemStack stack)
    {
        TileEntityGunItemStackRenderer.GunEntry entry = TileEntityGunItemStackRenderer.models.get(stack);

        if (entry == null)
        {
            this.props = NBTUtils.getGunProps(stack);
        }
        else
        {
            this.props = entry.props;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        /* Parse the raw props NBT slots into live morphs/transforms for editing */
        this.defaultMorphSlot = this.morphFrom(this.props.defaultMorph);
        this.firingMorphSlot = this.morphFrom(this.props.firingMorph);
        this.projectileMorphSlot = this.morphFrom(this.props.projectileMorph);
        this.impactMorphSlot = this.morphFrom(this.props.impactMorph);
        this.handsMorphSlot = this.morphFrom(this.props.handsMorph);
        this.zoomOverlayMorphSlot = this.morphFrom(this.props.zoomOverlayMorph);
        this.inventoryMorphSlot = this.morphFrom(this.props.inventoryMorph);
        this.reloadMorphSlot = this.morphFrom(this.props.reloadMorph);
        this.crosshairMorphSlot = this.morphFrom(this.props.crosshairMorph);

        this.gunTransform = this.transformFrom(this.props.gunTransform);
        this.gunTransformFirstPerson = this.transformFrom(this.props.gunTransformFirstPerson);
        this.projectileTransform = this.transformFrom(this.props.projectileTransform);

        /* Initialization of GUI elements */
        this.gunOptions = new GuiElement(mc);
        this.projectileOptions = new GuiElement(mc);
        this.aimOptions = new GuiElement(mc);
        this.aimOptionsSecond = new GuiElement(mc);
        this.transformOptions = new GuiElement(mc);
        this.transformFirstPersonOptions = new GuiElement(mc);
        this.impactOptions = new GuiElement(mc);

        this.panel = new GuiGunPanels(mc);
        this.panel.setPanel(this.gunOptions);
        this.panel.registerPanel(this.gunOptions, IKey.lang("blockbuster.gui.gun.fire_props"), Icons.GEAR);
        this.panel.registerPanel(this.projectileOptions, IKey.lang("blockbuster.gui.gun.projectile_props"), BBIcons.BULLET);
        this.panel.registerPanel(this.aimOptions, IKey.lang("blockbuster.gui.gun.aim_options_second"), Icons.SOUND);
        this.panel.registerPanel(this.aimOptionsSecond, IKey.lang("blockbuster.gui.gun.aim_options"), Icons.CURSOR);
        this.panel.registerPanel(this.impactOptions, IKey.lang("blockbuster.gui.gun.impact_props"), Icons.DOWNLOAD);
        this.panel.registerPanel(this.transformOptions, IKey.lang("blockbuster.gui.gun.transforms"), Icons.POSE);
        this.panel.registerPanel(this.transformFirstPersonOptions, IKey.lang("blockbuster.gui.gun.transforms_first_person"), Icons.WRENCH);

        this.morphs = new GuiCreativeMorphsMenu(mc, this::setMorph);

        /* Gun options */
        Area area = this.gunOptions.area;

        this.pickDefault = new GuiNestedEdit(mc, (editing) -> this.openMorphs(1, editing));
        this.pickFiring = new GuiNestedEdit(mc, false, (editing) -> this.openMorphs(2, editing));
        this.fireCommand = new GuiTextElement(mc, 10000, (value) -> this.props.fireCommand = value);
        this.delay = new GuiTrackpadElement(mc, (value) -> this.props.delay = value.intValue());
        this.delay.limit(0, Integer.MAX_VALUE, true);
        this.projectiles = new GuiTrackpadElement(mc, (value) -> this.props.projectiles = value.intValue());
        this.projectiles.limit(0, Integer.MAX_VALUE, true);
        this.scatterX = new GuiTrackpadElement(mc, (value) -> this.props.scatterX = value.floatValue());
        this.scatterX.tooltip(IKey.lang("blockbuster.gui.gun.scatter_x"));
        this.scatterY = new GuiTrackpadElement(mc, (value) -> this.props.scatterY = value.floatValue());
        this.scatterY.tooltip(IKey.lang("blockbuster.gui.gun.scatter_y"));
        this.launch = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.launch"), false, (b) -> this.props.launch = b.isToggled());
        this.useTarget = new GuiToggleElement(mc, IKey.lang("metamorph.gui.body_parts.use_target"), false, (b) -> this.props.useTarget = b.isToggled());
        this.ammoStack = new GuiSlotElement(mc, 0, this::pickItem);
        this.ammoStack.tooltip(IKey.lang("blockbuster.gui.gun.ammo_stack"));
        int firingOffset = 40;

        GuiElement scatterBar = new GuiElement(mc);

        scatterBar.flex().relative(area).set(0, 0, 0, 20).x(0.5F).y(1, -75).w(0.5F, -60).anchorX(0.5F).row(5);
        scatterBar.add(this.scatterX, this.scatterY);

        this.fireCommand.flex().relative(area).set(10, 0, 0, 20).w(1, -20).y(1F, -30);
        this.delay.flex().relative(scatterBar.resizer()).set(0, 0, 100, 20).x(-10).anchorX(1F);
        this.projectiles.flex().relative(scatterBar.resizer()).set(0, 0, 100, 20).x(1F, 10);
        this.pickDefault.flex().relative(this.delay.resizer()).w(1F).y(-5 - firingOffset);
        this.pickFiring.flex().relative(this.projectiles.resizer()).w(1F).y(-5 - firingOffset);
        this.ammoStack.flex().relative(this.pickFiring.resizer()).x(1F, 5).y(-2);

        GuiElement launchBar = new GuiElement(mc);

        launchBar.flex().relative(scatterBar.resizer()).y(-5 - firingOffset).w(1F).h(11).row(10);
        this.launch.flex().h(20);
        this.useTarget.flex().h(20);
        launchBar.add(this.launch, this.useTarget);

        this.gunOptions.add(scatterBar, launchBar, this.delay, this.projectiles, this.pickDefault, this.pickFiring, this.fireCommand, this.ammoStack);

        /* Projectile options */
        area = this.projectileOptions.area;

        this.pickProjectile = new GuiNestedEdit(mc, (editing) -> this.openMorphs(3, editing));
        this.tickCommand = new GuiTextElement(mc, 10000, (value) -> this.props.tickCommand = value);
        this.ticking = new GuiTrackpadElement(mc, (value) -> this.props.ticking = value.intValue());
        this.ticking.tooltip(IKey.lang("blockbuster.gui.gun.ticking"));
        this.ticking.limit(0, Integer.MAX_VALUE, true);
        this.lifeSpan = new GuiTrackpadElement(mc, (value) -> this.props.lifeSpan = value.intValue());
        this.lifeSpan.tooltip(IKey.lang("blockbuster.gui.gun.life_span"));
        this.lifeSpan.limit(0, Integer.MAX_VALUE, true);
        this.yaw = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.yaw"), false, (b) -> this.props.yaw = b.isToggled());
        this.pitch = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.pitch"), false, (b) -> this.props.pitch = b.isToggled());
        this.sequencer = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.enabled"), false, (b) -> this.props.sequencer = b.isToggled());
        this.random = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.random"), false, (b) -> this.props.random = b.isToggled());
        this.hitboxX = new GuiTrackpadElement(mc, (value) -> this.props.hitboxX = value.floatValue());
        this.hitboxX.tooltip(IKey.lang("blockbuster.gui.gun.hitbox_x"));
        this.hitboxY = new GuiTrackpadElement(mc, (value) -> this.props.hitboxY = value.floatValue());
        this.hitboxY.tooltip(IKey.lang("blockbuster.gui.gun.hitbox_y"));
        this.speed = new GuiTrackpadElement(mc, (value) -> this.props.speed = value.floatValue());
        this.speed.tooltip(IKey.lang("blockbuster.gui.gun.speed"));
        this.friction = new GuiTrackpadElement(mc, (value) -> this.props.friction = value.floatValue());
        this.friction.tooltip(IKey.lang("blockbuster.gui.gun.friction"));
        this.gravity = new GuiTrackpadElement(mc, (value) -> this.props.gravity = value.floatValue());
        this.gravity.tooltip(IKey.lang("blockbuster.gui.gun.gravity"));
        this.fadeIn = new GuiTrackpadElement(mc, (value) -> this.props.fadeIn = value.intValue());
        this.fadeIn.tooltip(IKey.lang("blockbuster.gui.gun.fade_in"));
        this.fadeIn.limit(0, Integer.MAX_VALUE, true);
        this.fadeOut = new GuiTrackpadElement(mc, (value) -> this.props.fadeOut = value.intValue());
        this.fadeOut.tooltip(IKey.lang("blockbuster.gui.gun.fade_out"));
        this.fadeOut.limit(0, Integer.MAX_VALUE, true);

        this.pickProjectile.flex().relative(area).w(100).x(0.75F, -50).y(1, -60);
        this.tickCommand.flex().relative(area).set(10, 0, 0, 20).w(1, -20).y(1, -30);

        GuiElement projectileFields = new GuiElement(mc);

        projectileFields.flex().relative(area).w(1F).h(1F, -40).column(5).width(100).height(20).padding(10);
        projectileFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.category.motion")).background(), this.speed, this.friction, this.gravity);
        projectileFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.category.hitbox")).background().marginTop(12), this.hitboxX, this.hitboxY);
        projectileFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.category.timers")).background().marginTop(12), this.ticking, this.lifeSpan);
        projectileFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.category.rotation")).background().marginTop(12), this.yaw, this.pitch);
        projectileFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.category.transition")).background().marginTop(12), this.fadeIn, this.fadeOut);
        projectileFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.sequencer")).background().marginTop(12), this.sequencer, this.random);
        this.projectileOptions.add(this.pickProjectile, this.tickCommand, projectileFields);

        /* Aim options */
        area = this.aimOptionsSecond.area;

        this.staticRecoil = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.static_recoil"), false, (b) -> this.props.staticRecoil = b.isToggled());
        this.staticRecoil.tooltip(IKey.lang("blockbuster.gui.gun.static_recoil_tooltip"));
        this.recoilXMin = new GuiTrackpadElement(mc, (value) -> this.props.recoilXMin = value.floatValue());
        this.recoilXMin.limit(-200, 200).tooltip(IKey.lang("blockbuster.gui.gun.recoil_min"));
        this.recoilXMax = new GuiTrackpadElement(mc, (value) -> this.props.recoilXMax = value.floatValue());
        this.recoilXMax.limit(-200, 200).tooltip(IKey.lang("blockbuster.gui.gun.recoil_max"));
        this.recoilYMin = new GuiTrackpadElement(mc, (value) -> this.props.recoilYMin = value.floatValue());
        this.recoilYMin.limit(-200, 200).tooltip(IKey.lang("blockbuster.gui.gun.recoil_min"));
        this.recoilYMax = new GuiTrackpadElement(mc, (value) -> this.props.recoilYMax = value.floatValue());
        this.recoilYMax.limit(-200, 200).tooltip(IKey.lang("blockbuster.gui.gun.recoil_max"));

        this.enableArmsShootingPose = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.arm_shooting_pose"), false, (b) -> this.props.enableArmsShootingPose = b.isToggled());
        this.enableArmsShootingPose.tooltip(IKey.lang("blockbuster.gui.gun.arm_shooting_pose_tooltip"));
        this.alwaysArmsShootingPose = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.arm_shooting_pose_always"), false, (b) -> this.props.alwaysArmsShootingPose = b.isToggled());
        this.alwaysArmsShootingPose.tooltip(IKey.lang("blockbuster.gui.gun.arm_shooting_pose_always_tooltip"));

        this.shootOffsetX = new GuiTrackpadElement(mc, (value) -> this.props.shootingOffsetX = value.floatValue());
        this.shootOffsetX.limit(-10, 10);
        this.shootOffsetY = new GuiTrackpadElement(mc, (value) -> this.props.shootingOffsetY = value.floatValue());
        this.shootOffsetY.limit(-10, 10);
        this.shootOffsetZ = new GuiTrackpadElement(mc, (value) -> this.props.shootingOffsetZ = value.floatValue());
        this.shootOffsetZ.limit(-10, 10);

        this.pickCrosshairMorph = new GuiNestedEdit(mc, (editing) -> this.openMorphs(9, editing));
        this.pickInventoryMorph = new GuiNestedEdit(mc, (editing) -> this.openMorphs(7, editing));
        this.pickHandsMorph = new GuiNestedEdit(mc, (editing) -> this.openMorphs(5, editing));
        this.pickReloadMorph = new GuiNestedEdit(mc, (editing) -> this.openMorphs(8, editing));
        this.pickZoomOverlayMorph = new GuiNestedEdit(mc, (editing) -> this.openMorphs(6, editing));

        this.hideCrosshairOnZoom = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.hide_crosshair_on_zoom"), false, (b) -> this.props.hideCrosshairOnZoom = b.isToggled());
        this.useInventoryMorph = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.use_inventory_morph"), false, (b) -> this.props.useInventoryMorph = b.isToggled());
        this.hideHandsOnZoom = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.hide_hands_on_zoom"), false, (b) -> this.props.hideHandsOnZoom = b.isToggled());
        this.useZoomOverlayMorph = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.use_zoom_overlay_morph"), false, (b) -> this.props.useZoomOverlayMorph = b.isToggled());

        this.zoomFactor = new GuiTrackpadElement(mc, (value) -> this.props.zoomFactor = value.floatValue());
        this.zoomFactor.limit(0, 1).increment(0.1).values(0.05, 0.01, 0.1).tooltip(IKey.lang("blockbuster.gui.gun.zoom_factor_tooltip"));
        this.ammo = new GuiTrackpadElement(mc, (value) -> this.props.ammo = value.intValue());
        this.ammo.limit(1).integer().tooltip(IKey.lang("blockbuster.gui.gun.ammo_tooltip"));
        this.useReloading = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.use_reloading"), false, (b) -> this.props.useReloading = b.isToggled());
        this.reloadingTime = new GuiTrackpadElement(mc, (value) -> this.props.reloadingTime = value.intValue());
        this.reloadingTime.limit(0).integer().tooltip(IKey.lang("blockbuster.gui.gun.reloading_time_tooltip"));
        this.shootWhenHeld = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.shoot_when_held"), false, (b) -> this.props.shootWhenHeld = b.isToggled());
        this.shotDelay = new GuiTrackpadElement(mc, (value) -> this.props.shotDelay = value.intValue());
        this.shotDelay.limit(0).integer().tooltip(IKey.lang("blockbuster.gui.gun.shot_delay_tooltip"));

        GuiScrollElement aimFields = new GuiScrollElement(mc, ScrollDirection.HORIZONTAL);

        aimFields.flex().relative(area).wh(1F, 1F).column(5).scroll().width(160).height(20).padding(10);
        aimFields.add(Elements.label(IKey.str("Recoil")).background(), this.staticRecoil);
        aimFields.add(
            Elements.label(IKey.lang("blockbuster.gui.gun.recoil_x")).marginBottom(-2),
            Elements.row(mc, 5, this.recoilXMin, this.recoilXMax)
        );
        aimFields.add(
            Elements.label(IKey.lang("blockbuster.gui.gun.recoil_y")).marginBottom(-2),
            Elements.row(mc, 5, this.recoilYMin, this.recoilYMax)
        );
        aimFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.arm_pose")).background().marginTop(12), this.enableArmsShootingPose, this.alwaysArmsShootingPose);
        aimFields.add(
            Elements.label(IKey.lang("blockbuster.gui.gun.shooting_offset")).background().marginTop(12).tooltip(IKey.lang("blockbuster.gui.gun.shooting_offset_tooltip")),
            this.shootOffsetX, this.shootOffsetY, this.shootOffsetZ.marginBottom(100000)
        );

        aimFields.add(
            Elements.column(mc, 5,
                Elements.label(IKey.lang("blockbuster.gui.gun.crosshair_morph")).background(),
                this.hideCrosshairOnZoom, this.pickCrosshairMorph
            )
        );
        aimFields.add(
            Elements.column(mc, 5,
                Elements.label(IKey.lang("blockbuster.gui.gun.inventory_morph")).background(),
                this.useInventoryMorph, this.pickInventoryMorph
            ).marginTop(12)
        );
        aimFields.add(
            Elements.column(mc, 5,
                Elements.label(IKey.lang("blockbuster.gui.gun.hands_morph")).background(),
                this.hideHandsOnZoom, this.pickHandsMorph
            ).marginTop(12)
        );
        aimFields.add(
            Elements.column(mc, 5,
                Elements.label(IKey.lang("blockbuster.gui.gun.reload_morph")).background(),
                this.pickReloadMorph
            ).marginTop(12)
        );
        aimFields.add(
            Elements.column(mc, 5,
                Elements.label(IKey.lang("blockbuster.gui.gun.overlay_morph")).background(),
                this.useZoomOverlayMorph, this.pickZoomOverlayMorph
            ).marginTop(12).marginBottom(100000)
        );

        aimFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.zoom_factor")).background().marginTop(12), this.zoomFactor);
        aimFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.ammo")).background().marginTop(12), this.ammo);
        aimFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.reloading")).background().marginTop(12), this.useReloading, reloadingTime);
        aimFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.shooting")).background().marginTop(12), this.shootWhenHeld, this.shotDelay);

        this.aimOptionsSecond.add(aimFields);

        /* Aim options 2 */
        area = this.aimOptions.area;

        this.destroyCommand = new GuiTextElement(mc, 10000, (value) -> this.props.destroyCommand = value);
        this.meleeCommand = new GuiTextElement(mc, 10000, (value) -> this.props.meleeCommand = value);
        this.reloadCommand = new GuiTextElement(mc, 10000, (value) -> this.props.reloadCommand = value);
        this.zoomOnCommand = new GuiTextElement(mc, 10000, (value) -> this.props.zoomOnCommand = value);
        this.zoomOffCommand = new GuiTextElement(mc, 10000, (value) -> this.props.zoomOffCommand = value);

        this.meleeDamage = new GuiTrackpadElement(mc, (value) -> this.props.meleeDamage = value.floatValue());
        this.mouseZoom = new GuiTrackpadElement(mc, (value) -> this.props.mouseZoom = value.floatValue());
        this.mouseZoom.limit(0, 1.5F);
        this.durability = new GuiTrackpadElement(mc, (value) -> this.props.durability = value.intValue());
        this.durability.limit(0, Integer.MAX_VALUE, true);
        this.preventLeftClick = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.prevent_left_click"), false, (b) -> this.props.preventLeftClick = b.isToggled());
        this.preventRightClick = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.prevent_right_click"), false, (b) -> this.props.preventRightClick = b.isToggled());
        this.preventEntityAttack = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.prevent_entity_attack"), false, (b) -> this.props.preventEntityAttack = b.isToggled());

        GuiElement aimTwo = Elements.column(mc, 5, 10,
            Elements.label(IKey.lang("blockbuster.gui.gun.melee_damage")).marginTop(5), this.meleeDamage,
            Elements.label(IKey.lang("blockbuster.gui.gun.mouse_zoom")).marginTop(5), this.mouseZoom,
            Elements.label(IKey.lang("blockbuster.gui.gun.durability")).marginTop(5), this.durability,
            this.preventLeftClick,
            this.preventRightClick,
            this.preventEntityAttack
        );

        aimTwo.flex().relative(area).x(1F).y(1F).w(200).anchor(1F, 1F);

        GuiElement aimCommands = Elements.column(mc, 3, 10,
            Elements.label(IKey.lang("blockbuster.gui.gun.destroyed_command")), this.destroyCommand,
            Elements.label(IKey.lang("blockbuster.gui.gun.melee_command")).marginTop(5), this.meleeCommand,
            Elements.label(IKey.lang("blockbuster.gui.gun.reload_command")).marginTop(5), this.reloadCommand,
            Elements.label(IKey.lang("blockbuster.gui.gun.zoom_on_command")).marginTop(5), this.zoomOnCommand,
            Elements.label(IKey.lang("blockbuster.gui.gun.zoom_off_command")).marginTop(5), this.zoomOffCommand
        );

        aimCommands.flex().relative(area).y(1F).wTo(aimTwo.area, 10).anchorY(1F);

        this.aimOptions.add(aimTwo, aimCommands);

        /* Impact options */
        area = this.impactOptions.area;

        this.pickImpact = new GuiNestedEdit(mc, (editing) -> this.openMorphs(4, editing));
        this.impactDelay = new GuiTrackpadElement(mc, (value) -> this.props.impactDelay = value.intValue());
        this.impactDelay.limit(0, Integer.MAX_VALUE, true);
        this.impactCommand = new GuiTextElement(mc, 10000, (value) -> this.props.impactCommand = value);
        this.impactEntityCommand = new GuiTextElement(mc, 10000, (value) -> this.props.impactEntityCommand = value);
        this.vanish = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.enabled"), false, (b) -> this.props.vanish = b.isToggled());
        this.bounce = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.enabled"), false, (b) -> this.props.bounce = b.isToggled());
        this.sticks = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.enabled"), false, (b) -> this.props.sticks = b.isToggled());
        this.hits = new GuiTrackpadElement(mc, (value) -> this.props.hits = value.intValue());
        this.hits.tooltip(IKey.lang("blockbuster.gui.gun.hits"));
        this.hits.limit(0, Integer.MAX_VALUE, true);
        this.damage = new GuiTrackpadElement(mc, (value) -> this.props.damage = value.floatValue());
        this.knockbackHorizontal = new GuiTrackpadElement(mc, (value) -> this.props.knockbackHorizontal = value.floatValue());
        this.knockbackHorizontal.tooltip(IKey.lang("blockbuster.gui.gun.knockback_horizontal"));
        this.knockbackVertical = new GuiTrackpadElement(mc, (value) -> this.props.knockbackVertical = value.floatValue());
        this.knockbackVertical.tooltip(IKey.lang("blockbuster.gui.gun.knockback_vertical"));
        this.bounceFactor = new GuiTrackpadElement(mc, (value) -> this.props.bounceFactor = value.floatValue());
        this.bounceFactor.tooltip(IKey.lang("blockbuster.gui.gun.bounce_factor"));
        this.vanishCommand = new GuiTextElement(mc, 10000, (value) -> this.props.vanishCommand = value);
        this.vanishDelay = new GuiTrackpadElement(mc, (value) -> this.props.vanishDelay = value.intValue());
        this.vanishDelay.limit(0).integer().tooltip(IKey.lang("blockbuster.gui.gun.vanish_delay"));
        this.penetration = new GuiTrackpadElement(mc, (value) -> this.props.penetration = value.floatValue());
        this.penetration.block().tooltip(IKey.lang("blockbuster.gui.gun.penetration"));
        this.ignoreBlocks = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.ignore_blocks"), false, (b) -> this.props.ignoreBlocks = b.isToggled());
        this.ignoreBlocks.tooltip(IKey.lang("blockbuster.gui.gun.ignore_blocks_tooltip"));
        this.ignoreEntities = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.gun.ignore_entities"), false, (b) -> this.props.ignoreEntities = b.isToggled());
        this.ignoreEntities.tooltip(IKey.lang("blockbuster.gui.gun.ignore_entities_tooltip"));

        this.pickImpact.flex().relative(area).w(100).x(0.75F, -40).y(1, -140);
        this.vanishCommand.flex().relative(area).set(10, 0, 0, 20).w(1, -20).y(1, -110);
        this.impactEntityCommand.flex().relative(this.vanishCommand).y(40).w(1F).h(20);
        this.impactCommand.flex().relative(this.impactEntityCommand).y(40).w(1F).h(20);

        GuiElement impactFields = new GuiElement(mc);

        impactFields.flex().relative(area).w(1F).h(1F, -120).column(5).width(100).height(20).padding(10);
        impactFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.impact_delay")).background(), this.impactDelay);
        impactFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.damage")).background().marginTop(12), this.damage, this.knockbackHorizontal, this.knockbackVertical);
        impactFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.bounce")).background().marginTop(12), this.bounce, this.hits, this.bounceFactor);
        impactFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.vanish")).background().marginTop(12), this.vanish, this.vanishDelay);
        impactFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.sticks")).background().marginTop(12), this.sticks, this.penetration);
        impactFields.add(Elements.label(IKey.lang("blockbuster.gui.gun.collision")).background().marginTop(12), this.ignoreBlocks, this.ignoreEntities);

        this.impactOptions.add(this.pickImpact, this.vanishCommand, this.impactEntityCommand, this.impactCommand, impactFields);

        /* Gun transforms */
        area = this.transformOptions.area;

        this.gun = new GuiPoseTransformations(mc);
        this.projectile = new GuiPoseTransformations(mc);

        this.arms = new GuiMorphRenderer(mc);
        this.arms.setRotation(61, -13);
        this.arms.setPosition(0.1048045F, 1.081198F, 0.22774392F);
        this.arms.setScale(1.5F);

        try
        {
            this.arms.morph = MorphManager.INSTANCE.morphFromNBT(StringNbtReader.parse("{Name:\"blockbuster.fred\"}"));

            if (this.arms.getEntity() != null)
            {
                this.arms.getEntity().equipStack(EquipmentSlot.MAINHAND, stack);
            }
        }
        catch (Exception e)
        {}

        this.bullet = new GuiProjectileModelRenderer(mc);

        if (this.bullet.projectile != null)
        {
            this.bullet.projectile.props = this.props;
            this.bullet.projectile.morph.setDirect(this.projectileMorphSlot);
        }

        this.bullet.setRotation(-64, 16);
        this.bullet.setPosition(-0.042806394F, 0.40452564F, -0.001203875F);
        this.bullet.setScale(2.5F);

        this.gun.flex().relative(area).x(0.25F, -95).y(1, -80).wh(190, 70);
        this.projectile.flex().relative(area).x(0.75F, -95).y(1, -80).wh(190, 70);
        this.arms.flex().relative(area).wTo(this.bullet.flex()).h(1F);
        this.bullet.flex().relative(area).x(0.5F).wh(0.5F, 1F);

        this.transformOptions.add(this.arms, this.bullet, this.gun, this.projectile);

        /* Gun transforms (first person) */
        area = this.transformFirstPersonOptions.area;

        this.gunFirstPerson = new GuiPoseTransformations(mc);
        this.gunFirstPerson.flex().relative(area).x(0.5F).y(1, -80).wh(190, 70).anchorX(0.5F);

        this.transformFirstPersonOptions.add(this.gunFirstPerson);

        /* Placement of the elements */
        this.morphs.flex().relative(this.viewport).wh(1F, 1F);
        this.panel.flex().relative(this.viewport).set(0, 35, 0, 0).w(1, 0).h(1, -35);

        /* Gun properties */
        this.pickDefault.setMorph(this.defaultMorphSlot);
        this.pickHandsMorph.setMorph(this.handsMorphSlot);
        this.pickInventoryMorph.setMorph(this.inventoryMorphSlot);
        this.pickReloadMorph.setMorph(this.reloadMorphSlot);
        this.pickZoomOverlayMorph.setMorph(this.zoomOverlayMorphSlot);
        this.pickFiring.setMorph(this.firingMorphSlot);
        this.pickCrosshairMorph.setMorph(this.crosshairMorphSlot);
        this.fireCommand.setText(this.props.fireCommand);
        this.delay.setValue(this.props.delay);
        this.projectiles.setValue(this.props.projectiles);
        this.zoomFactor.setValue(this.props.zoomFactor);
        this.recoilXMin.setValue(this.props.recoilXMin);
        this.recoilXMax.setValue(this.props.recoilXMax);
        this.shootOffsetX.setValue(this.props.shootingOffsetX);
        this.shootOffsetY.setValue(this.props.shootingOffsetY);
        this.durability.setValue(this.props.durability);
        this.mouseZoom.setValue(this.props.mouseZoom);
        this.shootOffsetZ.setValue(this.props.shootingOffsetZ);
        this.recoilYMin.setValue(this.props.recoilYMin);
        this.meleeDamage.setValue(this.props.meleeDamage);
        this.ammo.setValue(this.props.ammo);
        this.reloadingTime.setValue(this.props.reloadingTime);
        this.recoilYMax.setValue(this.props.recoilYMax);
        this.shotDelay.setValue(this.props.shotDelay);
        this.staticRecoil.toggled(this.props.staticRecoil);
        this.scatterX.setValue(this.props.scatterX);
        this.scatterY.setValue(this.props.scatterY);
        this.launch.toggled(this.props.launch);
        this.useTarget.toggled(this.props.useTarget);
        this.ammoStack.setStack(this.props.ammoStack);
        this.reloadCommand.setText(this.props.reloadCommand);
        this.meleeCommand.setText(this.props.meleeCommand);
        this.destroyCommand.setText(this.props.destroyCommand);
        this.meleeCommand.setText(this.props.meleeCommand);
        this.destroyCommand.setText(this.props.destroyCommand);
        this.zoomOnCommand.setText(this.props.zoomOnCommand);
        this.zoomOffCommand.setText(this.props.zoomOffCommand);

        this.useZoomOverlayMorph.toggled(this.props.useZoomOverlayMorph);
        this.hideHandsOnZoom.toggled(this.props.hideHandsOnZoom);
        this.hideCrosshairOnZoom.toggled(this.props.hideCrosshairOnZoom);
        this.enableArmsShootingPose.toggled(this.props.enableArmsShootingPose);
        this.preventRightClick.toggled(this.props.preventRightClick);
        this.preventLeftClick.toggled(this.props.preventLeftClick);
        this.preventEntityAttack.toggled(this.props.preventEntityAttack);
        this.useInventoryMorph.toggled(this.props.useInventoryMorph);
        this.useReloading.toggled(this.props.useReloading);
        this.alwaysArmsShootingPose.toggled(this.props.alwaysArmsShootingPose);
        this.shootWhenHeld.toggled(this.props.shootWhenHeld);

        /* Projectile properties */
        this.pickProjectile.setMorph(this.projectileMorphSlot);
        this.tickCommand.setText(this.props.tickCommand);
        this.ticking.setValue(this.props.ticking);
        this.lifeSpan.setValue(this.props.lifeSpan);
        this.yaw.toggled(this.props.yaw);
        this.pitch.toggled(this.props.pitch);
        this.sequencer.toggled(this.props.sequencer);
        this.random.toggled(this.props.random);
        this.hitboxX.setValue(this.props.hitboxX);
        this.hitboxY.setValue(this.props.hitboxY);
        this.speed.setValue(this.props.speed);
        this.friction.setValue(this.props.friction);
        this.gravity.setValue(this.props.gravity);
        this.fadeIn.setValue(this.props.fadeIn);
        this.fadeOut.setValue(this.props.fadeOut);

        /* Impact properties */
        this.pickImpact.setMorph(this.impactMorphSlot);
        this.impactCommand.setText(this.props.impactCommand);
        this.impactEntityCommand.setText(this.props.impactEntityCommand);
        this.impactDelay.setValue(this.props.impactDelay);
        this.vanish.toggled(this.props.vanish);
        this.bounce.toggled(this.props.bounce);
        this.sticks.toggled(this.props.sticks);
        this.hits.setValue(this.props.hits);
        this.damage.setValue(this.props.damage);
        this.knockbackHorizontal.setValue(this.props.knockbackHorizontal);
        this.knockbackVertical.setValue(this.props.knockbackVertical);
        this.bounceFactor.setValue(this.props.bounceFactor);
        this.vanishCommand.setText(this.props.vanishCommand);
        this.vanishDelay.setValue(this.props.vanishDelay);
        this.penetration.setValue(this.props.penetration);
        this.ignoreBlocks.toggled(this.props.ignoreBlocks);
        this.ignoreEntities.toggled(this.props.ignoreEntities);

        /* Gun transforms */
        this.gun.set(this.gunTransform);
        this.gunFirstPerson.set(this.gunTransformFirstPerson);
        this.projectile.set(this.projectileTransform);

        this.root.add(this.panel);
        this.root.keys().register(IKey.lang("blockbuster.gui.gun.keys.cycle"), LegacyKeyCodes.KEY_TAB, this::cycle);
    }

    private void pickItem(ItemStack stack)
    {
        this.props.ammoStack = stack;
    }

    protected void cycle()
    {
        int index = -1;

        for (int i = 0; i < this.panel.panels.size(); i++)
        {
            if (this.panel.view.delegate == this.panel.panels.get(i))
            {
                index = i;

                break;
            }
        }

        index += GuiUtils.isShiftKeyDown() ? 1 : -1;
        index = MathUtils.cycler(index, 0, this.panel.panels.size() - 1);

        this.panel.buttons.elements.get(index).clickItself(GuiBase.getCurrent());
    }

    @Override
    public boolean shouldPause()
    {
        return false;
    }

    private void openMorphs(int i, boolean editing)
    {
        AbstractMorph morph = this.defaultMorphSlot;

        if (i == 2)
        {
            morph = this.firingMorphSlot;
        }
        else if (i == 3)
        {
            morph = this.projectileMorphSlot;
        }
        else if (i == 4)
        {
            morph = this.impactMorphSlot;
        }
        else if (i == 5)
        {
            morph = this.handsMorphSlot;
        }
        else if (i == 6)
        {
            morph = this.zoomOverlayMorphSlot;
        }
        else if (i == 7)
        {
            morph = this.inventoryMorphSlot;
        }
        else if (i == 8)
        {
            morph = this.reloadMorphSlot;
        }
        else if (i == 9)
        {
            morph = this.crosshairMorphSlot;
        }

        if (this.morphs.hasParent())
        {
            if (i == this.index)
            {
                return;
            }
            else
            {
                this.morphs.finish();
                this.morphs.removeFromParent();
            }
        }

        this.index = i;
        this.morphs.resize();
        this.morphs.setSelected(morph);

        if (editing)
        {
            this.morphs.enterEditMorph();
        }

        this.root.add(this.morphs);
    }

    private void setMorph(AbstractMorph morph)
    {
        if (this.index == 1)
        {
            this.defaultMorphSlot = morph;
            this.props.defaultMorph = this.morphToNbt(morph);
            this.props.setCurrent(MorphUtils.copy(morph));
            this.pickDefault.setMorph(morph);
        }
        else if (this.index == 2)
        {
            this.firingMorphSlot = morph;
            this.props.firingMorph = this.morphToNbt(morph);
            this.pickFiring.setMorph(morph);
        }
        else if (this.index == 3)
        {
            this.projectileMorphSlot = morph;
            this.props.projectileMorph = this.morphToNbt(morph);
            this.pickProjectile.setMorph(morph);

            if (this.bullet.projectile != null)
            {
                this.bullet.projectile.morph.setDirect(this.projectileMorphSlot);
            }
        }
        else if (this.index == 4)
        {
            this.impactMorphSlot = morph;
            this.props.impactMorph = this.morphToNbt(morph);

            this.pickImpact.setMorph(morph);
        }
        else if (this.index == 5)
        {
            this.handsMorphSlot = morph;
            this.props.handsMorph = this.morphToNbt(morph);
            this.props.setHandsMorph(MorphUtils.copy(morph));
            this.pickHandsMorph.setMorph(morph);
        }
        else if (this.index == 6)
        {
            this.zoomOverlayMorphSlot = morph;
            this.props.zoomOverlayMorph = this.morphToNbt(morph);
            this.props.setCurrentZoomOverlay(MorphUtils.copy(morph));
            this.pickZoomOverlayMorph.setMorph(morph);
        }
        else if (this.index == 7)
        {
            this.inventoryMorphSlot = morph;
            this.props.inventoryMorph = this.morphToNbt(morph);
            this.props.setInventoryMorph(MorphUtils.copy(morph));
            this.pickInventoryMorph.setMorph(morph);
        }
        else if (this.index == 8)
        {
            this.reloadMorphSlot = morph;
            this.props.reloadMorph = this.morphToNbt(morph);
            this.pickReloadMorph.setMorph(morph);
        }
        else if (this.index == 9)
        {
            this.crosshairMorphSlot = morph;
            this.props.crosshairMorph = this.morphToNbt(morph);
            this.props.setCrosshairMorph(MorphUtils.copy(morph));
            this.pickCrosshairMorph.setMorph(morph);
        }
    }

    /**
     * Parse a raw props morph slot into a live morph for editing/preview.
     * Total: a malformed/unregistered slot yields {@code null} rather than
     * throwing ({@code morphFromNBT} mutates its tag, so a copy is passed).
     */
    private AbstractMorph morphFrom(NbtCompound tag)
    {
        if (tag == null)
        {
            return null;
        }

        try
        {
            return MorphManager.INSTANCE.morphFromNBT(tag.copy());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** Serialize an edited slot morph back to the props NBT slot (null → null). */
    private NbtCompound morphToNbt(AbstractMorph morph)
    {
        return morph == null ? null : morph.toNBT();
    }

    /** Parse a raw props transform slot into a live, editable transform. */
    private ModelTransform transformFrom(NbtCompound tag)
    {
        ModelTransform transform = new ModelTransform();

        if (tag != null)
        {
            transform.fromNBT(tag);
        }

        return transform;
    }

    /**
     * Write the three live transforms back into the props NBT slots. Legacy
     * edited the shared {@code ModelTransform} objects in place, so the render
     * cache previewed edits live and the close-time {@code toNBT} already saw
     * them; here we push the same edits back to the raw slots every frame (and
     * before close) to reproduce that. A default transform serializes to an
     * empty compound, which {@code GunProps.toNBT} omits — matching legacy.
     */
    private void syncTransforms()
    {
        this.props.gunTransform = this.gunTransform.toNBT();
        this.props.gunTransformFirstPerson = this.gunTransformFirstPerson.toNBT();
        this.props.projectileTransform = this.projectileTransform.toNBT();
    }

    @Override
    protected void closeScreen()
    {
        super.closeScreen();

        this.props.storedDurability = (int) this.durability.value;
        this.syncTransforms();

        this.sendGunInfo(new PacketGunInfo(this.props.toNBT(), 0));
    }

    /**
     * Outbound close-sync packet seam. Production sends it to the server;
     * headless tests override to capture it.
     */
    protected void sendGunInfo(PacketGunInfo packet)
    {
        Dispatcher.sendToServer(packet);
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks)
    {
        /* Keep the render cache / close-sync in step with live transform edits */
        this.syncTransforms();

        this.context.drawContext = drawContext;
        GuiDraw.bindDrawContext(drawContext);

        GuiDraw.drawCustomBackground(0, 0, this.width, this.height);

        GuiDraw.drawRect(0, 0, this.width, 35, ColorUtils.HALF_BLACK);
        GuiDraw.drawVerticalGradientRect(0, 35, this.width, 45, ColorUtils.HALF_BLACK, 0);
        GuiDraw.drawString(this.context.font, IKey.lang("blockbuster.gui.gun.title").get(), 10, 15, 0xffffffff, true);

        PlayerEntity player = this.context.mc == null ? null : this.context.mc.player;
        int w = this.viewport.w / 4;

        if (this.panel.view.delegate == this.gunOptions)
        {
            if (this.defaultMorphSlot != null)
            {
                this.defaultMorphSlot.renderOnScreen(player, this.pickDefault.area.mx(), this.pickDefault.area.y - 20, w * 0.5F, 1);
            }
            if (this.firingMorphSlot != null)
            {
                this.firingMorphSlot.renderOnScreen(player, this.pickFiring.area.mx(), this.pickFiring.area.y - 20, w * 0.5F, 1);

                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
            }

            GuiDraw.drawCenteredString(this.context.font, IKey.lang("blockbuster.gui.gun.default_morph").get(), this.pickDefault.area.mx(), this.pickFiring.area.y - 12, 0xffffff);
            GuiDraw.drawCenteredString(this.context.font, IKey.lang("blockbuster.gui.gun.fire_morph").get(), this.pickFiring.area.mx(), this.pickFiring.area.y - 12, 0xffffff);
            GuiDraw.drawCenteredString(this.context.font, IKey.lang("blockbuster.gui.gun.delay").get(), this.delay.area.mx(), this.delay.area.y - 12, 0xffffff);
            GuiDraw.drawCenteredString(this.context.font, IKey.lang("blockbuster.gui.gun.scatter").get(), this.scatterX.area.ex() + 3, this.scatterX.area.y - 12, 0xffffff);
            GuiDraw.drawCenteredString(this.context.font, IKey.lang("blockbuster.gui.gun.projectiles").get(), this.projectiles.area.mx(), this.projectiles.area.y - 12, 0xffffff);

            GuiDraw.drawString(this.context.font, IKey.lang("blockbuster.gui.gun.fire_command").get(), this.fireCommand.area.x, this.fireCommand.area.y - 12, 0xffffff, true);
        }
        else if (this.panel.view.delegate == this.projectileOptions)
        {
            if (this.projectileMorphSlot != null)
            {
                this.projectileMorphSlot.renderOnScreen(player, this.pickProjectile.area.mx(), this.pickProjectile.area.y - 20, w * 0.5F, 1);
            }

            GuiDraw.drawCenteredString(this.context.font, IKey.lang("blockbuster.gui.gun.projectile_morph").get(), this.pickProjectile.area.mx(), this.pickProjectile.area.y - 12, 0xffffff);

            GuiDraw.drawString(this.context.font, IKey.lang("blockbuster.gui.gun.tick_command").get(), this.tickCommand.area.x, this.tickCommand.area.y - 12, 0xffffff, true);
        }
        else if (this.panel.view.delegate == this.impactOptions)
        {
            if (this.impactMorphSlot != null)
            {
                this.impactMorphSlot.renderOnScreen(player, this.pickImpact.area.mx(), this.pickImpact.area.y - 20, w * 0.5F, 1);
            }

            GuiDraw.drawCenteredString(this.context.font, IKey.lang("blockbuster.gui.gun.impact_morph").get(), this.pickImpact.area.mx(), this.pickImpact.area.y - 12, 0xffffff);

            GuiDraw.drawString(this.context.font, IKey.lang("blockbuster.gui.gun.impact_command").get(), this.impactCommand.area.x, this.impactCommand.area.y - 12, 0xffffff, true);
            GuiDraw.drawString(this.context.font, IKey.lang("blockbuster.gui.gun.impact_entity_command").get(), this.impactEntityCommand.area.x, this.impactEntityCommand.area.y - 12, 0xffffff, true);
            GuiDraw.drawString(this.context.font, IKey.lang("blockbuster.gui.gun.vanish_command").get(), this.vanishCommand.area.x, this.vanishCommand.area.y - 12, 0xffffff, true);
        }

        super.render(drawContext, mouseX, mouseY, partialTicks);

        if (this.panel.view.delegate == this.transformOptions)
        {
            String gun = IKey.lang("blockbuster.gui.gun.gun_transforms").get();
            String trans = IKey.lang("blockbuster.gui.gun.projectile_transforms").get();

            GuiDraw.drawTextBackground(this.context.font, gun, this.gun.area.mx(GuiDraw.textWidth(this.context.font, gun)), this.arms.area.y + 15, 0xffffff, ColorUtils.HALF_BLACK);
            GuiDraw.drawTextBackground(this.context.font, trans, this.projectile.area.mx(GuiDraw.textWidth(this.context.font, trans)), this.arms.area.y + 15, 0xffffff, ColorUtils.HALF_BLACK);
        }
    }

    public static class GuiProjectileModelRenderer extends GuiModelRenderer
    {
        public EntityGunProjectile projectile;

        public GuiProjectileModelRenderer(MinecraftClient mc)
        {
            super(mc);

            /* Legacy: new EntityGunProjectile(mc.world) with null props, then
             * props/morph assigned by the host. The port's projectile requires a
             * world; a headless (unit) context has none, so the projectile stays
             * null and the preview simply doesn't render (totality). */
            if (mc != null && mc.world != null)
            {
                this.projectile = new EntityGunProjectile(mc.world, null, null);
            }
        }

        @Override
        protected void drawUserModel(GuiContext context)
        {
            if (this.projectile == null || this.mc == null)
            {
                return;
            }

            if (this.projectile.props != null)
            {
                this.projectile.ticksExisted = this.projectile.props.fadeIn;
            }

            EntityRenderDispatcher dispatcher = this.mc.getEntityRenderDispatcher();
            VertexConsumerProvider.Immediate immediate = this.mc.getBufferBuilders().getEntityVertexConsumers();

            dispatcher.setRenderShadows(false);
            dispatcher.render(this.projectile, 0, 0.5F, 0, 0, context.partialTicks, new MatrixStack(), immediate, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            immediate.draw();
            dispatcher.setRenderShadows(true);

            /* Direction indicator (legacy immediate-mode: black width-5 then
             * green width-3 line to (0,0,0.25), and a black/white origin point).
             * Core profile has no glBegin — reproduced on BufferBuilder with the
             * same colors/widths/order; the origin point is two nested cubes. */
            MatrixStack matrices = new MatrixStack();

            matrices.translate(0, 0.5F, 0);

            this.drawDirection(matrices);

            RenderSystem.enableDepthTest();
        }

        private void drawDirection(MatrixStack matrices)
        {
            try
            {
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.getBuffer();

                RenderSystem.disableDepthTest();
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                RenderSystem.lineWidth(5);
                buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                buffer.vertex(matrix, 0, 0, 0).color(0F, 0F, 0F, 1F).next();
                buffer.vertex(matrix, 0, 0, 0.25F).color(0F, 0F, 0F, 1F).next();
                tessellator.draw();

                RenderSystem.lineWidth(3);
                buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                buffer.vertex(matrix, 0, 0, 0).color(0F, 1F, 0F, 1F).next();
                buffer.vertex(matrix, 0, 0, 0.25F).color(0F, 1F, 0F, 1F).next();
                tessellator.draw();

                RenderSystem.lineWidth(1);

                /* Origin marker: 12px black point under a 10px white point. */
                this.drawCube(matrices, 0.012F, 0F, 0F, 0F, 1F);
                this.drawCube(matrices, 0.010F, 1F, 1F, 1F, 1F);
            }
            catch (Exception e)
            {
                /* No GL context — the indicator simply doesn't render. */
            }
        }

        private void drawCube(MatrixStack matrices, float r, float red, float green, float blue, float alpha)
        {
            Matrix4f m = matrices.peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            /* Top */
            buffer.vertex(m, -r, r, -r).color(red, green, blue, alpha).next();
            buffer.vertex(m, -r, r, r).color(red, green, blue, alpha).next();
            buffer.vertex(m, r, r, r).color(red, green, blue, alpha).next();
            buffer.vertex(m, r, r, -r).color(red, green, blue, alpha).next();

            /* Bottom */
            buffer.vertex(m, -r, -r, -r).color(red, green, blue, alpha).next();
            buffer.vertex(m, r, -r, -r).color(red, green, blue, alpha).next();
            buffer.vertex(m, r, -r, r).color(red, green, blue, alpha).next();
            buffer.vertex(m, -r, -r, r).color(red, green, blue, alpha).next();

            /* Left */
            buffer.vertex(m, -r, r, -r).color(red, green, blue, alpha).next();
            buffer.vertex(m, -r, -r, -r).color(red, green, blue, alpha).next();
            buffer.vertex(m, -r, -r, r).color(red, green, blue, alpha).next();
            buffer.vertex(m, -r, r, r).color(red, green, blue, alpha).next();

            /* Right */
            buffer.vertex(m, r, r, -r).color(red, green, blue, alpha).next();
            buffer.vertex(m, r, r, r).color(red, green, blue, alpha).next();
            buffer.vertex(m, r, -r, r).color(red, green, blue, alpha).next();
            buffer.vertex(m, r, -r, -r).color(red, green, blue, alpha).next();

            /* Front */
            buffer.vertex(m, -r, r, -r).color(red, green, blue, alpha).next();
            buffer.vertex(m, r, r, -r).color(red, green, blue, alpha).next();
            buffer.vertex(m, r, -r, -r).color(red, green, blue, alpha).next();
            buffer.vertex(m, -r, -r, -r).color(red, green, blue, alpha).next();

            /* Back */
            buffer.vertex(m, -r, r, r).color(red, green, blue, alpha).next();
            buffer.vertex(m, -r, -r, r).color(red, green, blue, alpha).next();
            buffer.vertex(m, r, -r, r).color(red, green, blue, alpha).next();
            buffer.vertex(m, r, r, r).color(red, green, blue, alpha).next();

            tessellator.draw();
        }
    }

    public static class GuiGunPanels extends GuiPanelBase<GuiElement>
    {
        public GuiGunPanels(MinecraftClient mc)
        {
            super(mc);
        }

        @Override
        protected void drawBackground(GuiContext context, int x, int y, int w, int h)
        {
            GuiDraw.drawRect(x, y, x + w, y + h, 0xff080808);
        }
    }
}
