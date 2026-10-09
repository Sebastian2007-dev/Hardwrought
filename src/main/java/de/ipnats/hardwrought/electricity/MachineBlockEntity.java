package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Locale;

/**
 * What every machine on current has in common: a resistance between the wire and the ground, made
 * for {@link #RATED_VOLTS}, that takes its current only while there is something in it to work on and
 * room for what it makes; standing idle it takes nothing.
 *
 * <p>At its voltage a piece takes the machine's {@link Machine#ticks}. With less it works slower by
 * the square of what is missing — power goes with the square of the voltage — and below
 * {@link #STALL_VOLTS} it hums and does nothing at all, still taking its current. Above
 * {@link #STRAIN_VOLTS} it runs faster and its windings cook; at {@link #BURN_VOLTS} they go at once.
 *
 * <p>A container with two slots, opened like a furnace ({@link MachineMenu}); hoppers feed it from the
 * top and sides and draw from the bottom.
 */
public class MachineBlockEntity extends ElectricBlockEntity implements WorldlyContainer, net.minecraft.world.MenuProvider {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final double RATED_VOLTS = 60.0;
    public static final double STALL_VOLTS = 30.0;
    public static final double STRAIN_VOLTS = 75.0;
    public static final double BURN_VOLTS = 110.0;
    private static final float STRAIN_LIMIT = 20.0f;

    private static final int[] INPUT_SLOTS = { INPUT };
    private static final int[] OUTPUT_SLOTS = { OUTPUT };

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private float progress;
    private float strain;
    /** Whether it asked for current when the network was last worked out. */
    private boolean asking;

