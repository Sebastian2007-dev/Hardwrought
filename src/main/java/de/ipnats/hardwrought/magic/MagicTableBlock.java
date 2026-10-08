package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.progression.HewnWood;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.function.Consumer;

/** A two-block ritual table awakened from two matching timber blocks with a wand. */
public final class MagicTableBlock extends Block {
    /** Client hook, assigned by {@code MagicClient} without loading client classes on a server. */
    public static Consumer<InteractionHand> openTable = hand -> { };

    public static final EnumProperty<HewnWood> WOOD = EnumProperty.create("wood", HewnWood.class);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final VoxelShape LEFT_SHAPE = Shapes.or(
            Block.box(0, 12, 0, 16, 16, 16),
            Block.box(1, 0, 1, 5, 12, 5),
            Block.box(1, 0, 11, 5, 12, 15),
            Block.box(0, 4, 3, 16, 6, 13));
    private static final VoxelShape RIGHT_SHAPE = Shapes.or(
            Block.box(0, 12, 0, 16, 16, 16),
            Block.box(11, 0, 1, 15, 12, 5),
            Block.box(11, 0, 11, 15, 12, 15),
            Block.box(0, 4, 3, 16, 6, 13));

    public MagicTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WOOD, HewnWood.OAK)
                .setValue(FACING, Direction.NORTH).setValue(PART, Part.LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WOOD, FACING, PART);
    }

    /** Turns two identical adjacent timber blocks into one table. Called by a wand on both sides. */
    public static InteractionResult awaken(Level level, BlockPos clicked, Player player, InteractionHand hand) {
        BlockState source = level.getBlockState(clicked);
        HewnWood wood = woodOf(source);
        if (wood == null) return InteractionResult.PASS;

        Direction preferredFacing = player.getDirection().getOpposite();
        Direction[] candidates = {
                preferredFacing.getClockWise(), preferredFacing.getCounterClockWise(),
                preferredFacing, preferredFacing.getOpposite()
        };
        BlockPos other = null;
        Direction pairDirection = null;
        for (Direction direction : candidates) {
            BlockPos candidate = clicked.relative(direction);
            BlockState neighbor = level.getBlockState(candidate);
            if (neighbor.getBlock() == source.getBlock() && woodOf(neighbor) == wood) {
                other = candidate;
                pairDirection = direction;
                break;
            }
        }
        if (other == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        Direction facing = pairDirection.getAxis() != preferredFacing.getAxis()
                ? preferredFacing : pairDirection.getCounterClockWise();
        Direction right = facing.getClockWise();
        BlockPos leftPos = clicked.relative(right).equals(other) ? clicked : other;
        BlockPos rightPos = leftPos.equals(clicked) ? other : clicked;
        BlockState base = Magic.MAGIC_TABLE.defaultBlockState().setValue(WOOD, wood).setValue(FACING, facing);
        int quietUpdate = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(leftPos, base.setValue(PART, Part.LEFT), quietUpdate);
        level.setBlock(rightPos, base.setValue(PART, Part.RIGHT), quietUpdate);
        level.updateNeighborsAt(leftPos, Magic.MAGIC_TABLE);
        level.updateNeighborsAt(rightPos, Magic.MAGIC_TABLE);
        level.playSound(null, leftPos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 0.9f);
        level.playSound(null, rightPos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.25f);
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative()) {
            player.getItemInHand(hand).hurtAndBreak(1, serverPlayer, hand);
        }
        return InteractionResult.SUCCESS;
    }

    private static HewnWood woodOf(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return null;
        String path = id.getPath().replace("stripped_", "");
        for (HewnWood wood : HewnWood.values()) {
            String name = wood.getSerializedName();
            if (path.equals(name + "_log") || path.equals(name + "_wood") || path.equals(name + "_planks")
                    || path.equals(name + "_stem") || path.equals(name + "_hyphae")
                    || path.equals(name + "_block")) {
                return wood;
            }
        }
        return null;
    }

    private static BlockPos partner(BlockPos pos, BlockState state) {
        Direction right = state.getValue(FACING).getClockWise();
        return pos.relative(state.getValue(PART) == Part.LEFT ? right : right.getOpposite());
    }

    private static boolean matches(BlockState state, BlockState other) {
        return other.is(state.getBlock()) && other.getValue(FACING) == state.getValue(FACING)
                && other.getValue(WOOD) == state.getValue(WOOD)
                && other.getValue(PART) != state.getValue(PART);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState,
                                     RandomSource random) {
        if (neighborPos.equals(partner(pos, state)) && !matches(state, neighborState)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof WandItem)) return InteractionResult.PASS;
        if (level.isClientSide()) openTable.accept(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = state.getValue(PART) == Part.LEFT ? LEFT_SHAPE : RIGHT_SHAPE;
        return rotateShape(shape, state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    private static VoxelShape rotateShape(VoxelShape shape, Direction facing) {
        VoxelShape result = shape;
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        for (int i = 0; i < turns; i++) {
            VoxelShape previous = result;
            result = Shapes.empty();
            for (var box : previous.toAabbs()) {
                result = Shapes.or(result, Shapes.create(1 - box.maxZ, box.minY, box.minX,
                        1 - box.minZ, box.maxY, box.maxX));
            }
        }
        return result;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        String name = state.getValue(WOOD).getSerializedName() + "_planks";
        Block planks = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
        return planks == null ? List.of() : List.of(new ItemStack(planks, 4));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public enum Part implements StringRepresentable {
        LEFT("left"), RIGHT("right");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
