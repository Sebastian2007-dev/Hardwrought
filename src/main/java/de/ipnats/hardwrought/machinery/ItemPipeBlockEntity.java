package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * What an item pipe is carrying just now: one batch, where it came in from, and how long until it
 * moves on.
 *
 * <p>Items come in from outside through Fabric's item storage, on any side, as long as the pipe has
 * room: a pipe holds one batch of its tier at a time. Once the batch has spent its time in the pipe,
 * the pipe looks along the connected pipes for the nearest block that will take it, and either puts
 * it in there, when that block is right beside it, or hands it to the next pipe on the way. A slow
 * pipe in a run holds back the fast ones behind it; a batch that nothing will take waits where it is.
 *
 * <p>The block the batch came in from is never offered it again, so a hopper that fills a pipe does
 * not get its items straight back.
 */
public class ItemPipeBlockEntity extends BlockEntity {
    /** How many pipes the search for a taker goes through at most. */
    private static final int SEARCH_LIMIT = 256;

    private ItemStack carried = ItemStack.EMPTY;
    /** The block outside the pipes the batch came in from, or null where it is not known. */
    private BlockPos origin;
    private int wait;
    private final Map<Direction, Inlet> inlets = new EnumMap<>(Direction.class);
    private final Inlet anySide = new Inlet(null);

    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PIPE, pos, state);
        for (Direction side : Direction.values()) inlets.put(side, new Inlet(side));
    }

    public ItemPipeTier tier() {
        return getBlockState().getBlock() instanceof ItemPipeBlock pipe ? pipe.tier() : ItemPipeTier.BRONZE;
    }

    /** What takes items into the pipe from the given side, or from nowhere in particular with null. */
    public Storage<ItemVariant> inlet(Direction side) {
        return side == null ? anySide : inlets.get(side);
    }

    public ItemStack carried() {
        return carried;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ItemPipeBlockEntity pipe) {
        if (pipe.carried.isEmpty()) return;
        if (pipe.wait > 0) {
            pipe.wait--;
            return;
        }
        pipe.passOn(level);
    }

    /** Where the batch goes next: straight into a block beside the pipe, or into the next pipe. */
    private record Step(Storage<ItemVariant> into, ItemPipeBlockEntity next) { }

    private void passOn(Level level) {
        Step step = route(level);
        if (step == null) {
            wait = tier().ticksPerBlock();
            return;
        }
        if (step.into() != null) {
            try (Transaction transaction = Transaction.openOuter()) {
                long moved = step.into().insert(ItemVariant.of(carried), carried.getCount(), transaction);
                transaction.commit();
                carried.shrink((int) moved);
            }
        } else {
            ItemPipeBlockEntity next = step.next();
            int moved = Math.min(carried.getCount(), next.room(carried));
            next.carried = carried.split(moved);
            next.origin = origin;
            next.wait = next.tier().ticksPerBlock();
            next.setChanged();
        }
        if (carried.isEmpty()) origin = null;
        wait = tier().ticksPerBlock();
        setChanged();
    }

    /** How many of these the pipe takes from another pipe: all it holds, as long as it is empty. */
    private int room(ItemStack stack) {
        return carried.isEmpty() ? Math.min(tier().batch(), stack.getMaxStackSize()) : 0;
    }

    /**
     * Searches along the pipes, nearest first, for a block that takes at least one of what this pipe
     * carries. Null while there is none, or while the next pipe on the way is still full.
     */
    private Step route(Level level) {
        ItemVariant variant = ItemVariant.of(carried);
        Map<BlockPos, BlockPos> firstStep = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        firstStep.put(worldPosition, null);
        queue.add(worldPosition);
        while (!queue.isEmpty() && firstStep.size() <= SEARCH_LIMIT) {
            BlockPos at = queue.poll();
            BlockState state = level.getBlockState(at);
            if (!(state.getBlock() instanceof ItemPipeBlock)) continue;
            for (Direction side : Direction.values()) {
                if (!state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side))) continue;
                BlockPos next = at.relative(side);
                if (firstStep.containsKey(next)) continue;
                if (level.getBlockState(next).getBlock() instanceof ItemPipeBlock) {
                    firstStep.put(next, at.equals(worldPosition) ? next : firstStep.get(at));
                    queue.add(next);
                    continue;
                }
                if (next.equals(origin)) continue;
                Storage<ItemVariant> taker = ItemStorage.SIDED.find(level, next, side.getOpposite());
                if (taker == null || !takes(taker, variant)) continue;
                if (at.equals(worldPosition)) return new Step(taker, null);
                return level.getBlockEntity(firstStep.get(at)) instanceof ItemPipeBlockEntity pipe
                        && pipe.room(carried) > 0 ? new Step(null, pipe) : null;
            }
        }
        return null;
    }

    private boolean takes(Storage<ItemVariant> taker, ItemVariant variant) {
        try (Transaction simulation = Transaction.openOuter()) {
            return taker.insert(variant, carried.getCount(), simulation) > 0;
        }
    }

    /** The pipe's one slot, as seen from one side: what comes in there came from that side. */
    private final class Inlet extends SingleStackStorage {
        private final Direction side;

        private Inlet(Direction side) {
            this.side = side;
        }

        @Override
        protected ItemStack getStack() {
            return carried;
        }

        @Override
        protected void setStack(ItemStack stack) {
            carried = stack;
        }

        @Override
        protected int getCapacity(ItemVariant variant) {
            return Math.min(tier().batch(), variant.toStack().getMaxStackSize());
        }

        @Override
        protected boolean canExtract(ItemVariant variant) {
            return false;
        }

        @Override
        protected void onFinalCommit() {
            origin = side == null ? null : worldPosition.relative(side);
            if (wait <= 0) wait = tier().ticksPerBlock();
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!carried.isEmpty()) output.store("carried", ItemStack.CODEC, carried);
        if (origin != null) output.store("origin", BlockPos.CODEC, origin);
        output.putInt("wait", wait);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        carried = input.read("carried", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        origin = input.read("origin", BlockPos.CODEC).orElse(null);
        wait = input.getIntOr("wait", 0);
    }

    /** A broken pipe spills what it was carrying. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !carried.isEmpty()) Block.popResource(level, pos, carried);
        carried = ItemStack.EMPTY;
        super.preRemoveSideEffects(pos, state);
    }
}
