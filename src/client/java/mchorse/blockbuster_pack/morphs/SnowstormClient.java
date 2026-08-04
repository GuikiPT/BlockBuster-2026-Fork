package mchorse.blockbuster_pack.morphs;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.particles.BedrockLibrary;
import mchorse.blockbuster.client.particles.BedrockScheme;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side emitter handling for {@link SnowstormMorph} (roadmap P164).
 *
 * <p>This is the half of the legacy {@code SnowstormMorph} that touched the
 * (client-only) Bedrock particle engine: emitter lifecycle (retiring old
 * emitters into {@code lastEmitters} so in-flight particles finish), the
 * hot-reload check against {@link BedrockLibrary#lastUpdate}, the retired-emitter
 * mirroring and the sanity-tick keep-alive. It is installed into
 * {@link SnowstormMorph#CLIENT} at client init.</p>
 *
 * <p>The <b>draw</b> half — feeding the emitter its per-frame anchor, rotation
 * and scale and handing it to the render handler — is
 * {@code SnowstormMorphRenderer} (P54), which drives the lifecycle methods here.
 * The split matters: a morph class that overrides {@code render} bypasses the
 * P54 dispatcher entirely, so the seam keeps only what the <i>data</i> path
 * needs (scheme changes, merges, the display name, the per-tick keep-alive).</p>
 *
 * <p>Per-instance state lives in a {@link State} stored opaquely on
 * {@link SnowstormMorph#emitters}.</p>
 */
public class SnowstormClient implements ISnowstormClient
{
    /** Install this handler as the morph's client seam. */
    public static void install()
    {
        SnowstormMorph.CLIENT = new SnowstormClient();
    }

    /** Per-morph client state (opaque to the {@code src/main} morph). */
    public static class State
    {
        public BedrockEmitter emitter;
        public List<BedrockEmitter> lastEmitters = new ArrayList<BedrockEmitter>();
        public long lastUpdate;
        public int lastAge = 0;
    }

    /**
     * The per-morph client state, created on demand. Public because the render
     * body lives in {@code SnowstormMorphRenderer} (P54) — legacy kept it on the
     * morph class, where these members were private.
     */
    public State state(SnowstormMorph morph)
    {
        if (!(morph.emitters instanceof State))
        {
            morph.emitters = new State();
        }

        return (State) morph.emitters;
    }

    /**
     * Emitter accessor mirroring the legacy lazy {@code getEmitter()}: creates
     * the emitter on first access.
     */
    public static BedrockEmitter getEmitter(SnowstormMorph morph)
    {
        SnowstormClient client = (SnowstormClient) SnowstormMorph.CLIENT;

        return client == null ? null : client.emitter(morph);
    }

    public BedrockEmitter emitter(SnowstormMorph morph)
    {
        State state = this.state(morph);

        if (state.emitter == null)
        {
            this.setClientScheme(morph, state, morph.scheme);
        }

        return state.emitter;
    }

    public void setClientScheme(SnowstormMorph morph, State state, String key)
    {
        if (state.emitter != null)
        {
            state.emitter.running = false;
            state.lastEmitters.add(state.emitter);
        }

        state.emitter = new BedrockEmitter();
        state.emitter.setScheme(this.getScheme(key), morph.variables);
    }

    public BedrockScheme getScheme(String key)
    {
        /* Null-safe: the preset library is absent on a dedicated server and in
         * headless tests. A null scheme leaves the emitter running-but-empty,
         * exactly as 1.12.2 tolerated a missing preset. */
        if (ClientProxy.particles == null)
        {
            return null;
        }

        return ClientProxy.particles.presets.get(key);
    }

    @Override
    public void setScheme(SnowstormMorph morph, String key)
    {
        State state = this.state(morph);

        if (state.emitter != null)
        {
            this.setClientScheme(morph, state, key);
        }
    }

    @Override
    public void replaceVariable(SnowstormMorph morph, String name, String expression)
    {
        this.emitter(morph).parseVariable(name, expression);
    }

    @Override
    public void merge(SnowstormMorph morph, String incomingScheme)
    {
        State state = this.state(morph);

        if (state.emitter != null)
        {
            if (!morph.scheme.equals(incomingScheme))
            {
                morph.setScheme(incomingScheme);
            }
            else
            {
                state.emitter.parseVariables(morph.variables);
            }
        }
    }

    @Override
    public String getSubclassDisplayName(SnowstormMorph morph)
    {
        BedrockEmitter emitter = this.emitter(morph);

        return emitter.scheme != null ? emitter.scheme.identifier : morph.name;
    }

    @Override
    public void update(SnowstormMorph morph)
    {
        State state = this.state(morph);

        this.emitter(morph).sanityTicks = 0;

        for (BedrockEmitter emitter : state.lastEmitters)
        {
            emitter.sanityTicks = 0;
        }
    }

    /**
     * Legacy's hot-reload check: when the preset library has been reloaded since
     * this emitter was built, rebuild it if the scheme object it holds is no
     * longer the library's. Guarded on a live emitter with a live scheme, so a
     * morph naming a missing preset does not rebuild every frame.
     */
    public void reloadIfStale(SnowstormMorph morph, State state)
    {
        if (state.emitter != null && state.emitter.scheme != null && state.lastUpdate < BedrockLibrary.lastUpdate)
        {
            state.lastUpdate = BedrockLibrary.lastUpdate;

            if (state.emitter.scheme != this.getScheme(morph.scheme))
            {
                this.setClientScheme(morph, state, morph.scheme);
            }
        }
    }

    /**
     * Retired emitters follow the live one's anchor so their in-flight particles
     * keep moving with the morph; those the render handler has already dropped
     * ({@code added == false}) leave the list.
     */
    public void mirrorLastEmitters(State state, BedrockEmitter emitter)
    {
        Iterator<BedrockEmitter> it = state.lastEmitters.iterator();

        while (it.hasNext())
        {
            BedrockEmitter last = it.next();

            if (!last.added)
            {
                it.remove();

                continue;
            }

            last.lastGlobal.set(emitter.lastGlobal);
            last.rotation.set(emitter.rotation);
        }
    }
}
