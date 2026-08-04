package mchorse.blockbuster.client.gui.dashboard.panels.scene;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanel;
import mchorse.blockbuster.common.BlockbusterPermissions;
import mchorse.blockbuster.common.item.ItemPlayback;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.client.recording.ClientHandlerFramesLoad;
import mchorse.blockbuster.network.client.recording.ClientHandlerFramesOverwrite;
import mchorse.blockbuster.network.common.PacketPlaybackButton;
import mchorse.blockbuster.network.common.recording.PacketUpdatePlayerData;
import mchorse.blockbuster.network.common.scene.PacketSceneCast;
import mchorse.blockbuster.network.common.scene.PacketScenePause;
import mchorse.blockbuster.network.common.scene.PacketScenePlayback;
import mchorse.blockbuster.network.common.scene.PacketSceneRecord;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiCollapseSection;
import mchorse.mclib.client.gui.framework.elements.GuiDelegateElement;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringListElement;
import mchorse.mclib.client.gui.framework.elements.modals.GuiModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiPopUpModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiPromptModal;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.GuiLabel;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.permissions.PermissionCategory;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.MathUtils;
import mchorse.mclib.utils.OpHelper;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.creative.GuiNestedEdit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Port of Blockbuster 2.7.2's {@code GuiScenePanel} (roadmap S11 P133) — the
 * director/scene dashboard panel: config options (title, start/stop command,
 * loops, audio), the replay strip + replay editor, scene manager pop-out, and
 * the transport methods driven by the scene-control keybinds (P133.1).
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/scene/GuiScenePanel.java</p>
 *
 * <p>Complete as of <b>P135.1</b> (2026-07-24): the last three stubs closed —
 * the rotation filter's server round-trip ({@link #rotationFilter()}), the
 * record button's clickable-chat injection ({@link #sendRecordMessage()}), and
 * the four in-GUI keybinds (N scene list, O config options, T teleport, C
 * camera editor; T/C replay-gated and hung off their own buttons, exactly as
 * legacy). {@link #openRecordEditor()} and {@link #attach()} closed earlier
 * with P138 and P185.1.</p>
 *
 * <p>Deliberate deviations, both in {@link #sendRecordMessage()}: legacy
 * hand-rolled the "don't repeat the last chat-history entry" check against the
 * raw sent-messages list; 1.20.4's {@code ChatHud.addToMessageHistory} makes
 * exactly that check itself, so we delegate — and inherit vanilla's 100-entry
 * history cap plus its command-history-file append for slash-prefixed lines.
 * Both are what typing the command by hand does.</p>
 *
 * <p>Everything else — the {@code openScene}/{@code setScene}/{@code set}
 * three-way branch, the replay-index fallback rule, the replay CRUD, the
 * duplicate-id label colouring, {@link #updatePlayerData()} — is a faithful
 * port. Headless coverage: {@code GuiScenePanelTest} (state machine),
 * {@code GuiScenePanelBehaviorTest} (chat command/component, keybinds,
 * rotation-filter guard), {@code RecordRequestRpcTest} (the filter's network
 * leg). Visual parity — the replay strip, the 0.55-height morph preview, the
 * {@code 0xff3355} duplicate-id label — stays a P146 checklist row.</p>
 */
public class GuiScenePanel extends GuiBlockbusterPanel
{
    private GuiElement subChildren;
    private GuiDelegateElement<GuiElement> mainView;
    private GuiElement replays;
    private GuiElement replayEditor;
    private GuiElement configOptions;
    private GuiReplaySelector selector;

    /* Config fields */
    public GuiTextElement title;
    public GuiTextElement startCommand;
    public GuiTextElement stopCommand;
    public GuiToggleElement loops;

    public GuiStringListElement audio;
    public GuiTrackpadElement audioShift;
    public GuiIconElement openAudioFolder;

    /* Replay fields */
    public GuiTextElement id;
    public GuiTextElement name;
    public GuiTextElement target;
    public GuiToggleElement playbackXPFood;
    public GuiToggleElement invincible;
    public GuiToggleElement invisible;
    public GuiToggleElement enableBurning;
    public GuiToggleElement enabled;
    public GuiToggleElement fake;
    public GuiToggleElement teleportBack;
    public GuiToggleElement renderLast;
    public GuiTrackpadElement health;
    public GuiTrackpadElement foodLevel;
    public GuiTrackpadElement totalExperience;

    public GuiNestedEdit pickMorph;

    public GuiButtonElement record;
    public GuiButtonElement rename;
    public GuiButtonElement attach;
    public GuiButtonElement camera;
    public GuiButtonElement teleport;
    public GuiCollapseSection eulerFilter;
    public GuiCirculateElement eulerFilterChannel;
    public GuiTrackpadElement eulerFilterFrom;
    public GuiTrackpadElement eulerFilterTo;
    public GuiButtonElement eulerFilterExecute;

    public GuiLabel recordingId;

    public GuiSceneManager scenes;

    private SceneLocation location = new SceneLocation();
    private Replay replay;

    private IKey noneAudioTrack = IKey.lang("blockbuster.gui.director.none");

    public GuiScenePanel(MinecraftClient mc, GuiDashboard dashboard)
    {
        super(mc, dashboard);

        this.selector = new GuiReplaySelector(mc, (replay) -> this.setReplay(replay.get(0)));
        this.selector.flex().set(0, 0, 0, 60).relative(this).w(1, -20).y(1, -60);

        GuiElement left = new GuiElement(mc);
        GuiElement right = new GuiElement(mc);

        left.flex().relative(this).w(120).y(20).hTo(this.selector.flex()).column(5).width(100).height(20).padding(10);
        right.flex().relative(this).x(1F).y(20).w(120).hTo(this.selector.flex()).anchorX(1F).column(5).flip().width(100).height(20).padding(10);

        this.subChildren = new GuiElement(mc).noCulling();
        this.subChildren.setVisible(false);
        this.replays = new GuiElement(mc).noCulling();
        this.replayEditor = new GuiElement(mc).noCulling();
        this.replayEditor.setVisible(false);
        this.replayEditor.add(left, right);
        this.configOptions = new GuiElement(mc).noCulling();
        this.mainView = new GuiDelegateElement<GuiElement>(mc, this.replays);
        this.mainView.noCulling();

        this.add(this.subChildren);
        this.subChildren.add(this.mainView);

        /* Config options */
        this.title = new GuiTextElement(mc, 80, (str) -> this.location.getScene().title = str);
        this.startCommand = new GuiTextElement(mc, 10000, (str) -> this.location.getScene().startCommand = str);
        this.stopCommand = new GuiTextElement(mc, 10000, (str) -> this.location.getScene().stopCommand = str);
        this.loops = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.loops"), false, (b) -> this.location.getScene().loops = b.isToggled());

        this.audio = new GuiStringListElement(mc, (value) -> this.location.getScene().setAudio(value.get(0).equals(this.noneAudioTrack.get()) ? "" : value.get(0)));
        this.audio.background().tooltip(IKey.lang("blockbuster.gui.director.audio_tooltip"), Direction.RIGHT);
        this.audioShift = new GuiTrackpadElement(mc, (value) -> this.location.getScene().setAudioShift(value.intValue()));
        this.audioShift.integer().tooltip(IKey.lang("blockbuster.gui.director.audio_shift_tooltip"));
        this.openAudioFolder = new GuiIconElement(mc, Icons.FOLDER, (b) ->
        {
            /* TODO(S9 audio): ClientProxy.audio is null until the client audio
             * lifecycle (P188.1/P189) lands; guard so the button never NPEs. */
            if (ClientProxy.audio != null)
            {
                GuiUtils.openFolder(ClientProxy.audio.folder.getAbsolutePath());
            }
        });
        this.openAudioFolder.tooltip(IKey.lang("blockbuster.gui.director.open_audio_folder"));

        this.title.flex().set(120, 50, 0, 20).relative(this.area).w(1, -130);
        this.startCommand.flex().set(120, 90, 0, 20).relative(this.area).w(1, -130);
        this.stopCommand.flex().set(120, 130, 0, 20).relative(this.area).w(1, -130);

        this.audio.flex().relative(this).xy(10, 50).w(100).hTo(this.stopCommand.area, 1F);
        this.audioShift.flex().relative(this.audio).y(1F, 5).w(1F);
        this.openAudioFolder.flex().relative(this.audio).x(1F, -16).y(-16).wh(16, 16);

        GuiElement row = Elements.row(mc, 5, 0, 20, this.loops);

        row.flex().relative(this.stopCommand).y(25).w(1F);
        this.loops.flex().h(20);

        this.configOptions.add(this.title, this.startCommand, this.stopCommand, row, this.audio, this.audioShift, this.openAudioFolder);

        /* Replay options */
        this.id = new GuiTextElement(mc, 120, (str) ->
        {
            this.replay.id = str;

            this.updateLabel(true);
        }).filename();
        this.name = new GuiTextElement(mc, 80, (str) -> this.replay.name = str);
        this.name.tooltip(IKey.lang("blockbuster.gui.director.name_tooltip"), Direction.RIGHT);
        this.target = new GuiTextElement(mc, 80, (str) -> this.replay.target = str);
        this.target.tooltip(IKey.lang("blockbuster.gui.director.target_tooltip"), Direction.LEFT);
        this.playbackXPFood = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.playback_xp_food_level_enabled"), (b) -> this.replay.playBackXPFood = b.isToggled());
        this.playbackXPFood.tooltip(IKey.lang("blockbuster.gui.director.playback_xp_food_level_tooltip"), Direction.LEFT);
        this.invincible = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.invincible"), false, (b) -> this.replay.invincible = b.isToggled());
        this.invisible = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.invisible"), false, (b) -> this.replay.invisible = b.isToggled());
        this.enableBurning = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.enable_burning"), true, (b) -> this.replay.enableBurning = b.isToggled());
        this.enabled = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.enabled"), false, (b) -> this.replay.enabled = b.isToggled());
        this.fake = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.fake_player"), false, (b) -> this.replay.fake = b.isToggled());
        this.teleportBack = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.tp_back"), false, (b) -> this.replay.teleportBack = b.isToggled());
        this.teleportBack.tooltip(IKey.lang("blockbuster.gui.director.tp_back_tooltip"), Direction.RIGHT);
        this.renderLast = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.render_last"), false, (b) -> this.replay.renderLast = b.isToggled());
        this.renderLast.tooltip(IKey.lang("blockbuster.gui.director.render_last_tooltip"), Direction.RIGHT);
        this.health = new GuiTrackpadElement(mc, (value) -> this.replay.health = value.floatValue());
        this.health.limit(0);
        this.foodLevel = new GuiTrackpadElement(mc, (value) -> this.replay.foodLevel = value.intValue());
        this.foodLevel.limit(0).integer();
        this.totalExperience = new GuiTrackpadElement(mc, (value) -> this.replay.totalExperience = value.intValue());
        this.totalExperience.limit(0).integer();
        this.recordingId = Elements.label(IKey.lang("blockbuster.gui.director.id")).color(0xcccccc);

        left.add(this.recordingId, this.id);
        left.add(Elements.label(IKey.lang("blockbuster.gui.director.name")).color(0xcccccc), this.name);
        left.add(Elements.label(IKey.lang("blockbuster.gui.director.health")).color(0xcccccc), this.health,
                 Elements.label(IKey.lang("blockbuster.gui.director.food_level")).color(0xcccccc), this.foodLevel,
                 Elements.label(IKey.lang("blockbuster.gui.director.total_experience")).color(0xcccccc), this.totalExperience,
                 this.invincible, this.invisible, this.enableBurning, this.enabled, this.fake, this.teleportBack, this.renderLast);
        this.replays.add(this.selector, this.replayEditor);

        /* Toggle view button */
        GuiIconElement toggle = new GuiIconElement(mc, Icons.GEAR, (b) ->
        {
            this.mainView.setDelegate(this.mainView.delegate == this.configOptions ? this.replays : this.configOptions);
        });

        GuiIconElement toggleScenes = new GuiIconElement(mc, Icons.MORE, (b) -> this.scenes.toggleVisible());
        toggleScenes.flex().y(4).relative(this.area).x(1, -24);

        toggle.tooltip(IKey.lang("blockbuster.gui.director.config"), Direction.LEFT);
        toggle.flex().y(4).relative(this.area).x(1, -44);

        this.add(toggleScenes);
        this.subChildren.add(toggle);

        /* Add, duplicate and remove replay buttons */
        GuiIconElement add = new GuiIconElement(mc, Icons.ADD, (b) -> this.addReplay());
        GuiIconElement dupe = new GuiIconElement(mc, Icons.DUPE, (b) -> this.dupeReplay());
        GuiIconElement remove = new GuiIconElement(mc, Icons.REMOVE, (b) -> this.removeReplay());

        add.tooltip(IKey.lang("blockbuster.gui.director.add_replay"), Direction.LEFT);
        dupe.tooltip(IKey.lang("blockbuster.gui.director.dupe_replay"), Direction.LEFT);
        remove.tooltip(IKey.lang("blockbuster.gui.director.remove_replay"), Direction.LEFT);

        add.flex().set(0, 0, 20, 20).relative(this.selector.resizer()).x(1F);
        dupe.flex().set(0, 20, 20, 20).relative(this.selector.resizer()).x(1F);
        remove.flex().set(0, 40, 20, 20).relative(this.selector.resizer()).x(1F);

        this.replays.add(add, dupe, remove);

        /* Additional utility buttons */
        IKey category = IKey.lang("blockbuster.gui.director.keys.category");
        Supplier<Boolean> active = () -> this.replay != null;

        this.pickMorph = new GuiNestedEdit(mc, (editing) ->
        {
            if (BlockbusterClient.panels != null && BlockbusterClient.panels.morphs != null)
            {
                BlockbusterClient.panels.addMorphs(this, editing, this.getReplayMorph());
            }
        });
        this.record = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.record"), (b) -> this.sendRecordMessage());
        GuiButtonElement edit = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.edit_record"), (b) -> this.openRecordEditor());
        GuiButtonElement update = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.update_data"), (b) -> this.updatePlayerData());
        this.rename = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.rename_prefix"), (b) -> this.renamePrefix());
        this.attach = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.attach"), (b) -> this.attach());
        this.teleport = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.tp"), (b) -> this.teleport());
        this.teleport.keys().register(this.teleport.label, LegacyKeyCodes.KEY_T, () -> this.teleport.clickItself(GuiBase.getCurrent())).category(category).active(active);

        this.eulerFilter = new GuiCollapseSection(mc, IKey.lang("blockbuster.gui.director.rotation_filter.title"));
        this.eulerFilter.setCollapsed(true);

        this.eulerFilterFrom = new GuiTrackpadElement(mc, (Consumer<Double>) null);
        this.eulerFilterFrom.limit(0).integer();
        this.eulerFilterFrom.tooltip(IKey.lang("blockbuster.gui.director.rotation_filter.from_tooltip"));

        this.eulerFilterTo = new GuiTrackpadElement(mc, (Consumer<Double>) null);
        this.eulerFilterTo.limit(0).integer();
        this.eulerFilterTo.tooltip(IKey.lang("blockbuster.gui.director.rotation_filter.to_tooltip"));

        this.eulerFilterChannel = new GuiCirculateElement(mc, null);
        this.eulerFilterChannel.addLabel(IKey.lang("blockbuster.gui.director.rotation_filter.head_yaw"));
        this.eulerFilterChannel.addLabel(IKey.lang("blockbuster.gui.director.rotation_filter.head_pitch"));
        this.eulerFilterChannel.addLabel(IKey.lang("blockbuster.gui.director.rotation_filter.body_yaw"));
        this.eulerFilterChannel.tooltip(IKey.lang("blockbuster.gui.director.rotation_filter.channel_tooltip"));

        this.eulerFilterExecute = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.rotation_filter.execute"), (b) -> this.rotationFilter());

        this.eulerFilter.addFields(this.eulerFilterChannel, this.eulerFilterFrom, this.eulerFilterTo, this.eulerFilterExecute);

        update.tooltip(IKey.lang("blockbuster.gui.director.update_data_tooltip"), Direction.LEFT);
        this.rename.tooltip(IKey.lang("blockbuster.gui.director.rename_prefix_tooltip"), Direction.LEFT);
        this.attach.tooltip(IKey.lang("blockbuster.gui.director.attach_tooltip"), Direction.LEFT);
        this.teleport.tooltip(IKey.lang("blockbuster.gui.director.tp_tooltip"), Direction.LEFT);

        this.pickMorph.flex().relative(this.selector).x(0.5F).y(-10).w(100).anchor(0.5F, 1F);

        right.add(this.attach, this.record, update, this.rename, edit);

        if (CameraHandler.isApertureLoaded())
        {
            this.camera = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.camera"), (b) ->
            {
                /* P185.1: stash the scene the camera editor syncs to, then open
                 * it — the editor's Init handler reads CameraHandler.get() */
                CameraHandler.location = this.location;
                CameraHandler.openCameraEditor();
            });

            this.camera.keys().register(this.camera.label, LegacyKeyCodes.KEY_C, () -> this.camera.clickItself(GuiBase.getCurrent())).category(category).active(active);

            right.add(this.camera);
        }

        right.add(this.teleport, this.eulerFilter);
        right.add(Elements.label(IKey.lang("blockbuster.gui.director.target")).color(0xcccccc).marginTop(12), this.target, Elements.label(IKey.lang("blockbuster.gui.director.playback_xp_food_level")), this.playbackXPFood);

        this.replayEditor.add(this.pickMorph);

        /* Scene manager */
        this.add(this.scenes = new GuiSceneManager(mc, this));
        this.scenes.flex().relative(toggleScenes).xy(1F, 1F).w(160).hTo(this.selector.flex()).anchorX(1F);
        this.scenes.setVisible(false);

        this.keys().register(IKey.lang("blockbuster.gui.director.keys.toggle_list"), LegacyKeyCodes.KEY_N, () -> toggleScenes.clickItself(GuiBase.getCurrent())).category(category);
        this.keys().register(IKey.lang("blockbuster.gui.director.keys.toggle_options"), LegacyKeyCodes.KEY_O, () -> toggle.clickItself(GuiBase.getCurrent())).category(category);
    }

    @Override
    public PermissionCategory getRequiredPermission()
    {
        return BlockbusterPermissions.openScene;
    }

    public SceneLocation getLocation()
    {
        return this.location;
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    public List<Replay> getReplays()
    {
        if (this.location.isEmpty())
        {
            return null;
        }

        return this.getLocation().getScene().replays;
    }

    public GuiScenePanel openScene(SceneLocation location)
    {
        this.scenes.setVisible(false);

        return this.setScene(location);
    }

    public GuiScenePanel setScene(SceneLocation location)
    {
        this.location = location == null ? new SceneLocation() : location;

        this.subChildren.setVisible(!this.location.isEmpty());
        this.replayEditor.setVisible(!this.location.isEmpty());
        this.scenes.setScene(this.location.getScene());

        if (this.location.isEmpty())
        {
            this.setReplay(null);

            return this;
        }

        this.selector.setList(this.location.getScene().replays);

        if (!this.location.getScene().replays.isEmpty())
        {
            int current = this.location.getScene().replays.indexOf(this.replay);

            this.setReplay(this.location.getScene().replays.get(current == -1 ? 0 : current));
        }
        else
        {
            this.setReplay(null);
        }

        this.fillData();

        return this;
    }

    public GuiScenePanel set(SceneLocation location)
    {
        this.location = location;
        this.scenes.setScene(location.getScene());

        return this;
    }

    protected void rotationFilter()
    {
        int fromTest = Math.min((int) this.eulerFilterFrom.value, (int) this.eulerFilterTo.value);
        int toTest = Math.max((int) this.eulerFilterFrom.value, (int) this.eulerFilterTo.value);

        if (toTest - fromTest + 1 < 2)
        {
            this.addPopUpModal(IKey.lang("blockbuster.gui.director.rotation_filter.not_enough_frames"));

            return;
        }

        /* Wait for the requested recording to return from the server. The two
         * client helpers legacy hung off the server handlers as
         * @SideOnly(CLIENT) statics live on the client handlers here — see
         * ClientHandlerFramesLoad.requestRecording. */
        ClientHandlerFramesLoad.requestRecording(this.replay.id, (record) ->
        {
            Frame.RotationChannel channel = Frame.RotationChannel.values()[this.eulerFilterChannel.getValue()];

            if (record == null)
            {
                this.addPopUpModal(IKey.lang("blockbuster.gui.director.rotation_filter.record_not_loaded"));

                return;
            }

            int from = MathUtils.clamp(Math.min((int) this.eulerFilterFrom.value, (int) this.eulerFilterTo.value), 0, record.frames.size() - 1);
            int to = MathUtils.clamp(Math.max((int) this.eulerFilterFrom.value, (int) this.eulerFilterTo.value), 0, record.frames.size() - 1);

            List<Frame> frames = RecordUtils.discontinuityEulerFilter(record.frames, from, to, channel);

            if (frames.isEmpty())
            {
                this.addPopUpModal(IKey.lang("blockbuster.gui.director.rotation_filter.empty_filtered_frames"));

                return;
            }

            ClientHandlerFramesOverwrite.sendFramesToServer(record.filename, frames, from, to, (obj) ->
            {
                this.addPopUpModal(obj.getKey());
            });
        });
    }

    private void addPopUpModal(IKey lang)
    {
        GuiPopUpModal modal = new GuiPopUpModal(this.mc, lang);
        modal.flex().relative(this.parent).wh(200, 50);
        modal.setFadeDuration(0);
        modal.resize();

        this.add(modal);
    }

    @Override
    public void appear()
    {
        super.appear();

        if (BlockbusterClient.panels != null && BlockbusterClient.panels.morphs != null)
        {
            BlockbusterClient.panels.picker(this::setMorph);
        }

        if (!this.location.isEmpty())
        {
            this.setScene(this.location);
        }
    }

    @Override
    public void open()
    {
        if (BlockbusterClient.panels != null && BlockbusterClient.panels.morphs != null)
        {
            BlockbusterClient.panels.morphs.reload();
        }

        this.setScene(this.location);
        this.scenes.setScene(this.location.getScene());
        this.scenes.updateSceneList();
    }

    @Override
    public void close()
    {
        if (!OpHelper.isPlayerOp())
        {
            return;
        }

        if (this.location.isScene())
        {
            if (BlockbusterClient.panels != null && BlockbusterClient.panels.morphs != null
                && BlockbusterClient.panels.morphs.hasParent())
            {
                BlockbusterClient.panels.morphs.finish();
            }

            Dispatcher.sendToServer(new PacketSceneCast(this.location));
        }
    }

    private void setReplay(Replay replay)
    {
        if (this.replay != null)
        {
            /* Legacy MorphUtils.copy(this.replay.morph) — the morph is opaque NBT
             * here (P127 seam), so the deep copy is the tag copy. */
            this.replay.morph = this.replay.morph == null ? null : this.replay.morph.copy();
        }

        this.replay = replay;
        this.replayEditor.setVisible(this.replay != null);
        this.mainView.setDelegate(this.replays);
        this.selector.setCurrent(replay);
        this.fillReplayData();
    }

    private void fillData()
    {
        this.title.setText(this.location.getScene().title);
        this.startCommand.setText(this.location.getScene().startCommand);
        this.stopCommand.setText(this.location.getScene().stopCommand);
        this.loops.toggled(this.location.getScene().loops);
        this.attach.setEnabled(false);

        this.audio.clear();
        this.audio.add(this.noneAudioTrack.get());

        /* TODO(S9 audio): ClientProxy.audio is null until the client audio
         * lifecycle lands; the .wav library populates the list here. */
        if (ClientProxy.audio != null)
        {
            this.audio.add(ClientProxy.audio.getFileNames());
        }

        this.audio.sort();

        String audio = this.location.getScene().getAudio();

        this.audio.setCurrentScroll(audio == null || audio.isEmpty() ? this.noneAudioTrack.get() : audio);

        this.audioShift.setValue(this.location.getScene().getAudioShift());

        if (this.mc != null && this.mc.player != null)
        {
            ItemStack stack = this.mc.player.getMainHandStack();

            this.attach.setEnabled(!this.location.isEmpty() && stack.getItem() instanceof ItemPlayback);
        }
    }

    private void fillReplayData()
    {
        if (this.replay == null)
        {
            return;
        }

        this.id.setText(this.replay.id);
        this.name.setText(this.replay.name);
        this.target.setText(this.replay.target);
        this.playbackXPFood.toggled(this.replay.playBackXPFood);
        this.invincible.toggled(this.replay.invincible);
        this.invisible.toggled(this.replay.invisible);
        this.enableBurning.toggled(this.replay.enableBurning);
        this.enabled.toggled(this.replay.enabled);
        this.fake.toggled(this.replay.fake);
        this.teleportBack.toggled(this.replay.teleportBack);
        this.renderLast.toggled(this.replay.renderLast);
        this.health.setValue(this.replay.health);
        this.foodLevel.setValue(this.replay.foodLevel);
        this.totalExperience.setValue(this.replay.totalExperience);

        this.pickMorph.setMorph(this.getReplayMorph());

        this.selector.setCurrent(this.replay);
        this.updateLabel(false);
    }

    /**
     * Add an empty replay
     */
    private void addReplay()
    {
        Replay replay = new Replay("");

        if (this.location.isScene())
        {
            replay.id = this.location.getScene().getNextBaseSuffix(this.location.getScene().getId());
        }

        this.location.getScene().replays.add(replay);
        this.setReplay(replay);
        this.selector.update();
    }

    /**
     * Duplicate a replay
     */
    private void dupeReplay()
    {
        if (this.selector.isDeselected())
        {
            return;
        }

        Scene scene = this.location.getScene();

        if (scene.dupe(scene.replays.indexOf(this.replay)))
        {
            this.selector.update();
            this.selector.scroll.scrollTo(this.selector.getIndex() * this.selector.scroll.scrollItemSize);
            this.setReplay(scene.replays.get(scene.replays.size() - 1));
        }
    }

    /**
     * Remove replay
     */
    private void removeReplay()
    {
        if (this.selector.isDeselected())
        {
            return;
        }

        Scene scene = this.location.getScene();
        int index = this.selector.getIndex();

        scene.replays.remove(this.replay);

        int size = scene.replays.size();
        index = MathHelper.clamp(index, 0, size - 1);

        this.setReplay(size == 0 ? null : scene.replays.get(index));
        this.selector.update();
    }

    /** The current replay's morph (legacy read {@code this.replay.morph}). */
    private AbstractMorph getReplayMorph()
    {
        return this.replay == null ? null : this.replay.morph;
    }

    /**
     * Morph-picker callback (legacy {@code setMorph}): write the chosen morph
     * back onto the replay and mirror it into the {@link #pickMorph} widget.
     */
    private void setMorph(AbstractMorph morph)
    {
        if (this.replay != null)
        {
            this.replay.morph = morph;
        }

        this.pickMorph.setMorph(morph);
    }

    /**
     * update the labels
     * @param gui true when this is called by a callback from a GuiElement
     */
    private void updateLabel(boolean gui)
    {
        boolean error = this.replay != null && this.replay.id.isEmpty();

        this.recordingId.color(error ? 0xff3355 : 0xcccccc);

        if (this.replay != null && !this.replay.id.isEmpty() && gui)
        {
            boolean isDuplicate = false;

            for (Replay element : this.scenes.parent.getLocation().getScene().replays)
            {
                if (element.id.equals(this.replay.id) && this.replay != element)
                {
                    isDuplicate = true;

                    break;
                }
            }

            if (isDuplicate)
            {
                GuiModal.addModal(this, () ->
                {
                    GuiPopUpModal modal = new GuiPopUpModal(this.mc, IKey.lang("blockbuster.gui.director.rename_replay_dupe_modal"));
                    modal.flex().relative(this.parent).wh(220, 50);

                    return modal;
                });
            }
        }
    }

    /**
     * Send record message to the player
     */
    private void sendRecordMessage()
    {
        PlayerEntity player = this.mc.player;

        if (this.replay.id.isEmpty())
        {
            Blockbuster.l10n.error(player, "recording.fill_filename");

            return;
        }

        String command = recordCommand(this.replay.id, this.location.getFilename());

        Blockbuster.l10n.info(player, "recording.message", this.replay.id, recordClickHere(command));

        /* Add the command to the history. Legacy hand-rolled the
         * "don't repeat the last entry" check against the raw sent-messages
         * list; 1.20.4's addToMessageHistory performs exactly that check
         * itself (peekLast().equals(message)), so the semantics carry over.
         * Two vanilla-side additions ride along and are deliberate: the
         * history caps at 100 entries, and a leading-slash message is also
         * appended to the modern command-history file — both are what typing
         * the command by hand would do. */
        this.mc.inGameHud.getChatHud().addToMessageHistory(command);
    }

    /**
     * The chat command the record button offers — {@code /action record
     * <replay id> <scene file>}. Extracted from {@link #sendRecordMessage()}
     * so the exact string is headless-testable.
     */
    public static String recordCommand(String replayId, String sceneFilename)
    {
        return "/action record " + replayId + " " + sceneFilename;
    }

    /**
     * Legacy's clickable "click here" chat component: underlined gray, running
     * the command on click, showing it verbatim on hover. Legacy resolved the
     * label client-side through {@code I18n.format} into a <b>literal</b>
     * component (not a translatable one) — kept, since the server never sees
     * this text.
     */
    public static Text recordClickHere(String command)
    {
        return Text.literal(I18n.translate("blockbuster.info.recording.clickhere")).setStyle(Style.EMPTY
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(command)))
            .withColor(Formatting.GRAY)
            .withUnderline(true));
    }

    /**
     * Legacy "Attach" — hand the held playback button off to the P185.1 screen
     * where a scene (and camera mode/profile) gets bound to it. The else-branch
     * is legacy's no-Aperture path; Aperture is bundled now, so it is
     * unreachable, and is kept for diff-ability.
     */
    private void attach()
    {
        if (CameraHandler.isApertureLoaded())
        {
            CameraHandler.attach(this.location, this.scenes.sceneList.getList());
        }
        else
        {
            Dispatcher.sendToServer(new PacketPlaybackButton(this.location, 0, ""));

            this.mc.setScreen(null);
        }
    }

    private void openRecordEditor()
    {
        /* Legacy "Edit record": switch the dashboard to the recording-editor
         * panel, request this replay's record actions from the server, and
         * hide the record list (the user is editing one specific record). The
         * empty-id guard is legacy — a blank replay id has no record file. */
        if (this.replay != null && !this.replay.id.isEmpty())
        {
            this.dashboard.panels.setPanel(BlockbusterClient.panels.recordingEditorPanel);
            BlockbusterClient.panels.recordingEditorPanel.selectRecord(this.replay.id);
            BlockbusterClient.panels.recordingEditorPanel.records.setVisible(false);
        }
    }

    private void updatePlayerData()
    {
        if (this.replay == null)
        {
            return;
        }

        Dispatcher.sendToServer(new PacketUpdatePlayerData(this.replay.id));
    }

    private void teleport()
    {
        if (this.replay == null)
        {
            return;
        }

        this.mc.setScreen(null);

        try
        {
            RecordUtils.applyFrameOnEntity(this.mc.player, ClientProxy.manager.get(this.replay.id), 0);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void renamePrefix()
    {
        GuiModal.addModal(this.replayEditor, () ->
        {
            GuiPromptModal modal = new GuiPromptModal(this.mc, IKey.lang("blockbuster.gui.director.rename_prefix_popup"), this::renamePrefix);

            modal.markIgnored().flex().relative(this.rename).y(1F).w(1F).h(120);

            return modal;
        });
    }

    private void renamePrefix(String newPrefix)
    {
        this.location.getScene().renamePrefix(newPrefix);
        this.fillReplayData();
    }

    @Override
    public void draw(GuiContext context)
    {
        if (this.scenes.isVisible())
        {
            int x = this.scenes.area.ex() - 20;
            int y = this.scenes.area.y - 20;

            GuiDraw.drawRect(x, y, x + 20, y + 20, ColorUtils.HALF_BLACK);
        }

        /* Draw additional stuff */
        if (this.mainView.delegate == this.replays)
        {
            GuiDraw.drawRect(this.selector.area.x, this.selector.area.y, this.selector.area.ex() + 20, this.selector.area.ey(), ColorUtils.HALF_BLACK);
            GuiDraw.drawVerticalGradientRect(this.selector.area.x, this.selector.area.y - 16, this.selector.area.ex() + 20, this.selector.area.y, 0, ColorUtils.HALF_BLACK);

            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.scenes.title"), this.area.x + 10, this.area.y + 10, 0xffffff);

            if (this.replay != null && this.mc != null && this.mc.player != null)
            {
                AbstractMorph morph = this.getReplayMorph();

                if (morph != null)
                {
                    int mx = this.area.mx();
                    int my = this.area.y(0.55F);

                    GuiDraw.scissor(this.area.x, this.area.y, this.area.w, this.area.h, context);

                    try
                    {
                        morph.renderOnScreen(this.mc.player, mx, my, this.area.h / 3.5F, 1.0F);
                    }
                    catch (Exception e)
                    {
                        morph.errorRendering = true;
                    }
                    finally
                    {
                        GuiDraw.unscissor(context);
                    }
                }
            }
        }
        else
        {
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.director.config"), this.area.x + 10, this.area.y + 10, 0xffffff);

            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.director.audio"), this.audio.area.x, this.audio.area.y - 12, 0xcccccc);
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.director.start_command"), this.startCommand.area.x, this.startCommand.area.y - 12, 0xcccccc);
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.director.stop_command"), this.stopCommand.area.x, this.stopCommand.area.y - 12, 0xcccccc);
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.director.display_title"), this.title.area.x, this.title.area.y - 12, 0xcccccc);
        }

        if (this.location.isEmpty())
        {
            String no = I18n.translate("blockbuster.gui.director.not_selected");

            GuiDraw.drawCenteredString(this.font, no, this.area.mx(), this.area.my() - 6, 0xffffff);
        }

        super.draw(context);
    }

    public void plause()
    {
        if (!OpHelper.isPlayerOp())
        {
            return;
        }

        if (this.location.isScene())
        {
            Dispatcher.sendToServer(new PacketScenePlayback(this.location));
        }
    }

    public void record()
    {
        if (!OpHelper.isPlayerOp())
        {
            return;
        }

        Replay replay = this.replay;

        if (replay != null && !replay.id.isEmpty() && this.location.isScene())
        {
            Dispatcher.sendToServer(new PacketSceneRecord(this.location, replay.id));
        }
    }

    public void pause()
    {
        if (!OpHelper.isPlayerOp())
        {
            return;
        }

        if (this.location.isScene())
        {
            Dispatcher.sendToServer(new PacketScenePause(this.location));
        }
    }

}
