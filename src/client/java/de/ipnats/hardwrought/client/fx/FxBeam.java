package de.ipnats.hardwrought.client.fx;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A beam of energy between two points. Straight, it is a streaming ribbon; as lightning it is a
 * jagged bolt with side branches that re-forks every few ticks.
 */
public final class FxBeam {
    Vec3 from, to;
    int age;
    int life = 20;
    int color = 0xFFFFFFFF;
    float width = 0.3f;
    float alpha = 1.0f;
    float turbulence = 0.6f;
    float speed = 2.0f;
    float fadeIn = 0.1f, fadeOut = 0.6f;

    boolean lightning;
    float jitter = 0.5f;
    int reshape = 2;
    int branches = 3;
    final List<List<Vec3>> paths = new ArrayList<>();

    FxBeam(Vec3 from, Vec3 to) {
        this.from = from;
        this.to = to;
    }

    public FxBeam life(int ticks) {
        this.life = Math.max(1, ticks);
        return this;
    }

    public FxBeam color(int color) {
        this.color = color;
        return this;
    }

    public FxBeam width(float width) {
        this.width = width;
        return this;
    }

    public FxBeam alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    /** How turbulent the energy inside the ribbon is, and how fast it streams. */
    public FxBeam energy(float turbulence, float speed) {
        this.turbulence = turbulence;
        this.speed = speed;
        return this;
    }

    public FxBeam fade(float in, float outFrom) {
        this.fadeIn = in;
        this.fadeOut = outFrom;
        return this;
    }

    /** Make it a bolt: how far it strays sideways, how often it re-forks and how many branches split off. */
    public FxBeam lightning(float jitter, int reshapeTicks, int branches) {
        this.lightning = true;
        this.jitter = jitter;
        this.reshape = Math.max(1, reshapeTicks);
        this.branches = branches;
        return this;
    }

    public FxBeam endpoints(Vec3 from, Vec3 to) {
        this.from = from;
        this.to = to;
        return this;
    }

    public void kill() {
        this.life = Math.min(this.life, this.age + 1);
    }

    void reshape(RandomSource random) {
        paths.clear();
        List<Vec3> trunk = bolt(from, to, jitter, random);
        paths.add(trunk);
        for (int i = 0; i < branches && trunk.size() > 3; i++) {
            Vec3 start = trunk.get(1 + random.nextInt(trunk.size() - 2));
            Vec3 along = to.subtract(from);
            double length = along.length() * (0.2 + random.nextDouble() * 0.3);
            Vec3 direction = along.normalize().add(randomUnit(random).scale(0.6)).normalize();
            paths.add(bolt(start, start.add(direction.scale(length)), jitter * 0.7f, random));
        }
    }

    /** A jagged path by repeated midpoint displacement: each halving strays half as far. */
    private static List<Vec3> bolt(Vec3 from, Vec3 to, float jitter, RandomSource random) {
        List<Vec3> points = new ArrayList<>();
        points.add(from);
        points.add(to);
        double offset = from.distanceTo(to) * 0.18 * jitter;
        int rounds = Math.min(6, Math.max(2, (int) Math.ceil(Math.log(from.distanceTo(to) * 2 + 1) / Math.log(2))));
        for (int round = 0; round < rounds; round++) {
            List<Vec3> next = new ArrayList<>(points.size() * 2);
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 a = points.get(i), b = points.get(i + 1);
                next.add(a);
                next.add(a.add(b).scale(0.5).add(randomUnit(random).scale(offset)));
            }
            next.add(points.getLast());
            points = next;
            offset *= 0.5;
        }
        return points;
    }

    private static Vec3 randomUnit(RandomSource random) {
        double theta = random.nextDouble() * Math.PI * 2, u = random.nextDouble() * 2 - 1;
        double s = Math.sqrt(1 - u * u);
        return new Vec3(s * Math.cos(theta), u, s * Math.sin(theta));
    }
}
