package de.ipnats.hardwrought.mobs;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * How a ranged fighter moves (mob specification §§ 8, 10, 16, 33, 34): it does not stand and trade
 * blows. A target that closes in is backed away from; every few seconds of fighting it takes up a new
 * position, around the target at a shooting distance, preferring high ground — or, for one that
 * fights from ambush, cover the target cannot see it behind. Vanilla's own attack goal does the
 * aiming and shooting from wherever this leaves it.
 */
public class RangedTacticsGoal extends Goal {
    /** Closer than this, the fighter backs away. */
    static final double TOO_CLOSE = 5;
    static final double SHOOTING_DISTANCE = 12;
    /** Ticks of fighting between one position and the next. */
    static final int RELOCATE_TICKS = 140;
    static final double SPEED = 1.15;
    static final int CANDIDATES = 8;
    static final int MAX_MOVE_TICKS = 60;

    private final PathfinderMob mob;
    private final Predicate<PathfinderMob> armed;
    private final boolean seeksCover;
    private int fighting;
    private int moving;
    /** Whether this run is a flight from a target too close, which may end in a panic. */
    private boolean flight;

    /**
     * @param armed      whether the mob is fighting at range right now — a skeleton holding a bow
     * @param seeksCover prefers positions out of the target's sight over high ground
     */
    public RangedTacticsGoal(PathfinderMob mob, Predicate<PathfinderMob> armed, boolean seeksCover) {
        this.mob = mob;
        this.armed = armed;
        this.seeksCover = seeksCover;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !armed.test(mob)) {
            fighting = 0;
            return false;
        }
        fighting += 2;
        // In a panic it neither flees nor repositions: it stands and shoots, point blank if need be.
        if (Panic.panicking(mob)) return false;
        return mob.distanceTo(target) < TOO_CLOSE || fighting >= RELOCATE_TICKS;
    }

    @Override
    public void start() {
        fighting = 0;
        moving = 0;
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        flight = mob.distanceTo(target) < TOO_CLOSE;
        if (flight && !Panic.startFlight(mob)) {
            // One flight too many: it turns to fight instead.
            moving = MAX_MOVE_TICKS;
            return;
        }
        Vec3 to = flight ? away(target) : position(target);
        if (to != null) mob.getNavigation().moveTo(to.x, to.y, to.z, SPEED);
    }

    @Override
    public boolean canContinueToUse() {
        if (flight && !Panic.keepFleeing(mob)) return false;
        return ++moving < MAX_MOVE_TICKS && !mob.getNavigation().isDone() && mob.getTarget() != null;
    }

    @Override
    public void stop() {
        if (flight) Panic.endFlight(mob);
        flight = false;
        mob.getNavigation().stop();
    }

    /** Straight back from the target. */
    private Vec3 away(LivingEntity target) {
        Vec3 back = mob.position().subtract(target.position());
        if (back.lengthSqr() < 1.0e-4) back = new Vec3(1, 0, 0);
        return mob.position().add(back.normalize().scale(TOO_CLOSE + 2));
    }

    /** A new position around the target, at shooting distance, high or hidden as this fighter likes. */
    private Vec3 position(LivingEntity target) {
        Vec3 best = null;
        double bestScore = -Double.MAX_VALUE;
        double angle = Math.atan2(mob.getZ() - target.getZ(), mob.getX() - target.getX());
        for (int i = 0; i < CANDIDATES; i++) {
            double turn = angle + (mob.getRandom().nextDouble() - 0.5) * Math.PI * 0.8;
            Vec3 guess = target.position().add(Math.cos(turn) * SHOOTING_DISTANCE, 0, Math.sin(turn) * SHOOTING_DISTANCE);
            Path path = mob.getNavigation().createPath(guess.x, guess.y, guess.z, 1);
            if (path == null || path.getEndNode() == null) continue;
            Vec3 end = Vec3.atBottomCenterOf(path.getEndNode().asBlockPos());
            double score = seeksCover ? (hidden(target, end) ? 10 : 0) : end.y - target.getY();
            score -= Math.abs(end.distanceTo(target.position()) - SHOOTING_DISTANCE) * 0.5;
            if (score > bestScore) {
                bestScore = score;
                best = end;
            }
        }
        return best;
    }

    private boolean hidden(LivingEntity target, Vec3 at) {
        return mob.level().clip(new ClipContext(target.getEyePosition(), at.add(0, 1.5, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target)).getType() != HitResult.Type.MISS;
    }
}
