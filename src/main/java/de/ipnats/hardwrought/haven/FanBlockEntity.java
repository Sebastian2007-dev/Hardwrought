package de.ipnats.hardwrought.haven;

import de.ipnats.hardwrought.environment.Gas;
import de.ipnats.hardwrought.environment.Gases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * A ventilation fan set in a wall: it draws the gas out of the air in front of it — carbon monoxide,
 * firedamp, the breath of a crowded room — and blows it out behind, {@link #UNITS_PER_SECOND} units a
 * second at full power. It reaches what the air in front of it reaches, through open space, up to
 * {@link #REACH} blocks; a wall in between keeps a room it is not in from it. Behind it there has to be
 * somewhere for the gas to go: a fan blowing into a wall moves nothing.
 */
public class FanBlockEntity extends ApplianceBlockEntity {
    public static final int UNITS_PER_SECOND = 8;
    public static final int REACH = 10;
    private static final int MAX_SEARCH = 1200;

    private int moved;

    public FanBlockEntity(BlockPos pos, BlockState state) {
        super(HavenBlocks.FAN_ENTITY, pos, state);
    }

    /** How much gas it moved in its last second. */
    public int moved() {
        return moved;
    }

    @Override
    protected void work(ServerLevel level, double power) {
        moved = 0;
        if (power <= 0.0) return;
        Direction front = getBlockState().getValue(ApplianceBlock.FACING);
        BlockPos outlet = worldPosition.relative(front.getOpposite());
        if (!level.isLoaded(outlet) || !Gases.passable(level.getBlockState(outlet))) return;
        int budget = (int) Math.round(UNITS_PER_SECOND * Math.min(1.0, power));
        // Through the open air in front of it, nearest first.
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        BlockPos start = worldPosition.relative(front);
        queue.add(start);
        seen.add(start.asLong());
        while (!queue.isEmpty() && budget > 0 && seen.size() < MAX_SEARCH) {
            BlockPos at = queue.poll();
            if (!level.isLoaded(at)) continue;
            BlockState state = level.getBlockState(at);
            if (!Gases.passable(state)) continue;
            if (Gases.isGas(state)) {
                for (Gas gas : Gas.values()) {
                    int units = Math.min(budget, Gases.units(state, gas));
                    if (units <= 0) continue;
                    int out = units - Gases.emit(level, outlet, gas, units);
                    if (out <= 0) continue;
                    state = Gases.with(state, gas, -out);
                    level.setBlock(at, state, Block.UPDATE_ALL);
                    budget -= out;
                    moved += out;
                    if (!Gases.isGas(state)) break;
                }
            }
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (next.distManhattan(start) > REACH || next.equals(worldPosition) || !seen.add(next.asLong())) continue;
                queue.add(next);
            }
        }
    }

    @Override
    protected Component state() {
        return Component.translatable("message.hardwrought.meter.fan", moved);
    }
}
