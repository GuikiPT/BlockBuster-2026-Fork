package mchorse.blockbuster.client.particles.components.appearance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentParticleInitialize;
import mchorse.blockbuster.client.particles.components.IComponentParticleMorphRender;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;
import mchorse.mclib.utils.Interpolations;
import mchorse.metamorph.api.Morph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.client.render.MorphRenderContext;
import mchorse.metamorph.client.render.MorphRenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.StringNbtReader;

import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

/**
 * Particle morph component — {@code blockbuster:particle_morph}.
 *
 * <p>Ported from the 1.12.2 legacy engine. The morph NBT is stored <b>as a
 * string</b> under {@code "nbt"}, alongside {@code enabled} and
 * {@code render_texture} flags. The morph is copied per particle on init.
 * Sorting index is <b>99</b>; when {@code render_texture} is true both the
 * billboard texture AND the morph render. NBT parse errors are silently
 * ignored (total reader).</p>
 *
 * <p>P230: the world-space morph <b>render path</b> is live. The per-particle
 * {@code DummyEntity} comes from {@link BedrockParticle#getDummy(BedrockEmitter)}
 * and the draw goes through {@link MorphRenderPipeline#drawEntity} — the port's
 * equivalent of legacy {@code MorphUtils.render(morph, dummy, 0, 0, 0, 0, partialTicks)},
 * with legacy's {@code RenderHelper.enableStandardItemLighting()} +
 * {@code OpenGlHelper.setLightmapTextureCoords} pair replaced by the packed light
 * value the 1.20.4 render layers consume.</p>
 *
 * <p>The render target (matrices + vertex consumers) is the ambient
 * {@link MorphRenderContext} frame: the world pass pushes one in
 * {@code WorldRendererParticlesMixin}, the GUI model renderer pushes its own. A
 * null frame means "no render target installed" and the draw is skipped, which is
 * what keeps this method callable headlessly.</p>
 */
public class BedrockComponentParticleMorph extends BedrockComponentBase implements IComponentParticleMorphRender, IComponentParticleInitialize
{
    public boolean enabled;
    public boolean renderTexture;
    public Morph morph = new Morph();

    @Override
    public BedrockComponentBase fromJson(JsonElement elem, MolangParser parser) throws MolangException
    {
        if (!elem.isJsonObject()) return super.fromJson(elem, parser);

        JsonObject element = elem.getAsJsonObject();

        if (element.has("enabled")) this.enabled = element.get("enabled").getAsBoolean();
        if (element.has("render_texture")) this.renderTexture = element.get("render_texture").getAsBoolean();

        if (element.has("nbt"))
        {
            try
            {
                this.morph.setDirect(MorphManager.INSTANCE.morphFromNBT(StringNbtReader.parse(element.get("nbt").getAsString())));
            }
            catch (CommandSyntaxException e) {}
        }

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject object = new JsonObject();

        object.addProperty("enabled", this.enabled);
        object.addProperty("render_texture", this.renderTexture);
        if (!this.morph.isEmpty()) object.addProperty("nbt", this.morph.toNBT().toString());

        return object;
    }

    @Override
    public void apply(BedrockEmitter emitter, BedrockParticle particle)
    {
        if (this.enabled && !this.morph.isEmpty())
        {
            particle.morph.set(MorphUtils.copy(this.morph.get()));
        }
    }

