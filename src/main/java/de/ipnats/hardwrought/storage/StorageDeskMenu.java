package de.ipnats.hardwrought.storage;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.networking.StoragePayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A storage desk opened: everything in the chests it reaches, like with like, above the player's
 * own inventory.
 *
 * <p>The upper half is not made of slots. A slot is a place, and what the desk shows is a count —
 * forty stone lying in three chests — so the list travels as a packet of its own and a click on it
 * travels back as what was clicked rather than where. The server then does the fetching and the
 * putting away, and sends the list again.
 */
public class StorageDeskMenu extends AbstractContainerMenu {
    public static final MenuType<StorageDeskMenu> TYPE = Registry.register(BuiltInRegistries.MENU,
            Hardwrought.id("storage_desk"), new MenuType<>(StorageDeskMenu::new, FeatureFlags.VANILLA_SET));

    /** Where the list stands, and how much of it shows at once. */
    public static final int GRID_X = 8, GRID_Y = 18, COLUMNS = 9, ROWS = 6;
    public static final int INVENTORY_Y = 140;
    /** How often the list is looked over again while the desk stands open, in ticks. */
    private static final int REFRESH_TICKS = 10;
    private static final double REACH_SQR = 8.0 * 8.0;
    /** The belt and the three rows: what a notch of the wheel may take from. */
    private static final int MAIN_SLOTS = 36;

    /** Null on the client: its side only draws what it is sent. */
    private final ServerLevel level;
    private final BlockPos pos;
    private final Player player;
    private Map<ItemVariant, Integer> sent;
    private int sentStores = -1;
    private boolean sentPowered;
    private int ticks;

    /** The client's side. */
    public StorageDeskMenu(int id, Inventory inventory) {
        this(id, inventory, null, BlockPos.ZERO);
    }

    public StorageDeskMenu(int id, Inventory inventory, ServerLevel level, BlockPos pos) {
        super(TYPE, id);
        this.level = level;
        this.pos = pos;
        this.player = inventory.player;
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
    }

    /** Touching the class registers the menu type. */
    public static void initialize() { }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // A hopper fills a chest and another desk empties it whether or not this one is looking.
        if (level != null && ticks++ % REFRESH_TICKS == 0) sync(StorageNetwork.find(level, pos));
    }

    /** Whether the desk has current to work with. */
    private boolean powered() {
        return level.getBlockEntity(pos) instanceof StorageDeskBlockEntity desk && desk.powered();
    }

    private void sync(StorageNetwork network) {
        if (!(player instanceof ServerPlayer viewer)) return;
        boolean powered = powered();
        // A desk without current shows nothing: the works that would look are standing still.
        Map<ItemVariant, Integer> held = powered ? network.contents() : Map.of();
        if (held.equals(sent) && network.stores() == sentStores && powered == sentPowered) return;
        sent = held;
        sentStores = network.stores();
        sentPowered = powered;
        List<StoragePayloads.Entry> entries = new ArrayList<>();
        for (Map.Entry<ItemVariant, Integer> kind : held.entrySet()) {
            if (entries.size() >= StoragePayloads.MAX_ENTRIES) break;
            entries.add(new StoragePayloads.Entry(kind.getKey().toStack(), kind.getValue()));
        }
        if (ServerPlayNetworking.canSend(viewer, StoragePayloads.Contents.TYPE)) {
            ServerPlayNetworking.send(viewer, new StoragePayloads.Contents(containerId, entries, network.stores(), powered));
        }
    }

    /**
     * A click on the list. With something on the cursor it is put away, whatever was clicked; with
     * an empty hand the thing clicked is fetched.
     */
    public void click(ItemStack clicked, int action) {
        if (level == null || !stillValid(player) || !powered()) return;
        StorageNetwork network = StorageNetwork.find(level, pos);
        ItemStack carried = getCarried();
        if (action == StoragePayloads.PUT_ONE || action == StoragePayloads.FETCH_ONE) {
            if (!clicked.isEmpty()) notch(network, clicked, action == StoragePayloads.FETCH_ONE);
        } else if (!carried.isEmpty()) {
            if (action == StoragePayloads.HALF) {
                // Right click sets one down, as it does in a chest.
                ItemStack one = carried.split(1);
                if (!network.insert(one).isEmpty()) carried.grow(1);
            } else {
                setCarried(network.insert(carried));
            }
        } else if (!clicked.isEmpty()) {
            int stack = clicked.getMaxStackSize();
            if (action == StoragePayloads.QUICK) {
                ItemStack fetched = network.extract(clicked, stack);
                player.getInventory().add(fetched);
                // What the player has no room for goes back where it came from.
                if (!fetched.isEmpty()) fetched = network.insert(fetched);
                if (!fetched.isEmpty()) player.drop(fetched, false, net.minecraft.util.Prediction.SERVER_ONLY);
            } else {
                int wanted = action == StoragePayloads.HALF
                        ? (Math.min(stack, network.count(clicked)) + 1) / 2 : stack;
                setCarried(network.extract(clicked, wanted));
            }
        }
        sync(network);
    }

    /**
     * One notch of the wheel over a kind of thing: one of it out of the store room into the
     * inventory, or one out of the inventory into the store room.
     */
    private void notch(StorageNetwork network, ItemStack kind, boolean fetch) {
        Inventory inventory = player.getInventory();
        if (fetch) {
            ItemStack one = network.extract(kind, 1);
            if (one.isEmpty()) return;
            inventory.add(one);
            // No room for it: it goes back where it came from.
            if (!one.isEmpty()) network.insert(one);
            return;
        }
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            ItemStack there = inventory.getItem(slot);
            if (!ItemStack.isSameItemSameComponents(there, kind)) continue;
            if (network.insert(there.copyWithCount(1)).isEmpty()) there.shrink(1);
            return;
        }
    }

    /** Shift-click in the inventory puts the stack away. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (level == null || !powered()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        StorageNetwork network = StorageNetwork.find(level, pos);
        slot.setByPlayer(network.insert(slot.getItem()));
        sync(network);
        // Nothing to repeat: what did not fit will not fit a second time either.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null) return true;
        return level.getBlockState(pos).getBlock() instanceof StorageDeskBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQR;
    }
}
