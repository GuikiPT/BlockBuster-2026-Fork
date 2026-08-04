package mchorse.blockbuster_pack.morphs;

import mchorse.blockbuster.api.ModelTransform;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.RenderingUtils;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.Animation;
import mchorse.metamorph.api.morphs.utils.IAnimationProvider;
import mchorse.metamorph.api.morphs.utils.IMorphGenerator;
import mchorse.metamorph.api.morphs.utils.ISyncableMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;

import javax.vecmath.Vector4d;
import javax.vecmath.Vector4f;
import java.util.Objects;

/**
 * Image morph (roadmap P159).
 *
 * <p>Billboard image morph — "basically replacement for Imaginary" (legacy
 * doc). Full port of Blockbuster 2.7.2's
 * {@code mchorse.blockbuster_pack.morphs.ImageMorph}. This is the data / NBT /
 * geometry-math half; the actual GL draw is an S6 client seam (see
 * {@link #textureSizeProvider}). The load-bearing quirks are all preserved:</p>
 *
 * <ul>
 *   <li><b>Crop NBT order {@code (L, T, R, B)}</b> with the vector packing
 *       {@code x = left, y = top, z = right, w = bottom}. The keys are written
 *       {@code Left}(x) / {@code Right}(z) / {@code Top}(y) / {@code Bottom}(w),
 *       every value truncated with {@code (int)}. Validated quirk — kept.</li>
 *   <li><b>Inverted-omission booleans</b>: {@code Shaded}/{@code Lighting}/
 *       {@code Shadow} are default-true and written only when <b>false</b>;
 *       {@code Billboard}/{@code RemoveParentSpace}/{@code ResizeCrop}/
 *       {@code Keying}/{@code Thickness} default-false and written only when
 *       <b>true</b>.</li>
 *   <li><b>Legacy {@code Scale} back-compat</b>: {@code fromNBT} reads the old
 *       {@code Scale} float and expands it into {@code pose.scale[0..2]};
 *       {@code toNBT} never writes it.</li>
 *   <li>Unknown {@code FacingMode} falls back to {@code ROTATE_XYZ} (total
 *       reader).</li>
 *   <li>{@code ImageAnimation.apply} <b>truncates the crop to {@code int} every
 *       frame</b> during interpolation.</li>
 * </ul>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/morphs/ImageMorph.java
 */
public class ImageMorph extends AbstractMorph implements IAnimationProvider, ISyncableMorph, IMorphGenerator
{
    /**
     * Seam (S6/S7): source of texture pixel dimensions for a
     * {@link ResourceLocation}. Legacy queried the bound GL texture via
     * {@code glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH/HEIGHT)};
     * the Fabric port reads the NativeImage size cached by the runtime texture
     * manager. Null on the server / in headless tests, where {@link #getWidth()}
     * and {@link #getHeight()} return 0 (empty morph → renders nothing).
     */
    public interface ITextureSizeProvider
    {
        int getWidth(ResourceLocation texture);

        int getHeight(ResourceLocation texture);
    }

    public static ITextureSizeProvider textureSizeProvider;

    /**
     * Facing modes exposed by the editor's 4-mode circulate button, in the
     * exact legacy cycle order {@code rotate_xyz → rotate_y → lookat_xyz →
     * lookat_y} ({@code GuiImageMorphPanel.SORTED_FACING_MODES}). Note this is
     * only four of the five {@link RenderingUtils.Facing} values —
     * {@code lookat_direction} is deliberately not offered in the image editor,
     * matching 1.12.2.
     */
    public static final RenderingUtils.Facing[] EDITOR_FACING_ORDER = {
        RenderingUtils.Facing.ROTATE_XYZ,
        RenderingUtils.Facing.ROTATE_Y,
        RenderingUtils.Facing.LOOKAT_XYZ,
        RenderingUtils.Facing.LOOKAT_Y
    };

    /**
     * Image morph's texture
     */
    public ResourceLocation texture;

    /**
     * Whether an image morph gets shaded
     */
    public boolean shaded = true;

