package mchorse.mclib.client.gui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.utils.KeyCodes;
import mchorse.mclib.utils.Keys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * GUI utilities (partial port of McLib 2.4.3's {@code GuiUtils}, roadmap P32;
 * the entity/model screen renderers land with P42).
 *
 * <p>1.20.4/headless boundary decisions:</p>
 * <ul>
 * <li>{@link #playClick()} —
 * {@code PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1F)}
 * through the sound manager; no-op headless.</li>
 * <li>Keyboard polling: legacy {@code Keyboard.isKeyDown(lwjgl2Code)} /
 * {@code GuiScreen.isShiftKeyDown()} & co. become {@link #isKeyDown(int)} (a
 * raw single-scancode check, LWJGL2 domain) polled via
 * {@code InputUtil.isKeyPressed} + the {@code KeyCodes} table. Headless (or
 * in tests) the {@link #keyDownOverride} seam supplies the answer.</li>
 * <li>Clipboard: legacy {@code GuiScreen.get/setClipboardString} become
 * {@link #getClipboardString()}/{@link #setClipboardString(String)} over
 * {@code MinecraftClient.keyboard}; headless they read/write the
 * {@link #headlessClipboard} seam so tests can inject a fake clipboard.</li>
 * </ul>
 */
public class GuiUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger("mclib");

    /**
     * Test/headless seam: when non-null, {@link #isKeyDown(int)} consults this
     * predicate (LWJGL2 scancode domain) instead of polling GLFW.
     */
    public static IntPredicate keyDownOverride;

    /**
     * Headless clipboard storage — used when there is no Minecraft window to
     * ask (fabric-loader-junit harness). Tests interact with the clipboard
     * paths through this seam.
     */
    public static String headlessClipboard = "";

    /** Optional full clipboard override (get) — takes precedence when set. */
    public static Supplier<String> clipboardGetter;

    /** Optional full clipboard override (set) — takes precedence when set. */
    public static Consumer<String> clipboardSetter;

    /**
     * Test/headless seam: when non-null, {@link #openWebLink(String)} and
     * {@link #openWebLink(URI)} route the address here instead of handing it to
     * {@code Util.getOperatingSystem().open}. Lets config-panel action rows
     * ({@code ValueMainButtons}) be verified without launching a browser.
     *
     * <p><b>P280 — read this before writing a test against it.</b> This seam is
     * exactly what hid the bug it now guards: for months every link test
     * installed the override and asserted "the override saw the URL", which is
     * true no matter what the production branch below does — and the production
     * branch below could never work. A test that only exercises this field
     * proves nothing about a click in game. {@code GuiUtilsOpenLinkTest} pins
     * the <i>compiled</i> no-override branch instead.</p>
     */
    public static Consumer<String> webLinkOverride;

    /**
     * Test/headless seam: when non-null, {@link #openFolder(String)} routes the
     * path here instead of invoking the OS file manager. Lets the models/skins/
     * audio "open folder" buttons be verified headlessly. Same P280 caveat as
     * {@link #webLinkOverride}.
     */
    public static Consumer<String> folderOverride;

    /**
     * Install the GLFW-backed raw-key poller into {@link Keys#keyDownPoller}
     * (called from the client initializer; P39).
     */
    public static void installKeyPoller()
    {
        Keys.keyDownPoller = GuiUtils::isKeyDown;
    }

    /**
     * Raw single-key check in the legacy LWJGL2 scancode domain (legacy
     * {@code Keyboard.isKeyDown}). Modifier left/right pairing lives in
     * {@link Keys#isKeyDown(int)}, exactly like legacy.
     */
    public static boolean isKeyDown(int lwjgl2Code)
    {
        if (keyDownOverride != null)
        {
            return keyDownOverride.test(lwjgl2Code);
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.getWindow() == null)
        {
            return false;
        }

        int glfw = KeyCodes.lwjgl2ToGlfw(lwjgl2Code);

        if (glfw == KeyCodes.GLFW_KEY_UNKNOWN)
        {
            return false;
        }

        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), glfw);
    }

    /* Legacy GuiScreen modifier checks (either side counts, like 1.12.2) */

    public static boolean isShiftKeyDown()
    {
        return isKeyDown(Keys.KEY_LSHIFT) || isKeyDown(Keys.KEY_RSHIFT);
    }

    public static boolean isCtrlKeyDown()
    {
        return isKeyDown(Keys.KEY_LCONTROL) || isKeyDown(Keys.KEY_RCONTROL);
    }

    public static boolean isAltKeyDown()
    {
        return isKeyDown(Keys.KEY_LMENU) || isKeyDown(Keys.KEY_RMENU);
    }

    /* Clipboard (legacy GuiScreen.getClipboardString/setClipboardString) */

    public static String getClipboardString()
    {
        if (clipboardGetter != null)
        {
            return clipboardGetter.get();
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.keyboard == null)
        {
            return headlessClipboard;
        }

        try
        {
            return mc.keyboard.getClipboard();
        }
        catch (Exception e)
        {
            /* P285: this used to swallow silently. A clipboard read that fails
             * every time reads to the user as "Ctrl+V does nothing", which is
             * exactly how P280's dead java.awt.Desktop branch stayed invisible
             * for months. Log it once per failure, and still return legacy's
             * empty string. */
            LOGGER.warn("Reading the system clipboard failed; pasting will yield nothing", e);

            return "";
        }
    }

    public static void setClipboardString(String string)
    {
        if (clipboardSetter != null)
        {
            clipboardSetter.accept(string);

            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.keyboard == null)
        {
            headlessClipboard = string;

            return;
        }

        try
        {
            mc.keyboard.setClipboard(string);
        }
        catch (Exception e)
        {
            /* P285: same reasoning as getClipboardString — a permanently
             * failing copy is indistinguishable from a dead button unless it
             * says so. */
            LOGGER.warn("Writing the system clipboard failed; the copy did not happen", e);
        }
    }

    /* Entity-on-screen renderers (P42).
     *
     * Legacy also had drawModel(ModelBase, ...) — 1.12's ModelBase doesn't
     * exist on 1.20.4; S6's custom-model module owns the equivalent once the
     * bundled ModelCustom lands. Both drawEntityOnScreen overloads are
     * reimplemented over EntityRenderDispatcher (legacy RenderManager) with
     * the transform order preserved; the EntityDragon 180° yaw flip quirk is
     * kept. No-op headless. */

    /**
     * Draw an entity on the screen (legacy static-pose overload).
     */
    public static void drawEntityOnScreen(int posX, int posY, float scale, LivingEntity ent, float alpha)
    {
        DrawContext dc = GuiDraw.getDrawContext();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (dc == null || mc == null || ent == null)
        {
            return;
        }

        MatrixStack matrices = dc.getMatrices();

        matrices.push();
        matrices.translate(posX, posY, 100.0F);
        matrices.multiplyPositionMatrix(new Matrix4f().scaling(-scale, scale, scale));
        matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(45.0F));
        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(45.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));

        boolean render = ent.isCustomNameVisible();

        if (ent instanceof EnderDragonEntity)
        {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        }

        RenderSystem.setShaderColor(1F, 1F, 1F, alpha);

        float f = ent.bodyYaw;
        float f1 = ent.getYaw();
        float f2 = ent.getPitch();
        float f3 = ent.prevHeadYaw;
        float f4 = ent.headYaw;

        ent.bodyYaw = 0;
        ent.setYaw(0);
        ent.setPitch(0);
        ent.headYaw = ent.getYaw();
        ent.prevHeadYaw = ent.getYaw();
        ent.setCustomNameVisible(false);

        DiffuseLighting.method_34742();

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        /* Legacy rendermanager.setPlayerViewY(180) */
        dispatcher.setRotation(new Quaternionf().rotationY((float) Math.PI));
        dispatcher.setRenderShadows(false);
        dispatcher.render(ent, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, matrices, dc.getVertexConsumers(), LightmapTextureManager.MAX_LIGHT_COORDINATE);
        dc.draw();
        dispatcher.setRenderShadows(true);

        ent.bodyYaw = f;
        ent.setYaw(f1);
        ent.setPitch(f2);
        ent.prevHeadYaw = f3;
        ent.headYaw = f4;

        ent.setCustomNameVisible(render);

        matrices.pop();
        DiffuseLighting.enableGuiDepthLighting();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /**
     * Draw an entity on the screen (legacy mouse-following overload).
     */
    public static void drawEntityOnScreen(int posX, int posY, int scale, int mouseX, int mouseY, LivingEntity ent)
    {
        DrawContext dc = GuiDraw.getDrawContext();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (dc == null || mc == null || ent == null)
        {
            return;
        }

        MatrixStack matrices = dc.getMatrices();

        matrices.push();
        matrices.translate(posX, posY, 100.0F);
        matrices.multiplyPositionMatrix(new Matrix4f().scaling(-scale, scale, scale));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));

        float f = ent.bodyYaw;
        float f1 = ent.getYaw();
        float f2 = ent.getPitch();
        float f3 = ent.prevHeadYaw;
        float f4 = ent.headYaw;

        ent.bodyYaw = (float) Math.atan(mouseX / 40.0F) * 20.0F;
        ent.setYaw((float) Math.atan(mouseX / 40.0F) * 40.0F);
        ent.setPitch(-((float) Math.atan(mouseY / 40.0F)) * 20.0F);
        ent.headYaw = ent.getYaw();
        ent.prevHeadYaw = ent.getYaw();

        DiffuseLighting.method_34742();

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        dispatcher.setRotation(new Quaternionf().rotationY((float) Math.PI));
        dispatcher.setRenderShadows(false);
        dispatcher.render(ent, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, matrices, dc.getVertexConsumers(), LightmapTextureManager.MAX_LIGHT_COORDINATE);
        dc.draw();
        dispatcher.setRenderShadows(true);

        ent.bodyYaw = f;
        ent.setYaw(f1);
        ent.setPitch(f2);
        ent.prevHeadYaw = f3;
        ent.headYaw = f4;

        matrices.pop();
        DiffuseLighting.enableGuiDepthLighting();
    }

    /* Miscellaneous */

    public static void playClick()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null && mc.getSoundManager() != null)
        {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    /**
     * Open web link (the entry point every link button uses — see
     * {@link #openWebLink(URI)} for the P280 dispatch note).
     */
    public static void openWebLink(String address)
    {
        if (webLinkOverride != null)
        {
            webLinkOverride.accept(address);

            return;
        }

        try
        {
            openWebLink(new URI(address));
        }
        catch (Exception e)
        {
            /* Legacy ignored an unparseable address. Say so once instead:
             * "the button did nothing and the log is empty" is the exact
             * report P280 came from. */
            LOGGER.warn("Not a valid web link, ignoring: {}", address);
        }
    }

    /**
     * Open a URL.
     *
     * <p><b>P280 — the port's silent-no-op bug.</b> Legacy (and this port's
     * first transcription of it) reflected {@code java.awt.Desktop.getDesktop()}
     * and swallowed every {@link Throwable}. On 1.12.2/desktop-Linux that
     * worked, so the empty catch was a belt-and-braces guard that never fired.
     * Minecraft 1.20.4 runs the client with {@code java.awt.headless=true}, so
     * {@code Desktop.getDesktop()} throws {@code HeadlessException} <i>every
     * time</i> — the catch swallowed it and all twelve link buttons (the
     * config panel's wiki/Discord/tutorial/YouTube/Twitter, the first-run
     * screen's five, the Aperture {@code ?} help icon and the math-expressions
     * wiki) did nothing at all when clicked, with nothing in the log.</p>
     *
     * <p>The dispatch is now {@code Util.getOperatingSystem().open(uri)} — the
     * same modern replacement {@link #openFolder(String)} already relied on, and
     * the mapping {@code plan/S03-mclib-gui.md} specified for both. No
     * {@code ConfirmLinkScreen} gate: 1.12.2 opened these links directly, they
     * are all mod-authored constants (no user- or server-supplied URL reaches
     * here), and nothing else in the port prompts before an outbound link.</p>
     */
    public static void openWebLink(URI uri)
    {
        if (uri == null)
        {
            return;
        }

        /* The URI overload is public API too — honour the headless seam here as
         * well, so a test that installs it captures both entry points. */
        if (webLinkOverride != null)
        {
            webLinkOverride.accept(uri.toString());

            return;
        }

        try
        {
            Util.getOperatingSystem().open(uri);
        }
        catch (Exception e)
        {
            LOGGER.warn("Failed to open web link {}", uri, e);
        }
    }

    /**
     * Open a folder (legacy referenced {@code OpenGlHelper.openFile}; the
     * LWJGL2 {@code Sys.openURL} last resort is gone — {@code Util.getOperatingSystem().open}
     * is the modern equivalent).
     *
     * <p><b>P280.</b> The Windows/OSX {@code Runtime.exec} branches are legacy's
     * and are kept verbatim. What is gone is the {@code java.awt.Desktop}
     * reflection legacy used between them and the fallback: under 1.20.4's
     * headless client it threw {@code HeadlessException} on every single click,
     * and because the port's catch called {@code printStackTrace()} before
     * setting {@code failed = true}, each click dumped ~45 lines of
     * {@code [STDERR]} into {@code latest.log} <i>and then did the right thing
     * anyway</i>. The doomed attempt is dropped; the working call is made
     * first.</p>
     */
    public static void openFolder(String url)
    {
        if (folderOverride != null)
        {
            folderOverride.accept(url);

            return;
        }

        File file = new File(url);

        switch (Util.getOperatingSystem())
        {
            case WINDOWS:
                try
                {
                    Runtime.getRuntime().exec(new String[]
                    {
                        "cmd.exe", "/C", "start", "\"Open file\"", file.getAbsolutePath()
                    });

                    return;
                }
                catch (IOException ioexception)
                {
                    LOGGER.warn("Failed to open folder {} through cmd.exe", file, ioexception);

                    break;
                }

            case OSX:
                try
                {
                    Runtime.getRuntime().exec(new String[]
                    {
                        "/usr/bin/open", file.getAbsolutePath()
                    });

                    return;
                }
                catch (IOException ioexception1)
                {
                    LOGGER.warn("Failed to open folder {} through /usr/bin/open", file, ioexception1);
                }

            default:
                break;
        }

        try
        {
            Util.getOperatingSystem().open(file);
        }
        catch (Exception e)
        {
            LOGGER.warn("Failed to open folder {}", file, e);
        }
    }
}
