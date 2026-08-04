package mchorse.vanilla_pack.morphs;

import java.util.Objects;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * Label morph — a floating text billboard (roadmap P53.2).
 *
 * <p>Default text {@code "Lorem ipsum"} ({@link #DEFAULT_LABEL}). NBT (all
 * omit-default): {@code Label}, {@code Max} (word-wrap width, written when
 * {@code > 0}), {@code AnchorX}/{@code AnchorY} (0.5), {@code Color} (0xffffff),
 * {@code Shadow}, {@code ShadowX}/{@code ShadowY} (1), {@code ShadowColor}
 * (written when non-zero), {@code Lighting} (written only when false),
 * {@code Background} (ARGB), {@code Offset} (3), {@code Billboard}.</p>
 *
 * <p>All rendering (world scale 1/48, ~16 chars per block, 12 px per wrapped
 * line, billboard matrix, shadow quad) is client-side (P54); only data + NBT
 * land here.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/morphs/LabelMorph.java
 */
public class LabelMorph extends AbstractMorph
{
    public static final String DEFAULT_LABEL = "Lorem ipsum";

    public String label = DEFAULT_LABEL;
    public int max = -1;
    public float anchorX = 0.5F;
    public float anchorY = 0.5F;
    public int color = 0xffffff;
    public boolean lighting = true;

    /* Shadow properties */
    public boolean shadow = false;
    public float shadowX = 1F;
    public float shadowY = 1F;
    public int shadowColor = 0;

    /* Background */
    public int background = 0x00000000;
    public float offset = 3;

    public boolean billboard;

    public LabelMorph()
    {
        this.name = "label";
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof LabelMorph)
        {
            LabelMorph label = (LabelMorph) obj;

            result = result && Objects.equals(this.label, label.label);
            result = result && this.max == label.max;
            result = result && this.anchorX == label.anchorX;
            result = result && this.anchorY == label.anchorY;
            result = result && this.color == label.color;
            result = result && this.shadow == label.shadow;
            result = result && this.shadowX == label.shadowX;
            result = result && this.shadowY == label.shadowY;
            result = result && this.shadowColor == label.shadowColor;
            result = result && this.lighting == label.lighting;
            result = result && this.background == label.background;
            result = result && this.offset == label.offset;
            result = result && this.billboard == label.billboard;
        }

        return result;
    }

    @Override
    public AbstractMorph create()
    {
        return new LabelMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof LabelMorph)
        {
            LabelMorph label = (LabelMorph) from;

            this.label = label.label;
            this.max = label.max;
            this.anchorX = label.anchorX;
            this.anchorY = label.anchorY;
            this.color = label.color;
            this.shadow = label.shadow;
            this.shadowX = label.shadowX;
            this.shadowY = label.shadowY;
            this.shadowColor = label.shadowColor;
            this.lighting = label.lighting;
            this.background = label.background;
            this.offset = label.offset;
            this.billboard = label.billboard;
        }
    }

    @Override
    public float getWidth(LivingEntity target)
    {
        return target.getWidth();
    }

    @Override
    public float getHeight(LivingEntity target)
    {
        return target.getHeight();
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (!this.label.equals(DEFAULT_LABEL)) tag.putString("Label", this.label);
        if (this.max > 0) tag.putInt("Max", this.max);
        if (this.anchorX != 0.5F) tag.putFloat("AnchorX", this.anchorX);
        if (this.anchorY != 0.5F) tag.putFloat("AnchorY", this.anchorY);
        if (this.color != 0xffffff) tag.putInt("Color", this.color);
        if (this.shadow) tag.putBoolean("Shadow", this.shadow);
        if (this.shadowX != 1F) tag.putFloat("ShadowX", this.shadowX);
        if (this.shadowY != 1F) tag.putFloat("ShadowY", this.shadowY);
        if (this.shadowColor != 0) tag.putInt("ShadowColor", this.shadowColor);
        if (!this.lighting) tag.putBoolean("Lighting", this.lighting);
        if (this.background != 0) tag.putInt("Background", this.background);
        if (this.offset != 3) tag.putFloat("Offset", this.offset);
        if (this.billboard) tag.putBoolean("Billboard", this.billboard);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Label")) this.label = tag.getString("Label");
        if (tag.contains("Max")) this.max = tag.getInt("Max");
        if (tag.contains("AnchorX")) this.anchorX = tag.getFloat("AnchorX");
        if (tag.contains("AnchorY")) this.anchorY = tag.getFloat("AnchorY");
        if (tag.contains("Color")) this.color = tag.getInt("Color");
        if (tag.contains("Shadow")) this.shadow = tag.getBoolean("Shadow");
        if (tag.contains("ShadowX")) this.shadowX = tag.getFloat("ShadowX");
        if (tag.contains("ShadowY")) this.shadowY = tag.getFloat("ShadowY");
        if (tag.contains("ShadowColor")) this.shadowColor = tag.getInt("ShadowColor");
        if (tag.contains("Lighting")) this.lighting = tag.getBoolean("Lighting");
        if (tag.contains("Background")) this.background = tag.getInt("Background");
        if (tag.contains("Offset")) this.offset = tag.getFloat("Offset");
        if (tag.contains("Billboard")) this.billboard = tag.getBoolean("Billboard");
    }
}
