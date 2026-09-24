package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A shaft's speed, and the other shaft its belt runs to, if it has one.
 *
 * <p>A belt joins two parallel shafts some blocks apart so that they turn together, the same way
 * round. Both ends remember each other; breaking either shaft throws the belt off, and it drops.
 */
public class ShaftBlockEntity extends KineticBlockEntity {
    /** How far apart two belted shafts may be, in blocks, measured straight between them. */
    public static final double MAX_BELT_LENGTH = 8.0;

    private BlockPos belt;

    public ShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHAFT, pos, state);
    }

    /** The other end of this shaft's belt, or null. */
    public BlockPos belt() {
        return belt;
    }

    public void setBelt(BlockPos other) {
        belt = other == null ? null : other.immutable();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Whether a belt may run from a shaft here to a shaft there: same axis, side by side, not too far. */
    public static boolean canBelt(Level level, BlockPos from, BlockPos to) {
        if (from.equals(to)) return false;
        BlockState a = level.getBlockState(from);
        BlockState b = level.getBlockState(to);
        if (!(a.getBlock() instanceof ShaftBlock) || !(b.getBlock() instanceof ShaftBlock)) return false;
        var axis = a.getValue(ShaftBlock.AXIS);
        if (b.getValue(ShaftBlock.AXIS) != axis) return false;
        // Side by side across the axis, not along it: along it they would simply be one line of shafts.
        if (axis.choose(from.getX(), from.getY(), from.getZ()) != axis.choose(to.getX(), to.getY(), to.getZ())) {
            return false;
        }
        if (Math.sqrt(from.distSqr(to)) > MAX_BELT_LENGTH) return false;
        return !(level.getBlockEntity(from) instanceof ShaftBlockEntity first && first.belt != null)
                && !(level.getBlockEntity(to) instanceof ShaftBlockEntity second && second.belt != null);
    }

    /** A shaft going away takes its belt with it: the belt drops, and the other end is freed. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (belt == null || level == null) return;
        BlockPos other = belt;
        belt = null;
        if (level.getBlockEntity(other) instanceof ShaftBlockEntity partner && pos.equals(partner.belt)) {
            partner.setBelt(null);
        }
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(ModItems.BELT));
        Kinetics.update(level, other);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long stored = input.getLongOr("belt", Long.MIN_VALUE);
        belt = stored == Long.MIN_VALUE ? null : BlockPos.of(stored);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (belt != null) output.putLong("belt", belt.asLong());
    }
}
