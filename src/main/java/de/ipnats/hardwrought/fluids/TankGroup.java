package de.ipnats.hardwrought.fluids;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Tanks that touch are one tank. Whatever fills any of them fills the lowest first; whatever draws
 * from any of them draws from the highest first; and all of them hold one liquid. Each tank still keeps
 * its own share, which settles downwards and levels out sideways as in joined vessels (see
 * {@link FluidTankBlock.Entity#settle}).
 */
final class TankGroup implements Storage<FluidVariant> {
    /** How many tanks one group reaches at most. */
    static final int LIMIT = 64;

    private final List<FluidTankBlock.Entity> lowestFirst;

    private TankGroup(List<FluidTankBlock.Entity> lowestFirst) {
        this.lowestFirst = lowestFirst;
    }

    /** The group of tanks the one at pos belongs to. */
    static TankGroup of(Level level, BlockPos pos) {
        List<FluidTankBlock.Entity> tanks = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(pos);
        queue.add(pos);
        while (!queue.isEmpty() && tanks.size() < LIMIT) {
            BlockPos at = queue.poll();
            if (!(level.getBlockEntity(at) instanceof FluidTankBlock.Entity tank)) continue;
            tanks.add(tank);
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (seen.add(next) && level.getBlockState(next).getBlock() instanceof FluidTankBlock) queue.add(next);
            }
        }
        tanks.sort(Comparator.comparingInt(tank -> tank.getBlockPos().getY()));
        return new TankGroup(tanks);
    }

    List<FluidTankBlock.Entity> tanks() {
        return lowestFirst;
    }

    /** The one liquid in the group, or blank while it is empty. */
    FluidVariant liquid() {
        for (FluidTankBlock.Entity tank : lowestFirst) {
            if (!tank.storage.isResourceBlank() && tank.storage.amount > 0) return tank.storage.variant;
        }
        return FluidVariant.blank();
    }

    long amount() {
        long total = 0;
        for (FluidTankBlock.Entity tank : lowestFirst) total += tank.storage.amount;
        return total;
    }

    long capacity() {
        return (long) lowestFirst.size() * FluidTankBlock.Entity.CAPACITY;
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        FluidVariant held = liquid();
        if (!held.isBlank() && !held.equals(resource)) return 0;
        long inserted = 0;
        for (FluidTankBlock.Entity tank : lowestFirst) {
            if (inserted >= maxAmount) break;
            inserted += tank.storage.insert(resource, maxAmount - inserted, transaction);
        }
        return inserted;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        long extracted = 0;
        for (int i = lowestFirst.size() - 1; i >= 0 && extracted < maxAmount; i--) {
            extracted += lowestFirst.get(i).storage.extract(resource, maxAmount - extracted, transaction);
        }
        return extracted;
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        List<StorageView<FluidVariant>> highestFirst = new ArrayList<>();
        for (int i = lowestFirst.size() - 1; i >= 0; i--) highestFirst.add(lowestFirst.get(i).storage);
        return highestFirst.iterator();
    }
}
