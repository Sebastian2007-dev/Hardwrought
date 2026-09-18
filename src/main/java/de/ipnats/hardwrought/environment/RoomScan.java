package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * One bounded flood fill that answers what kind of space a position sits in. This is the
 * "environmental cell" of specification section 13: no per-air-block-per-tick simulation, no world
 * scan, and never a chunk load from a tick job.
 *
 * <p>A wall does not have to be airtight to make a room. A fence, a grille or a door standing open
 * all bound a space and let gas through at the same time; such a block ends the fill and adds its
 * opening area, which is what decides how quickly the room exchanges air with the outside. An open
 * door is a whole doorway and is weighted as one. Only a real hole — air the fill can walk through
 * until it sees the sky — means the position is simply outdoors.
 */
public record RoomScan(Enclosure enclosure, BlockPos origin, int volume, double apertureArea,
                       int floorY, int ceilingY, double insulation, double coalExposure,
                       List<BlockPos> combustionSources) {
    /** What kind of space a position sits in. */
    public enum Enclosure {
        /** The fill reached the sky, or left the loaded chunks and cannot be judged. */
        OPEN,
        /** The fill closed inside the volume budget: a real room with its own atmosphere. */
        ROOM,
        /**
         * The fill ran out of budget without ever seeing the sky. A large cave is not outside air,
         * so it keeps an atmosphere, but it is tracked per chunk region rather than per room: at
         * that size the exact shape no longer matters and a single origin would not be stable.
         */
        LARGE
    }

    /** The largest space Hardwrought treats as one room. Anything larger becomes a region cell. */
    public static final int MAX_VOLUME = 512;
    private static final int MAX_COMBUSTION_SOURCES = 32;
    private static final int MAX_BOUNDARY_SAMPLES = 256;

    public RoomScan {
        if (enclosure == null || origin == null || combustionSources == null || volume < 0
                || !Double.isFinite(apertureArea) || apertureArea < 0
                || !Double.isFinite(insulation) || !Double.isFinite(coalExposure)
                || coalExposure < 0 || coalExposure > 1 || ceilingY < floorY) {
            throw new IllegalArgumentException("Invalid room scan");
        }
        combustionSources = List.copyOf(combustionSources);
    }

    /** True for anything that is not outside air, so it carries an atmosphere of its own. */
    public boolean sealed() {
        return enclosure != Enclosure.OPEN;
    }

    /** 0 at the floor of the space, 1 at its ceiling. Drives the vertical gas layering. */
    public double heightFraction(double y) {
        int height = ceilingY - floorY;
        if (height <= 0) return 0.5;
        return Math.max(0, Math.min(1, (y - floorY) / height));
    }

    public static RoomScan openAir(BlockPos at) {
        return new RoomScan(Enclosure.OPEN, at.immutable(), MAX_VOLUME, 0.0,
                at.getY(), at.getY(), 0.0, 0.0, List.of());
    }

    public static RoomScan scan(ServerLevel level, BlockPos start) {
        if (!level.hasChunkAt(start)) return openAir(start);
        if (!canHoldAir(level, start)) {
            BlockPos above = start.above();
            if (!level.hasChunkAt(above) || !canHoldAir(level, above)) return openAir(start);
            start = above;
        }

        var queue = new ArrayDeque<BlockPos>();
        var visited = new java.util.HashSet<Long>();
        var combustion = new ArrayList<BlockPos>();
        BlockPos origin = start.immutable();
        queue.add(origin);
        visited.add(origin.asLong());

        int volume = 0;
        double apertureArea = 0;
        int floorY = start.getY();
        int ceilingY = start.getY();
        double insulationSum = 0;
        int boundarySamples = 0;
        int coalSamples = 0;

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            volume++;
            if (volume > MAX_VOLUME) {
                // Out of budget, but the sky was never in sight: a large cave or hall is not
                // outside air, so it keeps an atmosphere instead of being declared open.
                double wideInsulation = boundarySamples == 0 ? 0.5 : insulationSum / boundarySamples;
                double wideCoal = boundarySamples == 0 ? 0 : coalSamples / (double) boundarySamples;
                return new RoomScan(Enclosure.LARGE, origin, MAX_VOLUME, apertureArea,
                        floorY, ceilingY, wideInsulation, wideCoal, combustion);
            }
            if (openToSky(level, pos)) return openAir(start);
            // Any deterministic extreme works as a stable identity for the same space.
            if (pos.asLong() < origin.asLong()) origin = pos;
            floorY = Math.min(floorY, pos.getY());
            ceilingY = Math.max(ceilingY, pos.getY());
            BlockState state = level.getBlockState(pos);
            if (isCombustionSource(state) && combustion.size() < MAX_COMBUSTION_SOURCES) {
                combustion.add(pos.immutable());
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                // A fill that would leave the loaded area is treated as open, never as a chunk load.
                if (!level.hasChunkAt(next)) return openAir(start);
                if (!visited.add(next.asLong())) continue;
                BlockState neighbour = level.getBlockState(next);
                double aperture = apertureWeight(neighbour);
                if (aperture > 0) {
                    // Bounds the room and lets gas through at the same time.
                    apertureArea += aperture;
                    continue;
                }
                if (blocksFace(level, pos, state, direction)
                        || blocksFace(level, next, neighbour, direction.getOpposite())) {
                    if (boundarySamples < MAX_BOUNDARY_SAMPLES) {
                        insulationSum += insulationOf(neighbour);
                        if (isCoalBearing(neighbour)) coalSamples++;
                        boundarySamples++;
                    }
                    continue;
                }
                queue.add(next.immutable());
            }
        }
        double insulation = boundarySamples == 0 ? 0.5 : insulationSum / boundarySamples;
        double coalExposure = boundarySamples == 0 ? 0 : coalSamples / (double) boundarySamples;
        return new RoomScan(Enclosure.ROOM, origin, volume, apertureArea, floorY, ceilingY,
                insulation, coalExposure, combustion);
    }

    /**
     * Whether the open sky is above this position. The heightmap is used rather than sky light
     * because the light engine updates asynchronously: a roof closed this tick would otherwise keep
     * counting as open until the light catches up.
     */
    public static boolean openToSky(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    /**
     * How much of a block-sized opening a boundary block leaves for the air; 0 means it holds the
     * air in. A door standing open is not a partial obstruction — the leaf swings flat against the
     * wall and what is left is an ordinary doorway, so it counts as a whole opening. Iron bars and
     * fences are mostly gap, a decorative wall much less so.
     */
    public static double apertureWeight(BlockState state) {
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock) {
            return state.getOptionalValue(BlockStateProperties.OPEN).orElse(false) ? 1.0 : 0.0;
        }
        if (isGlassPane(state)) return 0.0;
        if (state.is(Blocks.IRON_BARS)) return 0.80;
        if (state.getBlock() instanceof CrossCollisionBlock) return 0.70;
        if (state.getBlock() instanceof WallBlock) return 0.40;
        return 0.0;
    }

    /** A block that bounds a space and lets gas through at the same time. */
    public static boolean isPorousBoundary(BlockState state) {
        return apertureWeight(state) > 0;
    }

    /**
     * Glass panes are glass: a pane window connects to its neighbours into a continuous sheet and
     * holds the air in, even though it never fills a whole block.
     */
    public static boolean isGlassPane(BlockState state) {
        return state.is(Blocks.GLASS_PANE) || state.getBlock() instanceof StainedGlassPaneBlock;
    }

    /**
     * Whether a block closes off one of its faces. Judging a whole block by its collision box is
     * wrong in both directions — an open door still has a thin collision slab and a closed one has
     * the same slab — so doors are judged by their open state and everything else face by face. A
     * slab ceiling therefore seals upward while a carpet or a torch stays part of the room.
     */
    public static boolean blocksFace(ServerLevel level, BlockPos pos, BlockState state, Direction face) {
        if (!state.getFluidState().isEmpty()) return true;
        if (state.isAir()) return false;
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock) {
            return !state.getOptionalValue(BlockStateProperties.OPEN).orElse(false);
        }
        if (isGlassPane(state)) return true;
        if (isPorousBoundary(state)) return false;
        return Block.isFaceFull(state.getCollisionShape(level, pos), face);
    }

    /** A starting position has to be somewhere air can actually be. */
    private static boolean canHoldAir(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return false;
        return state.isAir() || !Block.isShapeFullBlock(state.getCollisionShape(level, pos));
    }

    /** Section 18.1 and 19: anything burning inside the room consumes its oxygen. */
    public static boolean isCombustionSource(BlockState state) {
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA)
                || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
            return true;
        }
        if (state.getBlock() instanceof CampfireBlock) {
            return state.getOptionalValue(BlockStateProperties.LIT).orElse(false);
        }
        if (state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES)) {
            return state.getOptionalValue(BlockStateProperties.LIT).orElse(false);
        }
        if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) {
            return state.getOptionalValue(BlockStateProperties.LIT).orElse(false);
        }
        return false;
    }

    /**
     * Section 18.3 puts methane in coal regions and deep caves. Until the geological regions of
     * Milestone 6 exist, exposed coal in the walls of a room is the honest available signal.
     */
    public static boolean isCoalBearing(BlockState state) {
        return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE) || state.is(Blocks.COAL_BLOCK);
    }

    /**
     * Section 17 lists the ordering directly: wool insulates excellently, wood and earth well, stone
     * medium, glass poorly and metal very poorly. Real per-material constants live in the material
     * definitions and replace this table once blocks are mapped to materials.
     */
    public static double insulationOf(BlockState state) {
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) return 0.95;
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS) || state.is(BlockTags.WOODEN_SLABS)) return 0.75;
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.CLAY)
                || state.is(BlockTags.TERRACOTTA)) {
            return 0.70;
        }
        if (state.is(BlockTags.IMPERMEABLE)) return 0.25;
        if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.GOLD_BLOCK)
                || state.is(Blocks.NETHERITE_BLOCK) || state.is(BlockTags.RAILS)) {
            return 0.10;
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.STONE_BRICKS)
                || state.is(BlockTags.SLABS) || state.is(BlockTags.WALLS)) {
            return 0.50;
        }
        return 0.50;
    }
}
