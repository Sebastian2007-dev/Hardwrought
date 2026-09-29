package de.ipnats.hardwrought.smeltery;

import de.ipnats.hardwrought.smithing.ForgeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The shape of a smeltery, as in Tinkers' Construct: a box of air — its tank — with a floor of
 * smeltery bricks under it and walls of bricks, glass, drains and the controller all round it, up to
 * {@value #MAX_WIDTH}×{@value #MAX_WIDTH} inside and {@value #MAX_HEIGHT} high. The controller sits in
 * the wall, facing out; the tank is found from behind it. The corners of the walls are not needed.
 *
 * <p>A layer counts only while its ring of wall is whole, from the floor up; the controller has to be
 * in one of the layers that count.
 */
public final class SmelteryStructure {
    public static final int MAX_WIDTH = 7, MAX_HEIGHT = 8;

    /** A smeltery as found: its tank from corner to corner, its drains, and whether a bellows blows. */
    public record Found(BlockPos min, BlockPos max, List<BlockPos> drains, boolean blown) {
        public int volume() {
            return (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        }

        public int area() {
            return (max.getX() - min.getX() + 1) * (max.getZ() - min.getZ() + 1);
        }

        public int height() {
            return max.getY() - min.getY() + 1;
        }
    }

    /** Which controller each drain belongs to, per level: how a faucet finds its smeltery. */
    private static final Map<Level, Map<Long, BlockPos>> DRAINS = new WeakHashMap<>();

    private SmelteryStructure() { }

    public static boolean isWall(BlockState state) {
        Block block = state.getBlock();
        return block == SmelteryBlocks.SMELTERY_BRICKS || block == SmelteryBlocks.SMELTERY_GLASS
                || block == SmelteryBlocks.DRAIN || block == SmelteryBlocks.CONTROLLER;
    }

    private static boolean isTank(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }

    /** The smeltery this controller stands in, or null where it is not whole. */
    public static Found scan(Level level, BlockPos controller, Direction facing) {
        BlockPos start = controller.relative(facing.getOpposite());
        if (!isTank(level, start)) return null;
        int minX = start.getX(), maxX = start.getX(), minZ = start.getZ(), maxZ = start.getZ();
        while (maxX - minX < MAX_WIDTH && isTank(level, new BlockPos(minX - 1, start.getY(), start.getZ()))) minX--;
        while (maxX - minX < MAX_WIDTH && isTank(level, new BlockPos(maxX + 1, start.getY(), start.getZ()))) maxX++;
        while (maxZ - minZ < MAX_WIDTH && isTank(level, new BlockPos(start.getX(), start.getY(), minZ - 1))) minZ--;
        while (maxZ - minZ < MAX_WIDTH && isTank(level, new BlockPos(start.getX(), start.getY(), maxZ + 1))) maxZ++;
        if (maxX - minX + 1 > MAX_WIDTH || maxZ - minZ + 1 > MAX_WIDTH) return null;

        int bottom = start.getY();
        while (start.getY() - bottom < MAX_HEIGHT && isTank(level, new BlockPos(start.getX(), bottom - 1, start.getZ()))) bottom--;
        // The floor: bricks under every block of the tank.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (level.getBlockState(new BlockPos(x, bottom - 1, z)).getBlock() != SmelteryBlocks.SMELTERY_BRICKS) return null;
            }
        }
        List<BlockPos> drains = new ArrayList<>();
        boolean blown = false;
        int top = bottom - 1;
        for (int y = bottom; y < bottom + MAX_HEIGHT; y++) {
            List<BlockPos> layerDrains = new ArrayList<>();
            boolean[] layerBlown = {false};
            if (!layer(level, minX, maxX, minZ, maxZ, y, layerDrains, layerBlown)) break;
            top = y;
            drains.addAll(layerDrains);
            blown |= layerBlown[0];
        }
        if (top < bottom || controller.getY() < bottom || controller.getY() > top) return null;
        return new Found(new BlockPos(minX, bottom, minZ), new BlockPos(maxX, top, maxZ), drains, blown);
    }

    /** One layer of the tank: all of it empty, all of its ring wall. */
    private static boolean layer(Level level, int minX, int maxX, int minZ, int maxZ, int y,
                                 List<BlockPos> drains, boolean[] blown) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!isTank(level, new BlockPos(x, y, z))) return false;
            }
        }
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                boolean edgeX = x == minX - 1 || x == maxX + 1, edgeZ = z == minZ - 1 || z == maxZ + 1;
                if (!edgeX && !edgeZ || edgeX && edgeZ) continue;
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (!isWall(state)) return false;
                if (state.getBlock() == SmelteryBlocks.DRAIN) drains.add(pos);
                if (!blown[0] && ForgeBlockEntity.blown(level, pos)) blown[0] = true;
            }
        }
        return true;
    }

    static void register(Level level, BlockPos controller, List<BlockPos> drains) {
        Map<Long, BlockPos> map = DRAINS.computeIfAbsent(level, ignored -> new HashMap<>());
        map.values().removeIf(controller::equals);
        for (BlockPos drain : drains) map.put(drain.asLong(), controller);
    }

    static void unregister(Level level, BlockPos controller) {
        Map<Long, BlockPos> map = DRAINS.get(level);
        if (map != null) map.values().removeIf(controller::equals);
    }

    /** The controller of the smeltery this drain is part of, or null. */
    public static SmelteryControllerBlockEntity controllerOf(Level level, BlockPos drain) {
        Map<Long, BlockPos> map = DRAINS.get(level);
        BlockPos controller = map == null ? null : map.get(drain.asLong());
        return controller != null && level.getBlockEntity(controller) instanceof SmelteryControllerBlockEntity entity
                ? entity : null;
    }
}
