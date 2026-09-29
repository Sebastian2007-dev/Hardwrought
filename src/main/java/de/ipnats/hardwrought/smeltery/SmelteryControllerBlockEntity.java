package de.ipnats.hardwrought.smeltery;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.smithing.Heat;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The heart of a smeltery: its fuel, the pieces melting in it, the molten metal in its tank and how
 * hot it is.
 *
 * <p>The smeltery is the easy way to ingots — no anvil, no hammering — so it has to earn that with
 * heat. It melts a thing only at that thing's full melting point, far past the forge's working heat,
 * and gets there slowly, eating fuel:
 *
 * <table>
 *   <tr><th>fuel</th><th>alone</th><th>with a bellows at the wall</th></tr>
 *   <tr><td>coal, charcoal</td><td>{@value #COAL_C} °C</td><td>{@value #COAL_BLOWN_C} °C</td></tr>
 *   <tr><td>coke</td><td>{@value #COKE_C} °C</td><td>{@value #COKE_BLOWN_C} °C</td></tr>
 * </table>
 * Coal alone melts copper, tin and gold, never iron; iron, and so steel, wants a bellows or coke;
 * tungsten wants both.
 */
public class SmelteryControllerBlockEntity extends BlockEntity implements Container, ExtendedMenuProvider<BlockPos> {
    public static final int FUEL_SLOT = 0;
    public static final int MELT_SLOTS = 27;
    public static final int CONTAINER_SIZE = 1 + MELT_SLOTS;
    /** How much a block of tank holds: eight ingots. */
    public static final int PER_BLOCK = 8 * MoltenMetals.INGOT;
    public static final double COAL_C = 1250, COAL_BLOWN_C = 1650, COKE_C = 2000, COKE_BLOWN_C = 3600;
    /** Share of the gap to the fire's heat closed each tick: a large tank of brick warms slowly. */
    static final double RESPONSE = 0.004;
    /** Ticks a piece takes to melt once the bath is hot enough for it. */
    static final int MELT_TICKS = 100;
    /** Most of one alloy made per tick, in units of its ratio. */
    static final int ALLOY_UNITS_PER_TICK = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private final int[] progress = new int[MELT_SLOTS];
    private final LinkedHashMap<String, Integer> fluids = new LinkedHashMap<>();
    private double temperature = Heat.AMBIENT;
    private double target = Heat.AMBIENT;
    private int burnTicks;
    private int burnTotal;
    private boolean hotFuel;
    private SmelteryStructure.Found structure;
    private BlockPos tankMin, tankMax;
    private boolean dirty;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) Math.round(temperature);
                case 1 -> (int) Math.round(target);
                case 2 -> burnTotal <= 0 ? 0 : burnTicks * 1000 / burnTotal;
                case 3 -> capacity();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) { }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public SmelteryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SmelteryBlocks.CONTROLLER_ENTITY, pos, state);
    }

    // ---------------------------------------------------------------- what others read

    public Map<String, Integer> fluids() {
        return fluids;
    }

    public int fluidTotal() {
        int total = 0;
        for (int amount : fluids.values()) total += amount;
        return total;
    }

    public int capacity() {
        if (structure != null) return structure.volume() * PER_BLOCK;
        if (tankMin != null && tankMax != null) {
            return (tankMax.getX() - tankMin.getX() + 1) * (tankMax.getY() - tankMin.getY() + 1)
                    * (tankMax.getZ() - tankMin.getZ() + 1) * PER_BLOCK;
        }
        return 0;
    }

    public double temperature() {
        return temperature;
    }

    public BlockPos tankMin() {
        return tankMin;
    }

    public BlockPos tankMax() {
        return tankMax;
    }

    public boolean formed() {
        return tankMin != null;
    }

    public ContainerData data() {
        return data;
    }

    /** The lowest metal in the tank that can be cast, or null. A faucet always pours from the bottom. */
    public String bottomCastable() {
        for (var entry : fluids.entrySet()) {
            if (entry.getValue() > 0 && MoltenMetals.ingot(entry.getKey()) != null) return entry.getKey();
        }
        return null;
    }

    /** Takes up to this much of a metal out of the tank; returns how much it took. */
    public int drain(String material, int max) {
        Integer have = fluids.get(material);
        if (have == null || have <= 0) return 0;
        int taken = Math.min(have, max);
        if (have - taken <= 0) fluids.remove(material);
        else fluids.put(material, have - taken);
        dirty = true;
        return taken;
    }

    // ---------------------------------------------------------------- every tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmelteryControllerBlockEntity smeltery) {
        if (!(level instanceof ServerLevel server)) return;
        if (level.getGameTime() % 20 == 0 || smeltery.structure == null && level.getGameTime() % 20 == 10) smeltery.check(server);
        smeltery.heat();
        if (smeltery.structure != null) {
            smeltery.melt(server);
            smeltery.alloy(server);
        }
        if (smeltery.dirty && level.getGameTime() % 5 == 0) {
            smeltery.dirty = false;
            smeltery.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void check(ServerLevel level) {
        Direction facing = getBlockState().getValue(SmelteryControllerBlock.FACING);
        SmelteryStructure.Found found = SmelteryStructure.scan(level, worldPosition, facing);
        boolean was = structure != null;
        structure = found;
        if (found == null) {
            SmelteryStructure.unregister(level, worldPosition);
            if (was || tankMin != null) {
                tankMin = null;
                tankMax = null;
                dirty = true;
            }
        } else {
            SmelteryStructure.register(level, worldPosition, found.drains());
            if (!found.min().equals(tankMin) || !found.max().equals(tankMax)) {
                tankMin = found.min();
                tankMax = found.max();
                dirty = true;
            }
        }
        boolean lit = found != null && burnTicks > 0;
        if (getBlockState().getValue(SmelteryControllerBlock.LIT) != lit) {
            level.setBlock(worldPosition, getBlockState().setValue(SmelteryControllerBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    private boolean hasWork() {
        for (int i = 1; i < CONTAINER_SIZE; i++) if (!items.get(i).isEmpty()) return true;
        return fluids.size() > 1;
    }

    private void heat() {
        if (burnTicks <= 0 && structure != null && hasWork()) {
            ItemStack fuel = items.get(FUEL_SLOT);
            int ticks = fuelTicks(fuel);
            if (ticks > 0) {
                hotFuel = fuel.is(SmelteryBlocks.COKE);
                burnTicks = ticks;
                burnTotal = ticks;
                fuel.shrink(1);
                dirty = true;
            }
        }
        if (burnTicks > 0) burnTicks--;
        boolean blown = structure != null && structure.blown();
        target = structure == null || burnTicks <= 0 ? Heat.AMBIENT
                : hotFuel ? (blown ? COKE_BLOWN_C : COKE_C) : (blown ? COAL_BLOWN_C : COAL_C);
        double before = temperature;
        temperature += (target - temperature) * RESPONSE;
        if (Math.abs(temperature - before) > 5) dirty = true;
    }

    /** Ticks a fuel burns in the smeltery: longer than in a furnace — a smeltery is a hungry thing. */
    public static int fuelTicks(ItemStack stack) {
        if (stack.is(SmelteryBlocks.COKE)) return 2400;
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 1200;
        if (stack.is(Items.COAL_BLOCK)) return 10800;
        return 0;
    }

    private void melt(ServerLevel level) {
        var runtime = CoreLifecycle.find(level.getServer());
        if (runtime == null) return;
        int capacity = capacity();
        for (int i = 0; i < MELT_SLOTS; i++) {
            ItemStack stack = items.get(1 + i);
            MoltenMetals.Melt melt = stack.isEmpty() ? null : MoltenMetals.melt(stack.getItem());
            if (melt == null || temperature < MoltenMetals.meltingPoint(melt.material(), runtime.materials())
                    || fluidTotal() + melt.amount() > capacity) {
                progress[i] = 0;
                continue;
            }
            if (++progress[i] < MELT_TICKS) continue;
            progress[i] = 0;
            stack.shrink(1);
            fluids.merge(melt.material(), melt.amount(), Integer::sum);
            dirty = true;
        }
    }

    private void alloy(ServerLevel level) {
        for (MoltenMetals.Alloy alloy : MoltenMetals.ALLOYS) {
            if (temperature < alloy.celsius()) continue;
            int units = ALLOY_UNITS_PER_TICK;
            for (var input : alloy.inputs().entrySet()) {
                units = Math.min(units, fluids.getOrDefault(input.getKey(), 0) / input.getValue());
            }
            if (units <= 0) continue;
            for (var input : alloy.inputs().entrySet()) drain(input.getKey(), input.getValue() * units);
            fluids.merge(alloy.output(), alloy.outputRatio() * units, Integer::sum);
            dirty = true;
        }
    }

    /** Sets the bath's heat directly, for tests: a real smeltery takes minutes to come up. */
    public void setTemperatureForTesting(double celsius) {
        temperature = celsius;
    }

    /** Pours metal straight into the tank, for tests and screenshots. */
    public void addFluidForTesting(String material, int amount) {
        fluids.merge(material, amount, Integer::sum);
        dirty = true;
    }

    /** Melting progress of a slot, 0 to 1, for the screen. */
    public float progress(int slot) {
        return progress[slot] / (float) MELT_TICKS;
    }

    // ---------------------------------------------------------------- saving and syncing

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        fluids.clear();
        input.read("fluids", net.minecraft.util.ExtraCodecs.compactListCodec(FluidEntry.CODEC))
                .ifPresent(list -> list.forEach(entry -> fluids.put(entry.material(), entry.amount())));
        temperature = input.getDoubleOr("temperature", Heat.AMBIENT);
        burnTicks = input.getIntOr("burn", 0);
        burnTotal = input.getIntOr("burn_total", 0);
        hotFuel = input.getBooleanOr("hot_fuel", false);
        tankMin = input.read("tank_min", BlockPos.CODEC).orElse(null);
        tankMax = input.read("tank_max", BlockPos.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items, true);
        List<FluidEntry> list = new ArrayList<>();
        fluids.forEach((material, amount) -> list.add(new FluidEntry(material, amount)));
        output.store("fluids", net.minecraft.util.ExtraCodecs.compactListCodec(FluidEntry.CODEC), list);
        output.putDouble("temperature", temperature);
        output.putInt("burn", burnTicks);
        output.putInt("burn_total", burnTotal);
        output.putBoolean("hot_fuel", hotFuel);
        if (tankMin != null) output.store("tank_min", BlockPos.CODEC, tankMin);
        if (tankMax != null) output.store("tank_max", BlockPos.CODEC, tankMax);
    }

    /** One layer of the tank, as saved. */
    public record FluidEntry(String material, int amount) {
        static final com.mojang.serialization.Codec<FluidEntry> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                        com.mojang.serialization.Codec.STRING.fieldOf("material").forGetter(FluidEntry::material),
                        com.mojang.serialization.Codec.INT.fieldOf("amount").forGetter(FluidEntry::amount)
                ).apply(instance, FluidEntry::new));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) SmelteryStructure.unregister(level, worldPosition);
        super.setRemoved();
    }

    // ---------------------------------------------------------------- container

    public NonNullList<ItemStack> items() {
        return items;
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, count);
        if (!taken.isEmpty()) setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (slot > 0) progress[slot - 1] = 0;
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == FUEL_SLOT ? fuelTicks(stack) > 0 : MoltenMetals.melt(stack.getItem()) != null;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.hardwrought.smeltery");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SmelteryMenu(id, inventory, this, data, worldPosition);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }
}
