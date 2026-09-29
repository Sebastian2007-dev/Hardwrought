package de.ipnats.hardwrought.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.Function;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * Mob specification § 5.3: a mob that cannot reach its target looks for the weak point in the way
 * and breaks through it — with what its tool lets it break, see {@link Breaching}.
 *
 * <p>It only starts once its path has failed for a while, so a mob that can walk round does. Among
 * the blocks it could break it prefers the quickest, a door or gate over a wall, and one at the
 * height it walks at over digging down or up — unless its target is above or below it. Every few
 * seconds it asks again whether a way has opened, and stops when one has.
 */
public class BreachGoal extends Goal {
    /** Ticks the path has to have failed before the mob starts on a wall. */
    static final int STUCK_TICKS = 40;
    static final int SEARCH_RADIUS = 3;
    /** How near the block's middle the mob has to be to work at it. */
    static final double REACH_SQR = 2.6 * 2.6;
    /** Ticks between asking whether the target can be reached after all. */
    static final int RECHECK_TICKS = 60;
    /** Doors, gates and trapdoors are made to be got through: weighed at this share of their cost. */
    static final double WEAK_POINT = 0.6;

    private final PathfinderMob mob;
    /** What the mob breaks with: its tool in hand, or the weight of a ravager as if it were an axe. */
    private final Function<Mob, ItemStack> tool;
    private int stuck;
    private int working;
    private BlockPos block;

    public BreachGoal(PathfinderMob mob) {
        this(mob, Mob::getMainHandItem);
    }

    public BreachGoal(PathfinderMob mob, Function<Mob, ItemStack> tool) {
        this.mob = mob;
        this.tool = tool;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || mob.distanceToSqr(target) < 4 || !blocked()) {
            stuck = 0;
            return false;
        }
        // Goals are asked every other tick.
        stuck += 2;
        if (stuck < STUCK_TICKS) return false;
        block = choose(level, target);
        return block != null;
    }

    /** No path, or one that ends short of the target with the mob already at its end. */
    private boolean blocked() {
        Path path = mob.getNavigation().getPath();
        if (path == null) return true;
        return !path.canReach() && (path.isDone() || mob.getNavigation().isDone()
                || path.getEndNode() != null && mob.blockPosition().distManhattan(path.getEndNode().asBlockPos()) <= 2);
    }

    @Override
    public void start() {
        stuck = 0;
        working = 0;
        mob.getNavigation().stop();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (block == null || target == null || !target.isAlive() || !(mob.level() instanceof ServerLevel level)) return false;
        return Breaching.rate(level, block, level.getBlockState(block), tool.apply(mob)) > 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || block == null) return;
        Vec3 centre = Vec3.atCenterOf(block);
        if (mob.distanceToSqr(centre) > REACH_SQR) {
            if (mob.getNavigation().isDone()) mob.getNavigation().moveTo(centre.x, block.getY(), centre.z, 1.0);
            return;
        }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(centre);
        if (mob.tickCount % 10 == 0) mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        Breaching.work(level, block, mob, Breaching.rate(level, block, level.getBlockState(block), tool.apply(mob)));
        if (++working % RECHECK_TICKS == 0 && mob.getTarget() != null) {
            Path path = mob.getNavigation().createPath(mob.getTarget(), 0);
            // A way round has opened: take it, and leave the wall.
            if (path != null && path.canReach()) block = null;
        }
    }

    @Override
    public void stop() {
        block = null;
    }

    /** The block in the way that is quickest to get through, or null where there is none this mob can break. */
    BlockPos choose(ServerLevel level, LivingEntity target) {
        Vec3 from = mob.position();
        Vec3 toward = target.position().subtract(from);
        double targetDistance = toward.length();
        BlockPos base = mob.blockPosition();
        double dy = target.getY() - mob.getY();
        BlockPos best = null;
        double bestCost = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-SEARCH_RADIUS, -1, -SEARCH_RADIUS),
                base.offset(SEARCH_RADIUS, 2, SEARCH_RADIUS))) {
            Vec3 centre = Vec3.atCenterOf(pos);
            Vec3 offset = centre.subtract(from);
            // In the way: towards the target and nearer to it than the mob is.
            if (offset.dot(toward) <= 0 || centre.distanceTo(target.position()) >= targetDistance) continue;
            BlockState state = level.getBlockState(pos);
            double rate = Breaching.rate(level, pos, state, tool.apply(mob));
            if (rate <= 0) continue;
            double cost = 1.0 / rate + offset.length() * 10;
            if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock
                    || state.getBlock() instanceof TrapDoorBlock) {
                cost *= WEAK_POINT;
            }
            // Walls at walking height first; the floor or the ceiling only where the target is that way.
            int level0 = pos.getY() - base.getY();
            if (level0 < 0 && dy > -1 || level0 > 1 && dy < 1) cost *= 3;
            if (cost < bestCost) {
                bestCost = cost;
                best = pos.immutable();
            }
        }
        return best;
    }
}
