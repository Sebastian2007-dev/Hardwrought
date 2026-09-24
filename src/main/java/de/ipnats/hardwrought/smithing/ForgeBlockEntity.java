package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.machinery.Driveline;
import de.ipnats.hardwrought.metallurgy.Smelting;
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
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * The smith's hearth: a bed of burning coal, a place for fuel and places for the pieces lying in it.
 *
 * <p>It gets hotter the better it is built, and that is the one way to reach the heat the hard metals
 * need. On its own it holds about 1300 °C — iron, not much more. Air driven through it by a bellows
 * on a turning shaft takes it to 2000 °C; a lining of refractory brick keeps that heat in and takes it
 * to 3500 °C, as hot as anything in the game gets.
 *
 * <p>A forge is slow and patient. Coal lasts four times as long as in a furnace, the fire comes up
 * slowly and the pieces follow it slowly; but whatever lies in the forge keeps its heat for as long as
 * it lies there, so a smith can take one piece out at a time and work it while the rest wait.
 *
 * <p>How many places there are depends on how big the forge is built (see {@link ForgeMultiblock}):
 * one fuel and one metal place on its own, one and four as a 2×2×2, four and nine as a 3×3×3. The
 * places of a joined forge all live in its controller; the other blocks hold nothing.
 */
public class ForgeBlockEntity extends BlockEntity implements Container, ExtendedMenuProvider<Integer> {
    public static final int FUEL_SLOTS = 4;
    public static final int METAL_SLOTS = 9;
    public static final int FIRST_METAL = FUEL_SLOTS;
    public static final int CONTAINER_SIZE = FUEL_SLOTS + METAL_SLOTS;

    public static final double BASE_C = 1300;
    public static final double BELLOWS_C = 2000;
    public static final double LINED_C = 1600;
    public static final double LINED_BELLOWS_C = 3500;
    /** How much of the gap to its target the fire closes each tick. */
    private static final double FIRE_RESPONSE = 0.005;
    /** How much of the gap to the fire a piece closes on each heating pass. */
    private static final double PIECE_RESPONSE = 0.03;
    private static final int HEATING_INTERVAL = 10;
    /**
     * How far ahead a held piece's heat is stamped. Longer than a heating pass, so that neither the
     * server nor a client whose clock runs a little ahead ever sees it begin to cool while it lies here.
     */
    private static final int HOLD_AHEAD = 60;
    /** How much longer an item of fuel lasts here than in a furnace. */
    public static final int FUEL_STRETCH = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private int burnTicks;
    private int burnTotal;
    private double temperature = Heat.AMBIENT;
    private int lastTarget = (int) Heat.AMBIENT;
    private BlockPos multiblockController;
    private byte multiblockSize;

    /** What the screen shows: the fire's heat, what it is heading for, and how much of the coal is left. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) Math.round(temperature);
                case 1 -> lastTarget;
                case 2 -> burnTotal <= 0 ? 0 : (int) ((long) burnTicks * 1000 / burnTotal);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) { }

        @Override
        public int getCount() {
            return ForgeMenu.DATA_COUNT;
        }
    };

    public ForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORGE, pos, state);
    }

    // ---- layout -------------------------------------------------------------------------------

    /** 0 on its own, 1 for the 2×2×2, 2 for the 3×3×3. */
    public int layout() {
        return isMultiblockPart() ? multiblockSize : 0;
    }

    public static int fuelSlots(int layout) {
        return layout == 2 ? 4 : 1;
    }

    public static int metalSlots(int layout) {
        return layout == 2 ? 9 : layout == 1 ? 4 : 1;
    }

    /** Whether this container slot is in use for a forge of this layout. */
    public static boolean activeSlot(int slot, int layout) {
        if (slot < FIRST_METAL) return slot < fuelSlots(layout);
        return slot - FIRST_METAL < metalSlots(layout);
    }

    /** Metal a forge will take: anything the anvil works, but not the powders that are cast. */
    public static boolean acceptsMetal(ItemStack stack) {
        return !stack.isEmpty() && Smithing.isForgeMetal(stack) && !Smelting.isCast(stack.getItem());
    }

