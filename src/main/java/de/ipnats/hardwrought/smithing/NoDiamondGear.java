package de.ipnats.hardwrought.smithing;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Diamond tools, weapons and armor do not exist here: a gem is no blade and no breastplate. Their
 * recipes never load (tools/armor_parts_assets.py writes them with a load condition that fails), and
 * wherever one would still turn up — in a chest, on a mob, from a villager — it is steel instead,
 * enchantments and wear carried over. Netherite is upgraded from tungsten steel.
 *
 * <p>The diamond itself stays: it is a gem, and gems have their uses.
 */
public final class NoDiamondGear {
    private static final Map<Item, Item> REPLACEMENTS = new IdentityHashMap<>();

    private NoDiamondGear() { }

    public static void initialize() {
        AlloyEquipment.Tier steel = AlloyEquipment.Tier.STEEL;
        REPLACEMENTS.put(Items.DIAMOND_SWORD, AlloyEquipment.tool(steel, AlloyEquipment.Tool.SWORD));
        REPLACEMENTS.put(Items.DIAMOND_PICKAXE, AlloyEquipment.tool(steel, AlloyEquipment.Tool.PICKAXE));
        REPLACEMENTS.put(Items.DIAMOND_AXE, AlloyEquipment.tool(steel, AlloyEquipment.Tool.AXE));
        REPLACEMENTS.put(Items.DIAMOND_SHOVEL, AlloyEquipment.tool(steel, AlloyEquipment.Tool.SHOVEL));
        REPLACEMENTS.put(Items.DIAMOND_HOE, AlloyEquipment.tool(steel, AlloyEquipment.Tool.HOE));
        REPLACEMENTS.put(Items.DIAMOND_HELMET, AlloyEquipment.armor(steel, ArmorType.HELMET));
        REPLACEMENTS.put(Items.DIAMOND_CHESTPLATE, AlloyEquipment.armor(steel, ArmorType.CHESTPLATE));
        REPLACEMENTS.put(Items.DIAMOND_LEGGINGS, AlloyEquipment.armor(steel, ArmorType.LEGGINGS));
        REPLACEMENTS.put(Items.DIAMOND_BOOTS, AlloyEquipment.armor(steel, ArmorType.BOOTS));
        // Steel has no spear and no armor for horses or nautiluses: iron does.
        REPLACEMENTS.put(Items.DIAMOND_SPEAR, Items.IRON_SPEAR);
        REPLACEMENTS.put(Items.DIAMOND_HORSE_ARMOR, Items.IRON_HORSE_ARMOR);
        REPLACEMENTS.put(Items.DIAMOND_NAUTILUS_ARMOR, Items.IRON_NAUTILUS_ARMOR);

        // Chests, fishing, mobs' drops: everything a loot table hands out.
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> drops.replaceAll(NoDiamondGear::replace));
        // Mobs spawned wearing or holding it.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof Mob mob)) return;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack worn = mob.getItemBySlot(slot);
                if (banned(worn)) mob.setItemSlot(slot, replace(worn));
            }
        });
        // Whatever reaches a player some other way — a trade, a command, an old world.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) sweep(player);
        });
        CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, output) -> {
            output.getDisplayStacks().removeIf(NoDiamondGear::banned);
            output.getSearchTabStacks().removeIf(NoDiamondGear::banned);
        });
    }

    /** Whether this is diamond equipment. */
    public static boolean banned(ItemStack stack) {
        return !stack.isEmpty() && REPLACEMENTS.containsKey(stack.getItem());
    }

    /**
     * What a stack is here: steel in place of diamond, with its enchantments, name and the same share
     * of wear; anything else unchanged.
     */
    public static ItemStack replace(ItemStack stack) {
        Item instead = stack.isEmpty() ? null : REPLACEMENTS.get(stack.getItem());
        if (instead == null) return stack;
        int max = stack.getMaxDamage();
        double worn = max > 0 ? stack.getDamageValue() / (double) max : 0;
        ItemStack copy = stack.transmuteCopy(instead, stack.getCount());
        if (copy.isDamageableItem()) copy.setDamageValue((int) Math.round(worn * copy.getMaxDamage()));
        return copy;
    }

    private static void sweep(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (banned(stack)) inventory.setItem(slot, replace(stack));
        }
    }

    /** For tests: every diamond item that is replaced, and by what. */
    public static Map<Item, Item> replacements() {
        return Map.copyOf(REPLACEMENTS);
    }
}
