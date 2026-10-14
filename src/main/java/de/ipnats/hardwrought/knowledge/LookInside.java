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
        return new LookPayloads.Contents(pos, kinds, more, progress(entity), tanks(level, pos));
    }

    /**
     * The liquids and gases in it: everything a pipe or a gas line would find there, from any side.
     * Liquids are put together by kind — a group of tanks is one tank — and where one liquid fills
     * part of a group, the empty rest of it counts as its room. Each gas store is its own line.
     */
    private static List<LookPayloads.Contents.Tank> tanks(Level level, BlockPos pos) {
        List<LookPayloads.Contents.Tank> tanks = new ArrayList<>();
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Map<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant, long[]> liquids = new java.util.LinkedHashMap<>();
        long emptyRoom = 0;
        java.util.List<net.minecraft.core.Direction> sides = new ArrayList<>(List.of(net.minecraft.core.Direction.values()));
        for (net.minecraft.core.Direction side : sides) {
            var storage = net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level, pos, side);
            if (storage == null || !seen.add(storage)) continue;
            for (var view : storage) {
                if (!seen.add(view)) continue;
                long capacity = view.getCapacity() * 1000 / net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET;
                if (view.isResourceBlank() || view.getAmount() <= 0) {
                    emptyRoom += capacity;
                    continue;
                }
                long[] sum = liquids.computeIfAbsent(view.getResource(), ignored -> new long[2]);
                sum[0] += view.getAmount() * 1000 / net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET;
                sum[1] += capacity;
            }
        }
        if (liquids.size() == 1) liquids.values().iterator().next()[1] += emptyRoom;
        for (var entry : liquids.entrySet()) {
            var variant = entry.getKey();
            ItemStack icon = FluidIcons.liquid(variant.getFluid(), (int) Math.min(Integer.MAX_VALUE, entry.getValue()[0]));
            tanks.add(new LookPayloads.Contents.Tank(liquidName(variant, icon), icon, entry.getValue()[0], entry.getValue()[1], false));
        }
        if (liquids.isEmpty() && emptyRoom > 0) {
            tanks.add(new LookPayloads.Contents.Tank(net.minecraft.network.chat.Component.translatable("hud.hardwrought.look.empty"),
                    ItemStack.EMPTY, 0, emptyRoom, false));
        }
        for (net.minecraft.core.Direction side : sides) {
            var gas = de.ipnats.hardwrought.gasline.GasStorage.SIDED.find(level, pos, side);
            if (gas == null || gas.capacity() <= 0 || !seen.add(gas)) continue;
            var kind = gas.kind();
            long amount = Math.round(gas.amount());
            tanks.add(kind == null || amount <= 0
                    ? new LookPayloads.Contents.Tank(net.minecraft.network.chat.Component.translatable("hud.hardwrought.look.no_gas"),
                            ItemStack.EMPTY, 0, Math.round(gas.capacity()), true)
                    : new LookPayloads.Contents.Tank(net.minecraft.network.chat.Component.translatable(kind.translationKey()),
                            FluidIcons.gas(kind, amount), amount, Math.round(gas.capacity()), true));
        }
        return tanks;
    }

    /** A liquid's name; water says what kind of water it is. */
    private static net.minecraft.network.chat.Component liquidName(
            net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant variant, ItemStack icon) {
        var quality = de.ipnats.hardwrought.chemistry.SaltPan.quality(variant);
        if (quality != null && quality != de.ipnats.hardwrought.water.WaterQuality.FRESH) {
            return net.minecraft.network.chat.Component.translatable("water_quality.hardwrought." + quality.serializedName());
        }
        FluidIcons.Ingredient ingredient = FluidIcons.of(icon);
        return ingredient != null ? ingredient.name()
                : net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getName(variant);
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