    /** The block that holds this forge's fire and places: the controller of a joined forge, or itself. */
    public ForgeBlockEntity working() {
        ForgeBlockEntity controller = controllerEntity();
        return controller == null ? this : controller;
    }

    private boolean holdsInventory() {
        return !isMultiblockPart() || worldPosition.equals(multiblockController);
    }

    // ---- fire ---------------------------------------------------------------------------------

    public NonNullList<ItemStack> items() {
        return items;
    }

    public double temperature() {
        return working().temperature;
    }

    public int burnTicks() {
        return working().burnTicks;
    }

    /** What the fire is heading for: nothing without fuel, more with a bellows and a lining. */
    public double target(Level level, BlockPos pos, BlockState state) {
        ForgeMultiblock.Structure structure = ForgeMultiblock.getOrForm(level, pos);
        ForgeBlockEntity controller = structure == null ? this : ForgeMultiblock.controller(level, structure);
        if (controller == null || controller.burnTicks <= 0) return Heat.AMBIENT;
        boolean bellows = structure == null ? blown(level, pos) : ForgeMultiblock.blown(level, structure);
        boolean lined = structure == null ? state.getValue(ForgeBlock.LINED) : ForgeMultiblock.isLined(level, structure);
        if (lined) return bellows ? LINED_BELLOWS_C : LINED_C;
        return bellows ? BELLOWS_C : BASE_C;
    }

