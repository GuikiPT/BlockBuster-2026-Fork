package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.tabs;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.GuiModelEditorPanel;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiTwoElement;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.PoseEditorOps;
import mchorse.blockbuster.client.gui.utils.GuiShapeKeysEditor;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringListElement;
import mchorse.mclib.client.gui.framework.elements.modals.GuiListModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiMessageModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiPromptModal;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.Consumer;

/**
 * Model editor Poses tab (roadmap P137; unblocks P158).
 *
 * <p>Legacy source (ported 1:1):
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/model_editor/tabs/GuiModelPoses.java}.</p>
 *
 * <p>Pose list with add/rename/remove, per-limb transform copy/apply between
 * poses, the size/hitbox two-element editor, the shape-keys editor (inserted
 * only when {@code model.shapes} is non-empty, shrinking the pose list to 40%
 * height), and the system-clipboard copy/paste-pose context menu.</p>
 *
 * <p><b>Parity notes.</b></p>
 * <ul>
 * <li><b>Clipboard + NBT parse.</b> Legacy used
 * {@code GuiScreen.getClipboardString()}/{@code setClipboardString(...)} and
 * {@code JsonToNBT.getTagFromJson(...)}; on 1.20.4 these become
 * {@code MinecraftClient.getInstance().keyboard.getClipboard()}/
 * {@code setClipboard(...)} and {@code StringNbtReader.parse(...)}. The paste
 * action is only offered when the clipboard parses into a {@link ModelPose}
 * (the broad {@code catch} swallow around the parse is legacy behaviour and is
 * preserved rather than "fixed").</li>
 * <li><b>{@link #createCopyPasteMenu} stays {@code public static}</b> — legacy
 * reused it from other GUIs (morph pose panels, P158).</li>
 * <li><b>REQUIRED_POSES protection.</b> Removing a required pose
 * ({@code Model.REQUIRED_POSES}, i.e. {@code "standing"}) is refused with the
 * {@code "standard"} message modal; otherwise post-removal selection falls back
 * previous-index &rarr; index 1 &rarr; first key.</li>
 * <li><b>Copy-pose vs apply-pose.</b> "Copy pose" pulls the current limb's
 * transform <i>from</i> a chosen pose onto {@code panel.transform}; "Apply pose"
 * is a multi-select modal (strips the first list entry, selects all, then
 * deselects the current pose) that pushes the current limb's transform <i>to</i>
 * every chosen pose, silently skipping poses lacking the limb.</li>
 * <li><b>Uniquifier.</b> Paste name defaults to {@code "pasted_pose"} with a
 * {@code _N} suffix until unique, exactly as legacy.</li>
 * </ul>
 */
public class GuiModelPoses extends GuiModelEditorTab
{
    private GuiIconElement addPose;
    private GuiIconElement removePose;
    private GuiIconElement copyPose;
    private GuiIconElement applyPose;

    private GuiStringListElement posesList;
    private GuiShapeKeysEditor shapeKeys;
    private GuiTwoElement hitbox;
    private GuiElement bottom;

    private String pose;

    public static GuiSimpleContextMenu createCopyPasteMenu(Runnable copy, Consumer<ModelPose> paste)
    {
        GuiSimpleContextMenu menu = new GuiSimpleContextMenu(MinecraftClient.getInstance());
        ModelPose pose = PoseEditorOps.parseClipboardPose(MinecraftClient.getInstance().keyboard.getClipboard());

        menu.action(Icons.COPY, IKey.lang("blockbuster.gui.me.poses.context.copy"), copy);

        if (pose != null)
        {
            final ModelPose innerPose = pose;

            menu.action(Icons.PASTE, IKey.lang("blockbuster.gui.me.poses.context.paste"), () -> paste.accept(innerPose));
        }

        return menu;
    }

