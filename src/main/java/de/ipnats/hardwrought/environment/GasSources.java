package de.ipnats.hardwrought.environment;

import de.ipnats.hardwrought.smithing.ForgeBlock;
import de.ipnats.hardwrought.smithing.ForgeBlockEntity;
import de.ipnats.hardwrought.smithing.ForgeHoodBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where gas comes from, and what it does back to the fires that make it.
 *
 * <p>Every burning thing near a player lets out carbon dioxide and some carbon monoxide above itself,
 * and every player breathes out carbon dioxide. A forge under a hood lets its fumes out at the top of
 * the flue instead. Deep coal seams bleed methane into the galleries beside them. A fire that sits in
 * air too full of gas to feed it goes out — which is how a closed room puts out its own fire.
 *
 * <p>Bounded like everything else here: only around players, only in loaded chunks, and only in the
 * chunk sections that can hold a fire at all.
 */
public final class GasSources {
    /** How often the sources are read, in ticks. Every rate below is per pass. */
    public static final int INTERVAL = 200;
    private static final int REACH = 16;
    private static final int REACH_UP_DOWN = 8;
    /** A fire goes out in a gas block this full: there is no air left in it to burn. */
    public static final int SMOTHERING = 6;

    private static final double BREATH = 0.06;
    private static final int SEAM_SAMPLES = 48;
    private static final int SEAM_REACH = 12;
    private static final double SEAM_RATE = 0.35;
    /** How much rock has to sit above before coal lets out methane at all, and when it is at its worst. */
    private static final int METHANE_MIN_COVER = 24;
    private static final int METHANE_FULL_COVER = 84;

    private GasSources() { }

    /** How much carbon dioxide and carbon monoxide a burning block lets out per pass, or null if it does not burn. */
    private record Output(double carbonDioxide, double carbonMonoxide) { }

