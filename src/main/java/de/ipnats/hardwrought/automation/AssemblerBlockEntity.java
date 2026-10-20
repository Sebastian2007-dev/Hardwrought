package de.ipnats.hardwrought.automation;

import de.ipnats.hardwrought.haven.ApplianceBlockEntity;
import de.ipnats.hardwrought.haven.HavenBlocks;
import de.ipnats.hardwrought.storage.StorageNetwork;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An assembler (stage E5): a frame, a gripper arm and a controller that make, from the parts put into
 * it, whatever the drawing in it shows — {@link #SECONDS} of work at full power each. Its three rows hold
 * the drawing, the parts and what it has made; hoppers feed it and draw from it.
 *
 * <p>Joined to a storage network by its tubes, it is a maker for the desk: a desk asked for something the
 * network has none of, and that this drawing makes, orders it here. Working an order, the assembler takes
 * the parts it lacks from the network, and puts what it makes back into the network for the desk. Short of
 * a part nowhere to be had, it waits.
 */
public class AssemblerBlockEntity extends ApplianceBlockEntity implements MenuProvider, StorageNetwork.Maker {
    public static final int SLOTS = 27;
    public static final double SECONDS = 8.0;

    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(AssemblerBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            AssemblerBlockEntity.this.setChanged();
        }
    };
    private double progress;
    /** Where an order came from, and how many are still owed. */
    private BlockPos desk;
    private int owed;
    private boolean waiting;

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(HavenBlocks.ASSEMBLER_ENTITY, pos, state);
    }

    public SimpleContainer items() {
        return items;
    }

    /** The plan of the drawing in it, or null. */
    public DrawingItem.Plan plan() {
        for (ItemStack stack : items.getItems()) {
            DrawingItem.Plan plan = DrawingItem.plan(stack);
            if (plan != null) return plan;
        }
        return null;
    }

    @Override
    public ItemStack makes() {
        DrawingItem.Plan plan = plan();
        return plan == null ? ItemStack.EMPTY : plan.result();
    }

    @Override
    public boolean order(BlockPos desk, int count) {
        if (plan() == null || count <= 0) return false;
        this.desk = desk.immutable();
        owed += count;
        electricDirty = true;
        setChanged();
        return true;
    }

    /** How many are still owed to a desk. */
    public int owed() {
        return owed;
    }

    /** Whether it is held up for want of a part. */
    public boolean waiting() {
        return waiting;
    }

    private List<Ingredient> ingredients(ServerLevel level, DrawingItem.Plan plan) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, plan.recipe());
        return level.getServer().getRecipeManager().byKey(key).map(holder -> holder.value().placementInfo().ingredients())
                .orElse(List.of());
    }

    /** For every part the drawing asks, a slot holding it, each slot used as often as it has items; null where one lacks. */
    private List<Integer> gather(List<Ingredient> parts) {
        int[] taken = new int[SLOTS];
        List<Integer> slots = new ArrayList<>();
        for (Ingredient part : parts) {
            int found = -1;
            for (int slot = 0; slot < SLOTS && found < 0; slot++) {
                ItemStack stack = items.getItem(slot);
                if (DrawingItem.plan(stack) == null && stack.getCount() > taken[slot] && part.test(stack)) found = slot;
            }
            if (found < 0) return null;
            taken[found]++;
            slots.add(found);
        }
        return slots;
    }

    /** Whether there is room for one more of what it makes. */
    private boolean room(ItemStack made) {
        for (ItemStack stack : items.getItems()) {
            if (stack.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(stack, made) && stack.getCount() + made.getCount() <= stack.getMaxStackSize()) return true;
        }
        return false;
    }

    @Override
    protected boolean wants() {
        if (!(level instanceof ServerLevel server)) return false;
        DrawingItem.Plan plan = plan();
        if (plan == null) return false;
        List<Ingredient> parts = ingredients(server, plan);
        if (parts.isEmpty()) return false;
        if (owed > 0) return !waiting;
        return gather(parts) != null && room(plan.result());
    }

    @Override
    protected void work(ServerLevel level, double power) {
        DrawingItem.Plan plan = plan();
        if (plan == null) return;
        List<Ingredient> parts = ingredients(level, plan);
        if (parts.isEmpty()) return;
        List<Integer> slots = gather(parts);
        if (slots == null && owed > 0) {
            fetch(level, parts);
            slots = gather(parts);
        }
        boolean stuck = slots == null;
        if (stuck != waiting) {
            waiting = stuck;
            electricDirty = true;
        }
        if (stuck || power <= 0.0) return;
        progress += power * (WORK_TICKS / 20.0);
        if (progress < SECONDS) return;
        progress = 0.0;
        for (int slot : slots) items.getItem(slot).shrink(1);
        ItemStack made = plan.result().copy();
        if (owed > 0 && desk != null) {
            made = StorageNetwork.find(level, desk).insert(made);
            owed--;
            if (owed == 0) desk = null;
        }
        if (!made.isEmpty()) {
            ItemStack left = items.addItem(made);
            if (!left.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, left);
        }
        items.setChanged();
        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4f, 1.6f);
        electricDirty = true;
    }

    /** Takes from the network what it lacks for one piece of the order: one of anything that fits each missing part. */
    private void fetch(ServerLevel level, List<Ingredient> parts) {
        if (desk == null) return;
        StorageNetwork network = StorageNetwork.find(level, desk);
        Map<ItemVariant, Integer> there = network.contents();
        int[] taken = new int[SLOTS];
        for (Ingredient part : parts) {
            boolean have = false;
            for (int slot = 0; slot < SLOTS && !have; slot++) {
                ItemStack stack = items.getItem(slot);
                if (DrawingItem.plan(stack) == null && stack.getCount() > taken[slot] && part.test(stack)) {
                    taken[slot]++;
                    have = true;
                }
            }
            if (have) continue;
            for (ItemVariant kind : there.keySet()) {
                ItemStack like = kind.toStack();
                if (!part.test(like)) continue;
                ItemStack got = network.extract(like, 1);
                if (got.isEmpty()) continue;
                ItemStack left = items.addItem(got);
                if (!left.isEmpty()) network.insert(left);
                break;
            }
        }
    }

    @Override
    protected Component state() {
        DrawingItem.Plan plan = plan();
        if (plan == null) return Component.translatable("message.hardwrought.meter.assembler.no_drawing");
        if (waiting) return Component.translatable("message.hardwrought.meter.assembler.waiting", plan.result().getHoverName());
        return Component.translatable("message.hardwrought.meter.assembler.making", plan.result().getHoverName(), owed);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return ChestMenu.threeRows(id, inventory, items);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, list);
        for (int slot = 0; slot < SLOTS; slot++) items.setItem(slot, list.get(slot));
        progress = input.getDoubleOr("progress", 0.0);
        owed = input.getIntOr("owed", 0);
        desk = input.read("desk", BlockPos.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < SLOTS; slot++) list.set(slot, items.getItem(slot));
        ContainerHelper.saveAllItems(output, list);
        output.putDouble("progress", progress);
        output.putInt("owed", owed);
        if (desk != null) output.store("desk", BlockPos.CODEC, desk);
    }
}