    @Override
    public void render(BedrockEmitter emitter, BedrockParticle particle, BufferBuilder builder, float partialTicks)
    {
        Entity camera = MinecraftClient.getInstance() == null ? null : MinecraftClient.getInstance().getCameraEntity();

        if (camera == null || this.morph.isEmpty() || !this.enabled)
        {
            return;
        }

        LivingEntity dummy = particle.getDummy(emitter);
        MorphRenderContext context = MorphRenderContext.current();

        if (dummy == null || context == null || context.matrices == null)
        {
            return;
        }

        double x = Interpolations.lerp(particle.prevPosition.x, particle.position.x, partialTicks);
        double y = Interpolations.lerp(particle.prevPosition.y, particle.position.y, partialTicks);
        double z = Interpolations.lerp(particle.prevPosition.z, particle.position.z, partialTicks);

        Vector3d position = this.calculatePosition(emitter, particle, x, y, z);

        x = position.x;
        y = position.y;
        z = position.z;

        if (!GuiModelRenderer.isRendering())
        {
            x -= Interpolations.lerp(camera.prevX, camera.getX(), partialTicks);
            y -= Interpolations.lerp(camera.prevY, camera.getY(), partialTicks);
            z -= Interpolations.lerp(camera.prevZ, camera.getZ(), partialTicks);
        }

        /* Legacy read {@code dummy.getBrightnessForRender()} — an *entity*
         * brightness lookup at the particle's global position, which never
         * consulted the emitter's {@code lit} flag. getWorldBrightness is that
         * same lookup without the fullbright short-circuit; using the billboard
         * path's getBrightnessForRender here would make a `lit` scheme render
         * its particle morphs fullbright where 1.12.2 lit them from the world. */
        Vector3d global = particle.getGlobalPosition(emitter);
        int light = emitter.getWorldBrightness(global.x, global.y, global.z);

        MatrixStack matrices = context.matrices;

        matrices.push();
        matrices.translate(x, y, z);

        if (particle.relativeScaleBillboard)
        {
            matrices.scale((float) emitter.scale[0], (float) emitter.scale[1], (float) emitter.scale[2]);
        }

        try
        {
            /* Legacy renders the <b>component's</b> morph here, not the
             * per-particle copy ({@code renderOnScreen} uses the particle's) —
             * kept as-is. Yaw 0 falls out of the dummy's zeroed body yaw. */
            MorphRenderPipeline.drawEntity(this.morph.get(), dummy, matrices, context.consumers, light, partialTicks);
        }
        finally
        {
            matrices.pop();
        }
    }

    protected Vector3d calculatePosition(BedrockEmitter emitter, BedrockParticle particle, double px, double py, double pz)
    {
        if (particle.relativePosition && particle.relativeRotation)
        {
            Vector3f vector = new Vector3f((float) px, (float) py, (float) pz);
            emitter.rotation.transform(vector);

            px = vector.x;
            py = vector.y;
            pz = vector.z;

            if (particle.relativeScale)
            {
                Vector3d pos = new Vector3d(px, py, pz);

                Matrix3d scale = new Matrix3d(emitter.scale[0], 0, 0,
                        0, emitter.scale[1], 0,
                        0, 0, emitter.scale[2]);

                scale.transform(pos);

                px = pos.x;
                py = pos.y;
                pz = pos.z;
            }

            px += emitter.lastGlobal.x;
            py += emitter.lastGlobal.y;
            pz += emitter.lastGlobal.z;
        }
        else if (particle.relativeScale)
        {
            Vector3d pos = new Vector3d(px, py, pz);

            Matrix3d scale = new Matrix3d(emitter.scale[0], 0, 0,
                    0, emitter.scale[1], 0,
                    0, 0, emitter.scale[2]);

            pos.sub(emitter.lastGlobal); //transform back to local
            scale.transform(pos);
            pos.add(emitter.lastGlobal); //transform back to global

            px = pos.x;
            py = pos.y;
            pz = pos.z;
        }

        return new Vector3d(px, py, pz);
    }

    @Override
    public void renderOnScreen(BedrockParticle particle, int x, int y, float scale, float partialTicks)
    {
        if (this.enabled && !particle.morph.isEmpty() && particle.morph.get() != null)
        {
            particle.morph.get().renderOnScreen(MinecraftClient.getInstance().player, x, y, scale, 1F);
        }
    }

    @Override
    public void preRender(BedrockEmitter emitter, float partialTicks)
    {}

    @Override
    public void postRender(BedrockEmitter emitter, float partialTicks)
    {}

    @Override
    public int getSortingIndex()
    {
        return 99;
    }
}
