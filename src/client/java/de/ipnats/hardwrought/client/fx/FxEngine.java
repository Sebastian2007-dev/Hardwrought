package de.ipnats.hardwrought.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The client's effects: particles, beams, decals, lights, and the tasks that spawn them over time.
 * Everything is simulated once per tick and drawn between ticks by {@link FxRenderer}, which
 * interpolates the motion.
 *
 * <p>Nothing here exists on the server. An effect is started by name through {@link FxPresets},
 * either locally or because the server sent a packet.
 */
public final class FxEngine {
    /** Beyond this many particles new ones are refused; a frame with more would stutter. */
    public static final int MAX_PARTICLES = 16000;

    final List<FxParticle> particles = new ArrayList<>();
    final List<FxBeam> beams = new ArrayList<>();
    final List<FxDecal> decals = new ArrayList<>();
    final List<FxLight> lights = new ArrayList<>();
    private final List<RunningTask> tasks = new ArrayList<>();
    private final List<RunningTask> newTasks = new ArrayList<>();
    final RandomSource random = RandomSource.create();
    private ClientLevel level;
    private long ticks;

    /** A piece of an effect that runs over several ticks; it returns false once it is done. */
    @FunctionalInterface
    public interface Task {
        boolean tick(FxEngine fx, int age);
    }

    private static final class RunningTask {
        final Task task;
        int age;

        RunningTask(Task task) {
            this.task = task;
        }
    }

    public RandomSource random() {
        return random;
    }

    public ClientLevel level() {
        return level;
    }

    /** A particle that is dropped silently when the engine is full. */
    public FxParticle particle(double x, double y, double z) {
        FxParticle particle = new FxParticle(x, y, z, random.nextFloat() * 100);
        if (particles.size() < MAX_PARTICLES) particles.add(particle);
        return particle;
    }

    public FxParticle particle(Vec3 pos) {
        return particle(pos.x, pos.y, pos.z);
    }

    public FxBeam beam(Vec3 from, Vec3 to) {
        FxBeam beam = new FxBeam(from, to);
        beams.add(beam);
        return beam;
    }

    public FxDecal decal(FxDecal.Kind kind, Vec3 center, Vec3 normal) {
        FxDecal decal = new FxDecal(kind, center, normal);
        decal.seed = random.nextInt(1000);
        decals.add(decal);
        return decal;
    }

    /** A coloured point light that reaches as far as the radius, in blocks. */
    public FxLight light(Vec3 pos, int color, float radius, int life) {
        FxLight light = new FxLight(pos, color, radius, life, random.nextFloat() * 100);
        lights.add(light);
        return light;
    }

    /** Runs the task every tick, starting with the next one, until it returns false. */
    public void task(Task task) {
        newTasks.add(new RunningTask(task));
    }

    /** Runs the action once, after the given number of ticks. */
    public void later(int delay, Runnable action) {
        task((fx, age) -> {
            if (age < delay) return true;
            action.run();
            return false;
        });
    }

    public int particleCount() {
        return particles.size();
    }

    public void clear() {
        particles.clear();
        beams.clear();
        decals.clear();
        lights.clear();
        tasks.clear();
        newTasks.clear();
    }

    void tick(ClientLevel level) {
        if (level != this.level) {
            clear();
            this.level = level;
        }
        if (level == null) return;
        ticks++;

        for (FxDecal decal : decals) decal.prevCenter = decal.center;
        for (FxLight light : lights) light.prevPos = light.pos;
        tasks.addAll(newTasks);
        newTasks.clear();
        for (Iterator<RunningTask> it = tasks.iterator(); it.hasNext(); ) {
            RunningTask running = it.next();
            if (!running.task.tick(this, running.age++)) it.remove();
        }

        for (Iterator<FxParticle> it = particles.iterator(); it.hasNext(); ) {
            FxParticle particle = it.next();
            tickParticle(particle, level);
            if (!particle.alive()) it.remove();
        }
        for (Iterator<FxBeam> it = beams.iterator(); it.hasNext(); ) {
            FxBeam beam = it.next();
            if (beam.lightning && (beam.age % beam.reshape == 0 || beam.paths.isEmpty())) beam.reshape(random);
            if (++beam.age >= beam.life) it.remove();
        }
        decals.removeIf(decal -> ++decal.age >= decal.life);
        lights.removeIf(light -> ++light.age >= light.life);
    }

    private void tickParticle(FxParticle p, ClientLevel level) {
        p.prevX = p.x;
        p.prevY = p.y;
        p.prevZ = p.z;
        if (p.attracted) {
            double dx = p.attractX - p.x, dy = p.attractY - p.y, dz = p.attractZ - p.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > 1.0e-4) {
                p.vx += dx / distance * p.attract;
                p.vy += dy / distance * p.attract;
                p.vz += dz / distance * p.attract;
            }
            double flat = Math.sqrt(dx * dx + dz * dz);
            if (p.swirl != 0 && flat > 1.0e-4) {
                p.vx += -dz / flat * p.swirl;
                p.vz += dx / flat * p.swirl;
            }
        }
        if (p.turbulence != 0) {
            float t = (ticks + p.seed * 7) * 0.11f;
            p.vx += Mth.sin((float) p.y * 1.7f + t + p.seed) * p.turbulence;
            p.vy += Mth.sin((float) p.z * 1.9f + t * 1.3f + p.seed * 2) * p.turbulence;
            p.vz += Mth.sin((float) p.x * 2.1f + t * 0.7f + p.seed * 3) * p.turbulence;
        }
        p.vy -= p.gravity;
        p.vx *= p.drag;
        p.vy *= p.drag;
        p.vz *= p.drag;

        double nx = p.x + p.vx, ny = p.y + p.vy, nz = p.z + p.vz;
        if (p.collide) {
            if (solid(level, p.x, ny, p.z)) {
                p.vy = -p.vy * p.bounce;
                p.vx *= 0.7;
                p.vz *= 0.7;
                ny = p.y;
            }
            if (solid(level, nx, ny, p.z)) {
                p.vx = -p.vx * p.bounce;
                nx = p.x;
            }
            if (solid(level, nx, ny, nz)) {
                p.vz = -p.vz * p.bounce;
                nz = p.z;
            }
        }
        p.x = nx;
        p.y = ny;
        p.z = nz;
        if (p.shape == FxParticle.Shape.SMOKE && (p.age & 3) == 0) {
            // The world's own light; the effects' lights are added in the shader.
            float raw = level.getMaxLocalRawBrightness(BlockPos.containing(p.x, p.y, p.z)) / 15f;
            p.brightness = Math.max(0.1f, raw * raw);
        }
        p.age++;
    }

    private static boolean solid(ClientLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }
}