    public GuiModelPoses(MinecraftClient mc, GuiModelEditorPanel panel)
    {
        super(mc, panel);

        this.title = IKey.lang("blockbuster.gui.me.poses.title");

        this.hitbox = new GuiTwoElement(mc, (values) ->
        {
            this.panel.pose.size[0] = values[0].floatValue();
            this.panel.pose.size[1] = values[1].floatValue();
            this.panel.dirty();
        });

        /* Buttons */
        this.addPose = new GuiIconElement(mc, Icons.ADD, (b) -> this.addPose());
        this.removePose = new GuiIconElement(mc, Icons.REMOVE, (b) -> this.removePose());
        this.copyPose = new GuiIconElement(mc, Icons.COPY, (b) -> this.copyPose());
        this.copyPose.tooltip(IKey.lang("blockbuster.gui.me.poses.copy_pose_tooltip"));
        this.applyPose = new GuiIconElement(mc, Icons.PASTE, (b) -> this.applyPose());
        this.applyPose.tooltip(IKey.lang("blockbuster.gui.me.poses.apply_pose_tooltip"));

        GuiElement sidebar = Elements.row(mc, 0, 0, 20, this.addPose, this.removePose, this.copyPose, this.applyPose);
        this.bottom = new GuiElement(mc);

        sidebar.flex().relative(this).x(1F).h(20).anchorX(1F).row(0).resize();
        this.bottom.flex().relative(this).y(1F).w(1F).anchorY(1F).column(5).vertical().stretch().height(20).padding(10);

        this.posesList = new GuiStringListElement(mc, (str) -> this.setPose(str.get(0)));
        this.posesList.flex().relative(this).y(20).w(1F).hTo(bottom.area);
        this.posesList.context(() ->
        {
            GuiSimpleContextMenu menu = createCopyPasteMenu(this::copyCurrentPose, this::pastePose);

            menu.action(Icons.EDIT, IKey.lang("blockbuster.gui.me.poses.context.rename"), this::renamePose);

            return menu;
        });

        this.shapeKeys = new GuiShapeKeysEditor(mc, () -> this.panel.model);
        this.shapeKeys.flex().relative(this.posesList).y(1F, 10).x(10).w(1F, -20).hTo(this.hitbox.area, -27);

        this.bottom.add(Elements.label(IKey.lang("blockbuster.gui.me.poses.hitbox")), this.hitbox);
        this.add(sidebar, this.bottom, this.posesList);
    }

    private void copyCurrentPose()
    {
        MinecraftClient.getInstance().keyboard.setClipboard(PoseEditorOps.serializePose(this.panel.pose));
    }

    private void pastePose(ModelPose pose)
    {
        GuiModal.addFullModal(this, () ->
        {
            GuiPromptModal modal = new GuiPromptModal(mc, IKey.lang("blockbuster.gui.me.poses.paste_pose"), (text) ->
            {
                this.addPose(text, pose);
            });

            return modal.setValue(PoseEditorOps.uniquePasteName(this.panel.model));
        });
    }

    private void renamePose()
    {
        GuiModal.addFullModal(this, () ->
        {
            GuiPromptModal modal = new GuiPromptModal(mc, IKey.lang("blockbuster.gui.me.poses.rename_pose"), this::renamePose);

            return modal.setValue(this.pose);
        });
    }

    private void renamePose(String name)
    {
        if (!this.panel.model.poses.containsKey(name))
        {
            this.panel.model.poses.put(name, this.panel.model.poses.remove(this.pose));
            this.posesList.remove(this.pose);
            this.posesList.add(name);
            this.posesList.sort();
            this.panel.setPose(name, true);
            this.panel.dirty();
        }
    }

    private void addPose()
    {
        GuiModal.addFullModal(this, () ->
        {
            GuiPromptModal modal = new GuiPromptModal(mc, IKey.lang("blockbuster.gui.me.poses.new_pose"), this::addPose);

            return modal.setValue(this.pose);
        });
    }

