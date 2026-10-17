package de.ipnats.hardwrought.ritual;

import de.ipnats.hardwrought.knowledge.Multiblocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The temple at the end of the game (endgame § 5), which its players build themselves, block by block,
 * as a multiblock (see {@link #multiblock}): a great square terrace with a stair down each side, a
 * colonnade round it under a cloister roof, four corner towers, and in the middle the round hall of the
 * ritual — buttressed, windowed, its floor inlaid with rings and rays, eight pillars and eight pedestals
 * round the altar on its dais, and over it a roof open at the top to the sky.
 *
 * <p>Three of them: of the <b>plains</b> — white quartz and stone under a green copper dome; of the
 * <b>desert</b> — sandstone, lapis and gold under a stepped pyramid; and <b>stony</b> — deepslate, tuff and
 * amethyst under a tall pointed spire. Every light in them is one that never goes out. The courtyard
 * between the colonnade and the hall, and the foundation under the terrace, are the builder's own: the
 * plan has a garden there for each (grass, flowers and trees round a ring of water; palms and cacti round
 * an oasis; boulders, moss and braziers), shown when a temple is set down whole, but none is asked for.
 */
public final class Temple {
    public enum Theme {
        PLAINS, DESERT, STONY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Half the width of the terrace, how far the stairs reach beyond it, the hall's radius, and the highest anything goes. */
    public static final int HALF = 26, STAIRS = 3, HALL = 13, TOP = 40;
    /** Where the colonnade stands, and how far the ritual chamber reaches from the altar. */
    static final int COLONNADE = 22;
    public static final double CHAMBER = HALL - 0.5;
    /** The radius the pedestals stand at round the altar. */
    static final double PEDESTAL_RING = 6.5;
    /** The share of a temple's blocks that has to be right for it to stand: a stray block does not undo it. */
    public static final double STANDS_AT = 0.98;

    /** What a theme builds with. */
    record Palette(BlockState wall, BlockState trim, BlockState pillar, BlockState beam, BlockState roof, BlockState roofAccent,
                   BlockState roofTrim, BlockState hallFloor, BlockState inlay, BlockState ray, BlockState glass, BlockState light,
                   BlockState base, BlockState stairs, BlockState slab, BlockState path, BlockState pathAlt) {
    }

    static Palette palette(Theme theme) {
        return switch (theme) {
            case PLAINS -> new Palette(Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(),
                    Blocks.QUARTZ_PILLAR.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.CUT_COPPER.waxed().oxidized().defaultBlockState(),
                    Blocks.CUT_COPPER.waxed().weathered().defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.POLISHED_DIORITE.defaultBlockState(),
                    Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.STAINED_GLASS.lime().defaultBlockState(),
                    Blocks.SEA_LANTERN.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_STAIRS.defaultBlockState(),
                    Blocks.CUT_COPPER_SLAB.waxed().oxidized().defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
                    Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            case DESERT -> new Palette(Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.CHISELED_SANDSTONE.defaultBlockState(),
                    Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_RED_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                    Blocks.DYED_TERRACOTTA.orange().defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                    Blocks.LAPIS_BLOCK.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.STAINED_GLASS.orange().defaultBlockState(),
                    Blocks.OCHRE_FROGLIGHT.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState(),
                    Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState());
            case STONY -> new Palette(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CHISELED_DEEPSLATE.defaultBlockState(),
                    Blocks.POLISHED_BASALT.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(),
                    Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                    Blocks.CHISELED_TUFF_BRICKS.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.STAINED_GLASS.purple().defaultBlockState(),
                    Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState(),
                    Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState(), Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.POLISHED_TUFF.defaultBlockState());
        };
    }

    /** The temple as planned: block by block, relative to the altar's floor, which lies at y 0. */
    static final class Plan {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        /** What the builder is free to make otherwise: the garden and the foundation. */
        final Set<BlockPos> free = new HashSet<>();
        final List<BlockPos> pedestals = new ArrayList<>();
        BlockPos altar;

        void set(int x, int y, int z, BlockState state) {
            BlockPos pos = new BlockPos(x, y, z);
            blocks.put(pos, state);
            free.remove(pos);
        }

        /** A block the plan shows, but does not ask for. */
        void suggest(int x, int y, int z, BlockState state) {
            BlockPos pos = new BlockPos(x, y, z);
            blocks.put(pos, state);
            free.add(pos);
        }

        boolean has(int x, int y, int z) {
            return blocks.containsKey(new BlockPos(x, y, z));
        }

        boolean asks(BlockPos pos) {
            BlockState state = blocks.get(pos);
            return state != null && !state.isAir() && !free.contains(pos);
        }
    }

    private static final Map<Theme, Plan> PLANS = new HashMap<>();
    private static final Map<Theme, Multiblocks.Multiblock> MULTIBLOCKS = new LinkedHashMap<>();

    private Temple() { }

    static void initialize() { }

    static synchronized Plan planOf(Theme theme) {
        return PLANS.computeIfAbsent(theme, Temple::plan);
    }

    // ------------------------------------------------------------------------------------ as a multiblock

    /**
     * A temple as the compendium shows it and its projection lays it out to build along: every block it
     * asks for, layer by layer, the courtyard and the foundation left out. The two kinds of path stone
     * count as one.
     */
    public static synchronized Multiblocks.Multiblock multiblock(Theme theme) {
        Multiblocks.Multiblock known = MULTIBLOCKS.get(theme);
        if (known != null) return known;
        Plan plan = planOf(theme);
        Palette p = palette(theme);
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, reach = HALF + STAIRS;
        for (BlockPos pos : plan.blocks.keySet()) {
            if (!plan.asks(pos)) continue;
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
        }
        String symbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#$%&*+=?@";
        Map<Identifier, Character> chars = new LinkedHashMap<>();
        Map<Character, List<Identifier>> legend = new LinkedHashMap<>();
        Identifier path = BuiltInRegistries.BLOCK.getKey(p.path().getBlock()), pathAlt = BuiltInRegistries.BLOCK.getKey(p.pathAlt().getBlock());
        List<List<String>> layers = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            List<String> rows = new ArrayList<>();
            for (int z = -reach; z <= reach; z++) {
                StringBuilder row = new StringBuilder();
                for (int x = -reach; x <= reach; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!plan.asks(pos)) {
                        row.append(' ');
                        continue;
                    }
                    Identifier id = BuiltInRegistries.BLOCK.getKey(plan.blocks.get(pos).getBlock());
                    if (id.equals(pathAlt)) id = path;
                    Character c = chars.get(id);
                    if (c == null) {
                        c = symbols.charAt(chars.size());
                        chars.put(id, c);
                        legend.put(c, id.equals(path) && !path.equals(pathAlt) ? List.of(path, pathAlt) : List.of(id));
                    }
                    row.append(c);
                }
                rows.add(row.toString());
            }
            layers.add(rows);
        }
        Multiblocks.Multiblock multiblock = new Multiblocks.Multiblock(de.ipnats.hardwrought.Hardwrought.id(theme.id() + "_temple"), layers,
                java.util.Collections.unmodifiableMap(legend), Optional.empty());
        MULTIBLOCKS.put(theme, multiblock);
        return multiblock;
    }

    public static List<Multiblocks.Multiblock> multiblocks() {
        List<Multiblocks.Multiblock> all = new ArrayList<>();
        for (Theme theme : Theme.values()) all.add(multiblock(theme));
        return all;
    }

    /** How much of a temple stands round an altar, 0 to 1, how many of its blocks are still wrong, and the nearest of them. */
    public record Survey(Theme theme, double share, int wrong, BlockPos nearestWrong) {
        public boolean stands() {
            return share >= STANDS_AT;
        }
    }

    /** Looks round an altar for whichever temple stands best there. */
    public static Survey survey(ServerLevel level, BlockPos altar) {
        Survey best = null;
        for (Theme theme : Theme.values()) {
            Plan plan = planOf(theme);
            Palette p = palette(theme);
            BlockPos origin = altar.subtract(plan.altar);
            int asked = 0, right = 0;
            BlockPos nearestWrong = null;
            for (Map.Entry<BlockPos, BlockState> e : plan.blocks.entrySet()) {
                if (!plan.asks(e.getKey())) continue;
                asked++;
                BlockPos at = origin.offset(e.getKey());
                BlockState there = level.getBlockState(at);
                Block want = e.getValue().getBlock();
                boolean ok = there.is(want) || want == p.path().getBlock() && there.is(p.pathAlt().getBlock())
                        || want == p.pathAlt().getBlock() && there.is(p.path().getBlock());
                if (ok) right++;
                else if (nearestWrong == null || at.distSqr(altar) < nearestWrong.distSqr(altar)) nearestWrong = at;
            }
            Survey survey = new Survey(theme, asked == 0 ? 0 : right / (double) asked, asked - right, nearestWrong);
            if (best == null || survey.share() > best.share()) best = survey;
        }
        return best;
    }

    /** What the temple of this theme asks for at this place round an altar; null where it asks for nothing. */
    public static BlockState wanted(Theme theme, BlockPos altar, BlockPos pos) {
        Plan plan = planOf(theme);
        BlockPos local = pos.subtract(altar).offset(plan.altar);
        return plan.asks(local) ? plan.blocks.get(local) : null;
    }

    /** Where round an altar the temple of this theme is still wrong, nearest the altar first, at most so many. */
    public static List<BlockPos> wrong(net.minecraft.world.level.Level level, BlockPos altar, Theme theme, int max) {
        Plan plan = planOf(theme);
        BlockPos origin = altar.subtract(plan.altar);
        List<BlockPos> wrong = new ArrayList<>();
        for (BlockPos local : plan.blocks.keySet()) {
            if (!plan.asks(local)) continue;
            BlockPos at = origin.offset(local);
            if (!fits(theme, altar, at, level.getBlockState(at))) wrong.add(at);
        }
        wrong.sort(java.util.Comparator.comparingDouble(at -> at.distSqr(altar)));
        return wrong.size() > max ? new ArrayList<>(wrong.subList(0, max)) : wrong;
    }

    /** Whether this block, at this place round an altar, is just what the temple of this theme asks for there. */
    public static boolean fits(Theme theme, BlockPos altar, BlockPos pos, BlockState state) {
        Plan plan = planOf(theme);
        BlockPos local = pos.subtract(altar).offset(plan.altar);
        if (!plan.asks(local)) return false;
        Palette p = palette(theme);
        net.minecraft.world.level.block.Block want = plan.blocks.get(local).getBlock();
        return state.is(want) || (want == p.path().getBlock() || want == p.pathAlt().getBlock())
                && (state.is(p.path().getBlock()) || state.is(p.pathAlt().getBlock()));
    }

    /** Every block a temple asks for, relative to its floor, the altar standing at 0, 1, 0. */
    public static Map<BlockPos, BlockState> required(Theme theme) {
        Plan plan = planOf(theme);
        Map<BlockPos, BlockState> required = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : plan.blocks.entrySet()) if (plan.asks(e.getKey())) required.put(e.getKey(), e.getValue());
        return required;
    }

    /** Where the pedestals stand round an altar, in the order of the offerings. */
    public static List<BlockPos> pedestals(BlockPos altar) {
        Plan plan = planOf(Theme.PLAINS);
        List<BlockPos> at = new ArrayList<>();
        for (BlockPos pedestal : plan.pedestals) at.add(altar.offset(pedestal.subtract(plan.altar)));
        return at;
    }

    /** Sets a whole temple down at once, garden and foundation too; for tests and showcases. */
    public static void buildNow(ServerLevel level, BlockPos altar, Theme theme) {
        Plan plan = planOf(theme);
        BlockPos origin = altar.subtract(plan.altar);
        int reach = HALF + STAIRS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -reach; x <= reach; x++) for (int z = -reach; z <= reach; z++) for (int y = 0; y <= TOP; y++) {
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (!plan.has(x, y, z) && !level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (Map.Entry<BlockPos, BlockState> e : plan.blocks.entrySet()) level.setBlock(origin.offset(e.getKey()), e.getValue(), Block.UPDATE_CLIENTS);
        BlockState base = palette(theme).base();
        for (int x = -reach; x <= reach; x++) for (int z = -reach; z <= reach; z++) {
            int top = Integer.MIN_VALUE;
            for (int y = -7; y <= 2 && top == Integer.MIN_VALUE; y++) if (plan.has(x, y, z)) top = y - 1;
            if (top == Integer.MIN_VALUE) continue;
            for (int y = top; y > top - 48; y--) {
                pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                if (pos.getY() <= level.getMinY()) break;
                BlockState there = level.getBlockState(pos);
                if (!there.isAir() && !there.canBeReplaced() && !there.is(BlockTags.LEAVES) && !there.is(BlockTags.LOGS)) break;
                level.setBlock(pos, base, Block.UPDATE_CLIENTS);
            }
        }
    }

    // ------------------------------------------------------------------------------------ planning it

    static Plan plan(Theme theme) {
        Palette p = palette(theme);
        Plan plan = new Plan();
        RandomSource random = RandomSource.create(theme.ordinal() * 7919L + 31);
        terrace(plan, p, theme, random);
        stairs(plan, p);
        colonnade(plan, p);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) tower(plan, p, theme, sx * COLONNADE, sz * COLONNADE);
        hall(plan, p);
        roof(plan, p, theme);
        chamber(plan, p);
        return plan;
    }

    private static double r(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    private static boolean onPath(int x, int z) {
        return Math.abs(x) <= 2 || Math.abs(z) <= 2;
    }

    /** The terrace: its rim, its paths, and the courtyard garden between the colonnade and the hall. */
    private static void terrace(Plan plan, Palette p, Theme theme, RandomSource random) {
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                double rr = r(x, z);
                plan.suggest(x, -2, z, p.base());
                if (edge == HALF) {
                    plan.set(x, -1, z, p.trim());
                    // A low parapet round the rim, open where the stairs come up.
                    boolean opening = Math.min(Math.abs(x), Math.abs(z)) <= 4;
                    if (!opening) plan.set(x, 0, z, (x + z) % 6 == 0 ? p.light() : p.wall());
                    continue;
                }
                if (rr <= HALL + 1.5) continue;
                if (onPath(x, z)) {
                    plan.set(x, -1, z, random.nextInt(5) == 0 ? p.pathAlt() : p.path());
                    // Lamp posts along the paths.
                    int along = Math.max(Math.abs(x), Math.abs(z));
                    if (Math.min(Math.abs(x), Math.abs(z)) == 2 && along % 6 == 0 && along > HALL + 3 && along < COLONNADE - 1) {
                        plan.set(x, 0, z, p.trim());
                        plan.set(x, 1, z, p.pillar());
                        plan.set(x, 2, z, p.light());
                    }
                    continue;
                }
                if (Math.max(Math.abs(x), Math.abs(z)) >= COLONNADE - 1) {
                    // Under the cloister and out to the rim: paved.
                    plan.set(x, -1, z, p.path());
                    continue;
                }
                garden(plan, theme, x, z, rr, random);
            }
        }
    }

    private static void garden(Plan plan, Theme theme, int x, int z, double rr, RandomSource random) {
        // A ring of water round the hall: the plains' moat, the desert's oasis.
        boolean channel = theme != Theme.STONY && rr >= 15.6 && rr < 17.2;
        switch (theme) {
            case PLAINS -> {
                if (channel) {
                    plan.suggest(x, -1, z, Blocks.WATER.defaultBlockState());
                    return;
                }
                plan.suggest(x, -1, z, Blocks.GRASS_BLOCK.defaultBlockState());
                int roll = random.nextInt(100);
                BlockState[] flowers = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(),
                        Blocks.AZURE_BLUET.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.ALLIUM.defaultBlockState()};
                if (roll < 28) plan.suggest(x, 0, z, Blocks.SHORT_GRASS.defaultBlockState());
                else if (roll < 40) plan.suggest(x, 0, z, flowers[random.nextInt(flowers.length)]);
            }
            case DESERT -> {
                if (channel) {
                    plan.suggest(x, -1, z, Blocks.WATER.defaultBlockState());
                    return;
                }
                plan.suggest(x, -1, z, rr < 18 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.SAND.defaultBlockState());
                int roll = random.nextInt(100);
                if (rr < 18 && roll < 30) plan.suggest(x, 0, z, Blocks.SHORT_GRASS.defaultBlockState());
                else if (rr >= 18 && roll < 4) plan.suggest(x, 0, z, Blocks.DEAD_BUSH.defaultBlockState());
            }
            case STONY -> {
                int roll = random.nextInt(100);
                BlockState ground = roll < 30 ? Blocks.GRAVEL.defaultBlockState() : roll < 58 ? Blocks.COARSE_DIRT.defaultBlockState()
                        : roll < 85 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                plan.suggest(x, -1, z, ground);
                if (ground.is(Blocks.MOSS_BLOCK) && random.nextInt(3) == 0) plan.suggest(x, 0, z, Blocks.MOSS_CARPET.defaultBlockState());
                else if (ground.is(Blocks.COARSE_DIRT) && random.nextInt(8) == 0) plan.suggest(x, 0, z, Blocks.FERN.defaultBlockState());
            }
        }
        // Trees, palms or boulders and braziers at fixed places in each quarter.
        int ax = Math.abs(x), az = Math.abs(z);
        if ((ax == 18 && az == 9) || (ax == 9 && az == 18)) feature(plan, theme, x, z, random);
    }

    private static void feature(Plan plan, Theme theme, int x, int z, RandomSource random) {
        switch (theme) {
            case PLAINS -> {
                BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                for (int y = 0; y < 4; y++) plan.suggest(x, y, z, Blocks.OAK_LOG.defaultBlockState());
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 3; dy <= 6; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + (dy - 4.5) * (dy - 4.5) * 1.6);
                    if (d <= 2.6 && !plan.has(x + dx, dy, z + dz)) plan.suggest(x + dx, dy, z + dz, leaves);
                }
            }
            case DESERT -> {
                BlockState frond = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                for (int y = 0; y < 6; y++) plan.suggest(x + (y > 3 ? 1 : 0), y, z, Blocks.STRIPPED_JUNGLE_LOG.defaultBlockState());
                int tx = x + 1, ty = 6;
                plan.suggest(tx, ty, z, frond);
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    for (int i = 1; i <= 3; i++) plan.suggest(tx + d.getStepX() * i, ty - (i == 3 ? 1 : 0), z + d.getStepZ() * i, frond);
                }
                // A cactus beside each palm.
                plan.suggest(x - 2, 0, z + 2, Blocks.CACTUS.defaultBlockState());
                plan.suggest(x - 2, 1, z + 2, Blocks.CACTUS.defaultBlockState());
                plan.suggest(x - 2, -1, z + 2, Blocks.SAND.defaultBlockState());
            }
            case STONY -> {
                if ((x + z) % 2 == 0) {
                    // A boulder.
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 2; dy++) {
                        double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.8);
                        if (d <= 2.2 + random.nextDouble() * 0.4) {
                            plan.suggest(x + dx, dy, z + dz, random.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                                    : Blocks.ANDESITE.defaultBlockState());
                        }
                    }
                } else {
                    // A brazier: a stone bowl with a light that does not burn out.
                    plan.suggest(x, 0, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    plan.suggest(x, 1, z, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                    plan.suggest(x, 2, z, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        plan.suggest(x + d.getStepX(), 2, z + d.getStepZ(), Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
                    }
                }
            }
        }
    }

    /** A stair down each side of the terrace, nine wide, between low walls. */
    private static void stairs(Plan plan, Palette p) {
        for (Direction out : Direction.Plane.HORIZONTAL) {
            BlockState stair = p.stairs().setValue(StairBlock.FACING, out.getOpposite());
            for (int k = 1; k <= STAIRS; k++) {
                int d = HALF + k;
                for (int t = -5; t <= 5; t++) {
                    int x = out.getStepX() * d + (out.getStepX() == 0 ? t : 0), z = out.getStepZ() * d + (out.getStepZ() == 0 ? t : 0);
                    if (Math.abs(t) == 5) {
                        for (int y = -1 - k; y <= 0 - k + 1; y++) plan.set(x, y, z, p.trim());
                    } else {
                        plan.set(x, -1 - k, z, stair);
                        plan.suggest(x, -2 - k, z, p.base());
                    }
                }
            }
        }
    }

    /** Pillars on a square round the courtyard, a beam over them and a cloister roof, and a gate over each path. */
    private static void colonnade(Plan plan, Palette p) {
        for (int x = -COLONNADE - 1; x <= COLONNADE + 1; x++) {
            for (int z = -COLONNADE - 1; z <= COLONNADE + 1; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                if (edge < COLONNADE - 1) continue;
                int along = Math.abs(x) == edge ? z : x;
                boolean gate = Math.abs(along) <= 3;
                // Over a gate the cloister roof stays open at the sides: nothing would hold it there.
                if (edge != COLONNADE && gate) continue;
                plan.set(x, 10, z, p.slab());
                if (edge != COLONNADE) continue;
                // Over a gate the beam is of the long-spanning trim: stone would not reach across.
                plan.set(x, 9, z, gate && Math.abs(along) <= 2 ? p.roofTrim() : p.beam());
                if (gate) {
                    // The gate: tall pillars either side, a lintel over the path.
                    if (Math.abs(along) == 3) for (int y = 0; y <= 9; y++) plan.set(x, y, z, p.pillar());
                    plan.set(x, 11, z, p.trim());
                    if (along == 0) plan.set(x, 12, z, p.roofTrim());
                    continue;
                }
                // Every third block: the beam never more than one from a pillar, the cloister roof one further out.
                if (Math.floorMod(along, 3) == 0) {
                    plan.set(x, 0, z, p.trim());
                    for (int y = 1; y <= 7; y++) plan.set(x, y, z, p.pillar());
                    plan.set(x, 8, z, p.trim());
                }
            }
        }
    }

    /** A tower at a corner of the colonnade: round with a pointed roof, an obelisk, or a battlemented keep. */
    private static void tower(Plan plan, Palette p, Theme theme, int cx, int cz) {
        switch (theme) {
            case PLAINS -> {
                for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                    double d = r(dx, dz);
                    for (int y = 0; y <= 16; y++) {
                        if (d < 3.5 && d >= 2.4) {
                            boolean window = y >= 9 && y <= 11 && (dx == 0 || dz == 0);
                            plan.set(cx + dx, y, cz + dz, window ? p.glass() : y == 0 || y == 16 ? p.trim() : p.wall());
                        } else if (d < 2.4) {
                            plan.set(cx + dx, y, cz + dz, y == 0 ? p.hallFloor() : Blocks.AIR.defaultBlockState());
                        }
                    }
                    for (int y = 17; y <= 25; y++) {
                        double cone = 4.2 * (1 - (y - 17) / 9.0);
                        if (d < cone && d >= cone - 1.4) plan.set(cx + dx, y, cz + dz, p.roof());
                    }
                }
                // A pillar up the middle carries the lamp and the tip of the roof: the cone alone would not close.
                for (int y = 1; y <= 23; y++) plan.set(cx, y, cz, p.pillar());
                plan.set(cx, 26, cz, p.roofTrim());
                plan.set(cx, 15, cz, p.light());
            }
            case DESERT -> {
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = 0; y <= 2; y++) {
                    plan.set(cx + dx, y, cz + dz, y == 2 ? p.trim() : p.wall());
                }
                for (int y = 3; y <= 22; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (y > 19 && (dx != 0 || dz != 0)) continue;
                    plan.set(cx + dx, y, cz + dz, y % 7 == 0 ? p.beam() : p.pillar());
                }
                plan.set(cx, 23, cz, p.roofTrim());
                plan.set(cx, 24, cz, p.light());
            }
            case STONY -> {
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                    boolean wall = Math.max(Math.abs(dx), Math.abs(dz)) == 3;
                    for (int y = 0; y <= 14; y++) {
                        if (wall) {
                            boolean slit = y >= 6 && y <= 9 && (dx == 0 || dz == 0);
                            plan.set(cx + dx, y, cz + dz, slit ? p.glass() : y == 14 ? p.trim() : p.wall());
                        } else {
                            plan.set(cx + dx, y, cz + dz, y == 0 ? p.hallFloor() : Blocks.AIR.defaultBlockState());
                        }
                    }
                    if (wall && (dx + dz) % 2 == 0) plan.set(cx + dx, 15, cz + dz, p.wall());
                    else if (!wall) plan.set(cx + dx, 14, cz + dz, p.beam());
                    if (dx == 0 && dz == 0) for (int y = 1; y <= 13; y++) plan.set(cx, y, cz, p.pillar());
                }
                plan.set(cx, 15, cz, p.light());
                plan.set(cx, 16, cz, p.roofTrim());
            }
        }
    }

    /** The round hall: its wall with windows and four arched doors, buttresses outside, and its inlaid floor. */
    private static void hall(Plan plan, Palette p) {
        for (int x = -HALL - 3; x <= HALL + 3; x++) {
            for (int z = -HALL - 3; z <= HALL + 3; z++) {
                double d = r(x, z);
                double angle = Math.toDegrees(Math.atan2(z, x));
                double toWindow = Math.abs(((angle - 22.5) % 45 + 45) % 45 - 22.5);
                // The floor, inlaid: rings, and rays out to the pedestals.
                if (d < HALL + 1.5) {
                    BlockState floor = p.hallFloor();
                    double toRay = Math.abs(((angle - 22.5) % 45 + 45 + 22.5) % 45 - 22.5);
                    if ((d >= 4 && d < 4.9) || (d >= 9 && d < 9.9)) floor = p.inlay();
                    else if (d > 3 && d < 9 && toRay * Math.PI / 180 * d < 0.55) floor = p.ray();
                    if (d >= 11 && d < 11.9 && toWindow > 21.5) floor = p.light();
                    plan.set(x, -1, z, floor);
                }
                // The wall.
                if (d >= HALL - 0.5 && d < HALL + 1) {
                    boolean door = (Math.abs(x) <= 2 || Math.abs(z) <= 2);
                    for (int y = 0; y <= 12; y++) {
                        BlockState block = y == 0 || y == 12 ? p.trim() : p.wall();
                        // The doorway: six high, under a lintel of the long-spanning trim (stone spans two blocks, it three).
                        if (door && y <= 5) block = null;
                        if (door && y == 6) block = p.roofTrim();
                        if (door && y == 7) block = p.trim();
                        if (!door && toWindow < 4 && y >= 3 && y <= 9) block = y == 3 || y == 9 ? p.trim() : p.glass();
                        if (block == null) plan.blocks.remove(new BlockPos(x, y, z));
                        else plan.set(x, y, z, block);
                    }
                }
                // Buttresses between the windows' bays, stepping down outwards.
                if (d >= HALL + 1 && d < HALL + 3.2 && toWindow > 20) {
                    int top = (int) Math.round(10 - (d - HALL - 1) * 3);
                    for (int y = 0; y <= top; y++) plan.set(x, y, z, y == top ? p.trim() : p.wall());
                }
            }
        }
    }

    /** The roof over the hall, open at the top: a dome, a stepped pyramid, or a spire. */
    private static void roof(Plan plan, Palette p, Theme theme) {
        for (int x = -HALL - 2; x <= HALL + 2; x++) {
            for (int z = -HALL - 2; z <= HALL + 2; z++) {
                double d = r(x, z);
                switch (theme) {
                    case PLAINS -> {
                        for (int y = 13; y <= 27; y++) {
                            double s = Math.sqrt(d * d + (y - 12) * (y - 12));
                            if (s < 12.4 || s >= 14) continue;
                            if (d < 3) continue;
                            plan.set(x, y, z, d < 4 ? p.roofTrim() : (y % 4 == 0 ? p.roofAccent() : p.roof()));
                        }
                    }
                    case DESERT -> {
                        for (int k = 0; k <= 11; k++) {
                            double outer = HALL + 1 - k;
                            if (outer <= 3) break;
                            if (d < outer && d >= outer - 1.5) plan.set(x, 13 + k, z, outer - 1 <= 4 ? p.roofTrim() : k % 2 == 1 ? p.roofAccent() : p.roof());
                        }
                    }
                    case STONY -> {
                        int height = 26;
                        for (int k = 0; k <= height; k++) {
                            double outer = (HALL + 1.2) * Math.pow(1 - k / (double) height, 1.25);
                            if (outer <= 2.6) {
                                if (d < 3.6 && d >= 2.6) plan.set(x, 13 + k, z, p.roofTrim());
                                break;
                            }
                            if (d < outer && d >= outer - 1.6) plan.set(x, 13 + k, z, k % 5 == 4 ? p.roofAccent() : p.roof());
                        }
                    }
                }
            }
        }
    }

    /** Inside: eight pillars, eight pedestals, the dais and the altar. */
    private static void chamber(Plan plan, Palette p) {
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(22.5 + i * 45);
            int px = (int) Math.round(Math.cos(a) * 10.5), pz = (int) Math.round(Math.sin(a) * 10.5);
            plan.set(px, 0, pz, p.trim());
            for (int y = 1; y <= 11; y++) plan.set(px, y, pz, p.pillar());
            plan.set(px, 12, pz, p.trim());
            plan.set(px, 6, pz, p.light());
            int sx = (int) Math.round(Math.cos(a) * PEDESTAL_RING), sz = (int) Math.round(Math.sin(a) * PEDESTAL_RING);
            plan.set(sx, 0, sz, RitualContent.PEDESTAL.defaultBlockState());
            plan.pedestals.add(new BlockPos(sx, 0, sz));
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (r(x, z) <= 2.6) plan.set(x, 0, z, Math.abs(x) == 2 || Math.abs(z) == 2 ? p.trim() : p.inlay());
        }
        plan.set(0, 1, 0, RitualContent.ALTAR.defaultBlockState());
        plan.altar = new BlockPos(0, 1, 0);
    }
}
