package de.ipnats.hardwrought.building;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A half block and a thin one in the same cell: a pane or iron bars set into a stair, a slab or a
 * door, or a carpet laid on a slab, a stair or through a doorway.
 *
 * <p>Vanilla allows one block per cell, so a window row ends where a stair begins and a carpet on a
 * slab floats half a block above it. Here the two share a {@link SharedCellBlock}, which keeps both
 * and draws both. Breaking it takes the thin part out first; the half block is left as it was.
 *
 * <p>Where the thin part goes follows the click:
 * <ul>
 *   <li>on a face inside the half block's cell — the top of a slab, the step of a stair — it goes
 *   into that cell;</li>
 *   <li>on a block face that borders the half block's cell, into that cell, which vanilla would have
 *   refused because the cell is taken.</li>
 * </ul>
 * A pane clicked onto the top of a slab or a trapdoor still goes above it, as it always has: a sill
 * with a window standing on it is the common case.
 */
public final class SharedCell {
    public enum Kind { PANE, CARPET }

    private static final double EDGE = 1e-4;

    private SharedCell() { }

    public static void initialize() {
        UseBlockCallback.EVENT.register(SharedCell::use);
        PlayerBlockBreakEvents.BEFORE.register(SharedCell::breakInsert);
    }

    /** The kind of thin block this item places, or null. */
    public static Kind kind(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        Block block = item.getBlock();
        if (block instanceof IronBarsBlock) return Kind.PANE;
        if (block instanceof CarpetBlock) return Kind.CARPET;
        return null;
    }

    /** Whether a thin block may be set into this one: a half block, and no water in it. */
    public static boolean canHost(BlockState state) {
        Block block = state.getBlock();
        boolean half = block instanceof SlabBlock && state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                || block instanceof StairBlock || block instanceof DoorBlock || block instanceof TrapDoorBlock;
        if (!half) return false;
        return !state.hasProperty(BlockStateProperties.WATERLOGGED) || !state.getValue(BlockStateProperties.WATERLOGGED);
    }

    /**
     * Where a carpet lies in this cell: on the top of the host's lower part where that covers the whole
     * floor of the cell — half a block up on a bottom slab or the lower step of a stair, a little up
     * on a closed trapdoor — and on the floor otherwise. Negative where there is no room at all.
     */
    public static double carpetFloor(BlockState host) {
        double floor = 0;
        for (AABB box : host.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
            if (box.minY <= EDGE && box.getXsize() >= 1 - EDGE && box.getZsize() >= 1 - EDGE) {
                floor = Math.max(floor, box.maxY);
            }
        }
        return floor >= 1 - EDGE ? -1 : floor;
    }

    /** Whether this thin block fits into this host at this position. */
    static boolean fits(Level level, BlockPos pos, BlockState host, Kind kind) {
        if (!canHost(host)) return false;
        if (kind == Kind.PANE) return true;
        double floor = carpetFloor(host);
        if (floor < 0) return false;
        // The upper half of a door has a whole door-height of nothing below it to lie on.
        if (host.getBlock() instanceof DoorBlock && host.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return false;
        // A carpet on the floor needs a floor, as vanilla's does.
        return floor > 0 || !level.getBlockState(pos.below()).isAir();
    }

    private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        Kind kind = kind(stack);
        if (kind == null || player.isSpectator()) return InteractionResult.PASS;
        BlockPos clicked = hit.getBlockPos();
        Direction face = hit.getDirection();
        Vec3 local = hit.getLocation().subtract(clicked.getX(), clicked.getY(), clicked.getZ());
        double along = face.getAxis().choose(local.x, local.y, local.z);
        boolean inside = along > EDGE && along < 1 - EDGE;
        BlockPos target = inside ? clicked : clicked.relative(face);
        BlockState host = level.getBlockState(target);
        if (!fits(level, target, host, kind)) return InteractionResult.PASS;
        if (inside && face == Direction.UP) {
            // Onto a floor inside the cell: a carpet anywhere, a pane only onto a stair's lower step.
            if (kind == Kind.PANE && !(host.getBlock() instanceof StairBlock)) return InteractionResult.PASS;
        } else if (inside && kind == Kind.CARPET) {
            return InteractionResult.PASS;
        }
        if (!player.mayUseItemAt(target, face, stack)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

        BlockState insert = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
        if (kind == Kind.PANE) insert = Block.updateFromNeighbourShapes(insert, server, target);
        combine(server, target, host, insert);
        server.playSound(null, target, insert.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.8f);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    /** Puts the thin block into the host's cell. The cell stays built, and anchored, if it was. */
    public static void combine(ServerLevel level, BlockPos pos, BlockState host, BlockState insert) {
        boolean built = BuiltBlocks.isBuilt(level, pos);
        boolean anchored = BuiltBlocks.isAnchored(level, pos);
        BlockState cell = ModBlocks.SHARED_CELL.defaultBlockState()
                .setValue(SharedCellBlock.CONNECTS, insert.getBlock() instanceof IronBarsBlock);
        // Both parts must be known before any neighbour looks at the cell: a door's other half asks
        // what is here, and would pop off if it found an empty shared cell instead of its door.
        level.setBlock(pos, cell, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (level.getBlockEntity(pos) instanceof SharedCellBlockEntity entity) entity.set(host, insert);
        cell.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, cell.getBlock());
        BuildingPhysics.carryOver(level, pos, built, anchored);
    }

    /** Breaking a shared cell takes the thin part out and leaves the half block standing. */
    private static boolean breakInsert(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!(blockEntity instanceof SharedCellBlockEntity entity) || !(level instanceof ServerLevel server)) return true;
        BlockState host = entity.host();
        BlockState insert = entity.insert();
        if (!player.isCreative()) Block.dropResources(insert, server, pos, null, player, player.getMainHandItem());
        server.levelEvent(2001, pos, Block.getId(insert));
        boolean built = BuiltBlocks.isBuilt(server, pos);
        boolean anchored = BuiltBlocks.isAnchored(server, pos);
        server.setBlock(pos, host, Block.UPDATE_ALL);
        BuildingPhysics.carryOver(server, pos, built, anchored);
        return false;
    }
}
