package de.ipnats.hardwrought.knowledge;

import de.ipnats.hardwrought.core.networking.LookPayloads;
import de.ipnats.hardwrought.machinery.ItemPipeBlockEntity;
import de.ipnats.hardwrought.mixin.AbstractFurnaceBlockEntityAccessor;
import de.ipnats.hardwrought.progression.DryingRackBlockEntity;
import de.ipnats.hardwrought.progression.KeptGridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.List;

/**
 * What the overlay may say is inside the thing a player is looking at.
 *
 * <p>Anything that holds items answers, without a line here per block: a chest, a furnace and every
 * machine of this mod are containers, and the three that keep their items some other way are asked
 * by name. What comes back is put together the way a player would count it — forty stone in three
 * heaps is forty stone — and each kind carries what this player knows of it, so an unfamiliar thing
 * in somebody else's chest is a shadow there as well.
 *
 * <p>Two things are never told. A chest whose loot has not been rolled yet has nothing in it to
 * report, and looking must not be what rolls it. And a container the player has not so much as
 * discovered is a block of question marks; what it holds would name it.
 */
public final class LookInside {
    /** A little past arm's length, squared: far enough for any reach, too near to scout a base. */
    private static final double RANGE_SQR = 10.0 * 10.0;

    private LookInside() { }

    public static LookPayloads.Contents of(ServerPlayer player, PlayerKnowledge known, BlockPos pos) {
        Level level = player.level();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > RANGE_SQR
                || !level.isLoaded(pos)) {
            return LookPayloads.Contents.none(pos);
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null || !known.level(BuiltInRegistries.ITEM.getKey(state.getBlock().asItem())).named()) {
            return LookPayloads.Contents.none(pos);
        }

        List<LookPayloads.Contents.Held> kinds = new ArrayList<>();
        int more = 0;
        for (ItemStack stack : stacks(level, pos, state, entity)) {
            if (stack.isEmpty()) continue;
            int same = -1;
            for (int index = 0; index < kinds.size() && same < 0; index++) {
                if (ItemStack.isSameItemSameComponents(kinds.get(index).stack(), stack)) same = index;
            }
            if (same >= 0) {
                LookPayloads.Contents.Held held = kinds.get(same);
                kinds.set(same, new LookPayloads.Contents.Held(held.stack(), held.count() + stack.getCount(), held.level()));
            } else {
                kinds.add(new LookPayloads.Contents.Held(stack.copyWithCount(1), stack.getCount(),
                        known.level(BuiltInRegistries.ITEM.getKey(stack.getItem())).ordinal()));
            }
        }
        if (kinds.size() > LookPayloads.Contents.MAX_STACKS) {
            more = kinds.size() - LookPayloads.Contents.MAX_STACKS;
            kinds = kinds.subList(0, LookPayloads.Contents.MAX_STACKS);
        }
        return new LookPayloads.Contents(pos, kinds, more, progress(entity));
    }

    private static List<ItemStack> stacks(Level level, BlockPos pos, BlockState state, BlockEntity entity) {
        if (sealed(entity)) return List.of();
        Container container = entity instanceof Container own ? own : null;
        // Both halves of a double chest are one chest to whoever looks at it.
        if (state.getBlock() instanceof ChestBlock chest && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockEntity other = level.getBlockEntity(ChestBlock.getConnectedBlockPos(pos, state));
            if (other != null && sealed(other)) return List.of();
            Container both = ChestBlock.getContainer(chest, state, level, pos, true);
            if (both != null) container = both;
        }
        if (container != null) {
            List<ItemStack> held = new ArrayList<>(container.getContainerSize());
            for (int slot = 0; slot < container.getContainerSize(); slot++) held.add(container.getItem(slot));
            return held;
        }
        if (entity instanceof DryingRackBlockEntity rack) return rack.items();
        if (entity instanceof KeptGridBlockEntity bench) return bench.contents();
        if (entity instanceof ItemPipeBlockEntity pipe) return List.of(pipe.carried());
        return List.of();
    }

    /** Loot not yet rolled, or a lock on the lid: either way there is nothing to be told. */
    private static boolean sealed(BlockEntity entity) {
        if (entity instanceof RandomizableContainer loot && loot.getLootTable() != null) return true;
        return entity instanceof BaseContainerBlockEntity lockable && lockable.isLocked();
    }

    private static float progress(BlockEntity entity) {
        if (!(entity instanceof AbstractFurnaceBlockEntity)) return -1.0F;
        ContainerData data = ((AbstractFurnaceBlockEntityAccessor) entity).hardwrought$data();
        int total = data.get(AbstractFurnaceBlockEntity.DATA_COOKING_TOTAL_TIME);
        int done = data.get(AbstractFurnaceBlockEntity.DATA_COOKING_PROGRESS);
        return total <= 0 || done <= 0 ? -1.0F : Math.clamp(done / (float) total, 0.0F, 1.0F);
    }
}