    private void addPose(String pose)
    {
        this.addPose(pose, this.panel.pose == null ? new ModelPose() : this.panel.pose.copy());
    }

    private void addPose(String name, ModelPose pose)
    {
        if (!this.panel.model.poses.containsKey(name))
        {
            this.panel.model.poses.put(name, pose);
            this.posesList.add(name);
            this.posesList.sort();
            this.panel.setPose(name, true);
            this.panel.dirty();
        }
    }

    private void removePose()
    {
        if (Model.REQUIRED_POSES.contains(this.pose))
        {
            GuiModal.addFullModal(this, () -> new GuiMessageModal(this.mc, IKey.lang("blockbuster.gui.me.poses.standard")));
        }
        else
        {
            this.panel.model.poses.remove(this.pose);

            String newPose = null;
            int index = this.posesList.getIndex();
            int size = this.posesList.getList().size();

            if (index > 0 && size > 1)
            {
                newPose = this.posesList.getList().get(this.posesList.getIndex() - 1);
            }
            else if (index == 0 && size > 1)
            {
                newPose = this.posesList.getList().get(1);
            }

            if (newPose == null)
            {
                newPose = this.panel.model.poses.keySet().iterator().next();
            }

            this.posesList.remove(this.pose);
            this.setPose(newPose);
            this.panel.dirty();
        }
    }

    private void copyPose()
    {
        GuiModal.addFullModal(this, () ->
        {
            GuiListModal modal = new GuiListModal(this.mc, IKey.lang("blockbuster.gui.me.poses.copy_pose"), this::copyPose);

            return modal.addValues(this.panel.model.poses.keySet());
        });
    }

    private void copyPose(String text)
    {
        if (PoseEditorOps.copyPoseInto(this.panel.model, text, this.panel.limb.name, this.panel.transform))
        {
            this.panel.dirty();
        }
    }

    private void applyPose()
    {
        GuiModal.addFullModal(this, () ->
        {
            GuiListModal modal = new GuiListModal(this.mc, IKey.lang("blockbuster.gui.me.poses.apply_pose"), null).callback(this::copyPose);

            modal.list.getList().remove(0);
            modal.list.multi();

            modal.addValues(this.panel.model.poses.keySet());
            modal.list.selectAll();
            modal.list.toggleIndex(modal.list.getList().indexOf(this.pose));

            return modal;
        });
    }

    private void copyPose(List<String> poses)
    {
        if (PoseEditorOps.applyLimbToPoses(this.panel.model, this.pose, this.panel.limb.name, poses))
        {
            this.panel.dirty();
        }
    }

    public void setPose(String str)
    {
        this.pose = str;
        this.panel.setPose(str);
        this.fillPoseData();
    }

    public void setCurrent(String pose, boolean scroll)
    {
        this.pose = pose;

        if (scroll)
        {
            this.posesList.setCurrentScroll(pose);
        }
        else
        {
            this.posesList.setCurrent(pose);
        }

        this.fillPoseData();
    }

    public void fillData(Model model)
    {
        this.posesList.clear();
        this.posesList.add(model.poses.keySet());
        this.posesList.sort();
    }

    public void fillPoseData()
    {
        this.hitbox.setValues(this.panel.pose.size[0], this.panel.pose.size[1]);

        boolean isVisible = !this.panel.model.shapes.isEmpty();

        if (isVisible)
        {
            this.shapeKeys.fillData(this.panel.pose.shapes);

            if (!this.shapeKeys.hasParent())
            {
                this.add(this.shapeKeys);
                this.posesList.flex().h(0.4F);
            }
        }
        else
        {
            this.shapeKeys.removeFromParent();
            this.posesList.flex().hTo(this.bottom.area);
        }

        this.resize();
    }

    @Override
    public void draw(GuiContext context)
    {
        this.area.draw(0xaa000000);

        super.draw(context);
    }
}
