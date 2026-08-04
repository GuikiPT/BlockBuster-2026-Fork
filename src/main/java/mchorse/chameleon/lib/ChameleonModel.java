package mchorse.chameleon.lib;

import mchorse.chameleon.lib.data.animation.Animations;
import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.data.model.Model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.common.collect.ImmutableList;

/**
 * One loaded Chameleon model folder: the parsed geometry, its animations, and
 * the bookkeeping the reloader needs ({@link #lastUpdate}, {@link #isStillPresent}).
 *
 * <p>{@link #getBoneNames()} is the <b>flattened, depth-first, parent-before-child</b>
 * bone order, cached on first use. That order is a contract, not a detail: the
 * editor's stencil picker encodes a bone as its index in this list + 1, so a
 * reordering would silently repoint every limb the user clicks.</p>
 *
 * Legacy source: chameleon/.../lib/ChameleonModel.java
 */
public class ChameleonModel
{
    public Model model;
    public Animations animations;
    public long lastUpdate;

    private List<String> boneNames;
    private boolean isStatic;
    private List<File> files;

    public ChameleonModel(Model model, Animations animations, List<File> files, long lastUpdate)
    {
        this.model = model;
        this.animations = animations;
        this.files = files;
        this.lastUpdate = lastUpdate;
        this.isStatic = animations == null || animations.getAll().isEmpty();
    }

    public List<String> getBoneNames()
    {
        if (this.boneNames != null)
        {
            return this.boneNames;
        }

        return this.boneNames = this.getBoneNames(new ArrayList<String>(), this.model.bones);
    }

    public List<String> getChildren(String parent)
    {
        List<String> children = new ArrayList<String>();

        for (ModelBone bone : this.model.bones)
        {
            if (Objects.equals(bone.id, parent))
            {
                this.getBoneNames(children, ImmutableList.of(bone));

                break;
            }
        }

        return children;
    }

    private List<String> getBoneNames(List<String> boneNames, List<ModelBone> bones)
    {
        for (ModelBone bone : bones)
        {
            boneNames.add(bone.id);

            this.getBoneNames(boneNames, bone.children);
        }

        return boneNames;
    }

    public boolean isStatic()
    {
        return this.isStatic;
    }

    public boolean isStillPresent()
    {
        for (File file : this.files)
        {
            if (!file.exists())
            {
                return false;
            }
        }

        return true;
    }
}
