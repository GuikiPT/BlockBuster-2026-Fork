package mchorse.vanilla_pack.editors;

import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.Label;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.bodypart.GuiBodyPartEditor;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import mchorse.metamorph.client.render.EntityMorphLimbNames;
import mchorse.metamorph.client.render.EntityMorphRenderer;
import mchorse.vanilla_pack.editors.panels.GuiEntityPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Entity morph editor (roadmap P59.2) — the body-part panel plus the entity
 * panel, and the hardcoded per-entity quick presets.
 *
 * <p>Port of Metamorph 1.4's {@code GuiEntityMorph}. Two things here are
 * behaviour, not decoration:</p>
 *
 * <ul>
 *   <li><b>Preset {@code EntityData} is replaced, never merged.</b>
 *       {@link #addPreset} removes the tag before merging the new one, so
 *       picking "Baby" on a sheared sheep gives a baby sheep with wool — see the
 *       overridden method.</li>
 *   <li><b>The editor is picked by the first {@code canEdit}</b>, and this one
 *       accepts every {@link EntityMorph}. Every subclass editor
 *       ({@link GuiPlayerMorph}) must therefore be registered before it —
 *       see {@code MetamorphMorphEditors}.</li>
 * </ul>
 *
 * <p><b>Preset payload modernization.</b> The plan allows presets (editor
 * conveniences, not disk data) to carry modern NBT where the 1.12 payload died.
 * The labels are legacy's; the deltas are:</p>
 *
 * <ul>
 *   <li><b>Villager professions</b> — the flat {@code ProfessionName} string
 *       became the {@code VillagerData} compound, so each preset writes
 *       {@code {type, profession, level}}. 1.12's {@code priest} is now
 *       {@code cleric}, and its single {@code smith} profession split into
 *       armorer / toolsmith / weaponsmith — the "Smith" preset takes
 *       {@code armorer}.</li>
 *   <li><b>Cats</b> — 1.12's ocelot carried the cat skins in {@code CatType};
 *       1.14 moved them to the separate {@code minecraft:cat} entity keyed by
 *       the {@code variant} registry id. The three "Cat #N" presets therefore
 *       hang off {@code minecraft:cat} now ({@code black}, {@code red},
 *       {@code siamese} — 1.12's tuxedo/tabby/siamese), and the ocelot keeps
 *       only the baby preset it shares with the other animals.</li>
 *   <li><b>{@code CustomName}</b> — the "Jeb" and "Toast" easter-egg presets
 *       have to quote the name as a JSON text component
 *       ({@code CustomName:'"jeb_"'}), since 1.13 changed the field's format;
 *       both easter eggs still compare against the plain rendered name.</li>
 * </ul>
 *
 * <p>Everything else parses on 1.20.4 exactly as written in 2.7.2: {@code Age},
 * {@code Sheared}, {@code Color}, {@code Size}, {@code Variant} (parrot / horse
 * / llama), {@code BatFlags}, {@code RabbitType} and {@code IsBaby} all kept
 * their keys.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/editors/GuiEntityMorph.java
 */
public class GuiEntityMorph extends GuiAbstractMorph<EntityMorph>
{
    /** Legacy's list of entity ids that get a "Baby" preset. */
    public static final List<String> animals = Arrays.asList("minecraft:pig", "minecraft:chicken", "minecraft:cow", "minecraft:mooshroom", "minecraft:polar_bear", "minecraft:sheep", "minecraft:ocelot");

    /** 1.12 {@code CatType} 1/2/3 (tuxedo, tabby, siamese) as 1.20.4 variants. */
    public static final String[] CAT_VARIANTS = {"minecraft:black", "minecraft:red", "minecraft:siamese"};

    public GuiEntityPanel entityPanel;
    public GuiBodyPartEditor bodyPart;

    public GuiEntityMorph(MinecraftClient mc)
    {
        super(mc);

        this.bodyPart = new GuiBodyPartEditor(mc, this);
        this.entityPanel = new GuiEntityPanel(mc, this);

        this.registerPanel(this.bodyPart, IKey.lang("metamorph.gui.body_parts.parts"), Icons.LIMB);
        this.registerPanel(this.entityPanel, IKey.lang("metamorph.gui.editor.entity"), Icons.POSE);
        this.defaultPanel = this.entityPanel;
    }

    /** The body-part panel wants the {@code B} hotkey (SEAM P59.1). */
    @Override
    protected boolean wantsBodyPartKey(GuiMorphPanel panel)
    {
        return panel instanceof GuiBodyPartEditor;
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof EntityMorph;
    }

    @Override
    public void startEdit(EntityMorph morph)
    {
        if (morph.getEntity() == null && this.mc != null)
        {
            morph.setupEntity(this.mc.world);
        }

        morph.parts.reinitBodyParts();
        this.bodyPart.setLimbs(this.limbsOf(morph));

        super.startEdit(morph);
    }

    /**
     * Legacy called {@code morph.setupLimbs()} and read the resulting field-name
     * map. The 1.20.4 limb names come off the renderer's model instead — see
     * {@link EntityMorphLimbNames} for why reflection could not be ported — and
     * an entity with no client renderer (headless, or no inner entity yet)
     * simply yields none.
     */
    protected Collection<String> limbsOf(EntityMorph morph)
    {
        LivingEntity entity = morph.getEntity();

        if (this.mc == null || entity == null)
        {
            return Collections.emptyList();
        }

        return EntityMorphLimbNames.names(EntityMorphRenderer.modelOf(this.mc.getEntityRenderDispatcher(), entity));
    }

    @Override
    public List<Label<NbtCompound>> getPresets(EntityMorph morph)
    {
        List<Label<NbtCompound>> presets = new ArrayList<Label<NbtCompound>>();
        String name = morph.name;

        if (animals.contains(name))
        {
            this.addPreset(morph, presets, "Baby", "{Age:-1}");
        }

        if (name.equals("minecraft:sheep"))
        {
            this.addPreset(morph, presets, "Sheared", "{Sheared:1b}");
            this.addPreset(morph, presets, "Sheared (baby)", "{Age:-1,Sheared:1b}");

            for (int i = 1; i < 16; i++)
            {
                this.addPreset(morph, presets, "Colored sheep #" + i, "{Color:" + i + "}");
            }

            this.addPreset(morph, presets, "Jeb", "{CustomName:'\"jeb_\"'}");
            this.addPreset(morph, presets, "Baby Jeb", "{Age:-1,CustomName:'\"jeb_\"'}");
        }

        if (name.equals("minecraft:slime") || name.equals("minecraft:magma_cube"))
        {
            this.addPreset(morph, presets, "Medium", "{Size:1}");
            this.addPreset(morph, presets, "Big", "{Size:2}");
        }

        if (name.equals("minecraft:cat"))
        {
            this.addPreset(morph, presets, "Baby", "{Age:-1}");

            for (int i = 1; i < 4; i++)
            {
                this.addPreset(morph, presets, "Cat #" + i, "{variant:\"" + CAT_VARIANTS[i - 1] + "\"}");
                this.addPreset(morph, presets, "Cat #" + i + " (baby)", "{variant:\"" + CAT_VARIANTS[i - 1] + "\",Age:-1}");
            }
        }

        if (name.equals("minecraft:parrot"))
        {
            for (int i = 1; i <= 4; i++)
            {
                this.addPreset(morph, presets, "Parrot #" + i, "{Variant:" + i + "}");
            }
        }

        if (name.equals("minecraft:horse"))
        {
            for (int i = 1; i <= 6; i++)
            {
                this.addPreset(morph, presets, "Horse #" + i, "{Variant:" + i + "}");
            }
        }

        if (name.equals("minecraft:llama"))
        {
            for (int i = 1; i < 4; i++)
            {
                this.addPreset(morph, presets, "Llama #" + i, "{Variant:" + i + "}");
            }
        }

        if (name.equals("minecraft:bat"))
        {
            this.addPreset(morph, presets, "Flying", "{BatFlags:2}");
        }

        if (name.equals("minecraft:rabbit"))
        {
            for (int i = 1; i < 6; i++)
            {
                this.addPreset(morph, presets, "Rabbit #" + i, "{RabbitType:" + i + "}");
            }

            this.addPreset(morph, presets, "Toast", "{CustomName:'\"Toast\"'}");
        }

        if (name.equals("minecraft:zombie"))
        {
            this.addPreset(morph, presets, "Baby", "{IsBaby:1b}");
        }

        if (name.equals("minecraft:villager") || name.equals("minecraft:zombie_villager"))
        {
            this.addPreset(morph, presets, "Librarian", villagerData("minecraft:librarian"));
            this.addPreset(morph, presets, "Priest", villagerData("minecraft:cleric"));
            this.addPreset(morph, presets, "Smith", villagerData("minecraft:armorer"));
            this.addPreset(morph, presets, "Butcher", villagerData("minecraft:butcher"));
            this.addPreset(morph, presets, "Nitwit", villagerData("minecraft:nitwit"));
        }

        return presets;
    }

    /**
     * The modern replacement for legacy's {@code {ProfessionName:"..."}}. All
     * three {@code VillagerData} fields are written: the codec that reads it
     * back has no defaults, and a partial compound would leave the villager on
     * its previous profession.
     */
    public static String villagerData(String profession)
    {
        return "{VillagerData:{type:\"minecraft:plains\",profession:\"" + profession + "\",level:1}}";
    }

    /**
     * Legacy's preset override: the morph's own {@code EntityData} is
     * <b>removed</b> and then the preset's is merged in — a replace, not a deep
     * merge, so presets never blend with whatever entity NBT the morph already
     * carried. Everything outside {@code EntityData} (display name, settings,
     * body parts) is untouched.
     *
     * <p>Unparseable SNBT is swallowed exactly as legacy did — a broken preset
     * string drops that one preset instead of taking down the editor.</p>
     */
    @Override
    protected void addPreset(AbstractMorph morph, List<Label<NbtCompound>> list, String label, String json)
    {
        try
        {
            NbtCompound tag = morph.toNBT();
            NbtCompound entity = new NbtCompound();

            tag.remove("EntityData");
            entity.put("EntityData", StringNbtReader.parse(json));
            tag.copyFrom(entity);
            list.add(new Label<NbtCompound>(IKey.str(label), tag));
        }
        catch (Exception e)
        {}
    }
}
