package de.ipnats.hardwrought.equipment;

import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * The two things a player wears that are neither armour nor held: the pack and the lamp.
 *
 * <p>The pack is the one piece of equipment in the mod that cannot be lost. It is not in the
 * inventory, so death does not drop it; and where a player somehow has none at all, one is put back.
 * That is not generosity — a player without a pack has no inventory beyond their belt, so losing it
 * to a mistake would take every slot they own with it and leave them no way back.
 *
 * <p>The one place a pack is <em>not</em> handed out is the first spawn on hardcore. There a player
 * gets the compendium and nothing else, and weaving the first pack out of leaf fibre is the opening
 * move rather than something they woke up holding.
 */
public final class EquipmentSystem {
    private final MinecraftServer server;
    private final CoreSaveData save;

    public EquipmentSystem(MinecraftServer server, CoreSaveData save) {
        this.server = server;
        this.save = save;
    }

    // ---------------------------------------------------------------- what is worn

    public PlayerEquipment equipment(ServerPlayer player) {
        return save.equipment(player.getUUID());
    }

    public void setEquipment(ServerPlayer player, PlayerEquipment value) {
        save.setEquipment(player.getUUID(), value);
    }

    public ItemStack backpack(ServerPlayer player) {
        return equipment(player).backpack();
    }

    public void setBackpack(ServerPlayer player, ItemStack stack) {
        setEquipment(player, equipment(player).withBackpack(stack));
    }

    public ItemStack lamp(ServerPlayer player) {
        return equipment(player).lamp();
    }

    public void setLamp(ServerPlayer player, ItemStack stack) {
        setEquipment(player, equipment(player).withLamp(stack));
    }

    /** What pack this player is wearing, or null for none. */
    public BackpackTier tier(ServerPlayer player) {
        return isCreative(player) ? BackpackTier.BASIC : equipment(player).tier();
    }

    /**
     * Whether this player is building rather than surviving.
     *
     * <p>Asked the careful way because of where it can be asked from: the player's own inventory menu
     * is built inside the {@code ServerPlayer} constructor, and the game mode is not assigned until
     * afterwards. {@code isCreative} dereferences it, so a player being created would take the whole
     * login down with it — which it did, as "Invalid player data".
     */
    private static boolean isCreative(ServerPlayer player) {
        return player.gameMode != null && player.isCreative();
    }

    /**
     * How many rows of the main grid this player may use. Nothing but the belt without a pack, which
     * is the whole of what a pack is for.
     */
    public int mainRows(ServerPlayer player) {
        return tier(player) == null ? 0 : BackpackTier.MAIN_ROWS;
    }

    /** What this player may carry before the weight starts to tell. */
    public double capacityKg(ServerPlayer player) {
        BackpackTier tier = tier(player);
        return tier == null ? BackpackTier.BARE_CAPACITY_KG : tier.capacityKg();
    }

    // ---------------------------------------------------------------- handing one out

    /**
     * Called when a player joins for the first time. Everywhere but hardcore that is a pack and the
     * book; on hardcore it is the book alone, and the pack is the player's own first problem.
     */
    public void welcome(ServerPlayer player) {
        if (!server.isHardcore()) ensureBackpack(player);
        giveOnce(player, new ItemStack(ModItems.COMPENDIUM));
    }

    /** Called after a respawn. A player who has died is always wearing a pack again. */
    public void respawned(ServerPlayer player) {
        ensureBackpack(player);
    }

    /**
     * Puts a starter pack back where there is none. Never an upgrade: a player who is wearing a
     * better pack keeps it, and one who is wearing nothing gets the plainest one there is.
     */
    public boolean ensureBackpack(ServerPlayer player) {
        if (!equipment(player).backpack().isEmpty()) return false;
        setBackpack(player, new ItemStack(ModItems.STARTER_BACKPACK));
        return true;
    }

    /**
     * Gives a stack, or leaves it at the player's feet where there is nowhere to put it. Nowhere to
     * put it is the ordinary case on hardcore, where a fresh player has only the nine belt slots.
     */
    private static void giveOnce(ServerPlayer player, ItemStack stack) {
        if (player.getInventory().add(stack)) return;
        net.minecraft.world.entity.item.ItemEntity dropped = new net.minecraft.world.entity.item.ItemEntity(
                player.level(), player.getX(), player.getY(), player.getZ(), stack);
        dropped.setNoPickUpDelay();
        player.level().addFreshEntity(dropped);
    }

    /** Operator and test entry point. */
    public void clear(UUID playerId) {
        save.setEquipment(playerId, PlayerEquipment.empty());
    }
}
