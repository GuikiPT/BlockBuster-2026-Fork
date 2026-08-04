package mchorse.chameleon.mclib;

import mchorse.chameleon.Chameleon;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.config.gui.ConfigGuiProviders;
import mchorse.mclib.config.gui.GuiConfigPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;

import java.util.Arrays;
import java.util.List;

/**
 * Client-side GUI contribution for Chameleon's config-panel button row.
 *
 * <p>Same split as {@code BlockbusterConfigButtons} (P210): the value node
 * ({@link ValueButtons}) lives in the main source set so the config file has the
 * same shape on a dedicated server, and the widgets — which need client-only
 * McLib types — are keyed on that class in the client
 * {@link ConfigGuiProviders} registry here.</p>
 *
 * <p>The three URLs are locale-overridable exactly as legacy: a translation for
 * {@code chameleon.gui.url.*} replaces the built-in link, so a localized wiki can
 * be pointed at. Legacy's own implementation of that check was broken
 * ({@code key.equals(key)} is always true, so the default always won) — the port
 * compares the <i>translated</i> string against the key, which is the check that
 * was meant and the one every other mchorse mod uses.</p>
 */
public class ChameleonConfigButtons
{
    /**
     * Wire the button-row factory into the client value→widget registry, keyed
     * on the main-source value class {@code Chameleon.onConfigRegister}
     * registers. Idempotent-safe (re-registering replaces the same factory).
     */
    public static void register()
    {
        ConfigGuiProviders.register(ValueButtons.class, ChameleonConfigButtons::fields);
    }

    /**
     * general.buttons — two rows: (models, tutorial), (discord, wiki). Verbatim
     * port of the legacy {@code ValueButtons.getFields}.
     */
    public static List<GuiElement> fields(MinecraftClient mc, GuiConfigPanel config, ValueButtons value)
    {
        GuiButtonElement models = new GuiButtonElement(mc, IKey.lang("chameleon.gui.config.models"), (button) -> GuiUtils.openFolder(modelsPath()));
        GuiButtonElement tutorial = new GuiButtonElement(mc, IKey.lang("chameleon.gui.config.tutorial"), (button) -> GuiUtils.openWebLink(getTutorialURL()));
        GuiButtonElement discord = new GuiButtonElement(mc, IKey.lang("chameleon.gui.config.discord"), (button) -> GuiUtils.openWebLink(getDiscordURL()));
        GuiButtonElement wiki = new GuiButtonElement(mc, IKey.lang("chameleon.gui.config.wiki"), (button) -> GuiUtils.openWebLink(getWikiURL()));

        GuiElement first = Elements.row(mc, 5, 0, 20, models, tutorial);
        GuiElement second = Elements.row(mc, 5, 0, 20, discord, wiki);

        return Arrays.asList(first, second);
    }

    private static String modelsPath()
    {
        return Chameleon.modelsFile == null ? "" : Chameleon.modelsFile.getAbsolutePath();
    }

    public static String getTutorialURL()
    {
        return getLangOrDefault("chameleon.gui.url.tutorial", "https://www.youtube.com/playlist?list=PLLnllO8nnzE94k_xh3tqX58_tJzx92NcG");
    }

    public static String getDiscordURL()
    {
        return getLangOrDefault("chameleon.gui.url.discord", "https://discord.gg/qfxrqUF");
    }

    public static String getWikiURL()
    {
        return getLangOrDefault("chameleon.gui.url.wiki", "https://github.com/mchorse/chameleon/wiki");
    }

    private static String getLangOrDefault(String key, String def)
    {
        String string = I18n.translate(key);

        return string.equals(key) ? def : string;
    }
}
