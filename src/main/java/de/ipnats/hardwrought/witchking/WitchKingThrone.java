package de.ipnats.hardwrought.witchking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Witch King's throne, at the head of his hall. He sleeps on it until someone comes into the hall;
 * then it wakes — five seconds of souls drawn in and the hall humming — and he rises out of a rift
 * before it. Beaten, he is gone for good and the throne goes cold; left alone long enough, he goes back
 * to it and waits, whole again.
 *
 * <p>Cold, the throne is a way out and back in for good: sitting on it carries one up to its seat in the
 * overworld ({@link ThronePassage}).
 */
public class WitchKingThrone extends BaseEntityBlock {
    public static final BooleanProperty COLD = BooleanProperty.create("cold");
    /** How near someone must come to wake him, and how long the waking takes. */
    static final double WAKING_REACH = 15;
    static final int WAKING_TICKS = 100;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 8, 16), Block.box(1, 8, 0, 15, 28, 4));

    public WitchKingThrone(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLD, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLD);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(COLD)) return InteractionResult.PASS;
        // It faces south, down its hall.
        if (level instanceof ServerLevel server) SeatMount.sit(server, pos, 0, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, WitchKingContent.THRONE_ENTITY, (l, pos, s, seat) -> seat.tick((ServerLevel) l));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(COLD)) return;
        for (int side : new int[] {-1, 1}) {
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5 + side * 0.42, pos.getY() + 0.85, pos.getZ() + 0.5, 0, 0.02, 0);
            }
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 1.8, pos.getZ() + 0.15, 0, 0.05, 0);
        }
    }

    /** The middle of the hall the throne stands at the head of. */
    public static BlockPos home(BlockPos throne) {
        return throne.offset(0, -1, Citadel.THRONE_DISTANCE);
    }

    public static class Entity extends BlockEntity {
        public static final int SLEEPING = 0, WAKING = 1, AWAKE = 2, DEFEATED = 3;
        int state = SLEEPING;
        int waking;
        /** The seat in the overworld the cold throne leads up to, once it has one. */
        private @Nullable BlockPos seat;

        public Entity(BlockPos pos, BlockState state) {
            super(WitchKingContent.THRONE_ENTITY, pos, state);
        }

        public int state() {
            return state;
        }

        public @Nullable BlockPos seat() {
            return seat;
        }

        public void link(BlockPos seat) {
            this.seat = seat;
            setChanged();
        }

        void tick(ServerLevel level) {
            switch (state) {
                case SLEEPING -> {
                    if (level.getGameTime() % 10 != 0) return;
                    Vec3 home = Vec3.atCenterOf(home(worldPosition));
                    for (Player player : level.players()) {
                        if (!player.isSpectator() && !player.isCreative() && player.position().distanceTo(home) < WAKING_REACH) {
                            wake(level);
                            return;
                        }
                    }
                }
                case WAKING -> waking(level);
                default -> { }
            }
        }

        /** Someone has come into the hall. */
        public void wake(ServerLevel level) {
            if (state != SLEEPING) return;
            state = WAKING;
            waking = WAKING_TICKS;
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.4f);
            level.playSound(null, worldPosition, SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
            setChanged();
        }

        private void waking(ServerLevel level) {
            waking--;
            Vec3 seat = Vec3.atCenterOf(worldPosition).add(0, 0.6, 0);
            // Souls drawn in to the throne from all round the hall.
            if (waking % 2 == 0) {
                double angle = waking * 0.6;
                Vec3 from = seat.add(Math.cos(angle) * 6, 1 + Math.sin(waking * 0.3), Math.sin(angle) * 6);
                Vec3 to = seat.subtract(from).scale(0.12);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 0, to.x, to.y, to.z, 1);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, seat.x, seat.y + 1, seat.z, 4, 0.3, 0.6, 0.3, 0.1);
            }
            if (waking % 20 == 0) level.playSound(null, worldPosition, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3.0f, 0.5f);
            if (waking > 0) return;
            WitchKing king = WitchKingContent.WITCH_KING.create(level, EntitySpawnReason.TRIGGERED);
            if (king == null) return;
            Vec3 at = seat.add(0, 0.5, 2.5);
            king.snapTo(at.x, at.y, at.z, 0, 0);
            king.settle(home(worldPosition), worldPosition);
            level.addFreshEntity(king);
            king.enter();
            state = AWAKE;
            setChanged();
        }

        /** He has gone back onto his throne to wait. */
        public void sleep() {
            state = SLEEPING;
            setChanged();
        }

        /** Beaten: gone for good, and the throne cold. */
        public void defeated() {
            state = DEFEATED;
            if (level != null) level.setBlockAndUpdate(worldPosition, getBlockState().setValue(COLD, true));
            // Written down for the soul compasses, which have to know it of halls far away.
            if (level instanceof ServerLevel server) FallenKings.fall(server, home(worldPosition));
            setChanged();
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putInt("state", state == WAKING ? SLEEPING : state);
            if (seat != null) output.store("seat", BlockPos.CODEC, seat);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            state = input.getIntOr("state", SLEEPING);
            seat = input.read("seat", BlockPos.CODEC).orElse(null);
        }
    }
}