    private static Output output(BlockState state) {
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return new Output(1.0, 0.2);
        if (state.getBlock() instanceof CampfireBlock) {
            return state.getValue(BlockStateProperties.LIT) ? new Output(0.5, 0.2) : null;
        }
        if (state.getBlock() instanceof AbstractFurnaceBlock) {
            return state.getValue(BlockStateProperties.LIT) ? new Output(0.5, 0.1) : null;
        }
        if (state.getBlock() instanceof ForgeBlock) {
            return state.getValue(ForgeBlock.LIT) ? new Output(0.4, 0.2) : null;
        }
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH)
                || state.is(Blocks.SOUL_WALL_TORCH)) {
            return new Output(0.04, 0);
        }
        if ((state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES))
                && state.getOptionalValue(BlockStateProperties.LIT).orElse(false)) {
            return new Output(0.02, 0);
        }
        return null;
    }

    private static boolean mayBurn(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.getBlock() instanceof CampfireBlock
                || state.getBlock() instanceof AbstractFurnaceBlock || state.getBlock() instanceof ForgeBlock
                || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH)
                || state.is(Blocks.SOUL_WALL_TORCH) || state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES);
    }

    /** One pass around these players in one level. */
    public static void pass(ServerLevel level, List<ServerPlayer> players, RandomSource random) {
        Set<Long> done = new HashSet<>();
        List<BlockPos> hoods = new java.util.ArrayList<>();
        for (ServerPlayer player : players) {
            BlockPos centre = player.blockPosition();
            burnAround(level, centre, random, done, hoods);
            emit(level, BlockPos.containing(player.getEyePosition()), Gas.CARBON_DIOXIDE, BREATH, random);
            seep(level, centre, random);
        }
        // Every canopy near a player draws in the gas around it, each canopy once.
        Set<Long> drawn = new HashSet<>();
        for (BlockPos hood : hoods) {
            if (!drawn.contains(hood.asLong())) Flues.draw(level, hood, drawn);
        }
    }

    private static boolean scanned(BlockState state) {
        return mayBurn(state) || state.getBlock() instanceof ForgeHoodBlock;
    }

    private static void burnAround(ServerLevel level, BlockPos centre, RandomSource random, Set<Long> done,
                                   List<BlockPos> hoods) {
        int minX = SectionPos.blockToSectionCoord(centre.getX() - REACH);
        int maxX = SectionPos.blockToSectionCoord(centre.getX() + REACH);
        int minZ = SectionPos.blockToSectionCoord(centre.getZ() - REACH);
        int maxZ = SectionPos.blockToSectionCoord(centre.getZ() + REACH);
        int minY = Math.max(level.getMinY(), centre.getY() - REACH_UP_DOWN);
        int maxY = Math.min(level.getMaxY(), centre.getY() + REACH_UP_DOWN);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    int index = level.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSections().length) continue;
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(GasSources::scanned)) continue;
                    burnInSection(level, section, cx, sy, cz, minY, maxY, random, done, hoods);
                }
            }
        }
    }

    private static void burnInSection(ServerLevel level, LevelChunkSection section, int cx, int sy, int cz,
                                      int minY, int maxY, RandomSource random, Set<Long> done,
                                      List<BlockPos> hoods) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 16; y++) {
            int worldY = SectionPos.sectionToBlockCoord(sy) + y;
            if (worldY < minY || worldY > maxY) continue;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (state.getBlock() instanceof ForgeHoodBlock) {
                        hoods.add(new BlockPos(SectionPos.sectionToBlockCoord(cx) + x, worldY,
                                SectionPos.sectionToBlockCoord(cz) + z));
                        continue;
                    }
                    if (!mayBurn(state)) continue;
                    Output output = output(state);
                    if (output == null) continue;
                    pos.set(SectionPos.sectionToBlockCoord(cx) + x, worldY, SectionPos.sectionToBlockCoord(cz) + z);
                    if (!done.add(pos.asLong())) continue;
                    burn(level, pos.immutable(), state, output, random);
                }
            }
        }
    }

    /** One pass for one block: if it burns, it lets out its gas, or goes out for want of air. */
    public static void burnAt(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        Output output = output(state);
        if (output != null) burn(level, pos, state, output, random);
    }

    private static void burn(ServerLevel level, BlockPos pos, BlockState state, Output output, RandomSource random) {
        BlockPos flue = state.getBlock() instanceof ForgeBlock ? flueTop(level, pos) : null;
        BlockPos out = flue != null ? flue : pos.above();
        // A hood draws its own air through the fire; an open fire has to find it in the gas above it.
        if (flue == null && Gases.total(level.getBlockState(pos.above())) >= SMOTHERING) {
            smother(level, pos, state);
            return;
        }
        emit(level, out, Gas.CARBON_DIOXIDE, output.carbonDioxide(), random);
        emit(level, out, Gas.CARBON_MONOXIDE, output.carbonMonoxide(), random);
    }

    /**
     * Where the fumes of this forge block come out: the outlet of the flue of the hood over it (see
     * {@link Flues}). Null where no hood covers it, or where its flue has no way out, and a hood
     * that cannot draw is no better than none.
     */
    public static BlockPos flueTop(ServerLevel level, BlockPos forge) {
        BlockPos hood = ForgeHoodBlock.hoodOver(level, forge);
        return hood == null ? null : Flues.outlet(level, hood);
    }

    /** Puts a fire out for want of air. */
    public static void smother(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.getBlock() instanceof ForgeBlock) {
            if (level.getBlockEntity(pos) instanceof ForgeBlockEntity forge) forge.extinguish();
        } else if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
        } else {
            return;
        }
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.0f);
    }

    /** Lets out a rate's worth of gas: the whole units, and the fraction left over by chance. */
    private static void emit(ServerLevel level, BlockPos pos, Gas gas, double rate, RandomSource random) {
        int units = (int) rate + (random.nextDouble() < rate - (int) rate ? 1 : 0);
        if (units > 0 && level.isLoaded(pos)) Gases.emit(level, pos, gas, units);
    }

    /**
     * Section 18.3: a deep coal seam bleeds methane into any open space beside it. Depth is measured
     * as the rock actually over the player, so a superflat or vertical world behaves the same.
     */
    private static void seep(ServerLevel level, BlockPos centre, RandomSource random) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX(), centre.getZ());
        int cover = surface - centre.getY();
        if (cover <= METHANE_MIN_COVER) return;
        double depth = Math.min(1.0, (cover - METHANE_MIN_COVER) / (double) (METHANE_FULL_COVER - METHANE_MIN_COVER));
        for (int i = 0; i < SEAM_SAMPLES; i++) {
            BlockPos pos = centre.offset(random.nextInt(2 * SEAM_REACH + 1) - SEAM_REACH,
                    random.nextInt(2 * SEAM_REACH + 1) - SEAM_REACH, random.nextInt(2 * SEAM_REACH + 1) - SEAM_REACH);
            if (!level.isLoaded(pos) || !RoomScan.isCoalBearing(level.getBlockState(pos))) continue;
            if (random.nextDouble() >= SEAM_RATE * depth) continue;
            Direction face = Direction.getRandom(random);
            BlockPos open = pos.relative(face);
            if (level.isLoaded(open) && Gases.room(level.getBlockState(open)) > 0) {
                Gases.add(level, open, Gas.METHANE, 1);
            }
        }
    }
}
