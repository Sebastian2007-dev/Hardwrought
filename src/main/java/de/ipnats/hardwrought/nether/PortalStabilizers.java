package de.ipnats.hardwrought.nether;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import de.ipnats.hardwrought.electricity.ElectricNodeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Portal stabilisers (see docs/endgame.md): set against a portal's frame, they hold it steady, and a
 * stable portal takes its traveller through with everything they carry and opens a way back
 * ({@link PortalStability}). Both kinds are made of what the Nether gives, so the first way in is
 * always the unstable one.
 *
 * <ul>
 *   <li><b>Provisional</b>: a magma heart in blackstone. It runs on the heat around it — the Nether's,
 *       or lava's or magma's right beside it — and on nothing else. The way home from a start over.
 *   <li><b>Stabiliser</b>: quartz and gold around a provisional one, lit by blaze rods. It runs on
 *       current, {@link #RUNNING_VOLTS} or more, taken like a machine's through a copper coil set
 *       against it, and works anywhere.
 * </ul>
 *
 * <p>Either shows that it is running: its heart glows, and it throws embers or sparks.
 */
public final class PortalStabilizers {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** The voltage a stabiliser needs to run: a water wheel's dynamo, or a battery. */
    public static final double RUNNING_VOLTS = 48.0;
    /** What a running stabiliser draws: a lamp's half ampere at its voltage. */
    public static final double OHMS = 96.0;
    /** How often a provisional stabiliser feels for heat, in ticks. */
    static final int HEAT_CHECK_TICKS = 40;

    public static final Block PROVISIONAL = register("provisional_portal_stabilizer", Provisional::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_BLACKSTONE).strength(3.0f)
                    .lightLevel(state -> state.getValue(ACTIVE) ? 9 : 0));
    public static final Block ELECTRIC = register("portal_stabilizer", Electric::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.QUARTZ_BLOCK).strength(3.0f).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ACTIVE) ? 12 : 0));
    public static final BlockEntityType<StabilizerEntity> ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hardwrought.id("portal_stabilizer"), new BlockEntityType<>(StabilizerEntity::new, Set.of(ELECTRIC)));

    private PortalStabilizers() { }

    /** Touching the class registers the blocks; this registers their items. */
    public static void initialize() {
        for (Block block : List.of(PROVISIONAL, ELECTRIC)) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
            BlockItem item = new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix());
            item.registerBlocks(Item.BY_BLOCK, item);
            Registry.register(BuiltInRegistries.ITEM, key, item);
        }
    }

    public static List<Item> items() {
        return List.of(PROVISIONAL.asItem(), ELECTRIC.asItem());
    }

    /** Whether this is a stabiliser that is running right now. */
    public static boolean running(BlockState state) {
        return (state.is(PROVISIONAL) || state.is(ELECTRIC)) && state.getValue(ACTIVE);
    }

    /** Whether there is heat enough here for a provisional stabiliser: the Nether, or lava or magma touching it. */
    public static boolean hot(Level level, BlockPos pos) {
        if (level.dimension() == Level.NETHER) return true;
        for (Direction side : Direction.values()) {
            BlockState next = level.getBlockState(pos.relative(side));
            if (next.is(Blocks.LAVA) || next.is(Blocks.MAGMA_BLOCK)) return true;
        }
        return false;
    }

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory,
                                                BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    /** Runs on the heat around it; looks again every two seconds, and whenever a neighbour changes. */
    public static final class Provisional extends Block {
        public Provisional(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ACTIVE);
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
            if (!previous.is(this)) level.scheduleTick(pos, this, 1);
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                       Orientation orientation, boolean moving) {
            level.scheduleTick(pos, this, 1);
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            feel(state, level, pos);
            level.scheduleTick(pos, this, HEAT_CHECK_TICKS);
        }

        /** Takes up, or loses, the heat around it. */
        public static void feel(BlockState state, ServerLevel level, BlockPos pos) {
            boolean hot = hot(level, pos);
            if (state.getValue(ACTIVE) == hot) return;
            level.setBlockAndUpdate(pos, state.setValue(ACTIVE, hot));
            level.playSound(null, pos, hot ? SoundEvents.FIRECHARGE_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 0.8f);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(ACTIVE)) return;
            Vec3 at = Vec3.atCenterOf(pos);
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SMALL_FLAME, at.x + (random.nextDouble() - 0.5) * 0.8, at.y + 0.55,
                        at.z + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
            }
            if (random.nextInt(8) == 0) level.addParticle(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 0, 0, 0);
            if (random.nextInt(4) == 0) towardsPortal(level, pos, random);
        }
    }

    /** Runs on current, through a copper coil set against it like a machine; the network says how much reaches it. */
    public static final class Electric extends ElectricNodeBlock {
        public Electric(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ACTIVE);
        }

        /** Where the network measures it from; the wires themselves go to its coils. */
        @Override
        public Vec3 terminal(BlockState state) {
            return new Vec3(0.5, 0.5, 0.5);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new StabilizerEntity(pos, state);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(ACTIVE)) return;
            Vec3 at = Vec3.atCenterOf(pos);
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + (random.nextDouble() - 0.5), at.y + (random.nextDouble() - 0.5),
                        at.z + (random.nextDouble() - 0.5), 0, 0, 0);
            }
            if (random.nextInt(40) == 0) {
                level.playLocalSound(at.x, at.y, at.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3f, 1.4f, false);
            }
            if (random.nextInt(3) == 0) towardsPortal(level, pos, random);
        }
    }

    /** A wisp drawn from a running stabiliser into a portal it touches, so the hold it has is seen. */
    static void towardsPortal(Level level, BlockPos pos, RandomSource random) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos portal = pos.offset(dx, dy, dz);
                    if (!level.getBlockState(portal).is(Blocks.NETHER_PORTAL)) continue;
                    Vec3 from = Vec3.atCenterOf(pos);
                    Vec3 to = Vec3.atCenterOf(portal).subtract(from);
                    level.addParticle(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, to.x * 0.1, to.y * 0.1, to.z * 0.1);
                    return;
                }
            }
        }
    }

    /** The stabiliser's point in the network: a resistance to ground, running from {@link #RUNNING_VOLTS}. */
    public static final class StabilizerEntity extends ElectricBlockEntity {
        public StabilizerEntity(BlockPos pos, BlockState state) {
            super(ENTITY, pos, state);
        }

        @Override
        public double conductance() {
            return 1.0 / OHMS;
        }

        @Override
        public boolean mindsRain() {
            return true;
        }

        /** No wire goes to it directly: like a machine, it is fed through a copper coil set against it. */
        @Override
        public boolean takesWires() {
            return false;
        }

        @Override
        protected void solved(ServerLevel level, double volts, double amps, boolean wet, double seconds) {
            super.solved(level, volts, amps, wet, seconds);
            boolean running = Math.abs(volts) >= RUNNING_VOLTS;
            BlockState state = getBlockState();
            if (state.getValue(ACTIVE) == running) return;
            level.setBlock(worldPosition, state.setValue(ACTIVE, running), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, running ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 0.6f, 1.4f);
        }
    }
}
