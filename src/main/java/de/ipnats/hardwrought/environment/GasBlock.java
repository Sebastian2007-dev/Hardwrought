package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Gas in the world: a block that can be walked through and seen through, holding up to eight units
 * of gas, of one kind or several (see {@link Gas}).
 *
 * <p>It moves like a liquid that does not know which way is down until it is told by its weight.
 * Each gas goes its own way on every step:
 * <ol>
 *   <li>With its drift — down for carbon dioxide, up for methane and carbon monoxide — into air, into
 *       a gas block with room, or past a lighter (or heavier) gas that is in its way, one unit for one.
 *   <li>Where that is closed, sideways toward the nearest place within four blocks where it could go
 *       on, the way water finds the edge of a ledge.
 *   <li>Where there is none, it joins the fuller of its neighbours: a unit beside seven makes eight,
 *       two beside seven make eight and leave one. Gas gathers into full blocks rather than spreading
 *       into a thin film, and a settled pocket stops ticking.
 * </ol>
 *
 * <p>Nothing simply disappears. Light gas that reaches open sky rises to the clouds and gathers
 * under them in a layer (see {@link #cloudCeiling}); above the clouds there is nothing left to stop
 * it. Carbon dioxide is taken up by the leaves and plants near it, and left lying it breaks down over
 * about a day into a lighter form that rises; carbon monoxide slowly burns in the air to carbon
 * dioxide. What gathers under the clouds the rain washes out, and where the layer is thick the rain
 * that brings it down is acid (see {@link AcidRain}).
 */
public class GasBlock extends Block {
    private static final Map<Gas, IntegerProperty> PROPERTIES = new HashMap<>();
    public static final IntegerProperty CARBON_DIOXIDE = register(Gas.CARBON_DIOXIDE);
    public static final IntegerProperty DECAYED_CARBON_DIOXIDE = register(Gas.DECAYED_CARBON_DIOXIDE);
    public static final IntegerProperty CARBON_MONOXIDE = register(Gas.CARBON_MONOXIDE);
    public static final IntegerProperty METHANE = register(Gas.METHANE);

    /** Ticks between two steps of moving gas. */
    public static final int STEP = 8;
    /** Rising gas moves in small, frequent steps so that a plume climbs instead of jumping. */
    public static final int RISING_STEP = 2;
    /** How far sideways gas looks for a way on. */
    private static final int SLOPE_SEARCH = 4;
    /**
     * The chance for each unit of carbon dioxide, on each random tick, to break down into the light
     * form. A block gets a random tick about every 1365 ticks, so a unit lasts about a day: 24000.
     */
    public static final double DECAY_CHANCE = 1365.0 / 24000.0;
    /** How many blocks below the clouds still count as the cloud layer, for the rain to wash out. */
    public static final int CLOUD_LAYER_DEPTH = 4;
    /** How far a leaf or a plant reaches to take up carbon dioxide. */
    private static final int PLANT_REACH = 2;

    private static IntegerProperty register(Gas gas) {
        IntegerProperty property = IntegerProperty.create(gas.id(), 0, Gas.CAPACITY);
        PROPERTIES.put(gas, property);
        return property;
    }

    public static IntegerProperty property(Gas gas) {
        return PROPERTIES.get(gas);
    }

    public GasBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CARBON_DIOXIDE, 0).setValue(CARBON_MONOXIDE, 0)
                .setValue(METHANE, 0).setValue(DECAYED_CARBON_DIOXIDE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CARBON_DIOXIDE, DECAYED_CARBON_DIOXIDE, CARBON_MONOXIDE, METHANE);
    }

    // ---- what it is to everything else: nothing solid ----------------------------------------------

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    /** Neighbouring gas is one body of gas: no faces drawn between them. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.getBlock() instanceof GasBlock || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        // Every gas can end up under the clouds for the rain to find, so every gas block ticks.
        return true;
    }

    // ---- moving ------------------------------------------------------------------------------------

    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState old,
                           boolean moved) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, tickDelay(state));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor,
                                     RandomSource random) {
        ticks.scheduleTick(pos, this, tickDelay(state));
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Gases.total(state) == 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (GasIgnition.explosive(state) && GasIgnition.flameBeside(level, pos)) {
            GasIgnition.ignite(level, pos);
            return;
        }
        boolean busy = false;
        for (Gas gas : Gas.values()) {
            if (Gases.units(level.getBlockState(pos), gas) == 0) continue;
            if (gas.sluggish() && random.nextInt(3) != 0) {
                busy = true;
                continue;
            }
            busy |= step(level, pos, gas);
        }
        BlockState now = level.getBlockState(pos);
        if (Gases.isGas(now) && GasIgnition.explosive(now) && GasIgnition.flameBeside(level, pos)) {
            GasIgnition.ignite(level, pos);
            return;
        }
        if (busy && Gases.isGas(now)) level.scheduleTick(pos, this, tickDelay(now));
    }

    /** One step of one gas. Returns whether anything moved. */
    static boolean step(ServerLevel level, BlockPos pos, Gas gas) {
        int units = Gases.units(level.getBlockState(pos), gas);
        if (units == 0) return false;
        Direction drift = gas.drift();

        // Light gas under open sky still passes through every block on its way to the clouds. It used
        // to teleport straight to the cloud layer, which made rising gas visibly jump. Above the
        // clouds there is nothing left to hold it and it rises away.
        int ceiling = cloudCeiling(level);
        if (drift == Direction.UP && openSky(level, pos)) {
            if (pos.getY() > ceiling) {
                level.setBlock(pos, Gases.with(level.getBlockState(pos), gas, -units), Block.UPDATE_ALL);
                return true;
            }
            // At the clouds it goes no higher; it only gathers with what is already there.
            if (pos.getY() >= ceiling) return gather(level, pos, gas, units);
        }
        BlockPos onward = pos.relative(drift);
        // Rising into a hood, it is drawn up the flue and comes out at its outlet.
        if (level.getBlockState(onward).getBlock() instanceof de.ipnats.hardwrought.smithing.ForgeHoodBlock) {
            BlockPos outlet = Flues.outlet(level, onward);
            if (outlet != null && !outlet.equals(pos)) {
                int left = Gases.emit(level, outlet, gas, units);
                if (left < units) {
                    level.setBlock(pos, Gases.with(level.getBlockState(pos), gas, -(units - left)), Block.UPDATE_ALL);
                    return true;
                }
            }
        }
        // A rising pocket releases one unit per short step. Adjacent densities therefore blend into
        // a continuous plume instead of the complete gas block hopping upward eight ticks at a time.
        int driftAmount = drift == Direction.UP ? 1 : units;
        if (move(level, pos, onward, gas, driftAmount, true) > 0) return true;

        Direction toward = slope(level, pos, gas);
        if (toward != null && move(level, pos, pos.relative(toward), gas, units, false) > 0) return true;

        return gather(level, pos, gas, units);
    }

    private static int tickDelay(BlockState state) {
        for (Gas gas : Gas.values()) {
            if (gas.drift() == Direction.UP && Gases.units(state, gas) > 0) return RISING_STEP;
        }
        return STEP;
    }

    /**
     * Moves up to this much of a gas from one place to the next. Where the next is full and holds a
     * gas that belongs on this side of it, one unit of each changes place. Returns how much moved.
     */
    public static int move(ServerLevel level, BlockPos from, BlockPos to, Gas gas, int amount, boolean mayTrade) {
        if (!level.isInWorldBounds(to) || !level.isLoaded(to)) return 0;
        BlockState source = level.getBlockState(from);
        BlockState target = level.getBlockState(to);
        int moved = Math.min(amount, Gases.room(target));
        if (moved > 0) {
            level.setBlock(to, Gases.with(target, gas, moved), Block.UPDATE_ALL);
            level.setBlock(from, Gases.with(source, gas, -moved), Block.UPDATE_ALL);
            return moved;
        }
        if (!mayTrade || !Gases.isGas(target)) return 0;
        for (Gas other : Gas.values()) {
            if (other == gas || Gases.units(target, other) == 0) continue;
            boolean belongsPast = gas.drift() == Direction.DOWN ? gas.heavierThan(other) : other.heavierThan(gas);
            if (!belongsPast) continue;
            level.setBlock(to, Gases.with(Gases.with(target, other, -1), gas, 1), Block.UPDATE_ALL);
            level.setBlock(from, Gases.with(Gases.with(source, gas, -1), other, 1), Block.UPDATE_ALL);
            return 1;
        }
        return 0;
    }

    /**
     * The first step sideways toward the nearest place, within a few blocks along the same level, from
     * which this gas could go on with its drift. Null where there is none, or where the first step
     * has no room for it.
     */
    private static Direction slope(ServerLevel level, BlockPos pos, Gas gas) {
        Direction drift = gas.drift();
        record Node(BlockPos pos, Direction first, int distance) { }
        ArrayDeque<Node> queue = new ArrayDeque<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        seen.add(pos.asLong());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            if (!level.isLoaded(next)) continue;
            BlockState state = level.getBlockState(next);
            if (Gases.room(state) == 0) continue;
            seen.add(next.asLong());
            queue.add(new Node(next, side, 1));
        }
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            BlockPos beyond = node.pos.relative(drift);
            if (level.isInWorldBounds(beyond) && level.isLoaded(beyond) && accepts(level.getBlockState(beyond), gas)) {
                return node.first;
            }
            if (node.distance >= SLOPE_SEARCH) continue;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = node.pos.relative(side);
                if (!seen.add(next.asLong()) || !level.isLoaded(next)) continue;
                if (!Gases.passable(level.getBlockState(next))) continue;
                queue.add(new Node(next, node.first, node.distance + 1));
            }
        }
        return null;
    }

    /** Whether this gas could go into a place: room there, or something there it would sink or rise past. */
    private static boolean accepts(BlockState state, Gas gas) {
        if (Gases.room(state) > 0) return true;
        if (!Gases.isGas(state)) return false;
        for (Gas other : Gas.values()) {
            if (other == gas || Gases.units(state, other) == 0) continue;
            if (gas.drift() == Direction.DOWN ? gas.heavierThan(other) : other.heavierThan(gas)) return true;
        }
        return false;
    }

    /**
     * Gas that can go nowhere joins its fullest neighbour at the same level that has room: the larger
     * pocket takes in the smaller. Between two equal ones the choice is fixed by position, so they
     * never trade back and forth.
     */
    private static boolean gather(ServerLevel level, BlockPos pos, Gas gas, int units) {
        int mine = Gases.total(level.getBlockState(pos));
        BlockPos best = null;
        int bestTotal = -1;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            if (!level.isLoaded(next)) continue;
            BlockState state = level.getBlockState(next);
            if (!Gases.isGas(state) || Gases.room(state) == 0) continue;
            int total = Gases.total(state);
            boolean fuller = total > mine || (total == mine && next.asLong() < pos.asLong());
            if (fuller && total > bestTotal) {
                best = next;
                bestTotal = total;
            }
        }
        return best != null && move(level, pos, best, gas, units, false) > 0;
    }

    /**
     * The highest place gas can be below the clouds of this level: where the cloud layer gathers. A
     * level without clouds has none, and this is above its top.
     */
    public static int cloudCeiling(net.minecraft.world.level.Level level) {
        float clouds = level.environmentAttributes()
                .getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_HEIGHT);
        int ceiling = (int) Math.floor(clouds) - 1;
        return ceiling <= level.getMinY() || ceiling > level.getMaxY() ? Integer.MAX_VALUE : ceiling;
    }

    private static boolean openSky(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    // ---- taken up, turned over ---------------------------------------------------------------------

    /**
     * Leaves and plants take up carbon dioxide from the gas around them, and carbon monoxide slowly
     * burns in the air to carbon dioxide. Both are slow, which is what random ticks are.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int ceiling = cloudCeiling(level);
        if (pos.getY() > ceiling - CLOUD_LAYER_DEPTH && pos.getY() <= ceiling) {
            // Under the clouds nothing grows and nothing breaks down; only the rain takes it away.
            AcidRain.washOut(level, pos, random);
            return;
        }
        if (state.getValue(CARBON_MONOXIDE) > 0 && random.nextInt(3) == 0) {
            state = Gases.with(Gases.with(state, Gas.CARBON_MONOXIDE, -1), Gas.CARBON_DIOXIDE, 1);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        int decayed = 0;
        for (int unit = 0; unit < Gases.units(state, Gas.CARBON_DIOXIDE); unit++) {
            if (random.nextDouble() < DECAY_CHANCE) decayed++;
        }
        if (decayed > 0) {
            state = Gases.with(Gases.with(state, Gas.CARBON_DIOXIDE, -decayed), Gas.DECAYED_CARBON_DIOXIDE, decayed);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        boolean carbon = Gases.units(state, Gas.CARBON_DIOXIDE) + Gases.units(state, Gas.DECAYED_CARBON_DIOXIDE) > 0;
        if (carbon && plantNear(level, pos)) {
            for (Gas gas : new Gas[] {Gas.CARBON_DIOXIDE, Gas.DECAYED_CARBON_DIOXIDE}) {
                if (Gases.units(state, gas) == 0) continue;
                level.setBlock(pos, Gases.with(state, gas, -1), Block.UPDATE_ALL);
                break;
            }
        }
    }

    /** Whether anything green grows close enough to take up carbon dioxide from here. */
    public static boolean plantNear(ServerLevel level, BlockPos pos) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-PLANT_REACH, -PLANT_REACH, -PLANT_REACH),
                pos.offset(PLANT_REACH, PLANT_REACH, PLANT_REACH))) {
            if (!level.isLoaded(near)) continue;
            if (green(level.getBlockState(near))) return true;
        }
        return false;
    }

    private static boolean green(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CROPS) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.VINE)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.KELP) || state.is(Blocks.SEAGRASS);
    }
}
