package de.ipnats.hardwrought.carried;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Charge carried about (stage E4): what is in a cell, and the cell in a device.
 *
 * <p>A cell keeps its charge in {@link #CHARGE}, in watt-seconds, and a silver–zinc cell how worn it is
 * in {@link #WORN}, in full charges. A device keeps the cell put into it in {@link #CELL}; everything a
 * device does it draws from that cell through {@link #draw}, and with the cell empty it does nothing. An
 * armour lining keeps its own device in {@link #LINING}, cell and all.
 */
public final class Cells {
    public static final int BAR_COLOR = 0x4FB4FF;
    /** What a charger gets into a cell of what it draws. */
    public static final double CHARGE_EFFICIENCY = 0.8;

    public static final DataComponentType<Float> CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("charge"), DataComponentType.<Float>builder().persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());
    public static final DataComponentType<Float> WORN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("cell_wear"), DataComponentType.<Float>builder().persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());
    public static final DataComponentType<ItemStack> CELL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("cell"), DataComponentType.<ItemStack>builder().persistent(ItemStack.CODEC)
                    .networkSynchronized(ItemStack.STREAM_CODEC).build());
    public static final DataComponentType<ItemStack> LINING = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("lining"), DataComponentType.<ItemStack>builder().persistent(ItemStack.CODEC)
                    .networkSynchronized(ItemStack.STREAM_CODEC).build());
    /** The part that wears in a device — a drill's bit, a saw's chain — with its own durability. */
    public static final DataComponentType<ItemStack> WORKING_PART = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("working_part"), DataComponentType.<ItemStack>builder().persistent(ItemStack.CODEC)
                    .networkSynchronized(ItemStack.STREAM_CODEC).build());

    /** The part that wears in a device; empty where none is fitted or it is worn through. */
    public static ItemStack part(ItemStack device) {
        ItemStack part = device.get(WORKING_PART);
        return part == null ? ItemStack.EMPTY : part;
    }

    /** Wears a device's part by one use; worn through, it is gone. */
    public static void wearPart(ItemStack device) {
        ItemStack part = part(device).copy();
        if (part.isEmpty()) return;
        part.setDamageValue(part.getDamageValue() + 1);
        if (part.getDamageValue() >= part.getMaxDamage()) device.remove(WORKING_PART);
        else device.set(WORKING_PART, part);
    }

    /** Whether a device that can be switched is switched on. */
    public static final DataComponentType<Boolean> SWITCHED_ON = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("switched_on"), DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

    private Cells() { }

    public static void initialize() { }

    public static Cell kind(ItemStack stack) {
        return stack.getItem() instanceof CellItem item ? item.cell() : null;
    }

    /** The cell this holds: itself if it is one, the one in it if it is a device, the one in its lining if it is armour. */
    public static ItemStack cellOf(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.getItem() instanceof CellItem) return stack;
        ItemStack held = stack.get(CELL);
        if (held != null && !held.isEmpty()) return held;
        ItemStack lining = stack.get(LINING);
        return lining != null && !lining.isEmpty() ? cellOf(lining) : ItemStack.EMPTY;
    }

    /** What a cell holds now, in watt-seconds. */
    public static double charge(ItemStack cell) {
        Float charge = cell.get(CHARGE);
        return charge == null ? 0.0 : charge;
    }

    /** What a cell holds full, worn as it is. */
    public static double capacity(ItemStack cell) {
        Cell kind = kind(cell);
        if (kind == null) return 0.0;
        Float worn = cell.get(WORN);
        return kind.capacity(worn == null ? 0.0 : worn);
    }

    /** How full the cell in this is, 0 to 1. */
    public static double fraction(ItemStack stack) {
        ItemStack cell = cellOf(stack);
        double capacity = capacity(cell);
        return capacity <= 0.0 ? 0.0 : Math.clamp(charge(cell) / capacity, 0.0, 1.0);
    }

    /** Whether the cell in this has anything left. */
    public static boolean charged(ItemStack stack) {
        return charge(cellOf(stack)) > 0.0;
    }

    /**
     * Takes this many watt-seconds from the cell in a device (or a lining, or a cell). True where it had
     * them; where it had less, it is emptied and false.
     */
    public static boolean draw(ItemStack holder, double joules) {
        ItemStack cell = cellOf(holder);
        if (cell.isEmpty()) return false;
        double charge = charge(cell);
        boolean enough = charge >= joules;
        cell.set(CHARGE, (float) Math.max(0.0, charge - joules));
        store(holder, cell);
        return enough;
    }

    /** Puts up to this many watt-seconds into the cell in this; returns how many went in. A silver–zinc cell wears for it. */
    public static double fill(ItemStack holder, double joules) {
        ItemStack cell = cellOf(holder);
        Cell kind = kind(cell);
        if (kind == null) return 0.0;
        double charge = charge(cell);
        double room = Math.max(0.0, capacity(cell) - charge);
        double in = Math.min(room, joules);
        if (in <= 0.0) return 0.0;
        cell.set(CHARGE, (float) (charge + in));
        if (kind.cycles() > 0) {
            Float worn = cell.get(WORN);
            cell.set(WORN, (float) ((worn == null ? 0.0 : worn) + in / kind.joules()));
        }
        store(holder, cell);
        return in;
    }

    /** Writes a changed cell back where it came from: components hold copies. */
    private static void store(ItemStack holder, ItemStack cell) {
        if (holder.getItem() instanceof CellItem) return;
        if (holder.has(CELL)) {
            holder.set(CELL, cell.copy());
            return;
        }
        ItemStack lining = holder.get(LINING);
        if (lining != null) {
            store(lining, cell);
            holder.set(LINING, lining.copy());
        }
    }

    /** A cell freshly made: empty. */
    public static ItemStack empty(ItemStack cell) {
        cell.set(CHARGE, 0.0f);
        return cell;
    }

    /** How much a thing holds, for tooltips. */
    public static Component describe(ItemStack stack) {
        ItemStack cell = cellOf(stack);
        if (cell.isEmpty()) return Component.translatable("tooltip.hardwrought.cell.none");
        Float worn = cell.get(WORN);
        Cell kind = kind(cell);
        Component said = Component.translatable("tooltip.hardwrought.cell.charge", cell.getHoverName(),
                String.format(Locale.ROOT, "%.1f", charge(cell) / 3600.0), String.format(Locale.ROOT, "%.0f", capacity(cell) / 3600.0));
        if (kind != null && kind.cycles() > 0 && worn != null) {
            said = said.copy().append(Component.translatable("tooltip.hardwrought.cell.worn",
                    String.format(Locale.ROOT, "%.0f", Math.min(100.0, worn / kind.cycles() * 100.0))));
        }
        return said;
    }

    /** Whether a device is switched on; one that cannot be switched is always on. */
    public static boolean on(ItemStack stack) {
        return stack.getOrDefault(SWITCHED_ON, false);
    }
}