    /**
     * Whether an image morph is affected by light map
     */
    public boolean lighting = true;

    /**
     * Whether an image morph should be always look at the player
     */
    public boolean billboard = false;

    /**
     * Whether to remove the scale and rotation of the parent space
     */
    public boolean removeParentScaleRotation = false;

    public RenderingUtils.Facing facing = RenderingUtils.Facing.ROTATE_XYZ;

    /**
     * Area to crop (x = left, z = right, y = top, w = bottom)
     */
    public Vector4f crop = new Vector4f();

    /**
     * Whether this image morph resizes cropped area
     */
    public boolean resizeCrop;

    /**
     * Color filter for the image morph
     */
    public int color = 0xffffffff;

    /**
     * UV horizontal shift
     */
    public float offsetX;

    /**
     * UV vertical shift
     */
    public float offsetY;

    /**
     * Rotation around Z axis
     */
    public float rotation;

    /**
     * TSR for image morph
     */
    public ModelTransform pose = new ModelTransform();

    /**
     * Whether this image morph should cut out background color
     */
    public boolean keying;

    /**
     * Whether it should have 3d effect (thickness)
     */
    public boolean thickness;

    /**
     * Whether Optifine's shadow should be disabled
     */
    public boolean shadow = true;

    public ImageAnimation animation = new ImageAnimation();
    public ImageProperties image = new ImageProperties();

    public ImageMorph()
    {
        super();

        this.name = "blockbuster.image";
    }

    @Override
    public void pause(AbstractMorph previous, int offset)
    {
        this.animation.pause(offset);

        if (previous instanceof ImageMorph)
        {
            ImageMorph image = (ImageMorph) previous;

            this.animation.last = new ImageMorph.ImageProperties();
            this.animation.last.from(image);
        }
        else
        {
            this.animation.last = new ImageMorph.ImageProperties();
            this.animation.last.from(this);
        }
    }

    @Override
    public boolean isPaused()
    {
        return this.animation.paused;
    }

    @Override
    public Animation getAnimation()
    {
        return this.animation;
    }

    @Override
    public boolean canGenerate()
    {
        return this.animation.isInProgress();
    }

    @Override
    public AbstractMorph genCurrentMorph(float partialTicks)
    {
        ImageMorph morph = (ImageMorph) this.copy();

        morph.image.from(this);
        this.animation.apply(morph.image, partialTicks);

        morph.color = morph.image.color.getRGBAColor();
        morph.crop.set(morph.image.crop);
        morph.pose.copy(morph.image.pose);
        morph.offsetX = morph.image.x;
        morph.offsetY = morph.image.y;
        morph.rotation = morph.image.rotation;

        morph.animation.duration = this.animation.progress;

        return morph;
    }

    @Override
    protected String getSubclassDisplayName()
    {
        /* Legacy resolved I18n.format("blockbuster.morph.image"); the raw key
         * is returned main-side and translated by the client GUI. */
        return "blockbuster.morph.image";
    }

    @Override
    public void update(LivingEntity target)
    {
        super.update(target);

        this.animation.update();
    }

    public int getWidth()
    {
        return textureSizeProvider != null ? textureSizeProvider.getWidth(this.texture) : 0;
    }

    public int getHeight()
    {
        return textureSizeProvider != null ? textureSizeProvider.getHeight(this.texture) : 0;
    }

