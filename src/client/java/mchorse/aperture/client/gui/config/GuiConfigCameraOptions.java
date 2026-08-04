package mchorse.aperture.client.gui.config;

import mchorse.aperture.Aperture;
import mchorse.aperture.ClientProxy;
import mchorse.aperture.client.gui.GuiCameraEditor;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.IGuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiColorElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTexturePicker;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

/**
 * P186 — Aperture's own section of the camera editor config popup.
 *
 * <p>Every toggle is bound straight to its {@code Aperture} config value, so a
 * click both flips the option and persists it; the three that need more than a
 * flip carry callbacks (outside mode attaches/detaches the runner camera and
 * re-syncs the player position, flight routes through
 * {@link GuiCameraEditor#setFlight(boolean)}, aspect ratio re-parses the
 * string). The editor's F / S / O / L keybinds click these very elements, which
 * is why they have to be the single source of truth for those modes.</p>
 *
 * <p>Quirks kept: {@code essentialsTeleport} is only added in multiplayer (an
 * integrated server never needs the Essentials {@code /tp} workaround), so the
 * child list is shorter in singleplayer; every {@link GuiToggleElement} is
 * re-flexed to 16px height <b>after</b> the adds, including the conditionally
 * added one; the overlay picker is parented to the editor's {@code top}
 * on demand rather than living in the column.</p>
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/config/GuiConfigCameraOptions.java</p>
 */
public class GuiConfigCameraOptions extends GuiAbstractConfigOptions
{
    public GuiToggleElement outside;
    public GuiToggleElement spectator;
    public GuiToggleElement renderPath;
    public GuiToggleElement sync;
    public GuiToggleElement flight;
    public GuiToggleElement displayPosition;
    public GuiToggleElement essentialsTeleport;
    public GuiColorElement guidesColor;
    public GuiToggleElement ruleOfThirds;
    public GuiToggleElement centerLines;
    public GuiToggleElement crosshair;
    public GuiToggleElement letterBox;
    public GuiTextElement aspectRatio;
    public GuiToggleElement loop;
    public GuiToggleElement overlay;
    public GuiButtonElement pickOverlay;
    public GuiTexturePicker overlayPicker;
    public GuiToggleElement hideChat;

    public GuiConfigCameraOptions(MinecraftClient mc, GuiCameraEditor editor)
    {
        super(mc, editor);

        this.outside = new GuiToggleElement(mc, Aperture.outside, (b) ->
        {
            if (b.isToggled())
            {
                ClientProxy.runner.attachOutside();
                this.editor.updatePlayerCurrently();
            }
            else
            {
                ClientProxy.runner.detachOutside();
            }
        });

        this.spectator = new GuiToggleElement(mc, Aperture.spectator);
        this.renderPath = new GuiToggleElement(mc, Aperture.profileRender);
        this.sync = new GuiToggleElement(mc, Aperture.editorSync);

        this.flight = new GuiToggleElement(mc, IKey.lang("aperture.gui.config.flight"), this.editor.flight.isFlightEnabled(), (b) ->
        {
            this.editor.setFlight(b.isToggled());
        });
        this.flight.tooltip(IKey.lang("aperture.gui.config.flight_tooltip"));

        this.displayPosition = new GuiToggleElement(mc, Aperture.editorDisplayPosition);
        this.essentialsTeleport = new GuiToggleElement(mc, Aperture.essentialsTeleport);
        this.guidesColor = new GuiColorElement(mc, Aperture.editorGuidesColor);
        this.guidesColor.picker.editAlpha();
        this.ruleOfThirds = new GuiToggleElement(mc, Aperture.editorRuleOfThirds);
        this.centerLines = new GuiToggleElement(mc, Aperture.editorCenterLines);
        this.crosshair = new GuiToggleElement(mc, Aperture.editorCrosshair);
        this.letterBox = new GuiToggleElement(mc, Aperture.editorLetterbox);
        this.aspectRatio = new GuiTextElement(mc, Aperture.editorLetterboxAspect, this.editor::setAspectRatio);
        this.aspectRatio.setText(Aperture.editorLetterboxAspect.get());

        this.loop = new GuiToggleElement(mc, Aperture.editorLoop);
        this.overlay = new GuiToggleElement(mc, Aperture.editorOverlay);

        this.pickOverlay = new GuiButtonElement(mc, IKey.lang("aperture.gui.config.pick_overlay"), (b) ->
        {
            this.overlayPicker.refresh();
            this.overlayPicker.fill(this.editor.overlayLocation);
            this.overlayPicker.resize();

            this.editor.top.add(this.overlayPicker);
        });

        this.overlayPicker = new GuiTexturePicker(mc, (rl) ->
        {
            Aperture.editorOverlayRL.set(rl);
            this.editor.updateOverlay();
        });
        this.overlayPicker.flex().relative(this.editor.viewport).wh(1F, 1F);
        this.hideChat = new GuiToggleElement(mc, Aperture.editorHideChat);

        this.add(this.outside, this.spectator, this.renderPath, this.sync, this.flight, this.displayPosition, this.guidesColor, this.ruleOfThirds, this.centerLines, this.crosshair, this.letterBox, this.aspectRatio, this.loop, this.overlay, this.pickOverlay, this.hideChat);

        /* Legacy `!mc.isSingleplayer()` — the Essentials teleport workaround is
         * a multiplayer-only concern */
        if (mc != null && !mc.isIntegratedServerRunning())
        {
            this.add(this.essentialsTeleport);
        }

        for (IGuiElement element : this.getChildren())
        {
            if (element instanceof GuiToggleElement)
            {
                ((GuiElement) element).flex().h(16);
            }
        }
    }

    @Override
    public void update()
    {
        this.outside.toggled(Aperture.outside.get());
        this.spectator.toggled(Aperture.spectator.get());
        this.renderPath.toggled(Aperture.profileRender.get());
        this.sync.toggled(Aperture.editorSync.get());
        this.loop.toggled(Aperture.editorLoop.get());
        this.flight.toggled(this.editor.flight.isFlightEnabled());
        this.displayPosition.toggled(Aperture.editorDisplayPosition.get());
        this.essentialsTeleport.toggled(Aperture.essentialsTeleport.get());
        this.guidesColor.picker.setColor(Aperture.editorGuidesColor.get());
        this.ruleOfThirds.toggled(Aperture.editorRuleOfThirds.get());
        this.centerLines.toggled(Aperture.editorCenterLines.get());
        this.crosshair.toggled(Aperture.editorCrosshair.get());
        this.letterBox.toggled(Aperture.editorLetterbox.get());
        this.aspectRatio.setText(Aperture.editorLetterboxAspect.get());
        this.overlay.toggled(Aperture.editorOverlay.get());
        this.hideChat.toggled(Aperture.editorHideChat.get());
    }

    @Override
    public IKey getTitle()
    {
        return IKey.lang("aperture.gui.config.title");
    }
}
