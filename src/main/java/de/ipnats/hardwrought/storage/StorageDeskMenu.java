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
import de.ipnats.hardwrought.progression.BenchTier;
import de.ipnats.hardwrought.progression.KeptGridBlockEntity;
import de.ipnats.hardwrought.progression.KeptGridUser;
import de.ipnats.hardwrought.progression.WorkshopBenchBlock;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.gamerules.GameRules;

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
 *
 * <p>With a workshop bench set against the desk ({@link WorkshopBenchBlock}), the desk has that bench's
 * grid beside its list: three by three and a result, the same grid the bench keeps when it is opened on
 * its own. What is made there is made as at the bench, and after every craft each square it emptied is
 * filled again out of the store room with what lay there, so the same thing can be made again and
 * again without fetching anything.
 */
public class StorageDeskMenu extends AbstractContainerMenu implements KeptGridUser {
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
    /** Where the bench's grid and its result stand, on the panel to the right of the desk. */
    public static final int PANEL_X = 176, PANEL_WIDTH = 82, CRAFT_X = PANEL_X + 11, CRAFT_Y = 30;
    public static final int RESULT_X = CRAFT_X + 18, RESULT_Y = CRAFT_Y + 3 * 18 + 22;
    /** The result, then the nine squares, after whatever slots the inventory brings (a pack adds its own). */
    public final int resultSlot, gridStart, gridEnd;

    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    /** 1 while a workshop bench stands against the desk and its grid is open here; synced, so the client shows it. */
    private final DataSlot benchHere = DataSlot.standalone();
    /** The bench whose grid this is; null on the client, and where there is none. */
    private KeptGridBlockEntity bench;
    /** Set while the grid is being filled from the bench or from the store room, when nothing need be worked out yet. */
    private boolean loading;

