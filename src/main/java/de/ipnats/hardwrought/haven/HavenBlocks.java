package de.ipnats.hardwrought.haven;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The safe haven (docs/electricity-roadmap.md, stage E3), registered: every {@link Appliance} as a block,
 * the ice box, and their block entities.
 */
public final class HavenBlocks {
    public static final Map<Appliance, ApplianceBlock> APPLIANCES = new EnumMap<>(Appliance.class);

    static {
        for (Appliance appliance : Appliance.values()) {
            APPLIANCES.put(appliance, block(appliance.id(), properties -> new ApplianceBlock(appliance, properties),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0f).noOcclusion()
                            .lightLevel(state -> state.getValue(ApplianceBlock.ON) && !state.getValue(ApplianceBlock.BROKEN)
                                    ? appliance.light() : 0)));
        }
    }

    public static final IceBoxBlock ICE_BOX = block("ice_box", IceBoxBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS).strength(2.0f));

    public static final BlockEntityType<BulbBlockEntity> BULB_ENTITY = entity("bulb",
            new BlockEntityType<>(BulbBlockEntity::new, Set.of(of(Appliance.ELECTRIC_LIGHT), of(Appliance.HEAT_LAMP))));
    public static final BlockEntityType<MotionLampBlockEntity> MOTION_LAMP_ENTITY = entity("motion_lamp",
            new BlockEntityType<>(MotionLampBlockEntity::new, Set.of(of(Appliance.MOTION_LAMP))));
    public static final BlockEntityType<FloodlightBlockEntity> FLOODLIGHT_ENTITY = entity("arc_floodlight",
            new BlockEntityType<>(FloodlightBlockEntity::new, Set.of(of(Appliance.ARC_FLOODLIGHT))));
    public static final BlockEntityType<AlarmBellBlockEntity> ALARM_BELL_ENTITY = entity("alarm_bell",
            new BlockEntityType<>(AlarmBellBlockEntity::new, Set.of(of(Appliance.ALARM_BELL))));
    public static final BlockEntityType<ClimateBlockEntity> CLIMATE_ENTITY = entity("climate",
            new BlockEntityType<>(ClimateBlockEntity::new, Set.of(of(Appliance.ELECTRIC_HEATER), of(Appliance.AIR_COOLER))));
    public static final BlockEntityType<FanBlockEntity> FAN_ENTITY = entity("ventilation_fan",
            new BlockEntityType<>(FanBlockEntity::new, Set.of(of(Appliance.VENTILATION_FAN))));
    public static final BlockEntityType<ColdStoreBlockEntity> COLD_STORE_ENTITY = entity("cold_store",
            new BlockEntityType<>(ColdStoreBlockEntity::new, Set.of(of(Appliance.REFRIGERATOR), of(Appliance.FREEZER))));
    public static final BlockEntityType<MonitorBlockEntity> MONITOR_ENTITY = entity("bedside_monitor",
            new BlockEntityType<>(MonitorBlockEntity::new, Set.of(of(Appliance.BEDSIDE_MONITOR))));
    public static final BlockEntityType<ChargerBlockEntity> CHARGER_ENTITY = entity("charging_station",
            new BlockEntityType<>(ChargerBlockEntity::new, Set.of(of(Appliance.CHARGING_STATION))));
    public static final BlockEntityType<de.ipnats.hardwrought.automation.AssemblerBlockEntity> ASSEMBLER_ENTITY = entity("assembler",
            new BlockEntityType<>(de.ipnats.hardwrought.automation.AssemblerBlockEntity::new, Set.of(of(Appliance.ASSEMBLER))));
    public static final BlockEntityType<de.ipnats.hardwrought.tesla.TeslaTowerBlockEntity> TESLA_TOWER_ENTITY = entity("tesla_tower",
            new BlockEntityType<>(de.ipnats.hardwrought.tesla.TeslaTowerBlockEntity::new, Set.of(of(Appliance.TESLA_TOWER))));
    public static final BlockEntityType<IceBoxBlockEntity> ICE_BOX_ENTITY = entity("ice_box",
            new BlockEntityType<>(IceBoxBlockEntity::new, Set.of(ICE_BOX)));

    private HavenBlocks() { }

    /** Touching the class registers everything in it. */
    public static void initialize() { }

    public static ApplianceBlock of(Appliance appliance) {
        return APPLIANCES.get(appliance);
    }

    /** Everything a player can hold, for the creative tab. */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (ApplianceBlock block : APPLIANCES.values()) items.add(block.asItem());
        items.add(ICE_BOX.asItem());
        return items;
    }

    private static <T extends Block> T block(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        T block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    private static <T extends BlockEntityType<?>> T entity(String name, T type) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id(name), type);
    }
}
