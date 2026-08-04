package mchorse.blockbuster.client.particles.components.expiration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentParticleUpdate;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.mclib.math.Operation;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;

import javax.vecmath.Vector3d;

/**
 * {@code minecraft:particle_kill_plane} — roadmap P150.
 *
 * <p>JSON is a 4-element array {@code [a, b, c, d]} describing the plane
 * {@code a·x + b·y + c·z + d = 0}. The particle is killed when the <b>sign</b>
 * of that expression differs between its previous and current position (strict
 * crossing detection — exactly touching, value {@code 0}, does <em>not</em>
 * kill). Non-relative particles are converted to emitter-local space first. An
 * all-zero plane serializes as an empty array (and is thus omitted per
 * {@code isEmpty}). {@link #getSortingIndex()} is {@code 100}.</p>
 *
 * <p>The public {@code a/b/c/d} floats are also read by the editor's kill-plane
 * visualization (P155) — keep field names / visibility.</p>
 */
public class BedrockComponentKillPlane extends BedrockComponentBase implements IComponentParticleUpdate
{
    public float a;
    public float b;
    public float c;
    public float d;

    @Override
    public BedrockComponentBase fromJson(JsonElement element, MolangParser parser) throws MolangException
    {
        if (!element.isJsonArray())
        {
            return super.fromJson(element, parser);
        }

        JsonArray array = element.getAsJsonArray();

        if (array.size() >= 4)
        {
            this.a = array.get(0).getAsFloat();
            this.b = array.get(1).getAsFloat();
            this.c = array.get(2).getAsFloat();
            this.d = array.get(3).getAsFloat();
        }

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonArray array = new JsonArray();

        if (Operation.equals(this.a, 0) && Operation.equals(this.b, 0) && Operation.equals(this.c, 0) && Operation.equals(this.d, 0))
        {
            return array;
        }

        array.add(this.a);
        array.add(this.b);
        array.add(this.c);
        array.add(this.d);

        return array;
    }

    @Override
    public void update(BedrockEmitter emitter, BedrockParticle particle)
    {
        if (particle.dead)
        {
            return;
        }

        Vector3d prevLocal = new Vector3d(particle.prevPosition);
        Vector3d local = new Vector3d(particle.position);

        if (!particle.relativePosition)
        {
            local.sub(emitter.lastGlobal);
            prevLocal.sub(emitter.lastGlobal);
        }

        double prev = this.a * prevLocal.x + this.b * prevLocal.y + this.c * prevLocal.z + this.d;
        double now = this.a * local.x + this.b * local.y + this.c * local.z + this.d;

        if ((prev > 0 && now < 0) || (prev < 0 && now > 0))
        {
            particle.dead = true;
        }
    }

    @Override
    public int getSortingIndex()
    {
        return 100;
    }
}