    /** Null on the client: its side only draws what it is sent. */
    private final ServerLevel level;
    private final BlockPos pos;
    private final Player player;
    private Map<ItemVariant, Integer> sent;
    private int sentStores = -1;
    private boolean sentPowered;
    private int sentMakers;
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
        resultSlot = slots.size();
        gridStart = resultSlot + 1;
        gridEnd = gridStart + 9;
        addSlot(new ResultSlot(player, craftSlots, resultSlots, 0, RESULT_X, RESULT_Y) {
            @Override
            public boolean isActive() {
                return benchHere();
            }

            @Override
            public void onTake(Player taker, ItemStack made) {
                List<ItemStack> before = craftSlots.getItems().stream().map(ItemStack::copy).toList();
                super.onTake(taker, made);
                refill(before);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(craftSlots, column + row * 3, CRAFT_X + column * 18, CRAFT_Y + row * 18) {
                    @Override
                    public boolean isActive() {
                        return benchHere();
                    }
                });
            }
        }
        addDataSlot(benchHere);
        if (level != null) takeBench();
    }

    /** Whether the bench's grid is open here. */
    public boolean benchHere() {
        return benchHere.get() == 1;
    }

    @Override
    public boolean isAt(KeptGridBlockEntity other) {
        return bench != null && bench == other;
    }

    /** Takes the grid of a workshop bench standing against the desk, unless somebody is working at it. */
    private void takeBench() {
        BlockPos at = WorkshopBenchBlock.beside(level, pos);
        if (at == null || !(level.getBlockEntity(at) instanceof KeptGridBlockEntity grid)) return;
        if (!(player instanceof ServerPlayer worker) || !grid.claim(worker)) return;
        bench = grid;
        benchHere.set(1);
        loading = true;
        List<ItemStack> kept = grid.contents();
        for (int slot = 0; slot < Math.min(kept.size(), craftSlots.getContainerSize()); slot++) craftSlots.setItem(slot, kept.get(slot));
        loading = false;
        craft();
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container != craftSlots || loading || bench == null) return;
        craft();
        if (!bench.isRemoved()) bench.store(craftSlots.getItems());
    }

    /** What the grid makes, as a nailed bench would make it: the first recipe by name the bench is good enough for. */
    private void craft() {
        if (level == null || bench == null) return;
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack made = ItemStack.EMPTY;
        RecipeHolder<CraftingRecipe> used = null;
        if (!input.isEmpty()) {
            int tier = BenchTier.of(bench.getBlockState());
            var map = ((de.ipnats.hardwrought.mixin.RecipeManagerAccessor) level.getServer().getRecipeManager()).hardwrought$recipeMap();
            boolean limited = level.getGameRules().get(GameRules.LIMITED_CRAFTING);
            for (RecipeHolder<CraftingRecipe> holder : map.getRecipesFor(RecipeType.CRAFTING, input, level)
                    .sorted(java.util.Comparator.comparing(holder -> holder.id().identifier().toString())).toList()) {
                if (limited && !holder.value().isSpecial() && player instanceof ServerPlayer known
                        && !known.getRecipeBook().contains(holder.id())) continue;
                ItemStack result = holder.value().assemble(input);
                if (result.isEmpty() || !result.isItemEnabled(level.enabledFeatures()) || !BenchTier.allows(tier, result)) continue;
                made = result;
                used = holder;
                break;
            }
        }
        resultSlots.setRecipeUsed(used);
        resultSlots.setItem(0, made);
    }

    /**
     * After a craft: every square the craft emptied is filled again out of the store room with what lay
     * in it before, one of it, so the grid stands ready to make the same again. Nothing is fetched without
     * current, and a square the store room has none for stays empty.
     */
    private void refill(List<ItemStack> before) {
        if (level == null || bench == null || !powered()) return;
        StorageNetwork network = StorageNetwork.find(level, pos);
        loading = true;
        for (int slot = 0; slot < before.size(); slot++) {
            ItemStack was = before.get(slot);
            if (was.isEmpty() || !craftSlots.getItem(slot).isEmpty()) continue;
            ItemStack one = network.extract(was, 1);
            if (!one.isEmpty()) craftSlots.setItem(slot, one);
        }
        loading = false;
        slotsChanged(craftSlots);
        sync(network);
    }

    /** Everything on the grid back into the store room, or the inventory where the store room will not take it. */
    public void clearGrid() {
        if (level == null || bench == null || !stillValid(player)) return;
        StorageNetwork network = powered() ? StorageNetwork.find(level, pos) : null;
        loading = true;
        for (int slot = 0; slot < craftSlots.getContainerSize(); slot++) {
            ItemStack there = craftSlots.removeItemNoUpdate(slot);
            if (there.isEmpty()) continue;
            if (network != null) there = network.insert(there);
            if (!there.isEmpty() && !player.getInventory().add(there)) player.drop(there, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        loading = false;
        slotsChanged(craftSlots);
        if (network != null) sync(network);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (bench == null) return;
        // The grid lives on the bench, and stays there; it is not handed back into the inventory.
        if (!bench.isRemoved()) bench.store(craftSlots.getItems());
        loading = true;
        craftSlots.clearContent();
        loading = false;
        bench.release(player);
    }

    /** Touching the class registers the menu type. */
    public static void initialize() { }

    @Override
    public void broadcastChanges() {
        // A bench broken while its grid is open here has dropped what lay on it: the copy here goes.
        if (bench != null && (bench.isRemoved() || WorkshopBenchBlock.beside(level, pos) == null)) {
            if (bench.isRemoved()) {
                loading = true;
                craftSlots.clearContent();
                resultSlots.clearContent();
                loading = false;
            } else {
                bench.store(craftSlots.getItems());
                loading = true;
                craftSlots.clearContent();
                resultSlots.clearContent();
                loading = false;
                bench.release(player);
            }
            bench = null;
            benchHere.set(0);
        }
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
        int makers = powered ? network.makers().size() : 0;
        if (held.equals(sent) && network.stores() == sentStores && powered == sentPowered && makers == sentMakers) return;
        sentMakers = makers;
        sent = held;
        sentStores = network.stores();
        sentPowered = powered;
        List<StoragePayloads.Entry> entries = new ArrayList<>();
        for (Map.Entry<ItemVariant, Integer> kind : held.entrySet()) {
            if (entries.size() >= StoragePayloads.MAX_ENTRIES) break;
            entries.add(new StoragePayloads.Entry(kind.getKey().toStack(), kind.getValue()));
        }
        // What an assembler on the network makes and the store room has none of: shown at nought, and a click orders it.
        if (powered) {
            for (StorageNetwork.Maker maker : network.makers()) {
                ItemStack made = maker.makes().copyWithCount(1);
                if (entries.size() >= StoragePayloads.MAX_ENTRIES || held.containsKey(ItemVariant.of(made))) continue;
                entries.add(new StoragePayloads.Entry(made, 0));
            }
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
        if (action == StoragePayloads.CLEAR_GRID) {
            clearGrid();
            return;
        }
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
        } else if (!clicked.isEmpty() && network.count(clicked) == 0 && network.maker(clicked) != null) {
            // None in store, and something on the network makes it: ordered, one, or a stack on a shift-click.
            int count = action == StoragePayloads.QUICK ? clicked.getMaxStackSize() : 1;
            if (network.maker(clicked).order(pos, count) && player instanceof ServerPlayer orderer) {
                orderer.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.hardwrought.storage.ordered",
                        count, clicked.getHoverName()));
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

    /**
     * Shift-click in the inventory puts the stack away. On the result it makes as many as fit into the
     * inventory, the grid filling again after each; on the grid it puts what lies there away.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == resultSlot) return quickCraft(player);
        if (index >= gridStart && index < gridEnd) return quickClear(player, index);
        if (level == null || !powered()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        StorageNetwork network = StorageNetwork.find(level, pos);
        slot.setByPlayer(network.insert(slot.getItem()));
        sync(network);
        // Nothing to repeat: what did not fit will not fit a second time either.
        return ItemStack.EMPTY;
    }

    /** One craft into the inventory, as the crafting table does it; the click repeats it while it can. */
    private ItemStack quickCraft(Player player) {
        Slot slot = slots.get(resultSlot);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        stack.getItem().onCraftedBy(stack, player);
        if (!moveItemStackTo(stack, 0, MAIN_SLOTS, true)) return ItemStack.EMPTY;
        slot.onQuickCraft(stack, before);
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        player.drop(stack, false, net.minecraft.util.Prediction.PREDICTED);
        return before;
    }

    /** A square of the grid shift-clicked: into the store room, or the inventory without current. */
    private ItemStack quickClear(Player player, int index) {
        Slot slot = slots.get(index);
        // Where it goes is the server's to say: the client cannot see the store room's room.
        if (level == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack there = slot.getItem();
        if (powered()) {
            StorageNetwork network = StorageNetwork.find(level, pos);
            slot.setByPlayer(network.insert(there));
            sync(network);
            return ItemStack.EMPTY;
        }
        if (!moveItemStackTo(there, 0, MAIN_SLOTS, false)) return ItemStack.EMPTY;
        slot.setChanged();
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null) return true;
        return level.getBlockState(pos).getBlock() instanceof StorageDeskBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQR;
    }
}
