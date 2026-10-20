package de.ipnats.hardwrought.food;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Section 107: food spoils. Every food has a shelf life in days; the moment it first comes into a
 * player's hands it is marked with when it will be rotten, and from then on it goes off whether anyone
 * looks at it or not — in a pack, in a chest, on the floor.
 *
 * <p>The mark is rounded up to the next quarter of a day, so that what was picked or cooked within a few
 * hours of each other still stacks. Food past three quarters of its life is <b>stale</b>, and upsets the
 * stomach more often; past its life it is <b>rotten</b>, and the next time a player has it or opens what
 * holds it, it is gone to rotten flesh (meat and fish) or to spoiled food, which the composter takes.
 *
 * <p>The cold keeps it: in an ice box it goes off at half the speed, in a refrigerator at a quarter, in a
 * freezer not at all ({@link #keep}). What keeps it pushes its mark back by the time it saved.
 */
public final class Spoilage {
    public static final long DAY = 24000L;
    /** Marks are rounded up to this, so that food of the same few hours stacks. */
    public static final long ROUNDING = DAY / 4;
    /** Past this share of its life food is stale. */
    public static final double STALE_AT = 0.75;
    /** How much more often stale and rotten food upsets the stomach. */
    public static final double STALE_ILLNESS = 0.20, ROTTEN_ILLNESS = 0.60;
    private static final int SWEEP_TICKS = 100;

    /** When this food is rotten, in game time. */
    public static final DataComponentType<Long> ROTS_AT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("rots_at"), DataComponentType.<Long>builder()
                    .persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());

    /** What plant food rots to. Meat and fish rot to rotten flesh. */
    public static final Item SPOILED_FOOD = Registry.register(BuiltInRegistries.ITEM, Hardwrought.id("spoiled_food"),
            new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Hardwrought.id("spoiled_food")))
                    .compostable(net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH)));

    /** Food that does not go off: it is preserved, already rotten, or not really food. */
    private static final Set<Item> KEEPS = Set.of(Items.HONEY_BOTTLE, Items.DRIED_KELP, Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT, Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
            Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW, Items.OMINOUS_BOTTLE);
    /** Shelf lives in days, where they are not the default. */
    private static final Map<Item, Double> DAYS = new HashMap<>();
    /** What has no shelf life of its own keeps this long. */
    public static final double DEFAULT_DAYS = 8.0;

    static {
        for (Item raw : new Item[] {Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT}) DAYS.put(raw, 3.0);
        for (Item fish : new Item[] {Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH}) DAYS.put(fish, 2.0);
        for (Item cooked : new Item[] {Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN,
                Items.COOKED_RABBIT}) DAYS.put(cooked, 6.0);
        DAYS.put(Items.COOKED_COD, 4.0);
        DAYS.put(Items.COOKED_SALMON, 4.0);
        for (Item stew : new Item[] {Items.MUSHROOM_STEW, Items.RABBIT_STEW, Items.BEETROOT_SOUP}) DAYS.put(stew, 2.0);
        DAYS.put(Items.BREAD, 6.0);
        DAYS.put(Items.PUMPKIN_PIE, 4.0);
        DAYS.put(Items.COOKIE, 16.0);
        DAYS.put(Items.BAKED_POTATO, 5.0);
        DAYS.put(Items.MELON_SLICE, 4.0);
        DAYS.put(Items.SWEET_BERRIES, 6.0);
        DAYS.put(Items.GLOW_BERRIES, 6.0);
        DAYS.put(Items.APPLE, 16.0);
        DAYS.put(Items.CARROT, 20.0);
        DAYS.put(Items.POTATO, 24.0);
        DAYS.put(Items.BEETROOT, 16.0);
    }

    private Spoilage() { }

    public static void initialize() {
        // It comes of food left too long, not of a recipe.
        de.ipnats.hardwrought.knowledge.LootSources.inWorld(() -> SPOILED_FOOD);
        ServerTickEvents.END_SERVER_TICK.register(Spoilage::sweepPlayers);
    }

    /** Whether this goes off at all. */
    public static boolean perishable(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD) && !KEEPS.contains(stack.getItem())
                && !stack.is(SPOILED_FOOD) && !stack.is(de.ipnats.hardwrought.underworld.UnderworldRuins.STALE_BREAD);
    }

    /** How long this keeps, in ticks. */
    public static long life(ItemStack stack) {
        return Math.round(DAYS.getOrDefault(stack.getItem(), DEFAULT_DAYS) * DAY);
    }

    /** Marks food with when it will be rotten, where it is not marked yet. True where it was marked now. */
    public static boolean mark(ItemStack stack, long now) {
        if (!perishable(stack) || stack.has(ROTS_AT)) return false;
        long rots = now + life(stack);
        stack.set(ROTS_AT, (rots + ROUNDING - 1) / ROUNDING * ROUNDING);
        return true;
    }

    /** How much of its life it has left, 1 fresh to 0 rotten; 1 for what is not marked. */
    public static double freshness(ItemStack stack, long now) {
        Long rots = stack.get(ROTS_AT);
        if (rots == null) return 1.0;
        return Math.clamp((double) (rots - now) / life(stack), 0.0, 1.0);
    }

    public static boolean rotten(ItemStack stack, long now) {
        Long rots = stack.get(ROTS_AT);
        return rots != null && now >= rots;
    }

    /** What rotten food has become: rotten flesh for meat and fish, spoiled food for the rest. */
    public static ItemStack rot(ItemStack stack) {
        boolean flesh = stack.is(ItemTags.MEAT) || stack.is(ItemTags.FISHES);
        return new ItemStack(flesh ? Items.ROTTEN_FLESH : SPOILED_FOOD, stack.getCount());
    }

    /**
     * The cold keeps food: over this many ticks it went off at only this share of the speed (0 for a
     * freezer), so its mark moves back by the rest.
     */
    public static void keep(ItemStack stack, long ticks, double speed) {
        Long rots = stack.get(ROTS_AT);
        if (rots == null || ticks <= 0) return;
        stack.set(ROTS_AT, rots + Math.round(ticks * (1.0 - speed)));
    }

    /** Marks what is new in a container and turns what has rotted. True where anything changed. */
    public static boolean sweep(Container container, long now) {
        boolean changed = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (mark(stack, now)) changed = true;
            if (rotten(stack, now)) {
                container.setItem(slot, rot(stack));
                changed = true;
            }
        }
        if (changed) container.setChanged();
        return changed;
    }

    /** Every few seconds: the food on every player, and in whatever they have open. */
    private static void sweepPlayers(MinecraftServer server) {
        if (server.getTickCount() % SWEEP_TICKS != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            long now = player.level().getGameTime();
            sweep(player.getInventory(), now);
            if (player.containerMenu != player.inventoryMenu) {
                for (Slot slot : player.containerMenu.slots) {
                    if (slot.container == player.getInventory()) continue;
                    ItemStack stack = slot.getItem();
                    mark(stack, now);
                    if (rotten(stack, now)) slot.set(rot(stack));
                }
            }
        }
    }

    /** How much more likely this food is to upset the stomach for how far gone it is. */
    public static double illness(ItemStack stack, long now) {
        if (!stack.has(ROTS_AT)) return 0.0;
        double fresh = freshness(stack, now);
        if (fresh <= 0.0) return ROTTEN_ILLNESS;
        return fresh < 1.0 - STALE_AT ? STALE_ILLNESS : 0.0;
    }
}
