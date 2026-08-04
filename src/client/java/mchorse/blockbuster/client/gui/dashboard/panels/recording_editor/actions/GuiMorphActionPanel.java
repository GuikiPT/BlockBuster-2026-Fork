package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.client.gui.GuiImmersiveEditor;
import mchorse.blockbuster.client.gui.GuiImmersiveMorphMenu;
import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.scene.sync.PacketSceneGoto;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.actions.MorphAction;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.recording.data.Record.FoundAction;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiColorElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.DummyEntity;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.metamorph.client.gui.creative.GuiCreativeMorphsList;
import mchorse.metamorph.client.gui.creative.GuiCreativeMorphsMenu;
import mchorse.metamorph.client.gui.creative.GuiCreativeMorphsList.OnionSkin;
import mchorse.metamorph.client.gui.creative.GuiNestedEdit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * P139 / P167 — editor for {@link MorphAction}.
 *
 * <p>{@link MorphAction#morph} is a real {@link AbstractMorph} (P167 completed
 * the S9 raw-NBT carrier stub). This panel ports the two self-contained,
 * load-bearing pieces: the scissored morph <b>preview</b> and the deep-<b>copy
 * on disappear</b> ({@link MorphUtils#copy}). The onion-skin colour control is
 * bound to the {@link Blockbuster#morphActionOnionSkinColor} config value
 * ({@code onion_skin.morph_action_color}, {@code 0x7FFFFF00}, colorAlpha); the
 * {@link GuiColorElement} auto-enables alpha editing and seeds its colour from
 * the value's {@code COLOR_ALPHA} subtype. The interactive morph picker
 * ({@code GuiNestedEdit}) and the immersive record-editor integration depend on
 * P143 (Metamorph creative GUI); the onion-skin panel's visibility gates on the
 * S15 Aperture camera-editor seam ({@link #isCameraEditorOpen()}).</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/.../recording_editor/actions/GuiMorphActionPanel.java}.</p>
 */
public class GuiMorphActionPanel extends GuiActionPanel<MorphAction>
{
    public GuiNestedEdit pickMorph;
    public GuiColorElement onionSkin;
    public GuiElement onionSkinPanel;

    private DummyEntity actor;
    private int lastTick;

    private OnionSkin skin;

    private boolean isImmersiveEditing;
    private boolean showRecordList;
    private int cursor;

    public GuiMorphActionPanel(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);

        this.pickMorph = new GuiNestedEdit(mc, this::doNestEdit);
        this.pickMorph.flex().relative(this.area).set(0, 5, 100, 20).x(0.5F, -30);

        this.onionSkin = new GuiColorElement(mc, Blockbuster.morphActionOnionSkinColor);

        this.onionSkinPanel = Elements.column(mc, 10, 5, Elements.label(IKey.lang("blockbuster.config.onion_skin.title")), this.onionSkin);
        this.onionSkinPanel.flex().relative(this.area).x(0F, 10).y(1F, -20).w(150).anchorY(1F);

        this.add(this.pickMorph, this.onionSkinPanel);

        /* Legacy `new DummyEntity(this.mc.world)` — yarn needs an entity type
         * too (the P84 armor-stand host convention), and a world-less client
         * (main menu / headless) simply gets no stand-in actor. */
        this.actor = mc == null || mc.world == null ? null : new DummyEntity(EntityType.ARMOR_STAND, mc.world);
    }

    /**
     * Legacy {@code doNestEdit} — three branches, in legacy order:
     *
     * <ol>
     *   <li>camera editor open <b>and</b> {@code immersive.record_editor} on →
     *       the in-world immersive editor, with this panel's record list and
     *       timeline re-parented onto its outer panel so the recording can be
     *       scrubbed while standing in the scene;</li>
     *   <li>camera editor open, immersive off → the flat picker, with an onion
     *       skin of what the recording is wearing at the current tick;</li>
     *   <li>no camera editor → the flat picker, no onion skin.</li>
     * </ol>
     *
     * <p>Branch 1 detaches the outside camera first and stashes the editor's
     * scrub in {@link #cursor}, because {@link #onImmersiveEditorClose} has to
     * put both back — the immersive editor takes over the camera while it is
     * open and the scene is scrubbed to the edited tick meanwhile.</p>
     */
    public void doNestEdit(boolean editing)
    {
        GuiCreativeMorphsMenu morphs = sharedMorphs();

        if (morphs == null)
        {
            return;
        }

        if (CameraHandler.get() != null && CameraHandler.isCameraEditorOpen())
        {
            this.lastTick = -1;

            if (Blockbuster.immersiveRecordEditor.get())
            {
                this.cursor = Math.max(0, CameraHandler.getOffset());

                CameraHandler.detachOutside();

                GuiImmersiveEditor editor = BlockbusterClient.panels.showImmersiveEditor(editing, this.action.morph);

                editor.morphs.updateCallback = this::updateMorphEditor;
                editor.morphs.frameProvider = this::getFrame;
                editor.onClose = this::onImmersiveEditorClose;

                this.panel.records.removeFromParent();
                this.panel.records.flex().relative(editor.outerPanel);
                this.panel.timeline.removeFromParent();
                this.panel.timeline.flex().relative(editor.outerPanel);

                editor.outerPanel.add(this.panel.records, this.panel.timeline);

                this.addOnionSkin(editor.morphs);

                this.isImmersiveEditing = true;
                this.showRecordList = this.panel.records.isVisible();
                this.panel.records.setVisible(true);
            }
            else
            {
                BlockbusterClient.panels.addMorphs(this, editing, this.action.morph);

                this.addOnionSkin(morphs);
            }
        }
        else
        {
            BlockbusterClient.panels.addMorphs(this, editing, this.action.morph);
        }
    }

    /**
     * Legacy {@code updateMorphEditor}: run once per render tick from the
     * immersive menu, this is what keeps the world in step with the tick being
     * edited.
     *
     * <p>Order matters and is legacy's: (1) resolve the tick — the timeline's
     * current tick, offset by the nested editor's own tick unless the menu is
     * nested; (2) only when it moved, send the scene there and pose the stand-in
     * actor from the record's <b>previous</b> frame ({@code tick - 1}, legacy's
     * off-by-one) — with the record missing, the menu loses its target entirely;
     * (3) re-resolve the target to a live actor playing this very record, else
     * the stand-in; (4) strip the target's morph so the preview is not drawn
     * twice; (5) offset the onion skin into the target's local space.</p>
     */
    public void updateMorphEditor(GuiImmersiveMorphMenu menu)
    {
        Record record = ClientProxy.manager.records.get(this.panel.record == null ? null : this.panel.record.filename);
        int tick = this.panel.timeline.getCurrentTick();

        if (menu.isNested())
        {
            tick = this.lastTick;
        }
        else
        {
            tick += menu.editor.delegate.getCurrentTick();
        }

        if (tick != this.lastTick)
        {
            Dispatcher.sendToServer(new PacketSceneGoto(CameraHandler.get(), tick, CameraHandler.actions.get()));

            if (record != null && record.getFrameSafe(0) != null && this.actor != null)
            {
                record.applyFrame(Math.max(tick - 1, 0), this.actor, true, true);

                Frame frame = record.getFrameSafe(tick - 1);

                if (frame != null && frame.hasBodyYaw)
                {
                    this.actor.bodyYaw = frame.bodyYaw;
                }
            }
            else
            {
                menu.target = null;
            }

            this.lastTick = tick;
        }

        boolean refreshTarget = true;

        if (menu.target != null && menu.target != this.actor && this.isLoaded(menu.target))
        {
            refreshTarget = false;
        }

        if (refreshTarget)
        {
            menu.target = this.findRecordActor();
        }

        if (menu.target instanceof EntityActor actor)
        {
            /* Clear the actor's live morph so the immersive preview is the only
             * thing drawn (legacy `morph.setDirect(null)` — direct, because a
             * `set(null)` would run the merge machinery on the way out). */
            actor.morph.setDirect(null);
        }
        else if (menu.target instanceof PlayerEntity player)
        {
            MorphAPI.morph(player, null, true);
        }

        if (record != null && !record.frames.isEmpty() && this.skin != null && menu.target != null)
        {
            Frame last = record.getFrameSafe(this.panel.timeline.getCurrentTick() - 1);
            LivingEntity actor = menu.target;
            float yaw = actor.getYaw();

            Vec3d pos = new Vec3d(last.x - actor.getX(), last.y - actor.getY(), last.z - actor.getZ());
            pos = pos.rotateY((float) Math.toRadians(yaw));

            this.skin.offset(pos.x, pos.y, pos.z, last.pitch, last.yawHead - yaw, last.bodyYaw - yaw);
        }
    }

    /**
     * The live actor currently playing the record under edit, or the stand-in
     * {@link #actor} when there is none (legacy's {@code getEntities} scan with
     * its first-match break).
     */
    private LivingEntity findRecordActor()
    {
        if (this.mc == null || this.mc.world == null || this.panel.record == null)
        {
            return this.actor;
        }

        for (Entity entity : this.mc.world.getEntities())
        {
            if (!(entity instanceof LivingEntity living) || !living.isAlive())
            {
                continue;
            }

            RecordPlayer player = EntityUtils.getRecordPlayer(living);

            if (player != null && player.record != null && this.panel.record.filename.equals(player.record.filename))
            {
                return living;
            }
        }

        return this.actor;
    }

    /** Legacy {@code world.getLoadedEntityList().contains(target)}. */
    private boolean isLoaded(LivingEntity target)
    {
        if (this.mc == null || this.mc.world == null)
        {
            return false;
        }

        return this.mc.world.getEntityById(target.getId()) == target;
    }

    /**
     * Legacy {@code getFrame}: the frame {@code tick} ticks away from the
     * timeline cursor, used by the immersive menu to draw the record's motion
     * trail. The {@code -1} is the same off-by-one {@code updateMorphEditor}
     * applies.
     */
    public Frame getFrame(int tick)
    {
        Record record = ClientProxy.manager.records.get(this.panel.record == null ? null : this.panel.record.filename);

        if (record != null)
        {
            return record.getFrameSafe(this.panel.timeline.getCurrentTick() + tick - 1);
        }
        else
        {
            return null;
        }
    }

    /**
     * Legacy {@code onImmersiveEditorClose}: restore the record list's
     * visibility, hand the camera back to the camera editor (the position has to
     * be re-seeded <b>before</b> re-attaching the outside camera), re-parent the
     * recording widgets, and scrub the scene back to where the camera editor
     * was.
     */
    public void onImmersiveEditorClose(GuiImmersiveEditor editor)
    {
        this.isImmersiveEditing = false;

        this.panel.records.setVisible(this.showRecordList);

        CameraHandler.updatePlayerPosition();
        CameraHandler.attachOutside();
        CameraHandler.moveRecordPanel(this.panel);

        Dispatcher.sendToServer(new PacketSceneGoto(CameraHandler.get(), this.cursor, CameraHandler.actions.get()));
    }

    /**
     * The shared creative morph menu, or {@code null} when there is no dashboard
     * (headless) or it was torn down on world unload — {@code panels.morphs} is
     * nulled by {@code GuiBlockbusterPanels.onUnregister}, so every access has to
     * tolerate it.
     */
    private static GuiCreativeMorphsMenu sharedMorphs()
    {
        return BlockbusterClient.panels == null ? null : BlockbusterClient.panels.morphs;
    }

    /**
     * Legacy {@code addOnionSkin}: seed the picker's onion-skin list with the
     * morph the recording is wearing at the current timeline tick — the morph
     * from the closest preceding {@link MorphAction}, or the scene replay's
     * morph when there is none.
     */
    public void addOnionSkin(GuiCreativeMorphsList morphs)
    {
        if (this.onionSkinDisabled())
        {
            return;
        }

        List<OnionSkin> skins = new ArrayList<OnionSkin>();
        Record record = this.panel.record;
        Color color = this.onionSkin.picker.color;

        if (record != null)
        {
            FoundAction found = record.seekMorphAction(this.panel.timeline.getCurrentTick(), this.action);
            AbstractMorph morph = null;
            int tick = 0;

            if (found != null)
            {
                morph = found.action.morph;
                tick = found.tick;
            }
            else if (BlockbusterClient.panels != null && BlockbusterClient.panels.scenePanel != null)
            {
                List<Replay> replays = BlockbusterClient.panels.scenePanel.getReplays();

                if (replays != null)
                {
                    for (Replay replay : replays)
                    {
                        if (replay.id.equals(record.filename))
                        {
                            morph = replay.morph;

                            break;
                        }
                    }
                }
            }

            if (morph != null)
            {
                MorphUtils.pause(morph, null, Math.max(0, this.panel.timeline.getCurrentTick() - tick));

                this.skin = new OnionSkin().color(color.r, color.g, color.b, color.a).morph(morph);
                skins.add(this.skin);
            }
        }

        morphs.lastOnionSkins = skins;
    }

    /**
     * Whether the onion skin is disabled by the alpha threshold — legacy
     * disables it below {@code 1/255} (0.003921) alpha.
     */
    public boolean onionSkinDisabled()
    {
        return this.onionSkin.picker.color.a < 0.003921F;
    }

    /**
     * Legacy: the onion-skin panel is only visible while a scene is synced and
     * Aperture's camera editor is open.
     */
    protected boolean isCameraEditorOpen()
    {
        return CameraHandler.get() != null && CameraHandler.isCameraEditorOpen();
    }

    @Override
    public void setMorph(AbstractMorph morph)
    {
        this.action.morph = morph;
        this.pickMorph.setMorph(this.action.morph);
    }

    @Override
    public void fill(MorphAction action)
    {
        super.fill(action);

        GuiCreativeMorphsMenu morphs = sharedMorphs();

        if (morphs != null)
        {
            morphs.removeFromParent();
        }

        this.pickMorph.setMorph(action.morph);

        this.onionSkinPanel.setVisible(this.isCameraEditorOpen());
    }

    @Override
    public void disappear()
    {
        GuiCreativeMorphsMenu morphs = sharedMorphs();

        if (morphs != null)
        {
            morphs.finish();
            morphs.removeFromParent();
        }

        if (this.isImmersiveEditing)
        {
            BlockbusterClient.panels.closeImmersiveEditor();
        }

        /* Load-bearing: deep-copy the morph so any editor that held a live
         * reference cannot keep mutating the record's stored morph. A
         * regression here corrupts saved records during editing. */
        this.action.morph = MorphUtils.copy(this.action.morph);

        super.disappear();
    }

    @Override
    public void draw(GuiContext context)
    {
        AbstractMorph morph = this.action.morph;

        if (morph != null && this.mc != null && this.mc.player != null)
        {
            int x = this.area.mx();
            int y = this.area.y(0.8F);

            GuiDraw.scissor(this.area.x, this.area.y, this.area.w, this.area.h, context);

            try
            {
                MorphRenderUtils.renderOnScreen(morph, this.mc.player, x, y, this.area.h / 3F, 1.0F);
            }
            finally
            {
                GuiDraw.unscissor(context);
            }
        }

        super.draw(context);
    }
}