    /** Whether a bellows beside the hearth is being worked, by a crank or a turning line. */
    public static boolean blown(Level level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = pos.relative(side);
            if (level.getBlockState(beside).getBlock() instanceof BellowsBlock
                    && Driveline.isDrivenInto(level, beside)) {
                return true;
            }
        }
        return false;
    }

    /** Lights the fire directly, for tests and commands: as if this many ticks of fuel had caught. */
    public void addFuel(int ticks) {
        ForgeBlockEntity target = working();
        target.burnTicks += ticks;
        target.burnTotal = Math.max(target.burnTotal, target.burnTicks);
        target.changed();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ForgeBlockEntity forge) {
        if (!(level instanceof ServerLevel server)) return;
        // A part of a larger forge does nothing on its own; the controller works the whole fire.
        if (forge.isMultiblockPart() && !pos.equals(forge.multiblockController())) return;
        // Looking for a structure to join is only worth doing now and then, not every tick.
        ForgeMultiblock.Structure structure = forge.isMultiblockPart() || level.getGameTime() % 20 == 0
                ? ForgeMultiblock.getOrForm(level, pos) : null;
        if (structure != null && !pos.equals(structure.controller())) return;
        if (structure != null && level.getGameTime() % 20 == 0 && !ForgeMultiblock.matches(level, structure)) {
            ForgeMultiblock.dissolve(level, structure);
            structure = null;
        } else if (structure != null && level.getGameTime() % 20 == 0) {
            ForgeMultiblock.applyParts(level, structure);
        }
        int layout = forge.layout();

        if (forge.burnTicks > 0) forge.burnTicks--;
        if (forge.burnTicks <= 0 && forge.hasMetal(layout)) forge.catchFuel(layout);
        forge.lastTarget = (int) Math.round(forge.target(level, pos, level.getBlockState(pos)));
        forge.temperature += (forge.lastTarget - forge.temperature) * FIRE_RESPONSE;

        boolean lit = forge.burnTicks > 0;
        BlockState now = level.getBlockState(pos);
        if (structure != null) {
            ForgeMultiblock.syncLit(level, structure, lit);
        } else if (now.getBlock() instanceof ForgeBlock && now.getValue(ForgeBlock.LIT) != lit) {
            level.setBlock(pos, now.setValue(ForgeBlock.LIT, lit), Block.UPDATE_ALL);
        }
        if (level.getGameTime() % HEATING_INTERVAL == 0) forge.heatPieces(server, layout);
        if (level.getGameTime() % 20 == 0) {
            forge.ejectUnused(level, pos, layout);
            forge.setChanged();
        }
    }

    private boolean hasMetal(int layout) {
        for (int i = 0; i < metalSlots(layout); i++) {
            if (!items.get(FIRST_METAL + i).isEmpty()) return true;
        }
        return false;
    }

    /** Takes one item of fuel into the fire. Only when there is metal to heat: an empty hearth waits. */
    private void catchFuel(int layout) {
        for (int i = 0; i < fuelSlots(layout); i++) {
            ItemStack fuel = items.get(i);
            int value = ForgeBlock.fuelValue(fuel);
            if (value <= 0) continue;
            burnTicks = burnTotal = value * FUEL_STRETCH;
            fuel.shrink(1);
            changed();
            return;
        }
    }

    /**
     * Brings every piece a step closer to the fire's heat, never past the top of its working range,
     * and keeps whatever it already has: nothing lying in a forge cools.
     */
    private void heatPieces(ServerLevel level, int layout) {
        var runtime = CoreLifecycle.find(level.getServer());
        if (runtime == null) return;
        long now = level.getGameTime();
        boolean moved = false;
        for (int i = 0; i < metalSlots(layout); i++) {
            ItemStack piece = items.get(FIRST_METAL + i);
            if (piece.isEmpty()) continue;
            Heat heat = piece.get(ModDataComponents.HEAT);
            double current = heat == null ? Heat.AMBIENT : heat.celsius();
            double next = current;
            if (temperature > current) {
                OptionalDouble melting = Smithing.meltingPoint(piece.getItem(), runtime.materials());
                double ceiling = melting.isPresent() ? melting.getAsDouble() * Smithing.WORKING_MAX : temperature;
                next = Math.max(current, Math.min(ceiling, current + (temperature - current) * PIECE_RESPONSE));
            }
            // Stamped until the next pass: a piece's heat only starts falling after its stamp, so
            // until then it holds exactly, and taken out it starts cooling from where it was.
            long until = now + HOLD_AHEAD;
            if (next > current + 0.5) {
                Smithing.heat(piece, next, now, runtime.materials());
                piece.set(ModDataComponents.HEAT, new Heat((float) next, until));
                moved = true;
            } else if (heat != null && heat.since() < now + HOLD_AHEAD / 2) {
                piece.set(ModDataComponents.HEAT, new Heat(heat.celsius(), until));
                moved = true;
            }
        }
        if (moved) changed();
    }

    /** Anything in a place this forge no longer has — it was taken apart, say — is put out beside it. */
    private void ejectUnused(Level level, BlockPos pos, int layout) {
        boolean ejected = false;
        for (int slot = 0; slot < CONTAINER_SIZE; slot++) {
            if (activeSlot(slot, layout) || items.get(slot).isEmpty()) continue;
            ItemStack stack = items.get(slot);
            items.set(slot, ItemStack.EMPTY);
            if (!place(stack, layout)) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
            }
            ejected = true;
        }
        if (ejected) changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- joining ------------------------------------------------------------------------------

    boolean isMultiblockPart() {
        return multiblockController != null && multiblockSize != 0;
    }

    BlockPos multiblockController() {
        return multiblockController;
    }

    /** Whether this block holds the fire and places of a joined forge. For the renderer. */
    public boolean isController() {
        return isMultiblockPart() && worldPosition.equals(multiblockController);
    }

    byte multiblockSize() {
        return multiblockSize;
    }

    int localBurnTicks() {
        return burnTicks;
    }

    int localBurnTotal() {
        return burnTotal;
    }

    double localTemperature() {
        return temperature;
    }

    private ForgeBlockEntity controllerEntity() {
        if (!isMultiblockPart() || level == null || worldPosition.equals(multiblockController)) return null;
        return level.getBlockEntity(multiblockController) instanceof ForgeBlockEntity forge ? forge : null;
    }

    void link(BlockPos controller, byte size) {
        multiblockController = controller.immutable();
        multiblockSize = size;
        changed();
    }

    void unlink() {
        multiblockController = null;
        multiblockSize = 0;
        changed();
    }

    /** Everything lying in this block's places, which are left empty. */
    List<ItemStack> takeAll() {
        List<ItemStack> taken = new ArrayList<>();
        for (int slot = 0; slot < CONTAINER_SIZE; slot++) {
            if (!items.get(slot).isEmpty()) taken.add(items.get(slot));
            items.set(slot, ItemStack.EMPTY);
        }
        changed();
        return taken;
    }

    void setThermalState(int burn, int total, double heat) {
        burnTicks = Math.max(0, burn);
        burnTotal = Math.max(burnTicks, total);
        temperature = Math.max(Heat.AMBIENT, heat);
        changed();
    }

    /**
     * Puts a stack into the places of its kind, merging with what is already there. Returns false,
     * and changes nothing, when it does not fit completely.
     */
    public boolean place(ItemStack stack, int layout) {
        boolean fuel = ForgeBlock.fuelValue(stack) > 0;
        if (!fuel && !acceptsMetal(stack)) return false;
        int first = fuel ? 0 : FIRST_METAL;
        int count = fuel ? fuelSlots(layout) : metalSlots(layout);
        int rest = stack.getCount();
        List<Runnable> writes = new ArrayList<>();
        for (int i = 0; i < count && rest > 0; i++) {
            int slot = first + i;
            ItemStack there = items.get(slot);
            if (there.isEmpty()) {
                ItemStack moved = stack.copyWithCount(rest);
                rest = 0;
                writes.add(() -> items.set(slot, moved));
            } else if (ItemStack.isSameItemSameComponents(there, stack)) {
                int move = Math.min(there.getMaxStackSize() - there.getCount(), rest);
                if (move <= 0) continue;
                rest -= move;
                writes.add(() -> there.grow(move));
            }
        }
        if (rest > 0) return false;
        writes.forEach(Runnable::run);
        changed();
        return true;
    }

    // ---- container ----------------------------------------------------------------------------

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
        if (!taken.isEmpty()) changed();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        changed();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!holdsInventory() || !activeSlot(slot, layout())) return false;
        return slot < FIRST_METAL ? ForgeBlock.fuelValue(stack) > 0 : acceptsMetal(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player, 8.0f + 2 * layout());
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ---- screen -------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable(switch (layout()) {
            case 1 -> "container.hardwrought.forge_small";
            case 2 -> "container.hardwrought.forge_large";
            default -> "container.hardwrought.forge";
        });
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ForgeMenu(id, inventory, this, data, layout());
    }

    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return layout();
    }

    // ---- lifecycle ----------------------------------------------------------------------------

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) ForgeMultiblock.beforeRemove(level, pos, this);
        if (level != null) Containers.dropContents(level, pos, items);
        items.clear();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        if (input.getIntOr("layout_version", 0) < 1) {
            // Forges saved before they had a fuel place kept their pieces in the first four places.
            for (int slot = FUEL_SLOTS - 1; slot >= 0; slot--) {
                items.set(FIRST_METAL + slot, items.get(slot));
                items.set(slot, ItemStack.EMPTY);
            }
        }
        burnTicks = input.getIntOr("burn", 0);
        burnTotal = input.getIntOr("burn_total", burnTicks);
        temperature = input.getDoubleOr("temperature", Heat.AMBIENT);
        long controller = input.getLongOr("multiblock_controller", Long.MIN_VALUE);
        multiblockController = controller == Long.MIN_VALUE ? null : BlockPos.of(controller);
        multiblockSize = input.getByteOr("multiblock_size", (byte) 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items, true);
        output.putInt("layout_version", 1);
        output.putInt("burn", burnTicks);
        output.putInt("burn_total", burnTotal);
        output.putDouble("temperature", temperature);
        if (multiblockController != null) {
            output.putLong("multiblock_controller", multiblockController.asLong());
            output.putByte("multiblock_size", multiblockSize);
        }
    }

    /** The pieces travel to the client so the hearth can show them lying in the coals. */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
