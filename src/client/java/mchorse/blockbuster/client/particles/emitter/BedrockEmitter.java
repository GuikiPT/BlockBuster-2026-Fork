package mchorse.blockbuster.client.particles.emitter;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.particles.BedrockScheme;
import mchorse.blockbuster.client.particles.SnowstormRenderSetup;
import mchorse.blockbuster.client.particles.components.IComponentEmitterInitialize;
import mchorse.blockbuster.client.particles.components.IComponentEmitterUpdate;
import mchorse.blockbuster.client.particles.components.IComponentParticleInitialize;
import mchorse.blockbuster.client.particles.components.IComponentParticleMorphRender;
import mchorse.blockbuster.client.particles.components.IComponentParticleRender;
import mchorse.blockbuster.client.particles.components.IComponentParticleUpdate;
import mchorse.blockbuster.client.particles.components.IComponentRenderBase;
import mchorse.blockbuster.client.particles.components.appearance.BedrockComponentAppearanceBillboard;
import mchorse.blockbuster.client.particles.components.appearance.BedrockComponentCollisionAppearance;
import mchorse.blockbuster.client.particles.components.appearance.BedrockComponentParticleMorph;
import mchorse.blockbuster.client.particles.components.meta.BedrockComponentInitialization;
import mchorse.blockbuster.client.particles.components.rate.BedrockComponentRateSteady;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.math.IValue;
import mchorse.mclib.math.Variable;
import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.vecmath.Matrix3f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Bedrock ("Snowstorm") emitter simulation core — roadmap P149.
 *
 * <p>Ported from the 1.12.2 legacy engine
 * ({@code mchorse.blockbuster.client.particles.emitter.BedrockEmitter}). This
 * file carries only the tick-exact <b>simulation</b> surface: the MoLang variable
 * plumbing, the emitter/particle lifecycle, particle creation with relative-space
 * finalization, and the per-tick component-interface driving. The <b>render</b>
 * half (renderOnScreen/render/renderParticles/setupOpenGL/depthSorting, and the
 * morph-particle predicate {@code isMorphParticle}) is deferred to P153 and is
 * intentionally absent here.</p>
 *
 * <p>All timings are in seconds ({@link #getAge(float)} divides ticks by 20);
 * lifetimes are stored in ticks. Determinism for the P156 golden comes from
 * {@link RandomProvider} (the emitter's four {@code random} fields and each
 * {@link #stop()} reroll draw through it).</p>
 */
public class BedrockEmitter
{
    public BedrockScheme scheme;
    public List<BedrockParticle> particles = new ArrayList<BedrockParticle>();
    public List<BedrockParticle> splitParticles = new ArrayList<BedrockParticle>();
    public Map<String, IValue> variables;
    public Map<String, Double> initialValues = new HashMap<String, Double>();

    public LivingEntity target;
    public World world;
    public boolean lit;

    public boolean added;
    public int sanityTicks;
    public boolean running = true;

    /* Intermediate values */
    public Vector3d lastGlobal = new Vector3d();
    public Vector3d prevGlobal = new Vector3d();
    public Matrix3f rotation = new Matrix3f(1, 0, 0, 0, 1, 0, 0, 0, 1);
    public Matrix3f prevRotation = new Matrix3f(1, 0, 0, 0, 1, 0, 0, 0, 1);
    public Vector3f angularVelocity = new Vector3f();
    /**
     * Translation of immediate bodypart
     */
    public Vector3d translation = new Vector3d();

    /* Runtime properties */
    public int age;
    public int lifetime;
    public double spawnedParticles;
    public boolean playing = true;

    public float random1 = (float) RandomProvider.nextDouble();
    public float random2 = (float) RandomProvider.nextDouble();
    public float random3 = (float) RandomProvider.nextDouble();
    public float random4 = (float) RandomProvider.nextDouble();

    private BlockPos.Mutable blockPos = new BlockPos.Mutable();

    public double[] scale = {1, 1, 1};

    /**
     * Persistent particle used by {@link #renderOnScreen(int, int, float)} for the
     * GUI preview (recreated when it dies). Legacy field of the same name.
     */
    public BedrockParticle guiParticle;

    /* Camera properties */
    public int perspective;
    public float cYaw;
    public float cPitch;

    public double cX;
    public double cY;
    public double cZ;

    /* ------------------------------------------------------------------ *
     * P275 — the render origin billboard vertices are emitted relative to *
     * ------------------------------------------------------------------ */

    /**
     * The point this emitter's billboard quads are emitted <b>relative to</b>,
     * set for the duration of a world pass by {@link #setupOpenGL(float)} and
     * cleared by {@link #endOpenGL()}. Zero everywhere else (the dashboard
     * viewport and the GUI thumbnail draw in their own local space, exactly as
     * 1.12.2 did).
     *
     * <p>This is the 1.20.4 spelling of legacy {@code setupOpenGL}'s
     * {@code BufferBuilder.setTranslation(-playerX, -playerY, -playerZ)}, which
     * subtracted the camera position from every vertex as it was written into
     * the buffer. 1.20.4 removed {@code setTranslation}, so the port had put the
     * same offset into {@link RenderSystem#getModelViewStack()} instead — the
     * two are equivalent for {@code gl_Position} but <b>not</b> for fog, and the
     * difference is a live rendering defect. See {@link #setupOpenGL(float)}.</p>
     */
    public double renderOriginX;
    public double renderOriginY;
    public double renderOriginZ;

    /* Cached variable references to avoid hash look ups */
    private Variable varAge;
    private Variable varLifetime;
    private Variable varRandom1;
    private Variable varRandom2;
    private Variable varRandom3;
    private Variable varRandom4;

    /* Exclusive Blockbuster variables */
    private Variable varSpeedABS;
    private Variable varSpeedX;
    private Variable varSpeedY;
    private Variable varSpeedZ;
    private Variable varPosX;
    private Variable varPosY;
    private Variable varPosZ;
    private Variable varPosDistance;
    private Variable varBounces;

    private Variable varEmitterAge;
    private Variable varEmitterLifetime;
    private Variable varEmitterRandom1;
    private Variable varEmitterRandom2;
    private Variable varEmitterRandom3;
    private Variable varEmitterRandom4;

    public boolean isFinished()
    {
        return !this.running && this.particles.isEmpty();
    }

    public double getDistanceSq()
    {
        this.setupCameraProperties(0F);

        double dx = this.cX - this.lastGlobal.x;
        double dy = this.cY - this.lastGlobal.y;
        double dz = this.cZ - this.lastGlobal.z;

        return dx * dx + dy * dy + dz * dz;
    }

    public double getAge()
    {
        return this.getAge(0);
    }

    public double getAge(float partialTicks)
    {
        return (this.age + partialTicks) / 20.0;
    }

    public void setTarget(LivingEntity target)
    {
        this.target = target;
        this.world = target == null ? null : target.getWorld();
    }

    public void setScheme(BedrockScheme scheme)
    {
        this.setScheme(scheme, null);
    }

    public void setScheme(BedrockScheme scheme, Map<String, String> variables)
    {
        this.scheme = scheme;

        if (this.scheme == null)
        {
            return;
        }

        if (variables != null)
        {
            this.parseVariables(variables);
        }

        this.lit = true;
        this.stop();
        this.start();

        this.setupVariables();
        this.setEmitterVariables(0);
    }

    /* Variable related code */

    public void setupVariables()
    {
        this.varAge = this.scheme.parser.variables.get("variable.particle_age");
        this.varLifetime = this.scheme.parser.variables.get("variable.particle_lifetime");
        this.varRandom1 = this.scheme.parser.variables.get("variable.particle_random_1");
        this.varRandom2 = this.scheme.parser.variables.get("variable.particle_random_2");
        this.varRandom3 = this.scheme.parser.variables.get("variable.particle_random_3");
        this.varRandom4 = this.scheme.parser.variables.get("variable.particle_random_4");

        this.varSpeedABS = this.scheme.parser.variables.get("variable.particle_speed.length");
        this.varSpeedX = this.scheme.parser.variables.get("variable.particle_speed.x");
        this.varSpeedY = this.scheme.parser.variables.get("variable.particle_speed.y");
        this.varSpeedZ = this.scheme.parser.variables.get("variable.particle_speed.z");
        this.varPosX = this.scheme.parser.variables.get("variable.particle_pos.x");
        this.varPosY = this.scheme.parser.variables.get("variable.particle_pos.y");
        this.varPosZ = this.scheme.parser.variables.get("variable.particle_pos.z");
        this.varPosDistance = this.scheme.parser.variables.get("variable.particle_pos.distance");
        this.varBounces = this.scheme.parser.variables.get("variable.particle_bounces");

        this.varEmitterAge = this.scheme.parser.variables.get("variable.emitter_age");
        this.varEmitterLifetime = this.scheme.parser.variables.get("variable.emitter_lifetime");
        this.varEmitterRandom1 = this.scheme.parser.variables.get("variable.emitter_random_1");
        this.varEmitterRandom2 = this.scheme.parser.variables.get("variable.emitter_random_2");
        this.varEmitterRandom3 = this.scheme.parser.variables.get("variable.emitter_random_3");
        this.varEmitterRandom4 = this.scheme.parser.variables.get("variable.emitter_random_4");
    }

    public void setParticleVariables(BedrockParticle particle, float partialTicks)
    {
        if (this.varAge != null) this.varAge.set(particle.getAge(partialTicks));
        if (this.varLifetime != null) this.varLifetime.set(particle.lifetime / 20.0);
        if (this.varRandom1 != null) this.varRandom1.set(particle.random1);
        if (this.varRandom2 != null) this.varRandom2.set(particle.random2);
        if (this.varRandom3 != null) this.varRandom3.set(particle.random3);
        if (this.varRandom4 != null) this.varRandom4.set(particle.random4);

        Vector3d relativePos = new Vector3d(particle.getGlobalPosition(this));
        relativePos.sub(this.lastGlobal);

        if (this.varPosDistance != null) this.varPosDistance.set(relativePos.length());
        if (this.varPosX != null) this.varPosX.set(relativePos.x);
        if (this.varPosY != null) this.varPosY.set(relativePos.y);
        if (this.varPosZ != null) this.varPosZ.set(relativePos.z);
        if (this.varSpeedABS != null) this.varSpeedABS.set(particle.speed.length());
        if (this.varSpeedX != null) this.varSpeedX.set(particle.speed.x);
        if (this.varSpeedY != null) this.varSpeedY.set(particle.speed.y);
        if (this.varSpeedZ != null) this.varSpeedZ.set(particle.speed.z);
        if (this.varBounces != null) this.varBounces.set(particle.bounces);

        this.scheme.updateCurves();

        BedrockComponentInitialization component = this.scheme.get(BedrockComponentInitialization.class);

        if (component != null)
        {
            /* P150: the Blockbuster/Chryfi per-particle particle_update_expression
             * runs on every particle variable-refresh (local to the particle). */
            component.particleUpdate.get();
        }
    }

    public void setEmitterVariables(float partialTicks)
    {
        for (Map.Entry<String, Double> entry : this.initialValues.entrySet())
        {
            Variable var = this.scheme.parser.variables.get(entry.getKey());

            if (var != null)
            {
                var.set(entry.getValue());
            }
        }

        if (this.varEmitterAge != null) this.varEmitterAge.set(this.getAge(partialTicks));
        if (this.varEmitterLifetime != null) this.varEmitterLifetime.set(this.lifetime / 20.0);
        if (this.varEmitterRandom1 != null) this.varEmitterRandom1.set(this.random1);
        if (this.varEmitterRandom2 != null) this.varEmitterRandom2.set(this.random2);
        if (this.varEmitterRandom3 != null) this.varEmitterRandom3.set(this.random3);
        if (this.varEmitterRandom4 != null) this.varEmitterRandom4.set(this.random4);

        this.scheme.updateCurves();
    }

    public void parseVariables(Map<String, String> variables)
    {
        this.variables = new HashMap<String, IValue>();

        for (Map.Entry<String, String> entry : variables.entrySet())
        {
            this.parseVariable(entry.getKey(), entry.getValue());
        }
    }

    public void parseVariable(String name, String expression)
    {
        try
        {
            this.variables.put(name, this.scheme.parser.parse(expression));
        }
        catch (Exception e)
        {}
    }

    public void replaceVariables()
    {
        if (this.variables == null)
        {
            return;
        }

        for (Map.Entry<String, IValue> entry : this.variables.entrySet())
        {
            Variable var = this.scheme.parser.variables.get(entry.getKey());

            if (var != null)
            {
                var.set(entry.getValue().get().doubleValue());
            }
        }
    }

    public void start()
    {
        if (this.playing)
        {
            return;
        }

        this.age = 0;
        this.spawnedParticles = 0;
        this.playing = true;

        for (IComponentEmitterInitialize component : this.scheme.emitterInitializes)
        {
            component.apply(this);
        }
    }

    public void stop()
    {
        if (!this.playing)
        {
            return;
        }

        this.spawnedParticles = 0;
        this.playing = false;

        this.random1 = (float) RandomProvider.nextDouble();
        this.random2 = (float) RandomProvider.nextDouble();
        this.random3 = (float) RandomProvider.nextDouble();
        this.random4 = (float) RandomProvider.nextDouble();
    }

    /**
     * Update this current emitter
     */
    public void update()
    {
        if (this.scheme == null)
        {
            return;
        }

        this.setEmitterVariables(0);

        for (IComponentEmitterUpdate component : this.scheme.emitterUpdates)
        {
            component.update(this);
        }

        this.setEmitterVariables(0);
        this.updateParticles();

        this.age += 1;
        this.sanityTicks += 1;
    }

    /**
     * Update all particles
     */
    private void updateParticles()
    {
        Iterator<BedrockParticle> it = this.particles.iterator();

        while (it.hasNext())
        {
            BedrockParticle particle = it.next();

            this.updateParticle(particle);

            if (particle.dead)
            {
                it.remove();
            }
        }

        if (!this.splitParticles.isEmpty())
        {
            this.particles.addAll(this.splitParticles);
            this.splitParticles.clear();
        }
    }

    /**
     * Update a single particle
     */
    private void updateParticle(BedrockParticle particle)
    {
        particle.update(this);

        this.setParticleVariables(particle, 0);

        for (IComponentParticleUpdate component : this.scheme.particleUpdates)
        {
            component.update(this, particle);
        }
    }

    /**
     * Spawn a particle
     */
    public void spawnParticle()
    {
        if (!this.running)
        {
            return;
        }

        this.particles.add(this.createParticle(false));
    }

    /**
     * Create a new particle
     *
     * <p>The {@code forceRelative} parameter is accepted but ignored (the legacy
     * GUI preview passes {@code true} with no effect) — kept as a dead parameter
     * for diff-ability / call-site parity.</p>
     */
    public BedrockParticle createParticle(boolean forceRelative)
    {
        BedrockParticle particle = new BedrockParticle();

        this.setParticleVariables(particle, 0);
        particle.setupMatrix(this);

        for (IComponentParticleInitialize component : this.scheme.particleInitializes)
        {
            component.apply(this, particle);
        }

        if (particle.relativePosition && !particle.relativeRotation)
        {
            Vector3f vec = new Vector3f(particle.position);

            particle.matrix.transform(vec);

            particle.position.x = vec.x;
            particle.position.y = vec.y;
            particle.position.z = vec.z;
        }

        if (!(particle.relativePosition && particle.relativeRotation))
        {
            particle.position.add(this.lastGlobal);
            particle.initialPosition.add(this.lastGlobal);
        }

        particle.prevPosition.set(particle.position);
        particle.rotation = particle.initialRotation;
        particle.prevRotation = particle.rotation;

        return particle;
    }

    /**
     * The camera-facing reference point every {@code lookat_*} billboard aims
     * at. Legacy: {@code BedrockEmitter.setupCameraProperties} (2.7.2 line 733),
     * whose {@code cY} term is {@code camera.getEyeHeight()} — 1.12.2's
     * <b>pose-aware</b> accessor ({@code EntityPlayer.getEyeHeight} returns
     * 1.62 standing, 1.54 sneaking, 0.2 sleeping, 0.4 elytra-flying).
     *
     * <p>P252 spells that {@code camera.getEyeHeight(camera.getPose())}, the
     * yarn 1.20.4 counterpart (verified with {@code javap} against the
     * loom-cache named jar: {@code public float getEyeHeight(EntityPose)}
     * &rarr; {@code getEyeHeight(pose, getDimensions(pose))}).</p>
     *
     * <p><b>Honest note:</b> this is a legibility/robustness change, <i>not</i> a
     * visible bug fix, and the "sneaking billboards aim ~0.3 blocks too high"
     * reading of the old line is wrong. Despite its name,
     * {@code getStandingEyeHeight()} is not the standing height — it returns the
     * {@code standingEyeHeight} field, which {@code Entity.calculateDimensions()}
     * recomputes for the <i>current</i> pose, and {@code onTrackedDataSet} calls
     * that whenever the {@code POSE} tracked value changes. The two therefore
     * agree for any entity whose pose has been applied. What the explicit
     * spelling buys is independence from that invariant — the call now says what
     * 1.12.2 meant instead of relying on a field's refresh contract.</p>
     */
    public void setupCameraProperties(float partialTicks)
    {
        if (this.world != null)
        {
            Entity camera = MinecraftClient.getInstance().getCameraEntity();

            this.perspective = MinecraftClient.getInstance().options.getPerspective().ordinal();
            this.cYaw = 180 - Interpolations.lerp(camera.prevYaw, camera.getYaw(), partialTicks);
            this.cPitch = 180 - Interpolations.lerp(camera.prevPitch, camera.getPitch(), partialTicks);
            this.cX = Interpolations.lerp(camera.prevX, camera.getX(), partialTicks);
            this.cY = Interpolations.lerp(camera.prevY, camera.getY(), partialTicks) + camera.getEyeHeight(camera.getPose());
            this.cZ = Interpolations.lerp(camera.prevZ, camera.getZ(), partialTicks);
        }
    }

    /* ==================================================================== *
     *  Render half — roadmap P153.                                          *
     *                                                                       *
     *  Faithful port of the 1.12.2 render surface. Per-vertex quad emission *
     *  lives in the appearance components (P152); this class only drives    *
     *  them: material GL bracketing, camera-relative modelview translation, *
     *  the two-pass collision-texture split, farthest-first depth sorting   *
     *  behind {@link Blockbuster#snowstormDepthSorting}, and the GUI preview *
     *  path. Everything routes through the component render interface so     *
     *  that when P152 fills the components in, world rendering just works.   *
     * ==================================================================== */

    /**
     * Whether this emitter renders its particles as morphs rather than billboard
     * quads (legacy {@code BedrockComponentParticleMorph.enabled}).
     *
     * <p>P230: this used to be hard-wired to {@code false} while
     * {@code BedrockComponentParticleMorph} was a stub, which made every emitter
     * take the billboard-quad path and left the morph path unreachable. The
     * component carries its {@code enabled} flag now, so the real answer is
     * returned. The {@code getOrCreate} call is still what creates the component —
     * mirrors {@code BedrockParticle.isCollisionTexture}.</p>
     */
    public boolean isMorphParticle()
    {
        return this.scheme.getOrCreate(BedrockComponentParticleMorph.class).enabled;
    }

    /**
     * Render all particles in this emitter (world-space). Legacy
     * {@code BedrockEmitter.render(float)}.
     */
    public void render(float partialTicks)
    {
        if (this.scheme == null)
        {
            return;
        }

        this.setupCameraProperties(partialTicks);

        BedrockComponentParticleMorph particleMorphComponent = this.scheme.getOrCreate(BedrockComponentParticleMorph.class);
        List<IComponentParticleRender> renders = this.scheme.particleRender;
        List<IComponentParticleMorphRender> morphRenders = this.scheme.particleMorphRender;

        boolean morphRendering = this.isMorphParticle();
        /* P230: legacy verbatim. A morph-particle emitter draws its billboards too
         * only when the component's {@code render_texture} flag is on. */
        boolean particleRendering = !morphRendering || particleMorphComponent.renderTexture;

        /* particle rendering */
        if (particleRendering)
        {
            this.setupOpenGL(partialTicks);

            for (IComponentParticleRender component : renders)
            {
                component.preRender(this, partialTicks);
            }

            if (!this.particles.isEmpty())
            {
                this.depthSorting();

                this.renderParticles(this.scheme.texture, renders, false, partialTicks);

                BedrockComponentCollisionAppearance collisionAppearance = this.scheme.getOrCreate(BedrockComponentCollisionAppearance.class);

                /* P230: rendering the collided particles with an extra component.
                 * The component carries its {@code texture} now, so the second pass
                 * is live again (legacy verbatim). */
                if (collisionAppearance != null && collisionAppearance.texture != null)
                {
                    this.renderParticles(collisionAppearance.texture, renders, true, partialTicks);
                }
            }

            for (IComponentParticleRender component : renders)
            {
                component.postRender(this, partialTicks);
            }

            this.endOpenGL();
        }

        /* Morph rendering */
        if (morphRendering)
        {
            for (IComponentParticleMorphRender component : morphRenders)
            {
                component.preRender(this, partialTicks);
            }

            if (!this.particles.isEmpty())
            {
                /* only depth sort either in particle rendering or morph rendering */
                if (!particleRendering)
                {
                    this.depthSorting();
                }

                this.renderParticles(morphRenders, false, partialTicks);
            }

            for (IComponentParticleMorphRender component : morphRenders)
            {
                if (component.getClass() == BedrockComponentRateSteady.class)
                {
                    if (!particleRendering)
                    {
                        /* only spawn particles either in particles or in morph rendering */
                        component.postRender(this, partialTicks);
                    }
                }
                else
                {
                    component.postRender(this, partialTicks);
                }
            }
        }
    }

    /**
     * Render the particles as morphs (morph components render themselves; no
     * shared buffer begin/draw here). Legacy morph-list {@code renderParticles}
     * overload. {@code collided} is unused (kept for call-site parity).
     */
    private void renderParticles(List<? extends IComponentParticleMorphRender> renderComponents, boolean collided, float partialTicks)
    {
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        for (BedrockParticle particle : this.particles)
        {
            this.setEmitterVariables(partialTicks);
            this.setParticleVariables(particle, partialTicks);

            for (IComponentRenderBase component : renderComponents)
            {
                component.render(this, particle, builder, partialTicks);
            }
        }
    }

    /**
     * Render the particles as the default Bedrock billboards through one buffer.
     * Legacy texture {@code renderParticles} overload.
     *
     * <p>1.20.4 idiom (BBS-validated): the particle shader
     * ({@code GameRenderer::getParticleProgram}) plus the vanilla {@code PARTICLE}
     * layout {@link VertexFormats#POSITION_TEXTURE_COLOR_LIGHT} replaces legacy
     * {@code DefaultVertexFormats.POSITION_TEX_LMAP_COLOR}; {@code DrawMode.QUADS}
     * preserves the legacy {@code GL_QUADS} primitive. GIF-frame texture selection
     * is keyed by emitter {@code age} (animated particle textures), matching
     * legacy {@code GifTexture.bindTexture(texture, this.age, partialTicks)}.
     * P275 moved the shader + sampler-0 pair into {@link SnowstormRenderSetup} so
     * this path and {@link #renderOnScreen(int, int, float)} cannot drift.</p>
     *
     * <p>Two-pass split: a particle whose collision texture/tinting is active is
     * skipped in the base pass ({@code collided == false}) and drawn in the
     * collided pass; there {@link BedrockComponentAppearanceBillboard} is
     * suppressed for it (the collision-appearance component draws instead).</p>
     */
    private void renderParticles(ResourceLocation texture, List<? extends IComponentParticleRender> renderComponents, boolean collided, float partialTicks)
    {
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        SnowstormRenderSetup.beginQuadPass(texture, this.age, partialTicks);

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);

        for (BedrockParticle particle : this.particles)
        {
            boolean collisionStuff = particle.isCollisionTexture(this) || particle.isCollisionTinting(this);

            if (collisionStuff != collided)
            {
                continue;
            }

            this.setEmitterVariables(partialTicks);
            this.setParticleVariables(particle, partialTicks);

            for (IComponentRenderBase component : renderComponents)
            {
                /* if collisionTexture or collisionTinting is enabled the old
                 * billboard appearance must not draw — the collision appearance
                 * component draws instead in this collided pass. */
                if (!(collisionStuff && component.getClass() == BedrockComponentAppearanceBillboard.class))
                {
                    component.render(this, particle, builder, partialTicks);
                }
            }
        }

        Tessellator.getInstance().draw();
    }

    /**
     * Enter the material's GL state and set the render origin so world-space
     * particle vertices are written into the buffer camera-relative. Legacy
     * {@code setupOpenGL} used {@code BufferBuilder.setTranslation(-camX,...)},
     * removed in 1.20.4; {@link #renderOriginX} and the appearance components'
     * {@code translateQuad} are its replacement. Skipped inside the McLib GUI
     * model renderer, which supplies its own transform.
     *
     * <p><b>P275 — why the offset must be in the vertices, not the model-view.</b>
     * From U-T until P275 this method pushed {@code translate(-cameraPos)} onto
     * {@link RenderSystem#getModelViewStack()} instead, leaving the vertices in
     * absolute world coordinates. For {@code gl_Position} the two are identical
     * — {@code Proj * MV * p} either way. For <b>fog</b> they are not. Vanilla's
     * {@code particle.vsh} computes
     * {@code vertexDistance = fog_distance(ModelViewMat, Position, FogShape)},
     * and for the {@code FogShape.CYLINDER} the world pass always uses
     * ({@code BackgroundRenderer.applyFog}, {@code FOG_TERRAIN}) that helper
     * <i>decomposes the raw attribute first</i>:</p>
     *
     * <pre>
     * distXZ = length((modelViewMat * vec4(pos.x, 0.0, pos.z, 1.0)).xyz);
     * distY  = length((modelViewMat * vec4(0.0, pos.y, 0.0, 1.0)).xyz);
     * return max(distXZ, distY);
     * </pre>
     *
     * <p>Zeroing two components of a position that is only camera-relative
     * <i>after</i> the matrix is applied leaks the camera translation into both
     * terms: {@code distY} becomes roughly the camera's horizontal distance from
     * the world origin. Past {@code FogEnd} (the render distance) the fragment
     * shader returns {@code mix(color.rgb, FogColor.rgb, 1.0)} — so every quad
     * drew as a flat sheet of <b>fog colour, i.e. the sky colour</b>, with
     * correct shape, motion and alpha and no texture visible at all. That is the
     * live report "storm particle works in the particle editor but not in game,
     * where it takes the color of the skybox". The editor was immune twice over:
     * {@code GuiModelRenderer.isRendering()} skips this branch, and
     * {@code BackgroundRenderer.clearFog()} has pushed {@code FogStart} to
     * {@code Float.MAX_VALUE} by the time a screen draws.</p>
     *
     * <p>With the offset in the vertices the pass is byte-for-byte what vanilla
     * particles do: camera-relative positions under a model-view that is a pure
     * camera rotation ({@code WorldRendererParticlesMixin} rebuilds it). Fog
     * distance, and therefore fog, now matches vanilla smoke standing next to
     * it. The float cast in {@code translateQuad} also happens after the
     * subtraction now, which is what keeps far-from-origin worlds free of
     * quad-sized jitter.</p>
     *
     * <p><b>U-S — the lightmap bind.</b> 1.12.2 drew these quads with the
     * fixed-function lightmap texture unit, which vanilla left enabled for the
     * whole world pass, so legacy {@code setupOpenGL} had nothing to do about it.
     * On 1.20.4 the lightmap is a shader sampler, and the vanilla particle shader
     * computes {@code vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0)} —
     * so every {@code .light(...)} we emit is multiplied by whatever texture is
     * bound to sampler 2. {@code ParticleManager.renderParticles} brackets itself
     * with {@code LightmapTextureManager.enable()} / {@code disable()}, and
     * {@code disable()} is {@code RenderSystem.setShaderTexture(2, 0)} — it
     * <i>unbinds</i> the lightmap. {@link mchorse.blockbuster.mixin.client.WorldRendererParticlesMixin}
     * injects <b>after</b> that call, so the emitters used to draw with sampler 2
     * unbound: an incomplete texture samples {@code (0,0,0,1)}, the vertex colour
     * collapses to black, and the particles rendered pitch black with perfectly
     * correct shape, motion and alpha (the exact live report). Re-enabling here
     * covers every caller of {@link #render(float)} — the world pass and the
     * Snowstorm editor viewport ({@code GuiSnowstormRenderer}) alike — and
     * {@link #endOpenGL()} restores the disabled postcondition vanilla left.
     * The morph path is unaffected: its {@code RenderLayer}s carry
     * {@code RenderPhase.ENABLE_LIGHTMAP} and bind sampler 2 themselves.</p>
     *
     * <p><b>U-T — which origin is subtracted.</b> Legacy subtracted the lerped
     * <i>feet</i> position of the render-view entity, because that is where
     * 1.12.2 put the model-view origin: {@code EntityRenderer.orientCamera}
     * ends with {@code GlStateManager.translate(0, -eyeHeight, 0)}, so after
     * {@code setupCameraTransform} the origin sat at the entity's feet, not at
     * the eye. On 1.20.4 the world pass's origin is {@code Camera.getPos()} —
     * the eye position, plus the third-person boom, view bob and camera shake —
     * and every vanilla world renderer subtracts exactly that. Subtracting the
     * entity position instead lifts the whole emitter by the eye height (and,
     * in third person, by the boom, and it ignores view bobbing). The
     * <i>visible</i> intent of legacy's line was "particles sit at their world
     * position", and on 1.20.4 that is {@code -camera.getPos()}. Do not
     * "restore" the entity lerp; {@code Camera.getPos()} is already interpolated
     * for this frame, which is why {@code partialTicks} is no longer read here.
     * The camera <i>rotation</i> half of the same transform is not ours to
     * apply — {@link mchorse.blockbuster.mixin.client.WorldRendererParticlesMixin}
     * re-establishes it around the whole pass (see there). P275 kept the origin
     * and moved only <i>where</i> it is subtracted.</p>
     */
    private void setupOpenGL(float partialTicks)
    {
        this.scheme.material.beginGL();

        MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().enable();

        if (!GuiModelRenderer.isRendering())
        {
            Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

            this.setRenderOrigin(camera.x, camera.y, camera.z);

            RenderSystem.disableCull();
        }
    }

    /**
     * Restore the render origin and the material's GL state (the "leave GL as
     * vanilla particles expect" postcondition). Legacy {@code endOpenGL}, whose
     * body was {@code Tessellator.getInstance().getBuffer().setTranslation(0, 0, 0)}
     * — {@link #clearRenderOrigin()} is the same statement in 1.20.4 terms.
     *
     * <p>Also unbinds the lightmap {@link #setupOpenGL(float)} bound, so the pass
     * hands GL back exactly as vanilla {@code ParticleManager.renderParticles}
     * left it (sampler 2 cleared).</p>
     *
     * <p><b>P275 — the cull leak.</b> Legacy {@code setupOpenGL} called
     * {@code GlStateManager.disableCull()} and never re-enabled it, and the port
     * copied that verbatim. On 1.12.2 it was survivable: the fixed-function
     * renderers downstream set their own cull state every pass. On 1.20.4
     * {@link RenderSystem} caches the flag, and the immediate-mode draws that
     * follow the particle pass in {@code WorldRenderer.render} (clouds, weather,
     * the block outline) do not all set it — so a Snowstorm emitter anywhere on
     * screen silently disabled back-face culling for the rest of the frame. This
     * is a deliberate deviation from legacy: {@code enableCull()} restores
     * 1.20.4's default, and it is the mirror image of the bug this phase fixes,
     * so it is fixed with it. The GUI thumbnail path already balanced its own
     * {@code disableCull}/{@code enableCull} pair.</p>
     */
    private void endOpenGL()
    {
        if (!GuiModelRenderer.isRendering())
        {
            this.clearRenderOrigin();

            RenderSystem.enableCull();
        }

        MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().disable();

        this.scheme.material.endGL();
    }

    /**
     * Emit this emitter's billboard vertices relative to {@code (x, y, z)} —
     * 1.20.4's replacement for legacy {@code BufferBuilder.setTranslation}. See
     * {@link #renderOriginX}.
     */
    public void setRenderOrigin(double x, double y, double z)
    {
        this.renderOriginX = x;
        this.renderOriginY = y;
        this.renderOriginZ = z;
    }

    /** Back to world-absolute vertices — legacy {@code setTranslation(0, 0, 0)}. */
    public void clearRenderOrigin()
    {
        this.setRenderOrigin(0, 0, 0);
    }

    /**
     * Optional farthest-first depth sort of this emitter's particles behind
     * {@link Blockbuster#snowstormDepthSorting} (squared distance to camera).
     * Legacy {@code depthSorting} (private there; package-visible here so the
     * headless depth-sort test in the same package can drive it without GL).
     */
    void depthSorting()
    {
        if (Blockbuster.snowstormDepthSorting.get())
        {
            this.particles.sort((a, b) ->
            {
                double ad = a.getDistanceSq(this);
                double bd = b.getDistanceSq(this);

                if (ad < bd)
                {
                    return 1;
                }
                else if (ad > bd)
                {
                    return -1;
                }

                return 0;
            });
        }
    }

    /**
     * GUI preview render path (editor preview P155, morph GUIs S14). Runs inside
     * the McLib GUI model renderer with an identity rotation swapped in and
     * restored. Legacy {@code renderOnScreen(int, int, float)}.
     */
    public void renderOnScreen(int x, int y, float scale)
    {
        if (this.scheme == null)
        {
            return;
        }

        BedrockComponentParticleMorph particleMorphComponent = this.scheme.getOrCreate(BedrockComponentParticleMorph.class);
        float partialTicks = MinecraftClient.getInstance().getTickDelta();

        List<IComponentParticleRender> listParticle = this.scheme.getComponents(IComponentParticleRender.class);
        List<IComponentParticleMorphRender> listMorph = this.scheme.getComponents(IComponentParticleMorphRender.class);

        Matrix3f rotation = this.rotation;

        this.rotation = new Matrix3f(1, 0, 0, 0, 1, 0, 0, 0, 1);

        /* SEAM(P152): the {@code particleMorphComponent.renderTexture} term of the
         * guard is owned by P152; isMorphParticle() is dormant (false) so the
         * particle branch always runs. Restore:
         *   (!this.isMorphParticle() || particleMorphComponent.renderTexture) */
        if (!listParticle.isEmpty() && !this.isMorphParticle())
        {
            /* P275: sampler 0 through the shared setup, so the thumbnail
             * resolves GIF frames and legacy 1.12.2 texture paths exactly as the
             * world pass does. The shader is *not* set here: the appearance
             * component's renderOnScreen owns the POSITION_TEXTURE_COLOR format
             * it begins and selects the matching program itself (this used to
             * set the particle program, which the component then immediately
             * overrode — a dead call that made the two look like they disagreed). */
            SnowstormRenderSetup.bindTexture(this.scheme.texture, this.age, partialTicks);

            this.scheme.material.beginGL();
            RenderSystem.disableCull();

            if (this.guiParticle == null || this.guiParticle.dead)
            {
                this.guiParticle = this.createParticle(true);
            }

            this.rotation.setIdentity();
            this.guiParticle.update(this);
            this.setEmitterVariables(partialTicks);
            this.setParticleVariables(this.guiParticle, partialTicks);

            for (IComponentParticleRender render : listParticle)
            {
                render.renderOnScreen(this.guiParticle, x, y, scale, partialTicks);
            }

            this.scheme.material.endGL();
            RenderSystem.enableCull();
        }

        if (!listMorph.isEmpty() && this.isMorphParticle())
        {
            if (this.guiParticle == null || this.guiParticle.dead)
            {
                this.guiParticle = this.createParticle(true);
            }

            this.rotation.setIdentity();
            this.guiParticle.update(this);
            this.setEmitterVariables(partialTicks);
            this.setParticleVariables(this.guiParticle, partialTicks);

            for (IComponentParticleMorphRender render : listMorph)
            {
                render.renderOnScreen(this.guiParticle, x, y, scale, partialTicks);
            }
        }

        this.rotation = rotation;
    }

    /**
     * Get brightness for the block
     */
    public int getBrightnessForRender(float partialTicks, double x, double y, double z)
    {
        if (this.lit || this.world == null)
        {
            return 15728880;
        }

        return this.getWorldBrightness(x, y, z);
    }

    /**
     * The world lightmap at a position, <b>without</b> the material's
     * {@code lit} short-circuit.
     *
     * <p>This is the particle-morph path's light source (P230). Legacy read
     * {@code dummy.getBrightnessForRender()} — an <i>entity</i> brightness
     * lookup, which never consulted the emitter's {@code lit} flag. Routing
     * the morph through {@link #getBrightnessForRender} instead would make a
     * {@code lit} scheme render its particle morphs fullbright where 1.12.2
     * lit them from the world, so the two paths are deliberately kept
     * separate. Do not "unify" them.</p>
     */
    public int getWorldBrightness(double x, double y, double z)
    {
        if (this.world == null)
        {
            return 15728880;
        }

        this.blockPos.set(x, y, z);

        return this.world.isChunkLoaded(this.blockPos) ? WorldRenderer.getLightmapCoordinates(this.world, this.blockPos) : 0;
    }
}
