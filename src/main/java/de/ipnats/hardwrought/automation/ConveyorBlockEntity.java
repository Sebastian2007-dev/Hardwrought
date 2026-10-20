package de.ipnats.hardwrought.automation;

import de.ipnats.hardwrought.machinery.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A length of belt in motion: everything lying on it — items, and creatures standing on it — is carried
 * along at {@link #BLOCKS_PER_TICK_PER_RPM} for every turn a minute of its rollers, drawn to the middle of
 * the belt as it goes. At the end of the line an item goes into whatever holds things in front of it, as
 * a hopper would put it there; with nothing there it falls off.
 */
public class ConveyorBlockEntity extends KineticBlockEntity {
    /** At 30 turns a minute a belt runs about two and a half blocks a second. */
    public static final double BLOCKS_PER_TICK_PER_RPM = 0.004;

    public ConveyorBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationBlocks.CONVEYOR_ENTITY, pos, state);
    }

    /** How fast the belt runs, in blocks a tick. */
    public double beltSpeed() {
        return Math.abs(kineticSpeed()) * BLOCKS_PER_TICK_PER_RPM;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ConveyorBlockEntity belt) {
        if (level.isClientSide()) KineticBlockEntity.clientTick(level, pos, state, belt);
        double speed = belt.beltSpeed();
        if (speed <= 0.0) return;
        Direction facing = state.getValue(ConveyorBlock.FACING);
        AABB top = new AABB(pos.getX(), pos.getY() + 0.25, pos.getZ(), pos.getX() + 1, pos.getY() + 1.0, pos.getZ() + 1);
        List<Entity> riding = level.getEntitiesOfClass(Entity.class, top, entity -> !entity.isSpectator() && !entity.isShiftKeyDown());
        BlockPos ahead = pos.relative(facing);
        boolean end = !(level.getBlockState(ahead).getBlock() instanceof ConveyorBlock);
        Container into = end && !level.isClientSide() ? HopperBlockEntity.getContainerAt(level, ahead) : null;
        for (Entity entity : riding) {
            // Along the belt, and toward its middle across it.
            Vec3 middle = Vec3.atBottomCenterOf(pos);
            double across = facing.getAxis() == Direction.Axis.X ? middle.z - entity.getZ() : middle.x - entity.getX();
            double cx = facing.getStepX() * speed + (facing.getAxis() == Direction.Axis.Z ? across * 0.1 : 0.0);
            double cz = facing.getStepZ() * speed + (facing.getAxis() == Direction.Axis.X ? across * 0.1 : 0.0);
            entity.setPos(entity.getX() + cx, entity.getY(), entity.getZ() + cz);
            if (entity instanceof ItemEntity item) {
                item.setDeltaMovement(0, item.getDeltaMovement().y, 0);
                if (into != null && reached(item, pos, facing)) HopperBlockEntity.addItem(into, item);
            }
        }
    }

    /** Whether an item has come to the front edge of this length of belt. */
    private static boolean reached(ItemEntity item, BlockPos pos, Direction facing) {
        double along = facing.getAxis() == Direction.Axis.X ? item.getX() - (pos.getX() + 0.5) : item.getZ() - (pos.getZ() + 0.5);
        return along * (facing.getAxis() == Direction.Axis.X ? facing.getStepX() : facing.getStepZ()) > 0.3;
    }
}
