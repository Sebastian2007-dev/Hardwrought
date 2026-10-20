package de.ipnats.hardwrought.encounter;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * A danger of a boss fight that only a group can answer: something one player alone could only take,
 * but several can deal with by doing the right thing together — standing close, standing apart,
 * pulling away from each other. Specification § 99: more players should bring more mechanics, not
 * more health.
 *
 * <p>Every one of them is shown before it strikes, long enough to react, and says in words what it
 * wants; a mechanic nobody can read is just damage.
 */
public interface Hazard {
    /** One tick of it; true when it is over. */
    boolean tick(ServerLevel level, Encounter encounter);

    /** A ring of particles on the ground round a place: where a danger reaches. */
    static void ring(ServerLevel level, ParticleOptions particle, Vec3 at, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            level.sendParticles(particle, at.x + Math.cos(a) * radius, at.y + 0.15, at.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0);
        }
    }
}
