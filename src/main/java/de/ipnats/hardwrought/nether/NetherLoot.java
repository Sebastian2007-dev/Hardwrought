package de.ipnats.hardwrought.nether;

import de.ipnats.hardwrought.core.registry.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.Set;

/**
 * What a start over in the Nether can find in the Nether's own chests (see docs/endgame.md): the makings
 * of the first tools and a sapling of ashcork, for one who woke far from an Ash Grove. Not riches —
 * the fortresses and bastions keep those — only enough that searching a ruin is worth it from the
 * first hour.
 */
public final class NetherLoot {
    static final Set<ResourceKey<LootTable>> CHESTS = Set.of(
            BuiltInLootTables.NETHER_BRIDGE, BuiltInLootTables.RUINED_PORTAL, BuiltInLootTables.BASTION_OTHER,
            BuiltInLootTables.BASTION_BRIDGE, BuiltInLootTables.BASTION_HOGLIN_STABLE);

    /** One kind of thing a chest may hold, how likely, and how many. */
    record Find(Item item, double chance, int min, int max) {
        ItemStack roll(RandomSource random) {
            if (random.nextDouble() >= chance) return ItemStack.EMPTY;
            return new ItemStack(item, min + random.nextInt(max - min + 1));
        }
    }

    private NetherLoot() { }

    static List<Find> finds() {
        return List.of(
                new Find(ModItems.FLINT_SHARD, 0.6, 2, 5),
                new Find(ModItems.LEAF_STRING, 0.5, 1, 3),
                new Find(Items.STICK, 0.4, 2, 6),
                new Find(Items.COAL, 0.5, 2, 6),
                new Find(Items.RAW_COPPER, 0.35, 2, 5),
                new Find(Items.RAW_IRON, 0.3, 1, 3),
                new Find(NetherOres.BLACKSTONE_PIECE, 0.35, 2, 4),
                new Find(Ashcork.SAPLING.asItem(), 0.3, 1, 2));
    }

    public static void initialize() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            if (table.unwrapKey().filter(CHESTS::contains).isEmpty()) return;
            for (Find find : finds()) {
                ItemStack stack = find.roll(context.getRandom());
                if (!stack.isEmpty()) drops.add(stack);
            }
        });
    }
}
