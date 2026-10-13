package de.ipnats.hardwrought.materials;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.gasline.GasStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The machines that make the materials: the electrolyser, the chemical reactor and the arc furnace. */
public final class MaterialBlocks {
    public static final Map<ChemMachine, ChemMachineBlock> MACHINES = new EnumMap<>(ChemMachine.class);

    static {
        for (ChemMachine machine : ChemMachine.values()) {
            ResourceKey<net.minecraft.world.level.block.Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(machine.id()));
            ChemMachineBlock block = Registry.register(BuiltInRegistries.BLOCK, key, new ChemMachineBlock(machine,
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 8.0f).requiresCorrectToolForDrops()
                            .sound(SoundType.METAL).setId(key)));
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Hardwrought.id(machine.id()));
            Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
            MACHINES.put(machine, block);
        }
    }

    public static final BlockEntityType<ChemMachineBlockEntity> CHEM_MACHINE_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("chem_machine"),
            new BlockEntityType<>(ChemMachineBlockEntity::new, Set.copyOf(MACHINES.values())));

    private MaterialBlocks() { }

    /** Touching the class registers everything; then pipes and gas lines are told what each takes and gives. */
    public static void initialize() {
        ChemMenu.initialize();
        FluidStorage.SIDED.registerForBlockEntity((machine, side) -> machine.fluid(), CHEM_MACHINE_ENTITY);
        // Gas goes in through the sides and comes out at the top, so one line does not take back what another gives.
        GasStorage.SIDED.registerForBlockEntity((machine, side) -> side == Direction.UP ? machine.gasOut
                : machine.machine().gases().isEmpty() ? null : machine.gasIn, CHEM_MACHINE_ENTITY);
    }

    public static ChemMachineBlock block(ChemMachine machine) {
        return MACHINES.get(machine);
    }

    public static List<Item> items() {
        return MACHINES.values().stream().map(ChemMachineBlock::asItem).toList();
    }
}
