package de.ipnats.hardwrought.steam;

import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A steam whistle on a gas line: a bronze bell that sounds when it is pulled, by hand or by a redstone
 * signal, as long as there is steam behind it. The higher the pressure, the higher and louder its note.
 * Each blast takes {@value Entity#BLAST} unit of steam.
 */
public class SteamWhistleBlock extends Block implements EntityBlock {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 14, 11);

    public SteamWhistleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity whistle)) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) whistle.blow(server, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean moving) {
        if (!(level instanceof ServerLevel server)) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (powered && level.getBlockEntity(pos) instanceof Entity whistle) whistle.blow(server, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    public static class Entity extends BlockEntity {
        /** Steam one blast takes, in units. */
        public static final double BLAST = 1.0;
        /** It sounds from this pressure on. */
        public static final double MIN_BAR = 1.0;

        public final GasStore inlet = new GasStore(5, 20, true, kind -> kind == GasKind.STEAM, this::setChanged);

        public Entity(BlockPos pos, BlockState state) {
            super(SteamBlocks.STEAM_WHISTLE_ENTITY, pos, state);
        }

        /** One blast, if there is the steam for it. Whether it sounded. */
        public boolean blow(ServerLevel level, BlockPos pos) {
            double bar = inlet.pressure();
            if (bar < MIN_BAR || inlet.amount() < BLAST) return false;
            inlet.extract(BLAST);
            float pitch = (float) Math.min(1.6, 0.7 + bar / 20.0);
            float volume = (float) Math.min(3.0, 1.0 + bar / 5.0);
            // Three bells of a chime whistle, a third and a fifth apart.
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.BLOCKS, volume, pitch);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.BLOCKS, volume * 0.8f, pitch * 1.26f);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.BLOCKS, volume * 0.7f, pitch * 1.5f);
            level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.1, 0.1, 0.1, 0.08);
            return true;
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            inlet.save(output, "inlet");
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            inlet.load(input, "inlet");
        }
    }
}
