package mchorse.blockbuster.client.gui.dashboard;

import mchorse.blockbuster.Blockbuster;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.IGuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.mclib.GuiAbstractDashboard;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.ColorUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Port of Blockbuster 2.7.2's {@code GuiFirstTime} (roadmap P142) — the
 * one-time 200x250 welcome modal shown on any Blockbuster dashboard panel's
 * {@code appear()} until the Done button is pressed.
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/GuiFirstTime.java</p>
 *
 * <p>1.12.2 -&gt; 1.20.4 boundary decisions (all behaviour-preserving):</p>
 * <ul>
 * <li>{@code I18n.format} for the wrapped paragraphs -&gt; {@code IKey.lang(...).get()}
 * (the bundled {@code LangKey}/{@code L10n} translation seam, P8).</li>
 * <li>{@code FontRenderer.listFormattedStringToWidth(text, 180)} -&gt;
 * {@link GuiDraw#listFormattedStringToWidth(String, int, java.util.function.ToIntFunction)}
 * (the same wrap algorithm, parametrised over the font-width function).</li>
 * <li>{@code GlStateManager.pushMatrix/translate/scale} for the 2x title -&gt;
 * the bound {@link DrawContext}'s {@link net.minecraft.client.util.math.MatrixStack}
 * (headless-safe: no-op when no frame is bound).</li>
 * </ul>
 */
public class GuiFirstTime extends GuiElement
{
    public GuiButtonElement close;
    public GuiButtonElement tutorial;
    public GuiButtonElement youtube;
    public GuiButtonElement channel;
    public GuiButtonElement discord;
    public GuiButtonElement twitter;

    private IKey title;
    private List<String> welcome;
    private List<String> social;

    private final Overlay overlay;

    public static boolean shouldOpen()
    {
        return Blockbuster.generalFirstTime.get();
    }

    public static void addOverlay(GuiAbstractDashboard dashboard)
    {
        if (!GuiFirstTime.shouldOpen())
        {
            return;
        }

        boolean alreadyHas = false;

        for (IGuiElement element : dashboard.root.getChildren())
        {
            if (element instanceof GuiFirstTime.Overlay)
            {
                alreadyHas = true;

                break;
            }
        }

        if (!alreadyHas)
        {
            GuiFirstTime.Overlay overlay = new GuiFirstTime.Overlay(MinecraftClient.getInstance());

            overlay.flex().relative(dashboard.viewport).w(1, 0).h(1, 0);
            overlay.resize();
            dashboard.root.add(overlay);
        }
    }

    public GuiFirstTime(MinecraftClient mc, Overlay overlay)
    {
        super(mc);

        this.overlay = overlay;

        this.close = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.done"), (button) -> this.close());
        this.tutorial = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.main.tutorial"), (button) -> GuiUtils.openWebLink(Blockbuster.TUTORIAL_URL()));
        this.discord = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.main.discord"), (button) -> GuiUtils.openWebLink(Blockbuster.DISCORD_URL()));
        this.youtube = new GuiButtonElement(mc, IKey.str("YouTube"), (button) -> GuiUtils.openWebLink("https://www.youtube.com/c/McHorsesMods"));
        this.channel = new GuiButtonElement(mc, IKey.str(Blockbuster.langOrDefault("blockbuster.gui.first_time.channel", "")), (button) -> GuiUtils.openWebLink(Blockbuster.CHANNEL_URL()));
        this.twitter = new GuiButtonElement(mc, IKey.str("Twitter"), (button) -> GuiUtils.openWebLink(Blockbuster.TWITTER_URL()));

        this.tutorial.flex().set(10, 0, 0, 20).relative(this.area).w(0.5F, -12);
        this.youtube.flex().set(0, 0, 0, 20).relative(this.area).x(0.5F, 2).w(0.5F, -12);
        this.discord.flex().set(10, 0, 0, 20).relative(this.area).w(0.5F, -12).y(1, -55);
        this.twitter.flex().set(0, 0, 0, 20).relative(this.area).x(0.5F, 2).w(0.5F, -12).y(1, -55);
        this.close.flex().set(10, 0, 0, 20).relative(this.area).w(1, -20).y(1, -30);

        this.add(this.tutorial, this.discord, this.youtube, this.twitter, this.close);

        if (!this.channel.label.get().isEmpty())
        {
            this.tutorial.flex().set(10, 0, 0, 20).relative(this.area).w(0.33F, -10);
            this.channel.flex().set(0, 0, 0, 20).relative(this.area).x(0.5F, -30).w(60);
            this.youtube.flex().set(0, 0, 0, 20).relative(this.area).x(0.67F, 0).w(0.33F, -10);

            this.add(this.channel);
        }

        this.title = IKey.lang("blockbuster.gui.first_time.title");
        this.welcome = GuiDraw.listFormattedStringToWidth(IKey.lang("blockbuster.gui.first_time.welcome").get(), 180, (s) -> GuiDraw.textWidth(this.font, s));
        this.social = GuiDraw.listFormattedStringToWidth(IKey.lang("blockbuster.gui.first_time.social").get(), 180, (s) -> GuiDraw.textWidth(this.font, s));
    }

    private void close()
    {
        this.overlay.removeFromParent();

        /* Don't show anymore this modal */
        Blockbuster.generalFirstTime.set(false);
    }

    @Override
    public void draw(GuiContext context)
    {
        final int lineHeight = 11;

        this.area.draw(0xff000000);

        /* Draw extra text */
        String title = this.title.get();

        DrawContext drawContext = GuiDraw.getDrawContext();

        if (drawContext != null)
        {
            drawContext.getMatrices().push();
            drawContext.getMatrices().translate(this.area.mx() - GuiDraw.textWidth(this.font, title), this.area.y + 10, 0);
            drawContext.getMatrices().scale(2, 2, 2);

            GuiDraw.drawStringWithShadow(this.font, title, 0, 0, 0xffffff);

            drawContext.getMatrices().pop();
        }

        /* Draw welcome paragraph */
        int y = this.area.y + 35;

        for (String label : this.welcome)
        {
            GuiDraw.drawStringWithShadow(this.font, label, this.area.x + 10, y, 0xaaaaaa);
            y += lineHeight;
        }

        y += 5;

        /* Readjust buttons */
        this.tutorial.flex().y(y - this.area.y);
        this.tutorial.resize();
        this.channel.flex().y(y - this.area.y);
        this.channel.resize();
        this.youtube.flex().y(y - this.area.y);
        this.youtube.resize();

        /* Draw social paragraph */
        y = this.discord.area.y - 5 - this.social.size() * lineHeight;

        for (String label : this.social)
        {
            GuiDraw.drawStringWithShadow(this.font, label, this.area.x + 10, y, 0xaaaaaa);
            y += lineHeight;
        }

        super.draw(context);
    }

    public static class Overlay extends GuiElement
    {
        public Overlay(MinecraftClient mc)
        {
            super(mc);

            GuiFirstTime firstTime = new GuiFirstTime(mc, this);

            firstTime.flex().set(0, 0, 200, 250).relative(this.area).x(0.5F, -100).y(0.5F, -125);

            this.add(firstTime);
            this.hideTooltip();
        }

        @Override
        public boolean mouseClicked(GuiContext context)
        {
            return super.mouseClicked(context) || this.isEnabled();
        }

        @Override
        public void draw(GuiContext context)
        {
            this.area.draw(ColorUtils.HALF_BLACK);

            super.draw(context);
        }
    }
}
