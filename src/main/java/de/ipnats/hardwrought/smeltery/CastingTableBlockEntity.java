package de.ipnats.hardwrought.smeltery;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
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
 * A casting table: an ingot cast on it, filled by a faucet, sets into a bar. The bar comes out hot —
 * cast metal is as hot as anything out of a forge.
 */
public class CastingTableBlockEntity extends BlockEntity {
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
        return !cast.isEmpty() && result.isEmpty() && MoltenMetals.ingot(material) != null
                && (metal.isEmpty() || metal.equals(material)) && amount < MoltenMetals.INGOT;
    }

    /** How much of this metal would still go in, up to the given amount. */
    public int room(String material, int max) {
        return accepts(material) ? Math.min(max, MoltenMetals.INGOT - amount) : 0;
    }

    public void fill(String material, int poured) {
        metal = material;
        amount += poured;
        changed();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CastingTableBlockEntity table) {
        if (table.amount < MoltenMetals.INGOT || !table.result.isEmpty()) return;
        if (++table.setting < SETTING_TICKS) return;
        Item ingot = MoltenMetals.ingot(table.metal);
        ItemStack bar = new ItemStack(ingot);
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

    /** Puts a cast on the table. */
    public boolean placeCast(ItemStack stack) {
        if (!cast.isEmpty() || !stack.is(SmelteryBlocks.INGOT_CAST)) return false;
        cast = stack.split(1);
        changed();
        return true;
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
