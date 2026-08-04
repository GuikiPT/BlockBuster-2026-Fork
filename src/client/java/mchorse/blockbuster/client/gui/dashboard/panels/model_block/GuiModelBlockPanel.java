package mchorse.blockbuster.client.gui.dashboard.panels.model_block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.client.gui.GuiImmersiveEditor;
import mchorse.blockbuster.client.gui.GuiImmersiveMorphMenu;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanel;
import mchorse.blockbuster.client.render.tileentity.ModelBlockTransform;
import mchorse.blockbuster.common.BlockbusterPermissions;
import mchorse.blockbuster.common.block.BlockModel;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.common.tileentity.TileEntityModelSettings;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketModifyModelBlock;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiSlotElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTransformations;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.permissions.PermissionCategory;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.mclib.utils.MatrixUtils.RotationOrder;
import mchorse.mclib.utils.MatrixUtils.Transformation;
import mchorse.mclib.utils.OpHelper;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.creative.GuiNestedEdit;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Model block dashboard panel (roadmap P136).
 *
 * <p>Port of 1.12.2
 * {@code client/gui/dashboard/panels/model_block/GuiModelBlockPanel.java}: pick
 * a placed {@link TileEntityModel} from the recently-edited list, live-edit its
 * morph / transformations / entity angles / settings / equipment slots, and
 * save the result to the server (OP-gated). Field names, widget layout, the
 * static {@link #lastBlocks} cache and its dedupe, the save-gating rules and the
 * inverted rotation-order button mapping are legacy-exact.</p>
 *
 * <p><b>Load-bearing quirks (recorded for P146):</b></p>
 * <ul>
 * <li><b>Inverted rotation-order button mapping.</b> Only two of the six
 * {@link RotationOrder}s are reachable from the two-label circulate: button
 * value {@code 0} ("ZYX") ↔ ordinal {@code 5}, button value {@code 1} ("XYZ") ↔
 * ordinal {@code 0}. The four other orders still load from NBT and leave the
 * button unchanged — see {@link #orderButtonValueForOrdinal(int)} /
 * {@link #orderIndexForButtonValue(int)}.</li>
 * <li><b>Same-BlockPos save skip.</b> {@link #save(TileEntityModel, boolean)}
 * skips (unless forced) when the incoming model is {@code null}/identical or
 * shares the current model's {@link BlockPos} — replacing a model block in-world
 * and reopening the panel therefore does <i>not</i> persist the old edits — see
 * {@link #shouldSkipSave}.</li>
 * <li><b>Uniform-scale OFF copies {@code sx} into {@code sy}/{@code sz}</b> (not
 * the reverse) — {@link GuiModelBlockTransformations}.</li>
 * <li>The {@code Look} button uses the <i>player's</i> yaw
 * ({@code 180 - player.getYaw()}).</li>
 * </ul>
 *
 * <p><b>P143 morph picking (landed).</b> The {@code pickMorph}
 * {@link GuiNestedEdit} button, {@code BlockbusterClient.panels
 * .picker/addMorphs/showImmersiveEditor}, and the four immersive callbacks
 * ({@link #updateMorphEditor}, {@link #beforeEditorRender},
 * {@link #afterEditorRender}, {@link #afterEditorClose}) are wired 1:1 with
 * legacy. Two GL-era translations: legacy's
 * {@code ClientProxy.modelRenderer.transform(te)} on the global
 * {@code GlStateManager} matrix becomes {@link ModelBlockTransform#apply} on
 * {@link RenderSystem#getModelViewStack()} (the stack the GUI model renderer
 * itself pushes around these hooks), and {@code getTileEntity} becomes
 * {@code getBlockEntity}.</p>
 */
public class GuiModelBlockPanel extends GuiBlockbusterPanel
{
    public static final List<BlockPos> lastBlocks = new ArrayList<BlockPos>();

    private TileEntityModel model;

    private GuiTrackpadElement yaw;
    private GuiTrackpadElement pitch;
    private GuiTrackpadElement body;

    private GuiModelBlockTransformations trans;

    /** The nested morph editor button (legacy {@code pickMorph}). */
    private GuiNestedEdit pickMorph;

    private GuiCirculateElement order;
    private GuiToggleElement shadow;
    private GuiToggleElement global;
    private GuiToggleElement enabled;
    private GuiToggleElement excludeResetPlayback;
    private GuiToggleElement renderLast;
    private GuiToggleElement renderAlways;
    private GuiToggleElement enableBlockHitbox;
    private GuiTrackpadElement lightLevel;

    private GuiModelBlockList list;
    private GuiElement subChildren;

    private GuiSlotElement[] slots = new GuiSlotElement[6];

    private Map<BlockPos, TileEntityModel> old = new HashMap<BlockPos, TileEntityModel>();

    /** Immersive-editing morph stash (see {@link #setMorph}). */
    private AbstractMorph morph;

    private boolean opened;

    /**
     * Try adding a block position, if it doesn't exist in list already.
     */
    public static void tryAddingBlock(BlockPos pos)
    {
        for (BlockPos stored : lastBlocks)
        {
            if (pos.equals(stored))
            {
                return;
            }
        }

        lastBlocks.add(pos);
    }

    /**
     * Inverted rotation-order button mapping (fill direction): map a
     * {@link RotationOrder} ordinal to the circulate button value, or {@code -1}
     * to leave the button unchanged (the four unreachable orders). Ordinal 5
     * ("ZYX") → 0, ordinal 0 ("XYZ") → 1. Pure; headless-testable.
     */
    public static int orderButtonValueForOrdinal(int ordinal)
    {
        if (ordinal == 5)
        {
            return 0;
        }
        else if (ordinal == 0)
        {
            return 1;
        }

        return -1;
    }

    /**
     * Inverted rotation-order button mapping (apply direction): button value 0 →
     * {@link RotationOrder} index 5, any other value → index 0. Pure;
     * headless-testable.
     */
    public static int orderIndexForButtonValue(int buttonValue)
    {
        return buttonValue == 0 ? 5 : 0;
    }

    /**
     * The non-OP-gated portion of the save-gating decision (legacy
     * {@link #save(TileEntityModel, boolean)}): whether the packet should be
     * skipped. Pure; headless-testable. The OP check is applied separately in
     * {@link #save(TileEntityModel, boolean)} — a non-op never saves at all.
     */
    public static boolean shouldSkipSave(TileEntityModel current, TileEntityModel model, boolean force)
    {
        if (force)
        {
            return false;
        }

        if (current == null || current == model)
        {
            return true;
        }

        if (model != null && current.getPos().equals(model.getPos()))
        {
            return true;
        }

        return false;
    }

    public GuiModelBlockPanel(MinecraftClient mc, GuiDashboard dashboard)
    {
        super(mc, dashboard);

        this.subChildren = new GuiElement(mc).noCulling();
        this.subChildren.setVisible(false);
        this.add(this.subChildren);

        /* Transformations */
        this.trans = new GuiModelBlockTransformations(mc);
        this.trans.flex().relative(this).x(0.5F, 42).y(1F, -10).wh(250, 70).anchor(0.5F, 1F);

        this.subChildren.add(this.trans);

        /* Entity angles */
        this.subChildren.add(this.yaw = new GuiTrackpadElement(mc, (value) -> this.model.getSettings().setRotateYawHead(value.floatValue())));
        this.yaw.tooltip(IKey.lang("blockbuster.gui.model_block.yaw"));
        this.subChildren.add(this.pitch = new GuiTrackpadElement(mc, (value) -> this.model.getSettings().setRotatePitch(value.floatValue())));
        this.pitch.tooltip(IKey.lang("blockbuster.gui.model_block.pitch"));
        this.subChildren.add(this.body = new GuiTrackpadElement(mc, (value) -> this.model.getSettings().setRotateBody(value.floatValue())));
        this.body.tooltip(IKey.lang("blockbuster.gui.model_block.body"));

        this.yaw.flex().set(-85, 0, 80, 20).relative(this.trans);
        this.pitch.flex().set(0, 25, 80, 20).relative(this.yaw.resizer());
        this.body.flex().set(0, 25, 80, 20).relative(this.pitch.resizer());

        this.subChildren.add(this.order = new GuiCirculateElement(mc, (b) ->
        {
            this.model.getSettings().setOrder(RotationOrder.values()[orderIndexForButtonValue(this.order.getValue())]);
        }));
        this.order.addLabel(IKey.str("ZYX"));
        this.order.addLabel(IKey.str("XYZ"));
        this.order.flex().relative(this.trans.rx).set(40, -22, 40, 20);

        /* Buttons */
        GuiElement column = new GuiElement(mc);

        column.flex().relative(this).w(120).column(5).vertical().stretch().height(20).padding(10);

        this.pickMorph = new GuiNestedEdit(mc, (editing) ->
        {
            if (Blockbuster.immersiveModelBlock.get())
            {
                GuiImmersiveEditor editor = BlockbusterClient.panels.showImmersiveEditor(editing, this.model.morph.get());

                editor.morphs.updateCallback = this::updateMorphEditor;
                editor.morphs.beforeRender = this::beforeEditorRender;
                editor.morphs.afterRender = this::afterEditorRender;
                editor.onClose = this::afterEditorClose;

                /* Avoid update. */
                this.morph = this.model.morph.get();
                this.model.morph.setDirect(MorphUtils.copy(this.morph));
            }
            else
            {
                BlockbusterClient.panels.addMorphs(this, editing, this.model.morph.get());
            }
        });

        GuiButtonElement look = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.model_block.look"), (button) ->
        {
            this.model.getSettings().setRy(180 - this.mc.player.getYaw());
            this.fillData();
        });

        this.shadow = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.model_block.shadow"), false, (button) -> this.model.getSettings().setShadow(button.isToggled()));

        this.global = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.model_block.global"), false, (button) -> this.model.getSettings().setGlobal(button.isToggled()));
        this.global.tooltip(IKey.lang("blockbuster.gui.model_block.global_tooltip"), Direction.BOTTOM);

        this.enabled = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.model_block.enabled"), false, (button) -> this.model.getSettings().setEnabled(button.isToggled()));
        this.enabled.tooltip(IKey.lang("blockbuster.gui.model_block.enabled_tooltip"), Direction.BOTTOM);

        this.excludeResetPlayback = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.model_block.exlude_reset_playback"), false, (button) -> this.model.getSettings().setExcludeResetPlayback(button.isToggled()));
        this.excludeResetPlayback.tooltip(IKey.lang("blockbuster.gui.model_block.exlude_reset_playback_tooltip"), Direction.BOTTOM);

        this.renderLast = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.model_block.render_last"), false, (button) -> this.model.getSettings().setRenderLast(button.isToggled()));
        this.renderLast.tooltip(IKey.lang("blockbuster.gui.model_block.render_last_tooltip"), Direction.BOTTOM);

        this.renderAlways = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.model_block.render_always"), false, (button) -> this.model.getSettings().setRenderAlways(button.isToggled()));
        this.renderAlways.tooltip(IKey.lang("blockbuster.gui.model_block.render_always_tooltip"), Direction.BOTTOM);

        this.enableBlockHitbox = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.model_block.enable_hitbox"), false, (b) -> this.model.getSettings().setEnableBlockHitbox(b.isToggled()));
        this.enableBlockHitbox.tooltip(IKey.lang("blockbuster.gui.model_block.enable_hitbox_tooltip"), Direction.BOTTOM);

        this.lightLevel = new GuiTrackpadElement(mc, (value) ->
        {
            this.model.getSettings().setLightValue(value.intValue());

            BlockEntity te = this.model;
            World world = te.getWorld();
            BlockPos pos = te.getPos();

            /* Live relighting: flag 2 (Block.NOTIFY_LISTENERS) parity with the
             * legacy setBlockState(..., 2). The pre-flattening BlockModel.LIGHT
             * metadata became a 0..15 blockstate property (P95) — this writes
             * the state property, not block metadata. */
            world.setBlockState(pos, world.getBlockState(pos).with(BlockModel.LIGHT, this.model.getSettings().getLightValue()), Block.NOTIFY_LISTENERS);
        });
        this.lightLevel.integer().limit(0, 15);
        this.lightLevel.tooltip(IKey.lang("blockbuster.gui.model_block.light_level_tooltip"));

        /* pickMorph is the first child added to the column (legacy order). */
        column.add(this.pickMorph, look, this.shadow, this.global, this.enabled, this.excludeResetPlayback, this.renderLast, this.renderAlways, this.enableBlockHitbox, Elements.label(IKey.lang("blockbuster.gui.model_block.light_level")), this.lightLevel);
        this.subChildren.add(column);

        /* Model blocks */
        this.list = new GuiModelBlockList(mc, IKey.lang("blockbuster.gui.model_block.title"), (tile) -> this.setModelBlock(tile.get(0)));
        this.list.flex().relative(this.flex()).set(0, 0, 120, 0).h(1F).x(1F, -120);
        this.add(this.list);

        GuiIconElement toggle = new GuiIconElement(mc, Icons.BLOCK, (b) -> this.list.toggleVisible());
        toggle.flex().set(0, 2, 24, 24).relative(this).x(1F, -28);

        this.add(toggle);

        /* Inventory */
        for (int i = 0; i < this.slots.length; i++)
        {
            final int slot = i;

            this.slots[i] = new GuiSlotElement(mc, i, (stack) -> this.pickItem(stack, slot));
            this.slots[i].flex().relative(this.area).anchor(0.5F, 0.5F);
            this.subChildren.add(this.slots[i]);
        }

        this.slots[0].flex().x(0.5F - 0.125F).y(0.5F, -15);
        this.slots[1].flex().x(0.5F - 0.125F).y(0.5F, 15);
        this.slots[2].flex().x(0.5F + 0.125F).y(0.5F, 45);
        this.slots[3].flex().x(0.5F + 0.125F).y(0.5F, 15);
        this.slots[4].flex().x(0.5F + 0.125F).y(0.5F, -15);
        this.slots[5].flex().x(0.5F + 0.125F).y(0.5F, -45);
    }

    public boolean isOpened()
    {
        return this.opened;
    }

    /**
     * @return true if the provided tileEntityModel reference matches the
     *         reference of the selected model.
     */
    public boolean isSelected(TileEntityModel tileEntityModel)
    {
        return this.model == tileEntityModel;
    }

    @Override
    public PermissionCategory getRequiredPermission()
    {
        return BlockbusterPermissions.editModelBlock;
    }

    @Override
    public boolean needsBackground()
    {
        return false;
    }

    private void pickItem(ItemStack stack, int slot)
    {
        this.model.getSettings().setSlot(stack, slot);
        this.model.updateEntity();
    }

    /**
     * Morph-picker callback (invoked by the shared creative-morph picker once
     * S4/P143 wires {@code BlockbusterClient.panels.picker(this::setMorph)} in
     * {@link #appear()}). In immersive mode the chosen morph is only stashed —
     * the tile keeps the working COPY until the editor closes; otherwise it is
     * applied directly.
     */
    void setMorph(AbstractMorph morph)
    {
        if (this.model != null)
        {
            if (Blockbuster.immersiveModelBlock.get())
            {
                this.morph = morph;
            }
            else
            {
                this.model.morph.setDirect(morph);
            }
        }

        this.pickMorph.setMorph(morph);
    }

    /**
     * Legacy {@code updateMorphEditor(GuiImmersiveMorphMenu)} — fired every
     * render-tick START while immersively editing: if the world replaced the
     * block entity under our {@link BlockPos} (a re-place, a chunk reload), swap
     * onto the new {@link TileEntityModel}; then point the menu at the tile's
     * dummy entity so the preview morph renders on it.
     */
    private void updateMorphEditor(GuiImmersiveMorphMenu menu)
    {
        if (this.model == null)
        {
            return;
        }

        BlockEntity te = this.model.getWorld().getBlockEntity(this.model.getPos());

        if (te != this.model)
        {
            if (te instanceof TileEntityModel)
            {
                this.setModelBlock((TileEntityModel) te);
            }
        }

        menu.target = this.model.entity;
    }

    /**
     * Legacy {@code beforeEditorRender(GuiContext)}:
     * {@code GlStateManager.pushMatrix(); ClientProxy.modelRenderer.transform(model)}.
     * The 1.20.4 analog of the legacy global GL matrix is
     * {@link RenderSystem#getModelViewStack()} — the same stack
     * {@link mchorse.mclib.client.gui.framework.elements.GuiModelRenderer} pushes
     * its orbit onto around these hooks — and the transform itself is the shared
     * {@link ModelBlockTransform#apply} extracted from the model-block BER.
     */
    private void beforeEditorRender(GuiContext context)
    {
        MatrixStack modelView = RenderSystem.getModelViewStack();

        modelView.push();

        if (this.model != null)
        {
            ModelBlockTransform.apply(modelView, this.model.getSettings());
        }

        RenderSystem.applyModelViewMatrix();
    }

    /** Legacy {@code afterEditorRender(GuiContext)}: {@code GlStateManager.popMatrix()}. */
    private void afterEditorRender(GuiContext context)
    {
        RenderSystem.getModelViewStack().pop();
        RenderSystem.applyModelViewMatrix();
    }

    /**
     * Legacy {@code afterEditorClose(GuiImmersiveEditor)}: put the ORIGINAL morph
     * back on the tile — everything the immersive session edited happened on the
     * working copy installed when the editor opened.
     */
    private void afterEditorClose(GuiImmersiveEditor editor)
    {
        this.model.morph.setDirect(this.morph);
    }

    @Override
    public void appear()
    {
        super.appear();

        BlockbusterClient.panels.picker(this::setMorph);
    }

    @Override
    public void open()
    {
        this.opened = true;

        this.updateList();

        /* Resetting the current model block, if it was removed from the world */
        if (this.model != null && this.mc.world.getBlockEntity(this.model.getPos()) == null)
        {
            this.setModelBlock(null);
        }
    }

    @Override
    public void close()
    {
        this.save(null);

        this.opened = false;
    }

    public void save(TileEntityModel model)
    {
        this.save(model, false);
    }

    public void save(TileEntityModel model, boolean force)
    {
        if (!OpHelper.isPlayerOp())
        {
            return;
        }

        if (shouldSkipSave(this.model, model, force))
        {
            return;
        }

        /* Finish + detach any nested morph editor before saving. */
        if (BlockbusterClient.panels.morphs != null && BlockbusterClient.panels.morphs.hasParent())
        {
            BlockbusterClient.panels.morphs.finish();
            BlockbusterClient.panels.morphs.removeFromParent();
        }

        Dispatcher.sendToServer(new PacketModifyModelBlock(this.model.getPos(), this.model));

        if (Blockbuster.modelBlockRestore.get())
        {
            this.old.put(this.model.getPos(), this.model);
        }
    }

    public GuiModelBlockPanel openModelBlock(TileEntityModel model)
    {
        if (model != null && Blockbuster.modelBlockRestore.get() && this.old.containsKey(model.getPos()))
        {
            TileEntityModel old = this.old.get(model.getPos());

            model.copyData(old, false);
        }

        tryAddingBlock(model.getPos());

        this.updateList();
        this.list.setVisible(false);

        return this.setModelBlock(model);
    }

    public GuiModelBlockPanel setModelBlock(TileEntityModel model)
    {
        this.save(model);
        this.list.setCurrent(model);
        this.subChildren.setVisible(model != null);
        this.model = model;
        this.fillData();

        return this;
    }

    private void updateList()
    {
        this.list.clear();

        for (BlockPos pos : lastBlocks)
        {
            this.list.addBlock(pos);
        }

        this.list.setCurrent(this.model);
    }

    private void fillData()
    {
        if (this.model != null)
        {
            this.yaw.setValue(this.model.getSettings().getRotateYawHead());
            this.pitch.setValue(this.model.getSettings().getRotatePitch());
            this.body.setValue(this.model.getSettings().getRotateBody());

            this.trans.set(this.model);

            this.pickMorph.setMorph(this.model.morph.get());

            int buttonValue = orderButtonValueForOrdinal(this.model.getSettings().getOrder().ordinal());

            if (buttonValue != -1)
            {
                this.order.setValue(buttonValue);
            }

            this.shadow.toggled(this.model.getSettings().isShadow());
            this.global.toggled(this.model.getSettings().isGlobal());
            this.enabled.toggled(this.model.getSettings().isEnabled());
            this.excludeResetPlayback.toggled(this.model.getSettings().isExcludeResetPlayback());
            this.renderLast.toggled(this.model.getSettings().isRenderLast());
            this.renderAlways.toggled(this.model.getSettings().isRenderAlways());
            this.enableBlockHitbox.toggled(this.model.getSettings().isBlockHitbox());
            this.lightLevel.setValue(this.model.getSettings().getLightValue());

            for (int i = 0; i < this.slots.length; i++)
            {
                this.slots[i].setStack(this.model.getSettings().getSlots()[i]);
            }
        }
    }

    @Override
    public void draw(GuiContext context)
    {
        if (this.model != null)
        {
            AbstractMorph morph = this.model.morph.get();

            if (morph != null)
            {
                int x = this.area.mx();
                int y = this.area.y + 30;

                int w = Math.max(this.font.getWidth(morph.name), this.font.getWidth(morph.getDisplayName()));

                GuiDraw.drawRect(x - w / 2 - 3, y - 20, x + w / 2 + 3, y, ColorUtils.HALF_BLACK);

                GuiDraw.drawCenteredString(this.font, morph.getDisplayName(), x, y - this.font.fontHeight * 2, 0xffffff);
                GuiDraw.drawCenteredString(this.font, morph.name, x, y - this.font.fontHeight, 0xcccccc);
            }
        }

        if (this.subChildren.isVisible())
        {
            GuiDraw.drawStringWithShadow(this.font, IKey.lang("blockbuster.gui.model_block.entity").get(), this.yaw.area.x + 2, this.yaw.area.y - 12, 0xffffff);
        }
        else if (this.model == null)
        {
            GuiDraw.drawCenteredString(this.font, IKey.lang("blockbuster.gui.model_block.not_selected").get(), this.area.mx(), this.area.my() - 6, 0xffffff);
        }

        super.draw(context);
    }

    /**
     * Model-block transformations sub-widget (roadmap P136).
     *
     * <p>Port of the legacy inner {@code GuiModelBlockTransformations}: edits the
     * tile's {@link TileEntityModelSettings} translate/scale/rotation. Uniform
     * OFF copies {@code sx} into {@code sy}/{@code sz}. {@code prepareRotation} /
     * {@code postRotation} honour the tile's rotation order via matrix multiply
     * in third → second → first index order.</p>
     *
     * <p>Port note: legacy {@code TileEntityModelSettings} implemented McLib's
     * client-only {@code ITransformationObject} and carried {@code addTranslation}.
     * In the Fabric main/client source-set split, the settings class (main) can
     * not reference the client-only {@code GuiTransformations.TransformOrientation};
     * so {@code addTranslation}'s math is inlined here in {@link #localTranslate}
     * (its only caller), operating on the settings via getters/setters. Behaviour
     * is identical.</p>
     */
    public static class GuiModelBlockTransformations extends GuiTransformations
    {
        public TileEntityModel model;

        public GuiModelBlockTransformations(MinecraftClient mc)
        {
            super(mc);

            this.one.callback = (toggle) ->
            {
                boolean one = toggle.isToggled();

                this.model.getSettings().setUniform(one);
                this.updateScaleFields();

                if (!one)
                {
                    this.sy.setValueAndNotify(this.sx.value);
                    this.sz.setValueAndNotify(this.sx.value);
                }
            };
        }

        public void set(TileEntityModel model)
        {
            this.model = model;

            if (model != null)
            {
                this.fillT(model.getSettings().getX(), model.getSettings().getY(), model.getSettings().getZ());
                this.fillS(model.getSettings().getSx(), model.getSettings().getSy(), model.getSettings().getSz());
                this.fillR(model.getSettings().getRx(), model.getSettings().getRy(), model.getSettings().getRz());
                this.one.toggled(model.getSettings().isUniform());
                this.updateScaleFields();
            }
        }

        @Override
        public void setT(double x, double y, double z)
        {
            this.model.getSettings().setX((float) x);
            this.model.getSettings().setY((float) y);
            this.model.getSettings().setZ((float) z);
        }

        @Override
        public void setS(double x, double y, double z)
        {
            this.model.getSettings().setSx((float) x);
            this.model.getSettings().setSy((float) y);
            this.model.getSettings().setSz((float) z);
        }

        @Override
        public void setR(double x, double y, double z)
        {
            this.model.getSettings().setRx((float) x);
            this.model.getSettings().setRy((float) y);
            this.model.getSettings().setRz((float) z);
        }

        @Override
        protected void localTranslate(double x, double y, double z)
        {
            TileEntityModelSettings settings = this.model.getSettings();
            Vector4f trans = new Vector4f((float) x, (float) y, (float) z, 1F);

            if (GuiStaticTransformOrientation.getOrientation() == TransformOrientation.LOCAL)
            {
                float rotX = (float) Math.toRadians(settings.getRx());
                float rotY = (float) Math.toRadians(settings.getRy());
                float rotZ = (float) Math.toRadians(settings.getRz());

                MatrixUtils.getRotationMatrix(rotX, rotY, rotZ, settings.getOrder()).transform(trans);
            }

            settings.setX(settings.getX() + trans.x);
            settings.setY(settings.getY() + trans.y);
            settings.setZ(settings.getZ() + trans.z);

            this.fillT(settings.getX(), settings.getY(), settings.getZ());
        }

        @Override
        protected void prepareRotation(Matrix4f mat)
        {
            RotationOrder order = RotationOrder.valueOf(this.model.getSettings().getOrder().toString());
            float[] rot = new float[] {(float) this.rx.value, (float) this.ry.value, (float) this.rz.value};
            Matrix4f trans = new Matrix4f();
            trans.setIdentity();
            trans.set(Transformation.getRotationMatrix(order.thirdIndex, rot[order.thirdIndex]));
            mat.mul(trans);
            trans.set(Transformation.getRotationMatrix(order.secondIndex, rot[order.secondIndex]));
            mat.mul(trans);
            trans.set(Transformation.getRotationMatrix(order.firstIndex, rot[order.firstIndex]));
            mat.mul(trans);
        }

        @Override
        protected void postRotation(Transformation transform)
        {
            Vector3f result = transform.getRotation(RotationOrder.valueOf(this.model.getSettings().getOrder().toString()), new Vector3f((float) this.rx.value, (float) this.ry.value, (float) this.rz.value));
            this.rx.setValueAndNotify(result.x);
            this.ry.setValueAndNotify(result.y);
            this.rz.setValueAndNotify(result.z);
        }
    }
}
