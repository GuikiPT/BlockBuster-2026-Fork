package mchorse.blockbuster.client.particles.components.motion;

import mchorse.blockbuster.client.particles.components.BedrockComponentBase;

/**
 * Shared abstract base for the {@code minecraft:particle_motion_*} update
 * components (roadmap P151). Carries no state — it exists only to preserve the
 * legacy class hierarchy ({@code BedrockComponentMotionDynamic} and
 * {@code BedrockComponentMotionParametric} extend it) for diff-ability against
 * the 1.12.2 source. {@code BedrockComponentMotionCollision} deliberately extends
 * {@link BedrockComponentBase} directly, exactly like the legacy engine.
 */
public abstract class BedrockComponentMotion extends BedrockComponentBase
{}