    /** What the piece in the input becomes, remembered for as long as the same kind of thing lies there. */
    private Item madeFrom;
    private ItemStack made = ItemStack.EMPTY;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ElectricBlocks.MACHINE_ENTITY, pos, state);
    }

    /** What the open screen is told: progress in thousandths, volts in tenths, amperes in hundredths, and how it is. */
    private final net.minecraft.world.inventory.ContainerData data = new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> Math.round(progress * 1000.0f / machine().ticks());
                case 1 -> Math.round(Math.abs(volts()) * 10.0f);
                case 2 -> Math.round(Math.abs(amps()) * 100.0f);
                default -> broken() ? MachineMenu.BURNT : !canWork() ? MachineMenu.IDLE
                        : rate(volts()) <= 0.0 ? MachineMenu.STALLED : MachineMenu.RUNNING;
            };
        }

        @Override
        public void set(int index, int value) { }

        @Override
        public int getCount() {
            return MachineMenu.DATA_COUNT;
        }
    };

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory,
                                                                          Player player) {
        return new MachineMenu(id, inventory, this, data);
    }

    public Machine machine() {
        return getBlockState().getBlock() instanceof MachineBlock block ? block.machine() : Machine.CRUSHER;
    }

    /** Whether this machine works on such a thing. */
    public boolean takes(ServerLevel level, ItemStack stack) {
        return !stack.isEmpty() && !machine().result(level, stack).isEmpty();
    }

    /** What one piece of the input becomes; empty where there is none or it is nothing this machine takes. */
    private ItemStack result() {
        ItemStack input = items.get(INPUT);
        if (input.isEmpty() || !(level instanceof ServerLevel server)) return ItemStack.EMPTY;
        if (madeFrom != input.getItem()) {
            madeFrom = input.getItem();
            made = machine().result(server, input);
        }
        return made;
    }

    private boolean broken() {
        return getBlockState().getValue(MachineBlock.BROKEN);
    }

    @Override
    public double conductance() {
        return !broken() && canWork() ? 1.0 / machine().ohms() : 0.0;
    }

    @Override
    public boolean takesWires() {
        return false;
    }

    @Override
    public boolean mindsRain() {
        return true;
    }

    /** How much of a tick's work a machine does at this voltage: 1 at the rated voltage, nothing below the stall. */
    public static double rate(double volts) {
        double share = Math.abs(volts) / RATED_VOLTS;
        return Math.abs(volts) < STALL_VOLTS ? 0.0 : share * share;
    }

    @Override
    public Component note() {
        if (broken()) return Component.translatable("message.hardwrought.meter.motor_burnt");
        if (!canWork()) return Component.translatable("message.hardwrought.meter.motor_idle");
        double rate = rate(volts());
        return rate <= 0.0 ? Component.translatable("message.hardwrought.meter.motor_stalled", (int) STALL_VOLTS)
                : Component.translatable("message.hardwrought.meter.motor_running", String.format(Locale.ROOT, "%.0f", rate * 100.0));
    }

    @Override
    protected void solved(ServerLevel level, double volts, double amps, boolean wet, double seconds) {
        super.solved(level, volts, amps, wet, seconds);
        if (seconds <= 0.0 || broken()) return;
        double live = Math.abs(amps) > 0.0 ? Math.abs(volts) : 0.0;
        if (live >= STRAIN_VOLTS) {
            double over = (live - STRAIN_VOLTS) / (RATED_VOLTS * 1.5 - STRAIN_VOLTS);
            strain += (float) ((0.25 + over * over) * seconds);
        } else {
            strain = Math.max(0.0f, strain - (float) seconds * 0.25f);
        }
        if (live >= BURN_VOLTS || strain >= STRAIN_LIMIT) {
            strain = 0.0f;
            level.setBlock(worldPosition, getBlockState().setValue(MachineBlock.BROKEN, true)
                    .setValue(MachineBlock.RUNNING, false), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.8f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.01);
        }
    }

    /**
     * One tick. It works by the voltage that was last worked out for it; and when it starts or stops
     * wanting current — something put in, the last piece done, the output full — the network is worked
     * out again, because the load on it has changed.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
        if (!(level instanceof ServerLevel server)) return;
        boolean wants = machine.conductance() > 0.0;
        if (wants != machine.asking) {
            machine.asking = wants;
            Electricity.update(server, pos);
        }
        double rate = wants ? rate(machine.volts()) : 0.0;
        boolean working = rate > 0.0;
        if (working) {
            int before = (int) machine.progress;
            machine.progress += (float) rate;
            if (machine.progress >= machine.machine().ticks()) {
                machine.workOne();
                machine.progress = 0;
            }
            if (before / 20 != (int) machine.progress / 20) {
                level.playSound(null, pos, machine.machine().sound(), SoundSource.BLOCKS, 0.4f,
                        0.9f + level.getRandom().nextFloat() * 0.2f);
            }
            machine.setChanged();
        } else if (machine.items.get(INPUT).isEmpty() && machine.progress != 0) {
            machine.progress = 0;
            machine.setChanged();
        }
        state = level.getBlockState(pos);
        if (state.getValue(MachineBlock.RUNNING) != working) {
            level.setBlock(pos, state.setValue(MachineBlock.RUNNING, working), Block.UPDATE_CLIENTS);
        }
    }

    private boolean canWork() {
        ItemStack result = result();
        if (result.isEmpty()) return false;
        ItemStack output = items.get(OUTPUT);
        return output.isEmpty() || (ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize());
    }

    private void workOne() {
        ItemStack result = result().copy();
        items.get(INPUT).shrink(1);
        ItemStack output = items.get(OUTPUT);
        if (output.isEmpty()) items.set(OUTPUT, result);
        else output.grow(result.getCount());
    }

    /** Moves as much of the held stack into the input as fits. False where nothing moved. Ask {@link #takes} first. */
    public boolean insertFrom(ItemStack held, boolean consume) {
        ItemStack input = items.get(INPUT);
        int room = input.isEmpty() ? held.getMaxStackSize()
                : ItemStack.isSameItemSameComponents(input, held) ? input.getMaxStackSize() - input.getCount() : 0;
        int moved = Math.min(room, held.getCount());
        if (moved <= 0) return false;
        if (input.isEmpty()) items.set(INPUT, held.copyWithCount(moved));
        else input.grow(moved);
        if (consume) held.shrink(moved);
        setChanged();
        return true;
    }

    /** Hands out what was made, or what was put in when nothing has been made yet. Empty when there is neither. */
    public ItemStack takeOut() {
        int slot = !items.get(OUTPUT).isEmpty() ? OUTPUT : INPUT;
        ItemStack taken = removeItemNoUpdate(slot);
        if (slot == INPUT) progress = 0;
        return taken;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        progress = input.getFloatOr("progress", 0.0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putFloat("progress", progress);
    }

    // ---------------------------------------------------------------- container

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && level instanceof ServerLevel server && takes(server, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /** In from the top and the sides, out of the bottom. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT;
    }
}
