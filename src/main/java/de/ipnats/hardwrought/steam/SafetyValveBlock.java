package de.ipnats.hardwrought.steam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A spring-loaded safety valve, set on top of a boiler. Above its setting it lifts and blows the steam
 * off into the air with a roar, until the pressure is back under it. It is the one thing that stands
 * between a boiler and its bursting pressure; a boiler without one holds whatever its fire makes.
 *
 * <p>An empty hand turns the spring through its settings, {@code 4} to {@code 12} bar. Set above what
 * the gas line from the boiler holds, it saves the boiler and bursts the line; set above the boiler's
 * own limit, it saves nothing.
 */
public class SafetyValveBlock extends Block {
    /** The settings the spring can be turned to, in bar. */
    private static final int[] SETTINGS = {4, 6, 8, 10, 12};
    public static final IntegerProperty SETTING = IntegerProperty.create("setting", 0, SETTINGS.length - 1);
    public static final BooleanProperty LIFTING = BooleanProperty.create("lifting");
    /** The most it lets off in a tick, in units: more than any fire makes. */
    public static final double FLOW = 3.0;
    /** How far under its setting it blows the boiler down before it reseats, in bar. */
    public static final double BLOWDOWN = 0.3;

    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 12, 11);

    public SafetyValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SETTING, 2).setValue(LIFTING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SETTING, LIFTING);
    }

    /** The pressure it lifts at, in bar. */
    public static double bar(BlockState state) {
        return SETTINGS[state.getValue(SETTING)];
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** It sits on a boiler and nowhere else. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof BoilerBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
                                     BlockPos pos, net.minecraft.core.Direction direction, BlockPos neighbourPos,
                                     BlockState neighbour, RandomSource random) {
        return canSurvive(state, level, pos) ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockState turned = state.setValue(SETTING, (state.getValue(SETTING) + 1) % SETTINGS.length);
        level.setBlock(pos, turned, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);
        player.sendOverlayMessage(Component.translatable("message.hardwrought.safety_valve.set", (int) bar(turned)));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIFTING)) return;
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 0.05, 0.25 + random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.05);
        }
        if (random.nextInt(3) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.NOTE_BLOCK_FLUTE.value(),
                    SoundSource.BLOCKS, 0.5f, 1.9f, false);
        }
    }
}
