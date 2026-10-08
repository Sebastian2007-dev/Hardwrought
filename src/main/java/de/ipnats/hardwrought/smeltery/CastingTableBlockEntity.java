package de.ipnats.hardwrought.smeltery;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.smithing.ForgeQuality;
import de.ipnats.hardwrought.smithing.Heat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A casting table: a cast on it, filled by a faucet, sets into a bar or a part. What sets comes out
 * hot — cast metal is as hot as anything out of a forge — and a part comes out rough (see {@link Casts}).
 *
 * <p>It is also where a cast gets its shape: a blank lying on it takes the shape of the bar or the
 * part pressed into it.
 *
 * <p>To a hopper the table is one place, holding what has set: a hopper under it draws the piece out
 * and leaves the cast where it lies, and nothing can be put in from outside.
 */
public class CastingTableBlockEntity extends BlockEntity implements net.minecraft.world.WorldlyContainer {
    private static final int[] SET_PIECE = {0}, NOTHING = {};

    /** Ticks a full cast takes to set. */
    public static final int SETTING_TICKS = 40;
    /** How hot a fresh casting is, as a share of its metal's melting point. */
    static final double CAST_HEAT = 0.7;

    private ItemStack cast = ItemStack.EMPTY;
    private ItemStack result = ItemStack.EMPTY;
    private String metal = "";
    private int amount;
    private int setting;

    public CastingTableBlockEntity(BlockPos pos, BlockState state) {
        super(SmelteryBlocks.CASTING_TABLE_ENTITY, pos, state);
    }

    public ItemStack cast() { return cast; }

    public ItemStack result() { return result; }

    public String metal() { return metal; }

    public int amount() { return amount; }

    /** Whether this metal could be poured in now. */
    public boolean accepts(String material) {
        Casts.Cast shape = Casts.of(cast);
        return shape != null && result.isEmpty() && shape.result(material) != null
                && (metal.isEmpty() || metal.equals(material)) && amount < shape.amount();
    }

    /** Whether a fired cast lies here with nothing standing or set in it. */
    public boolean ready() {
        return Casts.of(cast) != null && result.isEmpty();
    }

    /** How much of this metal would still go in, up to the given amount. */
    public int room(String material, int max) {
        return accepts(material) ? Math.min(max, Casts.of(cast).amount() - amount) : 0;
    }

    public void fill(String material, int poured) {
        metal = material;
        amount += poured;
        changed();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CastingTableBlockEntity table) {
        Casts.Cast shape = Casts.of(table.cast);
        if (shape == null || table.amount < shape.amount() || !table.result.isEmpty()) return;
        if (++table.setting < SETTING_TICKS) return;
        Item made = shape.result(table.metal);
        if (made == null) return;
        ItemStack bar = new ItemStack(made);
        if (shape.part() != null) {
            bar.set(ModDataComponents.FORGE_QUALITY, new ForgeQuality(Casts.CAST_CRAFTSMANSHIP, ForgeQuality.Treatment.AIR));
        }
        var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(level.getServer());
        if (runtime != null) {
            double melting = MoltenMetals.meltingPoint(table.metal, runtime.materials());
            if (melting < Double.MAX_VALUE) bar.set(ModDataComponents.HEAT, new Heat((float) (melting * CAST_HEAT), level.getGameTime()));
        }
        table.result = bar;
        table.metal = "";
        table.amount = 0;
        table.setting = 0;
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.4f);
        table.changed();
    }

    /** Puts a cast, or a blank that is to become one, on the table. */
    public boolean placeCast(ItemStack stack) {
        if (!cast.isEmpty() || !Casts.liesOnTable(stack)) return false;
        cast = stack.split(1);
        changed();
        return true;
    }

    /**
     * Presses a bar or a part into the blank lying here, which takes its shape; the piece itself is
     * not used up. Returns the cast it left, or null where nothing happened.
     */
    public Casts.Cast imprint(ItemStack pressed) {
        Casts.Cast shape = cast.is(Casts.BLANK) ? Casts.imprintOf(pressed) : null;
        if (shape == null) return null;
        cast = new ItemStack(shape.unfired());
        if (level != null) level.playSound(null, worldPosition, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.7f, 1.3f);
        changed();
        return shape;
    }

    /** What an empty hand takes: the bar if there is one, else the cast while nothing is poured in. */
    public ItemStack take() {
        if (!result.isEmpty()) {
            ItemStack taken = result;
            result = ItemStack.EMPTY;
            changed();
            return taken;
        }
        if (!cast.isEmpty() && amount == 0) {
            ItemStack taken = cast;
            cast = ItemStack.EMPTY;
            changed();
            return taken;
        }
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- what a hopper sees

    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        return side == net.minecraft.core.Direction.DOWN ? SET_PIECE : NOTHING;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) {
        return side == net.minecraft.core.Direction.DOWN;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return result.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? result : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot != 0 || result.isEmpty() || count <= 0) return ItemStack.EMPTY;
        ItemStack taken = result.split(count);
        changed();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack taken = result;
        result = ItemStack.EMPTY;
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        result = stack;
        changed();
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        result = ItemStack.EMPTY;
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cast = input.read("cast", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        result = input.read("result", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        metal = input.getStringOr("metal", "");
        amount = input.getIntOr("amount", 0);
        setting = input.getIntOr("setting", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!cast.isEmpty()) output.store("cast", ItemStack.CODEC, cast);
        if (!result.isEmpty()) output.store("result", ItemStack.CODEC, result);
        output.putString("metal", metal);
        output.putInt("amount", amount);
        output.putInt("setting", setting);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            if (!cast.isEmpty()) Block.popResource(level, pos, cast);
            if (!result.isEmpty()) Block.popResource(level, pos, result);
        }
    }
}
