package de.ipnats.hardwrought.refining;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.gasline.GasKind;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * The window of every block of the refinery and the gas lines: the column's base and trays, the cracker,
 * the reformer and the gas tank. It holds no items, only gauges — what is in each of the block's
 * tanks, of what and out of how much — and the state of the block: over a fire or not, working or not,
 * how far the batch is, and one number of the block's own (the trays on a base, the height of a tray,
 * a tank's pressure).
 *
 * <p>The numbers go to the client as the menu's data, so the window follows the block as it works.
 */
public class RefineryMenu extends AbstractContainerMenu {
    public static final ExtendedMenuType<RefineryMenu, BlockPos> TYPE = Registry.register(BuiltInRegistries.MENU,
            Hardwrought.id("refinery"), new ExtendedMenuType<>(RefineryMenu::new, BlockPos.STREAM_CODEC));

    /** What the window is of: tells the screen which line of state to write. */
    public static final int KIND_BASE = 0, KIND_STAGE = 1, KIND_PROCESSOR = 2, KIND_GAS_TANK = 3, KIND_FLARE = 4,
            KIND_DIESEL = 5, KIND_ENGINE = 6, KIND_BURNER = 7, KIND_TURBINE = 8, KIND_BATTERY_BANK = 9;
    public static final int GAUGES = 6;
    private static final int KIND = 0, HEATED = 1, RUNNING = 2, PROGRESS = 3, INFO = 4, COUNT = 5, MORE = 6, MOST = 7,
            FIRST_GAUGE = 8;
    public static final int SIZE = FIRST_GAUGE + GAUGES * 3;

    /** What a gauge holds: nothing, crude oil, one of the products by its order, or a gas. */
    public static final int NOTHING = 0, CRUDE = 1, PRODUCT = 2, WATER = 49, GAS = 50;

    private final ContainerData data;
    private final BlockPos pos;

    public RefineryMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, new SimpleContainerData(SIZE), pos);
    }

    public RefineryMenu(int id, ContainerData data, BlockPos pos) {
        super(TYPE, id);
        this.data = data;
        this.pos = pos;
        checkContainerDataCount(data, SIZE);
        addDataSlots(data);
    }

    /** The data of a block's window, read afresh from what the block reports each time it is sent. */
    public static ContainerData data(Supplier<Panel> panel) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return panel.get().values()[index];
            }

            @Override
            public void set(int index, int value) { }

            @Override
            public int getCount() {
                return SIZE;
            }
        };
    }

    /** What one block shows, built on the server. */
    public static final class Panel {
        private final int[] values = new int[SIZE];

        public Panel(int kind, boolean heated, boolean running, int progressPerMille, int info) {
            values[KIND] = kind;
            values[HEATED] = heated ? 1 : 0;
            values[RUNNING] = running ? 1 : 0;
            values[PROGRESS] = progressPerMille;
            values[INFO] = info;
        }

        /** Two more numbers of the block's own, for a block with more to say than one. */
        public Panel more(int more, int most) {
            values[MORE] = more;
            values[MOST] = most;
            return this;
        }

        public Panel gauge(int substance, long amount, long capacity) {
            int at = FIRST_GAUGE + values[COUNT] * 3;
            if (values[COUNT] >= GAUGES) return this;
            values[at] = substance;
            values[at + 1] = (int) Math.min(Short.MAX_VALUE, amount);
            values[at + 2] = (int) Math.min(Short.MAX_VALUE, capacity);
            values[COUNT]++;
            return this;
        }

        public Panel liquid(Fluid fluid, ProcessTank tank, long capacityMillibuckets) {
            return gauge(substance(fluid), tank.millibuckets(), capacityMillibuckets);
        }

        int[] values() {
            return values;
        }
    }

    public static int substance(Fluid fluid) {
        if (fluid == de.ipnats.hardwrought.oil.ModFluids.CRUDE_OIL) return CRUDE;
        if (fluid == net.minecraft.world.level.material.Fluids.WATER) return WATER;
        for (RefineryProduct product : RefineryProduct.values()) {
            if (product.source() == fluid) return PRODUCT + product.ordinal();
        }
        return NOTHING;
    }

    public static int substance(GasKind gas) {
        return GAS + gas.ordinal();
    }

    public static boolean isGas(int substance) {
        return substance >= GAS;
    }

    public static Component name(int substance) {
        if (substance == CRUDE) return Component.translatable("block.hardwrought.crude_oil");
        if (substance == WATER) return Component.translatable("block.minecraft.water");
        if (isGas(substance)) return Component.translatable(GasKind.values()[substance - GAS].translationKey());
        if (substance >= PRODUCT && substance - PRODUCT < RefineryProduct.values().length) {
            return Component.translatable("fluid.hardwrought." + RefineryProduct.values()[substance - PRODUCT].getSerializedName());
        }
        return Component.translatable("gui.hardwrought.refinery.nothing");
    }

    /** The colour a gauge is filled with. */
    public static int colour(int substance) {
        if (substance == CRUDE) return 0xFF1E1712;
        if (substance == WATER) return 0xFF3F76E4;
        if (substance == GAS + GasKind.NATURAL_GAS.ordinal()) return 0xFFA8C8E0;
        if (substance == GAS + GasKind.LPG.ordinal()) return 0xFFF0A040;
        if (substance == GAS + GasKind.CHLORINE.ordinal()) return 0xFFC0D850;
        if (substance == GAS + GasKind.STEAM.ordinal()) return 0xFFE8EEF2;
        if (substance == GAS + GasKind.URANIUM_HEXAFLUORIDE.ordinal()) return 0xFFD8D0A0;
        if (substance == GAS + GasKind.ENRICHED_HEXAFLUORIDE.ordinal()) return 0xFFB8E070;
        if (substance >= PRODUCT && substance - PRODUCT < RefineryProduct.values().length) {
            return 0xFF000000 | RefineryProduct.values()[substance - PRODUCT].color();
        }
        return 0xFF2A2320;
    }

    public BlockPos pos() { return pos; }

    public int kind() { return data.get(KIND); }

    public boolean heated() { return data.get(HEATED) != 0; }

    public boolean running() { return data.get(RUNNING) != 0; }

    public int progress() { return data.get(PROGRESS); }

    public int info() { return data.get(INFO); }

    public int more() { return data.get(MORE); }

    public int most() { return data.get(MOST); }

    public int gauges() { return Math.min(GAUGES, data.get(COUNT)); }

    public int substance(int gauge) { return data.get(FIRST_GAUGE + gauge * 3); }

    public int amount(int gauge) { return data.get(FIRST_GAUGE + gauge * 3 + 1); }

    public int capacity(int gauge) { return data.get(FIRST_GAUGE + gauge * 3 + 2); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) != null && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64;
    }

    /** Touching the class registers the menu type. */
    public static void initialize() { }
}
