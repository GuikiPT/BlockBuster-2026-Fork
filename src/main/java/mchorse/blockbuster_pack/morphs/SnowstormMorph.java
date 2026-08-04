package mchorse.blockbuster_pack.morphs;

import mchorse.mclib.utils.Interpolations;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector4f;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Snowstorm morph (roadmap P164) — a Bedrock ("Snowstorm") particle emitter for
 * a named scheme, attached to the rendered body-part matrix.
 *
 * <p>Port note (source-set split). The 1.12.2 class held its
 * {@code BedrockEmitter} directly, guarding the client-only members with
 * {@code @SideOnly(Side.CLIENT)}. On Fabric the whole Bedrock particle engine
 * (S13) lives in the <b>client</b> source set, which a {@code src/main} class
 * cannot reference. So the NBT/variables/scheme <b>data model</b> stays here
 * (records, scenes, player data and the {@code /morph} command all need to
 * parse the morph server-side) and every emitter-touching behaviour is routed
 * through the {@link ISnowstormClient} seam, installed at client init. On a
 * dedicated server {@link #CLIENT} is {@code null} and the morph is pure data —
 * exactly the shape the legacy {@code @SideOnly} stripping produced.</p>
 *
 * <p>The two render methods are <b>not</b> overridden here: they go through the
 * P54 dispatcher to {@code SnowstormMorphRenderer}, which is also what drives
 * the seam's emitter lifecycle per frame. An override would bypass the
 * dispatcher.</p>
 *
 * <p>The per-instance client state (the live emitter + retired
 * {@code lastEmitters} + the {@code lastUpdate}/{@code lastAge} bookkeeping) is
 * held opaquely in {@link #emitters}; only the client seam knows its concrete
 * type.</p>
 *
 * <p>{@link #getMatrix()}, {@link #getVector()} and {@link #calculateGlobal}
 * are pure math (no engine types) and stay here so they can be <b>shared</b>
 * with {@code ParticleMorph} (P163) exactly as in 1.12.2, where
 * {@code ParticleMorph} calls {@code SnowstormMorph.calculateGlobal}.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/morphs/SnowstormMorph.java
 */
public class SnowstormMorph extends AbstractMorph
{
    /**
     * Client-side emitter seam. Installed at client init (see the client
     * {@code SnowstormClient}). {@code null} on the server — the morph is then
     * pure data, mirroring 1.12.2's {@code @SideOnly(Side.CLIENT)} stripping.
     *
     * <p>P230: the install call lives in
     * {@code BlockbusterMorphRenderers.register()} (reached from
     * {@code BlockbusterClient}), alongside the morph's renderer registration.
     * On a dedicated server, and in headless tests that do not install it, this
     * stays null and only the data path (NBT/merge/copy) is exercised.</p>
     */
    public static ISnowstormClient CLIENT;

    /** Legacy static scratch {@link Matrix4f}, created lazily. */
    private static Matrix4f matrix;

    /** Legacy static scratch {@link Vector4f}, created lazily. */
    private static Vector4f vector;

    public String scheme = "";
    public Map<String, String> variables = new HashMap<String, String>();

    /**
     * Opaque per-instance client state (the emitter + {@code lastEmitters} +
     * {@code lastUpdate}/{@code lastAge}). Concrete type known only to the
     * client seam; {@code null} until an emitter is first requested.
     */
    public Object emitters;

    public static Matrix4f getMatrix()
    {
        if (matrix == null)
        {
            matrix = new Matrix4f();
        }

        return matrix;
    }

    public static Vector4f getVector()
    {
        if (vector == null)
        {
            vector = new Vector4f();
        }

        return vector;
    }

    /**
     * Transform {@code (x, y, z, 1)} by the given (entity-relative) matrix and
     * add the entity's lerped world position — the emitter's global anchor.
     * Shared verbatim with {@code ParticleMorph} (P163), as in 1.12.2.
     */
    public static Vector4f calculateGlobal(Matrix4f matrix, LivingEntity entity, float x, float y, float z, float partial)
    {
        Vector4f vector4f = getVector();

        vector4f.set(x, y, z, 1);
        matrix.transform(vector4f);
        vector4f.add(new Vector4f(
            (float) Interpolations.lerp(entity.prevX, entity.getX(), partial),
            (float) Interpolations.lerp(entity.prevY, entity.getY(), partial),
            (float) Interpolations.lerp(entity.prevZ, entity.getZ(), partial),
            (float) 0
        ));

        return vector4f;
    }

    public SnowstormMorph()
    {
        super();
        this.name = "snowstorm";
    }

    public void replaceVariable(String name, String expression)
    {
        this.variables.put(name, expression);

        if (CLIENT != null)
        {
            CLIENT.replaceVariable(this, name, expression);
        }
    }

    public void setScheme(String key)
    {
        this.scheme = key;

        if (CLIENT != null)
        {
            CLIENT.setScheme(this, key);
        }
    }

    @Override
    protected String getSubclassDisplayName()
    {
        return CLIENT != null ? CLIENT.getSubclassDisplayName(this) : this.name;
    }

    @Override
    public void update(LivingEntity target)
    {
        super.update(target);

        if (target.getWorld().isClient() && CLIENT != null)
        {
            CLIENT.update(this);
        }
    }

    @Override
    public AbstractMorph create()
    {
        return new SnowstormMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof SnowstormMorph)
        {
            SnowstormMorph morph = (SnowstormMorph) from;

            this.setScheme(morph.scheme);
            this.variables.putAll(morph.variables);
        }
    }

    @Override
    public float getWidth(LivingEntity entityLivingBase)
    {
        return 0.6F;
    }

    @Override
    public float getHeight(LivingEntity entityLivingBase)
    {
        return 1.8F;
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof SnowstormMorph)
        {
            SnowstormMorph morph = (SnowstormMorph) obj;

            result = result && Objects.equals(this.scheme, morph.scheme);
            result = result && Objects.equals(this.variables, morph.variables);
        }

        return result;
    }

    @Override
    public boolean useTargetDefault()
    {
        return true;
    }

    @Override
    public boolean canMerge(AbstractMorph morph)
    {
        if (morph instanceof SnowstormMorph)
        {
            SnowstormMorph snow = (SnowstormMorph) morph;

            this.mergeBasic(morph);

            /* Legacy adopts the incoming variables map by reference (kept). */
            this.variables = snow.variables;

            /* The scheme is only updated inside the client seam's emitter-gated
             * branch (setScheme on a scheme change). On the server the emitter
             * is null, so this.scheme is deliberately NOT changed by a merge —
             * a faithful 1.12.2 quirk. */
            if (CLIENT != null)
            {
                CLIENT.merge(this, snow.scheme);
            }

            return true;
        }

        return super.canMerge(morph);
    }

    @Override
    public void reset()
    {
        super.reset();

        this.scheme = "";
        this.variables.clear();
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Vars"))
        {
            NbtCompound vars = tag.getCompound("Vars");

            for (String key : vars.getKeys())
            {
                this.variables.put(key, vars.getString(key));
            }
        }

        if (tag.contains("Scheme"))
        {
            this.setScheme(tag.getString("Scheme"));
        }
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (!this.variables.isEmpty())
        {
            NbtCompound vars = new NbtCompound();

            for (Map.Entry<String, String> entry : this.variables.entrySet())
            {
                vars.putString(entry.getKey(), entry.getValue());
            }

            tag.put("Vars", vars);
        }

        /* Written unconditionally, even when empty — unlike almost every other
         * morph field. Goldens must expect Scheme:"" on a fresh morph. */
        tag.putString("Scheme", this.scheme);
    }
}
