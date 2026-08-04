package mchorse.blockbuster.utils.mclib;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.utils.BlockbusterPaths;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.config.gui.ConfigGuiProviders;
import mchorse.mclib.config.gui.GuiConfigPanel;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Client-side GUI contribution for Blockbuster's two config-panel action rows
 * (roadmap P210), reconciled with P208's main-source value classes.
 *
 * <p>Port split (batch-4 integration, P208 + P210): the {@code buttons} value
 * nodes themselves — {@link ValueMainButtons} (general category) and
 * {@link ValueAudioButtons} (audio category) — live in the <b>main</b> source
 * set (P208) so {@code Blockbuster.onConfigRegister} can register them for
 * byte-identical {@code config/blockbuster/config.json} parity on both client
 * and dedicated server (each serializes as {@code {}}). They carry no GUI
 * method because their button rows need client-only McLib/MC types. Exactly as
 * {@link ValueMainButtons}'s own javadoc describes, the widget contribution is
 * keyed on those classes in the client {@link ConfigGuiProviders} factory
 * registry — this class holds the legacy {@code getFields} bodies and registers
 * them.</p>
 *
 * <p>Two distinct folder roots (easy to mix up): the <b>models</b> button opens
 * {@code config/blockbuster/models} ({@link BlockbusterPaths#models()}, legacy
 * {@code new File(ClientProxy.configFile, "models")}) while the <b>skins</b>
 * button opens the top-level {@code config/blockbuster/skins}
 * ({@link BlockbusterPaths#skins()}, legacy {@code ClientProxy.skinsFolder}).
 * URLs resolve through {@link Blockbuster#WIKI_URL()} &amp; co. (locale
 * overridable, preserved).</p>
 */
public class BlockbusterConfigButtons
{
    /**
     * Wire the two button-row factories into the client value→widget registry,
     * keyed on the main-source value classes {@code Blockbuster.onConfigRegister}
     * registers. Idempotent-safe (re-registering replaces the same factory).
     */
    public static void register()
    {
        ConfigGuiProviders.register(ValueMainButtons.class, BlockbusterConfigButtons::mainFields);
        ConfigGuiProviders.register(ValueAudioButtons.class, BlockbusterConfigButtons::audioFields);
    }

    /**
     * general.buttons — three rows: (models, skins), (tutorial, wiki),
     * (discord + twitter/youtube icons). Verbatim port of the legacy
     * {@code ValueMainButtons.getFields}.
     */
    public static List<GuiElement> mainFields(MinecraftClient mc, GuiConfigPanel config, ValueMainButtons value)
    {
        GuiButtonElement wiki = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.main.wiki"), (button) -> GuiUtils.openWebLink(Blockbuster.WIKI_URL()));
        GuiButtonElement discord = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.main.discord"), (button) -> GuiUtils.openWebLink(Blockbuster.DISCORD_URL()));
        GuiButtonElement tutorial = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.main.tutorial"), (button) -> GuiUtils.openWebLink(Blockbuster.TUTORIAL_URL()));
        GuiButtonElement models = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.main.models"), (button) -> GuiUtils.openFolder(BlockbusterPaths.models().toFile().getAbsolutePath()));
        GuiButtonElement skins = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.main.skins"), (button) -> GuiUtils.openFolder(BlockbusterPaths.skins().toFile().getAbsolutePath()));
        GuiIconElement youtube = new GuiIconElement(mc, BBIcons.YOUTUBE, (button) -> GuiUtils.openWebLink(Blockbuster.CHANNEL_URL()));
        GuiIconElement twitter = new GuiIconElement(mc, BBIcons.TWITTER, (button) -> GuiUtils.openWebLink(Blockbuster.TWITTER_URL()));

        GuiElement first = Elements.row(mc, 5, 0, 20, models, skins);
        GuiElement second = Elements.row(mc, 5, 0, 20, tutorial, wiki);
        GuiElement third = Elements.row(mc, 5, 0, 20, discord, twitter, youtube);

        return Arrays.asList(first, second, third);
    }

    /**
     * audio.buttons — one row: (reset_audio, open_audio). Verbatim port of the
     * legacy {@code ValueAudioButtons.getFields}. Both callbacks null-guard the
     * {@code ClientProxy.audio} holder (populated by the P188.1 client audio
     * lifecycle seam; may still be null on a client that never reached it) —
     * reset no-ops, open falls back to {@code config/blockbuster/audio}.
     */
    public static List<GuiElement> audioFields(MinecraftClient mc, GuiConfigPanel config, ValueAudioButtons value)
    {
        GuiButtonElement resetAudio = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.main.reset_audio"), (button) ->
        {
            if (ClientProxy.audio != null)
            {
                ClientProxy.audio.reset();
            }
        });
        GuiButtonElement openAudio = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.main.open_audio"), (button) -> GuiUtils.openFolder(audioFolder().getAbsolutePath()));

        return Arrays.asList(Elements.row(mc, 5, 0, 20, resetAudio, openAudio));
    }

    /**
     * Legacy {@code ClientProxy.audio.folder}; falls back to the canonical
     * {@code config/blockbuster/audio} path when the library holder is not yet
     * populated.
     */
    private static File audioFolder()
    {
        return ClientProxy.audio != null ? ClientProxy.audio.folder : BlockbusterPaths.audio().toFile();
    }
}