    @Override
    public AbstractMorph create()
    {
        return new ImageMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof ImageMorph)
        {
            ImageMorph morph = (ImageMorph) from;

            this.texture = RLUtils.clone(morph.texture);
            this.shaded = morph.shaded;
            this.lighting = morph.lighting;
            this.billboard = morph.billboard;
            this.removeParentScaleRotation = morph.removeParentScaleRotation;
            this.facing = morph.facing;
            this.crop.set(morph.crop);
            this.resizeCrop = morph.resizeCrop;
            this.color = morph.color;
            this.offsetX = morph.offsetX;
            this.offsetY = morph.offsetY;
            this.rotation = morph.rotation;
            this.pose.copy(morph.pose);
            this.keying = morph.keying;
            this.thickness = morph.thickness;
            this.shadow = morph.shadow;
            this.animation.copy(morph.animation);
            this.animation.reset();
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof ImageMorph)
        {
            ImageMorph image = (ImageMorph) obj;

            result = result && Objects.equals(image.texture, this.texture);
            result = result && image.shaded == this.shaded;
            result = result && image.lighting == this.lighting;
            result = result && image.billboard == this.billboard;
            result = result && image.removeParentScaleRotation == this.removeParentScaleRotation;
            result = result && image.facing == this.facing;
            result = result && image.crop.equals(this.crop);
            result = result && image.resizeCrop == this.resizeCrop;
            result = result && image.color == this.color;
            result = result && image.offsetX == this.offsetX;
            result = result && image.offsetY == this.offsetY;
            result = result && image.rotation == this.rotation;
            result = result && Objects.equals(image.pose, this.pose);
            result = result && image.keying == this.keying;
            result = result && image.thickness == this.thickness;
            result = result && image.shadow == this.shadow;
            result = result && Objects.equals(image.animation, this.animation);
        }

