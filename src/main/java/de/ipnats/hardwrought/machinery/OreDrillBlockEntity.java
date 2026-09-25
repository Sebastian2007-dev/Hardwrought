package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.geology.DrillTier;
import de.ipnats.hardwrought.geology.DrillYield;
import de.ipnats.hardwrought.geology.Geology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The ore drill: a drill head with a frame round it, three by three and two high.
 *
 * <pre>
 *   layer 2   F F F        F  a drill frame, of any of the five metals
 *             F F F        D  the drill head, driven by a line from any side
 *             F F F
 *   layer 1   F F F
 *             F D F
 *             F F F
 * </pre>
 *
 * <p>The frame's weakest block is the drill's tier (see {@link DrillTier}), and the tier decides which
 * of the ores in the chunk under it the drill can reach — the chunk's own mix, see {@link DrillYield}.
 * It works at a rate, not through an amount: one piece of ore every so often for as long as it is
 * turned, faster when turned faster, faster over a body of ore, and for ever.
 */
public class OreDrillBlockEntity extends BlockEntity implements WorldlyContainer, KineticHolder, MenuProvider {
    public static final int SIZE = 9;
    /** Blocks in a complete frame: eight round the head, nine above it. */
    public static final int FRAME_BLOCKS = 17;
    /** The speed the drill is built for. */
    public static final float RATED_SPEED = 16.0f;
    /** Strength per turn per minute, rising with the tier: 160 at 16 RPM for bronze, a hand crank's worth. */
    public static final float BASE_IMPACT = 6.0f;
    public static final float IMPACT_PER_TIER = 4.0f;
    /** How often the frame is looked over, in ticks. */
    private static final int CHECK_INTERVAL = 40;
    private static final int[] ALL = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private float speed;
    private float progress;
    /** The tier of the frame as last found, or null where the frame is not complete. */
    private DrillTier tier;
    /** Frame blocks missing when last looked. */
    private int missing = -1;
    private DrillYield yield;

