package mchorse.blockbuster.aperture.gui;

import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.CameraAPI;
import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.destination.AbstractDestination;
import mchorse.aperture.camera.destination.ClientDestination;
import mchorse.aperture.client.gui.GuiProfilesManager;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.aperture.network.common.PacketRequestProfiles;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketPlaybackButton;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.nbt.NbtCompound;

import java.util.List;

/**
 * P185.1 — the playback button's editor screen.
 *
 * <p>Attaches a scene (and, with Aperture, a camera mode + profile) to the
 * playback button item the player is holding; the whole state is shipped in one
 * {@link PacketPlaybackButton} when Done is pressed. Reached from the scene
 * panel's camera button via {@link CameraHandler#attach}, and from the
 * server-driven screen-open packet.</p>
 *
 * <p>Shim-collapse note (P186): {@code aperture} is now always {@code true}, so
 * the else-branches below are unreachable in the port. They are kept verbatim
 * because they are the structure legacy shipped and the fallback ("send mode 0
 * and an empty profile") is exactly what a no-Aperture install did.</p>
 *
 * <p>Quirks kept 1:1: the frame is 300px wide with Aperture and 150 without
 * (which is also why the scene list is re-flexed to 147 inside the branch);
 * mode 2 ("load profile") is the only mode that shows the profile list;
 * {@code fillData} reads the <b>main-hand</b> item's tag and falls through to
 * mode 0 for a tagless stack; and the server profile request only goes out when
 * the client knows a server destination exists
 * ({@code ClientProxy.server}).</p>
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/gui/GuiPlayback.java</p>
 */
public class GuiPlayback extends GuiBase
{
    private String stringTitle = I18n.translate("blockbuster.gui.playback.title");
    private String stringCameraMode = I18n.translate("blockbuster.gui.playback.camera_mode");
    private String stringProfile = I18n.translate("blockbuster.gui.playback.profile");
    private String stringScene = I18n.translate("blockbuster.gui.playback.scene");

    private GuiCirculateElement cameraMode;
    private GuiButtonElement done;

    public GuiStringListElement scenes;
    public GuiListElement<CameraProfile> profiles;
    public Area frame = new Area();

    private SceneLocation location;
    private String profile = "";

    private boolean aperture;
    private int frameWidth = 150;

    public GuiPlayback()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        this.aperture = CameraHandler.isApertureLoaded();

        this.scenes = new GuiStringListElement(mc, (value) -> this.location = new SceneLocation(value.get(0)));
        this.scenes.background().flex().relative(this.frame).y(35).w(1F).h(1F, -65);

        this.done = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.done"), (b) -> this.saveAndQuit());
        this.done.flex().relative(this.frame).set(0, 0, 0, 20).y(1, -20).w(1F, 0);

        if (this.aperture)
        {
            this.frameWidth = 300;

            this.profiles = this.createListElement(mc);
            this.profiles.background().flex().set(153, 35, 0, 0).relative(this.frame).w(147).h(1, -105);

            this.scenes.flex().w(147);

            this.cameraMode = new GuiCirculateElement(mc, (b) -> this.setValue(this.cameraMode.getValue()));
            this.cameraMode.addLabel(IKey.lang("blockbuster.gui.playback.nothing"));
            this.cameraMode.addLabel(IKey.lang("blockbuster.gui.playback.play"));
            this.cameraMode.addLabel(IKey.lang("blockbuster.gui.playback.load_profile"));
            this.cameraMode.flex().relative(this.frame).set(153, 0, 0, 20).y(1, -50).w(147);

            this.root.add(this.profiles, this.cameraMode);
            this.fillData();
        }