        return result;
    }

    @Override
    public boolean canMerge(AbstractMorph morph)
    {
        if (morph instanceof ImageMorph)
        {
            ImageMorph image = (ImageMorph) morph;

            this.mergeBasic(morph);

            if (!image.animation.ignored)
            {
                this.animation.merge(this, image);
                this.copy(image);
                this.animation.progress = 0;
            }

            return true;
        }

        return super.canMerge(morph);
    }

    @Override
    public float getWidth(LivingEntity target)
    {
        return 0;
    }

    @Override
    public float getHeight(LivingEntity target)
    {
        return 0;
    }

    @Override
    public void reset()
    {
        super.reset();

        this.animation.reset();
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (this.texture != null) tag.put("Texture", RLUtils.writeNbt(this.texture));
        if (this.shaded == false) tag.putBoolean("Shaded", this.shaded);
        if (this.lighting == false) tag.putBoolean("Lighting", this.lighting);
        if (this.billboard == true) tag.putBoolean("Billboard", this.billboard);
        if (this.removeParentScaleRotation == true) tag.putBoolean("RemoveParentSpace", this.removeParentScaleRotation);
        if (this.facing != RenderingUtils.Facing.ROTATE_XYZ) tag.putString("FacingMode", this.facing.id);
        if (this.crop.x != 0) tag.putInt("Left", (int) this.crop.x);
        if (this.crop.z != 0) tag.putInt("Right", (int) this.crop.z);
        if (this.crop.y != 0) tag.putInt("Top", (int) this.crop.y);
        if (this.crop.w != 0) tag.putInt("Bottom", (int) this.crop.w);
        if (this.resizeCrop) tag.putBoolean("ResizeCrop", this.resizeCrop);
        if (this.color != 0xffffffff) tag.putInt("Color", this.color);
        if (this.offsetX != 0) tag.putFloat("OffsetX", this.offsetX);
        if (this.offsetY != 0) tag.putFloat("OffsetY", this.offsetY);
        if (this.rotation != 0) tag.putFloat("Rotation", this.rotation);
        if (!this.pose.isDefault()) tag.put("Pose", this.pose.toNBT());
        if (this.keying) tag.putBoolean("Keying", this.keying);
        if (this.thickness) tag.putBoolean("Thickness", this.thickness);
        if (!this.shadow) tag.putBoolean("Shadow", this.shadow);

        NbtCompound animation = this.animation.toNBT();

        if (!animation.isEmpty())
        {
            tag.put("Animation", animation);
        }
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Texture")) this.texture = RLUtils.create(tag.get("Texture"));
        if (tag.contains("Shaded")) this.shaded = tag.getBoolean("Shaded");
        if (tag.contains("Lighting")) this.lighting = tag.getBoolean("Lighting");
        if (tag.contains("Billboard")) this.billboard = tag.getBoolean("Billboard");
        if (tag.contains("RemoveParentSpace")) this.removeParentScaleRotation = tag.getBoolean("RemoveParentSpace");
        if (tag.contains("FacingMode"))
        {
            RenderingUtils.Facing facing = RenderingUtils.Facing.fromString(tag.getString("FacingMode"));
            this.facing = facing != null ? facing : RenderingUtils.Facing.ROTATE_XYZ;
        }
        if (tag.contains("Left")) this.crop.x = tag.getInt("Left");
        if (tag.contains("Right")) this.crop.z = tag.getInt("Right");
        if (tag.contains("Top")) this.crop.y = tag.getInt("Top");
        if (tag.contains("Bottom")) this.crop.w = tag.getInt("Bottom");
        if (tag.contains("ResizeCrop")) this.resizeCrop = tag.getBoolean("ResizeCrop");
        if (tag.contains("Color")) this.color = tag.getInt("Color");
        if (tag.contains("OffsetX")) this.offsetX = tag.getFloat("OffsetX");
        if (tag.contains("OffsetY")) this.offsetY = tag.getFloat("OffsetY");
        if (tag.contains("Rotation")) this.rotation = tag.getFloat("Rotation");
        if (tag.contains("Animation")) this.animation.fromNBT(tag.getCompound("Animation"));
        if (tag.contains("Pose")) this.pose.fromNBT(tag.getCompound("Pose"));
        if (tag.contains("Keying")) this.keying = tag.getBoolean("Keying");
        if (tag.contains("Thickness")) this.thickness = tag.getBoolean("Thickness");
        if (tag.contains("Shadow")) this.shadow = tag.getBoolean("Shadow");

        if (tag.contains("Scale"))
        {
            float scale = tag.getFloat("Scale");

            this.pose.scale[0] = scale;
            this.pose.scale[1] = scale;
            this.pose.scale[2] = scale;
        }
    }

    /* Geometry math (render core, pure — headless-testable) */

    /**
     * Result of {@link #computeGeometry}: the raw texture UVs used for the quad
     * ({@code uv}), the {@code finalUv} used to size the quad (reset to full
     * when {@code resizeCrop}), and the four quad-vertex positions ({@code pos},
     * packed {@code x = left, y = right, z = top, w = bottom}). All in
     * {@code double} to match the legacy {@code Vector4d} arithmetic exactly.
     */
    public static class Geometry
    {
        public final Vector4d uv;
        public final Vector4d finalUv;
        public final Vector4d pos;

        public Geometry(Vector4d uv, Vector4d finalUv, Vector4d pos)
        {
            this.uv = uv;
            this.finalUv = finalUv;
            this.pos = pos;
        }
    }

    /**
     * Compute the billboard quad geometry exactly as legacy {@code renderPicture}
     * did (before the CPU/GL texture-matrix step). {@code w}/{@code h} are the
     * texture pixel dimensions.
     *
     * <p>UVs: {@code uv.x = cropL / w}, {@code uv.y = 1 - cropR / w},
     * {@code uv.z = cropT / h}, {@code uv.w = 1 - cropB / h}. When
     * {@code resizeCrop} the {@code finalUv} resets to {@code (0, 1, 0, 1)} and
     * the effective size shrinks by the crop margins; otherwise {@code finalUv}
     * equals {@code uv}. Aspect ratios normalise the longer edge, and the quad
     * corners derive from {@code finalUv} centred on {@code 0.5}, scaled.</p>
     */
    public static Geometry computeGeometry(float cropL, float cropT, float cropR, float cropB, float w, float h, boolean resizeCrop, float scale)
    {
        Vector4d uv = new Vector4d();

        /* x = u1, y = u2, z = v1, w = v2 */
        uv.x = cropL / (double) w;
        uv.y = 1.0F - cropR / (double) w;
        uv.z = cropT / (double) h;
        uv.w = 1.0F - cropB / (double) h;

        Vector4d finalUv = new Vector4d(uv);

        if (resizeCrop)
        {
            finalUv.set(0F, 1F, 0F, 1F);

            w = w - cropL - cropR;
            h = h - cropT - cropB;
        }

        double ratioX = w > h ? h / (double) w : 1D;
        double ratioY = h > w ? w / (double) h : 1D;

        Vector4d pos = new Vector4d(
            -(finalUv.x - 0.5) * ratioY,
            -(finalUv.y - 0.5) * ratioY,
            (finalUv.z - 0.5) * ratioX,
            (finalUv.w - 0.5) * ratioX
        );
        pos.scale(scale);

        return new Geometry(uv, finalUv, pos);
    }

    /**
     * CPU port of the legacy {@code GL_TEXTURE} matrix path in
     * {@code renderPicture}. Core-profile Minecraft has no texture-matrix stack,
     * so each UV is pre-transformed on the CPU by the identical affine
     * {@code T(0.5) · T(offset/size) · R(rotation, z) · T(-0.5)}:
     *
     * <pre>u' = R(u - 0.5) + offset/size + 0.5</pre>
     *
     * with {@code R} the GL counter-clockwise Z rotation for the given degrees.
     * Only applied when any of {@code offsetX}/{@code offsetY}/{@code rotation}
     * are non-zero (matching the {@code textureMatrix} gate).
     *
     * @return the transformed {@code [u, v]}.
     */
    public static double[] transformUV(double u, double v, float offsetX, float offsetY, float ow, float oh, float rotationDeg)
    {
        double rad = Math.toRadians(rotationDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double cu = u - 0.5;
        double cv = v - 0.5;

        /* GL rotate about +Z: x' = x cos - y sin, y' = x sin + y cos */
        double ru = cu * cos - cv * sin;
        double rv = cu * sin + cv * cos;

        ru += offsetX / (double) ow + 0.5;
        rv += offsetY / (double) oh + 0.5;

        return new double[] {ru, rv};
    }

    public static class ImageAnimation extends Animation
    {
        public ImageProperties last;

        public void merge(ImageMorph last, ImageMorph next)
        {
            this.merge(next.animation);

            if (this.last == null)
            {
                this.last = new ImageProperties();
            }

            this.last.from(last);
        }

        public void apply(ImageProperties properties, float partialTicks)
        {
            if (this.last == null)
            {
                return;
            }

            float factor = this.getFactor(partialTicks);

            properties.color.r = this.interp.interpolate(this.last.color.r, properties.color.r, factor);
            properties.color.g = this.interp.interpolate(this.last.color.g, properties.color.g, factor);
            properties.color.b = this.interp.interpolate(this.last.color.b, properties.color.b, factor);
            properties.color.a = this.interp.interpolate(this.last.color.a, properties.color.a, factor);
            properties.crop.x = (int) this.interp.interpolate(this.last.crop.x, properties.crop.x, factor);
            properties.crop.y = (int) this.interp.interpolate(this.last.crop.y, properties.crop.y, factor);
            properties.crop.z = (int) this.interp.interpolate(this.last.crop.z, properties.crop.z, factor);
            properties.crop.w = (int) this.interp.interpolate(this.last.crop.w, properties.crop.w, factor);
            properties.pose.interpolate(this.last.pose, properties.pose, factor, this.interp);
            properties.x = this.interp.interpolate(this.last.x, properties.x, factor);
            properties.y = this.interp.interpolate(this.last.y, properties.y, factor);
            properties.rotation = this.interp.interpolate(this.last.rotation, properties.rotation, factor);
        }
    }

    public static class ImageProperties
    {
        public Color color = new Color();
        public Vector4f crop = new Vector4f();
        public ModelTransform pose = new ModelTransform();
        public float x;
        public float y;
        public float rotation;

        public void from(ImageMorph morph)
        {
            this.color.set(morph.color, true);
            this.crop.set(morph.crop);
            this.pose.copy(morph.pose);
            this.x = morph.offsetX;
            this.y = morph.offsetY;
            this.rotation = morph.rotation;
        }
    }
}
