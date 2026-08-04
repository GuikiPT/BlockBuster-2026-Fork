package mchorse.metamorph.api.morphs.utils;

import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * A morph that can generate a per-frame snapshot morph (roadmap P50).
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/morphs/utils/IMorphGenerator.java
 */
public interface IMorphGenerator
{
    public boolean canGenerate();

    public AbstractMorph genCurrentMorph(float partialTicks);
}