        this.root.add(this.scenes, this.done);
    }

    /* Aperture specific methods */

    private GuiListElement<CameraProfile> createListElement(MinecraftClient mc)
    {
        return new GuiProfilesManager.GuiCameraProfilesList(mc, null);
    }

    private void fillData()
    {
        /* Fill data */
        for (String filename : CameraAPI.getClientProfiles())
        {
            this.addDestination(new ClientDestination(filename));
        }

        this.profiles.sort();

        if (ClientProxy.server)
        {
            Dispatcher.sendToServer(new PacketRequestProfiles());
        }

        /* Fill the camera mode button */
        MinecraftClient mc = MinecraftClient.getInstance();
        NbtCompound compound = mc.player == null ? null : mc.player.getMainHandStack().getNbt();

        if (compound != null)
        {
            if (compound.contains("CameraPlay"))
            {
                this.setValue(1);
            }
            else if (compound.contains("CameraProfile"))
            {
                this.setValue(2, compound.getString("CameraProfile"));
            }
            else
            {
                this.setValue(0);
            }
        }
        else
        {
            this.setValue(0);
        }
    }

    public void selectCurrent()
    {
        this.selectCurrent(this.profile);
    }

    public void selectCurrent(String profile)
    {
        List<CameraProfile> list = this.profiles.getList();

        for (int i = 0; i < list.size(); i ++)
        {
            if (list.get(i).getDestination().toResourceLocation().toString().equals(profile))
            {
                this.profiles.setIndex(i);

                break;
            }
        }
    }

    private void sendPlaybackButton()
    {
        Dispatcher.sendToServer(new PacketPlaybackButton(this.location, this.cameraMode.getValue(), this.getSelected()));
    }

    private String getSelected()
    {
        CameraProfile current = this.profiles.getCurrentFirst();

        if (current != null)
        {
            return current.getDestination().toResourceLocation().toString();
        }

        return "";
    }

    public void addDestination(AbstractDestination destination)
    {
        this.profiles.add(new CameraProfile(destination));
    }

    /* Remaining methods */

    public GuiPlayback setLocation(SceneLocation location, List<String> scenes)
    {
        this.location = location;

        this.scenes.clear();
        this.scenes.add(scenes);
        this.scenes.sort();
        this.scenes.setCurrentScroll(location.getFilename());

        return this;
    }

    public void setValue(int value)
    {
        this.cameraMode.setValue(value);
        this.profiles.setVisible(value == 2);
    }

    public void setValue(int value, String profile)
    {
        this.profile = profile;
        this.setValue(value);

        if (this.aperture)
        {
            this.selectCurrent(profile);
        }
    }

    @Override
    public boolean shouldPause()
    {
        return false;
    }

    private void saveAndQuit()
    {
        if (this.aperture)
        {
            this.sendPlaybackButton();
        }
        else
        {
            Dispatcher.sendToServer(new PacketPlaybackButton(this.location, 0, ""));
        }

        /* Legacy `mc.displayGuiScreen(null)` — GuiBase's closeScreen is the
         * port's equivalent (it also restores the in-game focus) */
        this.closeScreen();
    }

    @Override
    protected void init()
    {
        this.frame.set(this.width / 2 - this.frameWidth / 2, 10, this.frameWidth, this.height - 20);

        super.init();
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks)
    {
        this.context.drawContext = drawContext;
        GuiDraw.bindDrawContext(drawContext);

        GuiDraw.drawCustomBackground(0, 0, this.width, this.height);
        GuiDraw.drawStringWithShadow(this.context.font, this.stringTitle, this.frame.x, this.frame.y, 0xffffffff);

        if (this.cameraMode != null)
        {
            GuiDraw.drawStringWithShadow(this.context.font, this.stringCameraMode, this.cameraMode.area.x, this.cameraMode.area.y - 12, 0xffcccccc);

            if (this.cameraMode.getValue() == 2)
            {
                GuiDraw.drawStringWithShadow(this.context.font, this.stringProfile, this.profiles.area.x, this.profiles.area.y - 12, 0xffcccccc);
            }
        }

        GuiDraw.drawStringWithShadow(this.context.font, this.stringScene, this.scenes.area.x, this.scenes.area.y - 12, 0xffcccccc);

        super.render(drawContext, mouseX, mouseY, partialTicks);
    }
}
