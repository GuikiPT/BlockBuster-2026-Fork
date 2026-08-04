package mchorse.blockbuster_pack.morphs;

import mchorse.blockbuster.legacy.LegacyParticleTypes;
import mchorse.mclib.utils.Interpolations;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.MathHelper;

import javax.vecmath.Matrix3f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Vanilla / morph particle emitter morph (roadmap P163).
 *
 * <p>Two modes: {@link ParticleMode#VANILLA} spawns vanilla particles
 * (type/frequency/duration/delay/cap, gaussian spread + speed, int args,
 * local-rotation transform by the captured parent basis) and
 * {@link ParticleMode#MORPH} maintains up to {@link #maximum}
 * {@link MorphParticle}s rendering a nested morph with out/in/drop movement and
 * a lifespan/fade envelope.</p>
 *
 * <p>NBT is a disk/wire contract — keys and default-elision match 1.12.2
 * exactly ({@code Type} holds the 1.12.2 particle name string, resolved to a
 * modern effect through {@link LegacyParticleTypes}). {@code vanillaType} is
 * held as the legacy name string so any saved name round-trips byte-identically
 * even when it has no modern mapping (total reader: unknown/removed names skip
 * emission, never crash).</p>
 *
 * <p>Port notes (source-set split): the emission gate, cap logic, movement math
 * and NBT parity live here and are headless-testable; the two client-only halves
 * — the in-world matrix capture that fills {@link #lastGlobal}/{@link
 * #lastRotation} and the {@code MorphParticle} draw — are
 * {@code ParticleMorphRenderer} (P54). A morph that is never drawn keeps
 * emitting at its last captured anchor (identity / entity position), the same
 * "unrendered morphs emit at stale positions" behaviour as 1.12.2.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/.../blockbuster_pack/morphs/ParticleMorph.java}.</p>
 */
public class ParticleMorph extends AbstractMorph
{
    private static final int[] EMPTY_ARGS = {};

    /** 1.12.2 default {@code EnumParticleTypes.EXPLOSION_NORMAL} name. */
    public static final String DEFAULT_TYPE = "explode";

    /**
     * The {@code alwaysSpawn} flag every vanilla-mode emission passes (P276).
     *
     * <p><b>This boolean is the entire reason the Particle morph is not subject
     * to caps 1.12.2 never had.</b> Legacy called
     * {@code world.spawnParticle(type, ignoreRange = true, …)}, and
     * {@code RenderGlobal.spawnParticle0} branches on that flag <i>before</i> it
     * consults anything else — so on 1.12.2 the Particle morph was immune to
     * both the 32-block ({@code 1024.0} squared) range cull and the
     * {@code gameSettings.particleSetting} decimation. Emission was governed
     * only by Blockbuster's own {@link #cap} / {@link #maximum}.</p>
     *
     * <p>1.20.4 kept that shape: {@code WorldRenderer.spawnParticle} returns
     * {@code client.particleManager.addParticle(…)} immediately when
     * {@code alwaysSpawn}, skipping the identical {@code 1024.0} distance test
     * and the {@link net.minecraft.client.option.ParticlesMode#MINIMAL} test
     * below it — and {@code ClientWorld.addParticle} ORs this flag with the
     * type's own {@code shouldAlwaysSpawn()}, so passing {@code true} always
     * wins. Flip it to {@code false} and the morph silently inherits two
     * modern-only limits: nothing emits at all on Particles = Minimal, and
     * nothing emits past 32 blocks. Pinned by {@code ParticleLimitParityTest};
     * do not "clean up" the literal.</p>
     */
    public static final boolean ALWAYS_SPAWN = true;

    /**
     * Where one vanilla-mode particle emission goes. Exists so the emission
     * loop — the place a stray count clamp would be introduced — can be driven
     * headlessly without a {@code ClientWorld}; in game the only implementation
     * forwards to {@code World.addParticle}.
     */
    public interface Emission
    {
        void emit(double x, double y, double z, double sx, double sy, double sz);
    }

    /* Common arguments */
    public ParticleMode mode = ParticleMode.VANILLA;
    public int frequency = 2;
    public int duration = -1;
    public int delay = 0;
    public int cap = 2500;

    /* Vanilla parameters */
    public String vanillaType = DEFAULT_TYPE;
    public double vanillaX;
    public double vanillaY;
    public double vanillaZ;
    public double vanillaDX = 0.1;
    public double vanillaDY = 0.1;
    public double vanillaDZ = 0.1;
    public double speed = 0.1;
    public int count = 10;
    public boolean localRotation = true;
    public int[] arguments = EMPTY_ARGS;

    /* Morph parameters */
    public AbstractMorph morph;
    public MorphParticle.MovementType movementType = MorphParticle.MovementType.OUT;
    public boolean yaw = true;
    public boolean pitch = true;
    public boolean sequencer;
    public boolean random;
    public int fade = 10;
    public int lifeSpan = 50;
    public int maximum = 25;

    /* Runtime fields (public so the client render seam can drive them) */
    public Vector3d lastGlobal = new Vector3d();
    public Matrix3f lastRotation = new Matrix3f();
    int tick;
    List<MorphParticle> morphParticles = new ArrayList<MorphParticle>();
    int morphIndex;
    public Random rand = new Random();

    public ParticleMorph()
    {
        super();

        this.name = "particle";
        this.lastRotation.setIdentity();
    }

    /**
     * Pull the morph to spawn for a new {@link MorphParticle}. With
     * {@link #sequencer} enabled and a sequencer nested morph, entries are
     * pulled randomly or in round-robin order.
     */
    public AbstractMorph getMorph()
    {
        AbstractMorph morph = this.morph;

        /* Legacy pulled entries straight out of a nested sequencer, either at
         * random or round-robin. An empty sequencer divides by zero here, as it
         * did on 1.12.2 — the editor cannot produce one (an entry list is added
         * before the sequencer is nested) and swallowing it would instead spawn
         * particles of the sequencer itself. */
        if (this.sequencer && morph instanceof SequencerMorph)
        {
            SequencerMorph seq = (SequencerMorph) morph;

            morph = this.random ? seq.getRandom() : seq.get(this.morphIndex++ % seq.morphs.size());
        }

        return MorphUtils.copy(morph.copy());
    }

    /* Emission gate helpers (exact 1.12.2 expressions, exposed for tests) */

    /** Legacy {@code alive = duration < 0 || tick < duration}. */
    public boolean isAlive()
    {
        return this.duration < 0 || this.tick < this.duration;
    }

    /**
     * Legacy fire gate:
     * {@code frequency != 0 && tick >= delay && tick % frequency == 0 && alive}.
     */
    public boolean shouldFire()
    {
        return this.frequency != 0 && this.tick >= this.delay && this.tick % this.frequency == 0 && this.isAlive();
    }

    /** Legacy {@code particlesPerSecond = (int) (20 / (float) frequency * count)}. */
    public int particlesPerSecond()
    {
        return (int) (20 / (float) this.frequency * this.count);
    }

    /**
     * Legacy cap gate: vanilla mode compares particles-per-second against
     * {@link #cap}, morph mode compares {@link #maximum}. Over-cap silently
     * emits nothing.
     */
    public boolean withinCap()
    {
        if (this.mode == ParticleMode.VANILLA)
        {
            return this.particlesPerSecond() <= this.cap;
        }

        return this.maximum <= this.cap;
    }

    public int getTick()
    {
        return this.tick;
    }

    public void setTick(int tick)
    {
        this.tick = tick;
    }

    public List<MorphParticle> getMorphParticles()
    {
        return this.morphParticles;
    }

    @Override
    public void update(LivingEntity target)
    {
        super.update(target);

        boolean client = target.getWorld().isClient;

        if (this.shouldFire())
        {
            if (this.withinCap() && client)
            {
                if (this.mode == ParticleMode.VANILLA && this.vanillaType != null)
                {
                    this.emitVanilla(target);
                }
                else if (this.mode == ParticleMode.MORPH && this.morph != null && this.morphParticles.size() < this.maximum)
                {
                    for (int i = 0; i < this.count && this.morphParticles.size() < this.maximum; i++)
                    {
                        this.morphParticles.add(new MorphParticle(this));
                    }
                }
            }
        }

        /* Update morph based particles */
        if (client)
        {
            Iterator<MorphParticle> it = this.morphParticles.iterator();

            while (it.hasNext())
            {
                MorphParticle particle = it.next();

                particle.update(target);

                if (particle.isDead())
                {
                    it.remove();
                }
            }
        }

        this.tick++;
    }

    private void emitVanilla(LivingEntity target)
    {
        ParticleEffect effect = LegacyParticleTypes.create(this.vanillaType, this.arguments);

        if (effect == null)
        {
            return;
        }

        this.emitVanilla((x, y, z, sx, sy, sz) ->
            target.getWorld().addParticle(effect, ALWAYS_SPAWN, x, y, z, sx, sy, sz));
    }

    /**
     * The vanilla-mode emission loop, minus the world.
     *
     * <p>Emits exactly {@link #count} particles per fire — legacy ran a bare
     * {@code for (i < count)} with no live-population check of any kind (unlike
     * morph mode, which tests {@code morphParticles.size() < maximum} on every
     * iteration). The only thing standing between a caller and this loop is
     * {@link #withinCap()}, which is a gate, not a clamp. Returns the number
     * emitted so a test can assert nothing truncated it.</p>
     */
    int emitVanilla(Emission sink)
    {
        double x = this.lastGlobal.x + this.vanillaX;
        double y = this.lastGlobal.y + this.vanillaY;
        double z = this.lastGlobal.z + this.vanillaZ;

        Vector3f vector = new Vector3f(0, 0, 0);

        for (int i = 0; i < this.count; i++)
        {
            double dx = this.rand.nextGaussian() * this.vanillaDX;
            double dy = this.rand.nextGaussian() * this.vanillaDY;
            double dz = this.rand.nextGaussian() * this.vanillaDZ;
            double sx = this.rand.nextGaussian() * this.speed;
            double sy = this.rand.nextGaussian() * this.speed;
            double sz = this.rand.nextGaussian() * this.speed;

            if (this.localRotation)
            {
                vector.set((float) dx, (float) dy, (float) dz);
                this.lastRotation.transform(vector);

                dx = vector.x;
                dy = vector.y;
                dz = vector.z;
            }

            try
            {
                sink.emit(x + dx, y + dy, z + dz, sx, sy, sz);
            }
            catch (Throwable e)
            {}
        }

        return this.count;
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof ParticleMorph)
        {
            ParticleMorph particle = (ParticleMorph) obj;

            /* Common properties */
            result = result && this.mode == particle.mode;
            result = result && this.frequency == particle.frequency;
            result = result && this.duration == particle.duration;
            result = result && this.delay == particle.delay;
            result = result && this.cap == particle.cap;

            /* Vanilla properties */
            result = result && Objects.equals(this.vanillaType, particle.vanillaType);
            result = result && this.vanillaX == particle.vanillaX;
            result = result && this.vanillaY == particle.vanillaY;
            result = result && this.vanillaZ == particle.vanillaZ;
            result = result && this.vanillaDX == particle.vanillaDX;
            result = result && this.vanillaDY == particle.vanillaDY;
            result = result && this.vanillaDZ == particle.vanillaDZ;
            result = result && this.speed == particle.speed;
            result = result && this.count == particle.count;
            result = result && this.localRotation == particle.localRotation;

            boolean sameArgs = false;

            if (this.arguments.length == particle.arguments.length)
            {
                int same = 0;

                for (int i = 0; i < this.arguments.length; i++)
                {
                    if (this.arguments[i] == particle.arguments[i])
                    {
                        same++;
                    }
                }

                sameArgs = same == this.arguments.length;
            }

            result = result && sameArgs;

            result = result && Objects.equals(this.morph, particle.morph);
            result = result && this.movementType == particle.movementType;
            result = result && this.yaw == particle.yaw;
            result = result && this.pitch == particle.pitch;
            result = result && this.sequencer == particle.sequencer;
            result = result && this.random == particle.random;
            result = result && this.fade == particle.fade;
            result = result && this.lifeSpan == particle.lifeSpan;
            result = result && this.maximum == particle.maximum;
        }

        return result;
    }

    @Override
    public boolean canMerge(AbstractMorph morph)
    {
        if (morph instanceof ParticleMorph)
        {
            this.copy(morph);
            this.tick = this.morphIndex = 0;

            return true;
        }

        return super.canMerge(morph);
    }

    @Override
    public boolean useTargetDefault()
    {
        return true;
    }

    @Override
    public AbstractMorph create()
    {
        return new ParticleMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof ParticleMorph)
        {
            ParticleMorph morph = (ParticleMorph) from;

            this.mode = morph.mode;
            this.frequency = morph.frequency;
            this.duration = morph.duration;
            this.delay = morph.delay;
            this.cap = morph.cap;

            this.vanillaType = morph.vanillaType;
            this.vanillaX = morph.vanillaX;
            this.vanillaY = morph.vanillaY;
            this.vanillaZ = morph.vanillaZ;
            this.vanillaDX = morph.vanillaDX;
            this.vanillaDY = morph.vanillaDY;
            this.vanillaDZ = morph.vanillaDZ;
            this.speed = morph.speed;
            this.count = morph.count;
            this.localRotation = morph.localRotation;
            this.arguments = morph.arguments;

            this.morph = MorphUtils.copy(morph.morph);
            this.movementType = morph.movementType;
            this.yaw = morph.yaw;
            this.pitch = morph.pitch;
            this.sequencer = morph.sequencer;
            this.random = morph.random;
            this.fade = morph.fade;
            this.lifeSpan = morph.lifeSpan;
            this.maximum = morph.maximum;
        }
    }

    @Override
    public float getWidth(LivingEntity target)
    {
        return 0.6F;
    }

    @Override
    public float getHeight(LivingEntity target)
    {
        return 1.8F;
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Mode")) this.mode = tag.getString("Mode").equals(ParticleMode.MORPH.type) ? ParticleMode.MORPH : ParticleMode.VANILLA;
        if (tag.contains("Frequency")) this.frequency = tag.getInt("Frequency");
        if (tag.contains("Duration")) this.duration = tag.getInt("Duration");
        if (tag.contains("Delay")) this.delay = tag.getInt("Delay");
        if (tag.contains("Cap")) this.cap = tag.getInt("Cap");

        if (tag.contains("Type")) this.vanillaType = tag.getString("Type");
        if (tag.contains("X")) this.vanillaX = tag.getDouble("X");
        if (tag.contains("Y")) this.vanillaY = tag.getDouble("Y");
        if (tag.contains("Z")) this.vanillaZ = tag.getDouble("Z");
        if (tag.contains("DX")) this.vanillaDX = tag.getDouble("DX");
        if (tag.contains("DY")) this.vanillaDY = tag.getDouble("DY");
        if (tag.contains("DZ")) this.vanillaDZ = tag.getDouble("DZ");
        if (tag.contains("Speed")) this.speed = tag.getDouble("Speed");
        if (tag.contains("Count")) this.count = tag.getInt("Count");
        if (tag.contains("LocalRotation")) this.localRotation = tag.getBoolean("LocalRotation");
        if (tag.contains("Args")) this.arguments = tag.getIntArray("Args");

        if (tag.contains("Morph")) this.morph = MorphManager.INSTANCE.morphFromNBT(tag.getCompound("Morph"));
        if (tag.contains("Movement")) this.movementType = MorphParticle.MovementType.getType(tag.getString("Movement"));
        if (tag.contains("Yaw")) this.yaw = tag.getBoolean("Yaw");
        if (tag.contains("Pitch")) this.pitch = tag.getBoolean("Pitch");
        if (tag.contains("Sequencer")) this.sequencer = tag.getBoolean("Sequencer");
        if (tag.contains("Random")) this.random = tag.getBoolean("Random");
        if (tag.contains("Fade")) this.fade = tag.getInt("Fade");
        if (tag.contains("Life")) this.lifeSpan = tag.getInt("Life");
        if (tag.contains("Max")) this.maximum = tag.getInt("Max");
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (this.mode != ParticleMode.VANILLA) tag.putString("Mode", this.mode.type);
        if (this.frequency != 2) tag.putInt("Frequency", this.frequency);
        if (this.duration != -1) tag.putInt("Duration", this.duration);
        if (this.delay != 0) tag.putInt("Delay", this.delay);
        if (this.cap != 2500) tag.putInt("Cap", this.cap);

        if (!DEFAULT_TYPE.equals(this.vanillaType)) tag.putString("Type", this.vanillaType);
        if (this.vanillaX != 0) tag.putDouble("X", this.vanillaX);
        if (this.vanillaY != 0) tag.putDouble("Y", this.vanillaY);
        if (this.vanillaZ != 0) tag.putDouble("Z", this.vanillaZ);
        if (this.vanillaDX != 0.1) tag.putDouble("DX", this.vanillaDX);
        if (this.vanillaDY != 0.1) tag.putDouble("DY", this.vanillaDY);
        if (this.vanillaDZ != 0.1) tag.putDouble("DZ", this.vanillaDZ);
        if (this.speed != 0.1) tag.putDouble("Speed", this.speed);
        if (this.count != 10) tag.putInt("Count", this.count);
        if (!this.localRotation) tag.putBoolean("LocalRotation", this.localRotation);
        if (this.arguments.length != 0) tag.putIntArray("Args", this.arguments);

        if (this.morph != null)
        {
            NbtCompound morph = new NbtCompound();

            this.morph.toNBT(morph);
            tag.put("Morph", morph);
        }

        if (this.movementType != MorphParticle.MovementType.OUT) tag.putString("Movement", this.movementType.id);
        if (!this.yaw) tag.putBoolean("Yaw", this.yaw);
        if (!this.pitch) tag.putBoolean("Pitch", this.pitch);
        if (this.sequencer) tag.putBoolean("Sequencer", this.sequencer);
        if (this.random) tag.putBoolean("Random", this.random);
        if (this.fade != 10) tag.putInt("Fade", this.fade);
        if (this.lifeSpan != 50) tag.putInt("Life", this.lifeSpan);
        if (this.maximum != 25) tag.putInt("Max", this.maximum);
    }

    /**
     * A single morph-mode particle: a nested morph moved by a
     * {@link MovementType} over a {@link ParticleMorph#lifeSpan}-tick life with
     * velocity-aligned yaw/pitch. The motion/rotation math here is pure; the
     * draw is {@code ParticleMorphRenderer} (P54).
     */
    public static class MorphParticle
    {
        public ParticleMorph parent;
        public AbstractMorph morph;
        public MovementType movementType;

        public float targetX;
        public float targetY;
        public float targetZ;

        public float x;
        public float y;
        public float z;
        public float prevX;
        public float prevY;
        public float prevZ;

        public float yaw;
        public float pitch;
        public float prevYaw;
        public float prevPitch;

        public int timer;

        public MorphParticle(ParticleMorph morph)
        {
            this.parent = morph;
            this.morph = morph.getMorph();
            this.movementType = morph.movementType;
            this.movementType.calculateInitial(this);

            /* Stupid workaround to fix initial rotation */
            this.timer = 1;
            this.movementType.calculate(this);
            this.calculateRotation();
            this.movementType.calculateInitial(this);

            this.prevYaw = this.yaw;
            this.prevPitch = this.pitch;
        }

        public void update(LivingEntity entity)
        {
            this.timer++;
            this.prevX = this.x;
            this.prevY = this.y;
            this.prevZ = this.z;
            this.prevYaw = this.yaw;
            this.prevPitch = this.pitch;

            this.movementType.calculate(this);
            this.calculateRotation();

            this.morph.update(entity);
        }

        public void calculateRotation()
        {
            double dX = this.x - this.prevX;
            double dY = this.y - this.prevY;
            double dZ = this.z - this.prevZ;

            double horizontalDistance = (double) MathHelper.sqrt((float) (dX * dX + dZ * dZ));
            this.yaw = (float) (180 - MathHelper.atan2(dZ, dX) * 180 / Math.PI) + 90;
            this.pitch = (float) (MathHelper.atan2(dY, horizontalDistance) * 180 / Math.PI);
        }

        public boolean isDead()
        {
            return this.timer >= this.parent.lifeSpan;
        }

        public float getFactor()
        {
            return this.parent.lifeSpan == 0 ? 1 : this.timer / (float) this.parent.lifeSpan;
        }

        public static enum MovementType
        {
            OUT("out")
            {
                @Override
                public void calculateInitial(MorphParticle particle)
                {
                    particle.targetX = (particle.parent.rand.nextFloat() * 2 - 1) * (float) particle.parent.vanillaDX;
                    particle.targetY = (particle.parent.rand.nextFloat() * 2 - 1) * (float) particle.parent.vanillaDY;
                    particle.targetZ = (particle.parent.rand.nextFloat() * 2 - 1) * (float) particle.parent.vanillaDZ;

                    particle.x = particle.prevX = 0;
                    particle.y = particle.prevY = 0;
                    particle.z = particle.prevZ = 0;
                }

                @Override
                public void calculate(MorphParticle particle)
                {
                    float factor = particle.getFactor();

                    particle.x = Interpolations.lerp(0, particle.targetX, factor);
                    particle.y = Interpolations.lerp(0, particle.targetY, factor);
                    particle.z = Interpolations.lerp(0, particle.targetZ, factor);
                }
            },
            IN("in")
            {
                @Override
                public void calculateInitial(MorphParticle particle)
                {
                    particle.targetX = particle.x = particle.prevX = (particle.parent.rand.nextFloat() * 2 - 1) * (float) particle.parent.vanillaDX;
                    particle.targetY = particle.y = particle.prevY = (particle.parent.rand.nextFloat() * 2 - 1) * (float) particle.parent.vanillaDY;
                    particle.targetZ = particle.z = particle.prevZ = (particle.parent.rand.nextFloat() * 2 - 1) * (float) particle.parent.vanillaDZ;
                }

                @Override
                public void calculate(MorphParticle particle)
                {
                    float factor = particle.getFactor();

                    particle.x = Interpolations.lerp(particle.targetX, 0, factor);
                    particle.y = Interpolations.lerp(particle.targetY, 0, factor);
                    particle.z = Interpolations.lerp(particle.targetZ, 0, factor);
                }
            },
            DROP("drop")
            {
                @Override
                public void calculateInitial(MorphParticle particle)
                {
                    particle.x = particle.prevX = (particle.parent.rand.nextFloat() * 2 - 1) * (float) particle.parent.vanillaDX;
                    particle.y = particle.prevY = (particle.parent.rand.nextFloat() * 2 - 1) * (float) particle.parent.vanillaDY;
                    particle.z = particle.prevZ = (particle.parent.rand.nextFloat() * 2 - 1) * (float) particle.parent.vanillaDZ;
                }

                @Override
                public void calculate(MorphParticle particle)
                {
                    if (particle.targetY < 5)
                    {
                        particle.targetY += 0.02F;
                    }

                    particle.y -= particle.targetY;
                }
            };

            public final String id;

            public static MovementType getType(String id)
            {
                for (MovementType type : values())
                {
                    if (type.id.equals(id)) return type;
                }

                return OUT;
            }

            private MovementType(String type)
            {
                this.id = type;
            }

            public abstract void calculateInitial(MorphParticle particle);

            public abstract void calculate(MorphParticle particle);
        }
    }

    public static enum ParticleMode
    {
        VANILLA("vanilla"), MORPH("morph");

        public final String type;

        private ParticleMode(String type)
        {
            this.type = type;
        }
    }
}