    public OreDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORE_DRILL, pos, state);
    }

    /** Where the frame of a drill head at this position has to be: eight round it, nine above. */
    public static List<BlockPos> framePositions(BlockPos head) {
        List<BlockPos> positions = new ArrayList<>(17);
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dy == 0 && dx == 0 && dz == 0) continue;
                    positions.add(head.offset(dx, dy, dz));
                }
            }
        }
        return positions;
    }

    /** Where the head of the drill a frame block belongs to is, from the frame block and its part number. */
    public static BlockPos headOf(BlockPos frame, int part) {
        BlockPos offset = framePositions(BlockPos.ZERO).get(part - 1);
        return frame.subtract(offset);
    }

    /**
     * Draws the drill whole or takes it apart again: every frame block round the head is told its part
     * of the finished drill, or that it is a loose frame once more, and the head whether it is closed in.
     */
    public static void shape(Level level, BlockPos head, boolean formed) {
        List<BlockPos> frame = framePositions(head);
        for (int i = 0; i < frame.size(); i++) {
            BlockPos pos = frame.get(i);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DrillFrameBlock)) continue;
            int part = formed ? i + 1 : 0;
            if (state.getValue(DrillFrameBlock.PART) != part) {
                level.setBlock(pos, state.setValue(DrillFrameBlock.PART, part), Block.UPDATE_CLIENTS);
            }
        }
        BlockState headState = level.getBlockState(head);
        if (headState.getBlock() instanceof OreDrillBlock && headState.getValue(OreDrillBlock.FORMED) != formed) {
            level.setBlock(head, headState.setValue(OreDrillBlock.FORMED, formed), Block.UPDATE_CLIENTS);
        }
    }

    /** The tier a complete frame gives, or null where a block of it is missing. Counts what is missing. */
    public static DrillTier frameTier(Level level, BlockPos head, int[] missingOut) {
        DrillTier weakest = null;
        int missing = 0;
        for (BlockPos pos : framePositions(head)) {
            if (level.getBlockState(pos).getBlock() instanceof DrillFrameBlock frame) {
                if (weakest == null || frame.tier().level() < weakest.level()) weakest = frame.tier();
            } else {
                missing++;
            }
        }
        if (missingOut != null && missingOut.length > 0) missingOut[0] = missing;
        return missing == 0 ? weakest : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OreDrillBlockEntity drill) {
        if (!(level instanceof ServerLevel server)) return;
        if (drill.missing < 0 || level.getGameTime() % CHECK_INTERVAL == 0) drill.lookOverFrame(server, pos);
        boolean working = drill.tier != null && drill.speed != 0.0f && drill.yield != null && !drill.yield.barren()
                && drill.hasRoom();
        if (working) {
            drill.progress += Math.abs(drill.speed) / RATED_SPEED;
            if (drill.progress >= drill.yield.intervalTicks()) {
                drill.progress = 0;
                drill.bringUp(server, pos);
            }
            drill.setChanged();
        }
        if (state.getValue(OreDrillBlock.RUNNING) != working) {
            level.setBlock(pos, state.setValue(OreDrillBlock.RUNNING, working), Block.UPDATE_CLIENTS);
        }
    }

    private void lookOverFrame(ServerLevel level, BlockPos pos) {
        int[] counted = new int[1];
        DrillTier found = frameTier(level, pos, counted);
        missing = counted[0];
        shape(level, pos, found != null);
        if (found != tier) {
            tier = found;
            // A heavier frame is a heavier load on the line.
            Kinetics.update(level, pos);
        }
        yield = tier == null ? null : DrillYield.forChunk(level.getSeed(), Geology.profiles(level.getServer()),
                pos.getX(), pos.getZ(), tier);
    }

    private boolean hasRoom() {
        for (ItemStack stack : items) {
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    private void bringUp(ServerLevel level, BlockPos pos) {
        Identifier ore = yield.roll(level.getRandom());
        if (ore == null) return;
        Item item = BuiltInRegistries.ITEM.getValue(ore);
        if (item == null) return;
        ItemStack piece = new ItemStack(item);
        for (int slot = 0; slot < SIZE && !piece.isEmpty(); slot++) {
            ItemStack there = items.get(slot);
            if (there.isEmpty()) {
                items.set(slot, piece);
                piece = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(there, piece) && there.getCount() < there.getMaxStackSize()) {
                there.grow(1);
                piece = ItemStack.EMPTY;
            }
        }
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.5f, 0.7f);
    }

    /** How strongly the drill loads its line per turn, by its tier. Nothing while its frame is incomplete. */
    public float impact() {
        return tier == null ? BASE_IMPACT : BASE_IMPACT + IMPACT_PER_TIER * tier.level();
    }

    /** What reading the drill tells: its tier and state. */
    public Component describe() {
        if (tier == null) return Component.translatable("message.hardwrought.ore_drill.incomplete", Math.max(0, missing));
        Component tierName = Component.translatable("drill_tier.hardwrought." + tier.serializedName());
        if (yield == null || yield.barren()) return Component.translatable("message.hardwrought.ore_drill.barren", tierName);
        if (speed == 0.0f) return Component.translatable("message.hardwrought.ore_drill.still", tierName);
        double seconds = yield.intervalTicks() / 20.0 * RATED_SPEED / Math.abs(speed);
        return Component.translatable("message.hardwrought.ore_drill.working", tierName,
                String.format(Locale.ROOT, "%.0f", seconds));
    }

    /** What the chunk under the drill holds for it, largest share first, as lines for the chat. */
    public List<Component> composition() {
        List<Component> lines = new ArrayList<>();
        if (yield == null || yield.barren()) return lines;
        List<DrillYield.Entry> entries = new ArrayList<>(yield.entries());
        entries.sort((first, second) -> Integer.compare(second.weight(), first.weight()));
        for (DrillYield.Entry entry : entries) {
            Item item = BuiltInRegistries.ITEM.getValue(entry.ore());
            lines.add(Component.translatable("message.hardwrought.ore_drill.share",
                    new ItemStack(item).getHoverName(),
                    String.format(Locale.ROOT, "%.1f", yield.share(entry.ore()) * 100)));
        }
        return lines;
    }

    // ---------------------------------------------------------------- for tests

    public DrillTier tier() {
        return tier;
    }

    public DrillYield yield() {
        return yield;
    }

    /** Looks the frame over now rather than on the next check: when it is used, or a frame block comes or goes. */
    public void lookOverFrameNow() {
        if (level instanceof ServerLevel server) lookOverFrame(server, worldPosition);
    }

    // ---------------------------------------------------------------- menu, kinetics, saving

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.hardwrought.ore_drill");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DispenserMenu(id, inventory, this);
    }

    @Override
    public float kineticSpeed() {
        return speed;
    }

    @Override
    public void setKineticSpeed(float speed) {
        this.speed = speed;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        speed = input.getFloatOr("speed", 0.0f);
        progress = input.getFloatOr("progress", 0.0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putFloat("speed", speed);
        output.putFloat("progress", progress);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    /** Nothing goes in: what is in it came up out of the ground. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }
}
