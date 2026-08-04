package mchorse.metamorph.api.models;

import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * Something that provides an inner {@link AbstractMorph} (roadmap P50).
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/models/IMorphProvider.java
 */
public interface IMorphProvider
{
    public AbstractMorph getMorph();
}
