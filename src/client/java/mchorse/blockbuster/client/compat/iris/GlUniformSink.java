package mchorse.blockbuster.client.compat.iris;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import mchorse.blockbuster.client.compat.iris.ShaderCurveBridge.Sink;

/**
 * The GL half of {@link ShaderCurveBridge.Sink} (S21 P218).
 *
 * <p>Writes a curve's value straight into the currently bound program. This is
 * the modern shape of Optifine's {@code ShaderUniform1f.setValue}, which is all
 * legacy's intercepted {@code setProgramUniform1f} ultimately did — the whole
 * ASM apparatus existed to find the <i>call site</i>, not to do anything exotic
 * with GL.</p>
 *
 * <p>Locations are cached per {@code (program, name)} and, critically, the
 * cache stores misses too: a program that does not declare {@code wetness}
 * costs one {@code glGetUniformLocation} for the lifetime of the pack rather
 * than one per frame. {@link #invalidate()} drops the cache — a pack reload
 * recreates every program object, so stale ids must not survive it.</p>
 *
 * <p>Kept out of {@link ShaderCurveBridge} so that class stays free of both
 * Iris <i>and</i> GL, which is what makes the whole machine unit-testable with
 * a fake sink.</p>
 */
public final class GlUniformSink implements Sink
{
    private static GlUniformSink instance;

    /** program id → uniform name → location ({@code -1} = absent). */
    private final Map<Integer, Map<String, Integer>> locations = new HashMap<>();

    private GlUniformSink()
    {}

    /**
     * Install this sink on first use. Idempotent, and called from the render
     * path rather than from client init so the sink is only ever created on an
     * install where the Iris mixins actually applied.
     */
    public static void install()
    {
        if (instance == null)
        {
            instance = new GlUniformSink();
            ShaderCurveBridge.setSink(instance);
        }
    }

    @Override
    public void pushFloat(String name, float value)
    {
        int program = currentProgram();
        int location = this.location(program, name);

        if (location >= 0)
        {
            GL20.glUniform1f(location, value);
        }
    }

    @Override
    public void pushInt(String name, int value)
    {
        int program = currentProgram();
        int location = this.location(program, name);

        if (location >= 0)
        {
            GL20.glUniform1i(location, value);
        }
    }

    @Override
    public void invalidate()
    {
        this.locations.clear();
    }

    private int location(int program, String name)
    {
        if (program <= 0)
        {
            return -1;
        }

        Map<String, Integer> byName = this.locations.computeIfAbsent(program, key -> new HashMap<>());
        Integer cached = byName.get(name);

        if (cached != null)
        {
            return cached;
        }

        int location = GL20.glGetUniformLocation(program, name);

        byName.put(name, location);

        return location;
    }

    private static int currentProgram()
    {
        return GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    }
}
