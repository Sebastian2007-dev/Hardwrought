package de.ipnats.hardwrought.smeltery;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A faucet's pour: which metal is running, if any. Drawn by the client as a stream to the table. */
public class FaucetBlockEntity extends BlockEntity {
    /** Millibuckets a faucet runs per tick: an ingot in about a second. */
    public static final int FLOW = 8;

    private String pouring = "";

    public FaucetBlockEntity(BlockPos pos, BlockState state) {
        super(SmelteryBlocks.FAUCET_ENTITY, pos, state);
    }

    /** The metal running, or an empty string. */
    public String pouring() {
        return pouring;
    }

    private SmelteryControllerBlockEntity smeltery() {
        if (level == null) return null;
        BlockPos drain = worldPosition.relative(getBlockState().getValue(FaucetBlock.FACING).getOpposite());
        return SmelteryStructure.controllerOf(level, drain);
    }

    private CastingTableBlockEntity table() {
        return level != null && level.getBlockEntity(worldPosition.below()) instanceof CastingTableBlockEntity table ? table : null;
    }

    /** Opened by hand: starts pouring, if there is something to pour and somewhere for it to go. */
    public void open() {
        SmelteryControllerBlockEntity smeltery = smeltery();
        CastingTableBlockEntity table = table();
        if (smeltery == null || table == null) return;
        String metal = smeltery.bottomCastable();
        if (metal == null || !table.accepts(metal)) return;
        set(metal);
        level.playSound(null, worldPosition, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8f, 0.9f);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FaucetBlockEntity faucet) {
        if (faucet.pouring.isEmpty()) return;
        SmelteryControllerBlockEntity smeltery = faucet.smeltery();
        CastingTableBlockEntity table = faucet.table();
        if (smeltery == null || table == null || !faucet.pouring.equals(smeltery.bottomCastable())) {
            faucet.set("");
            return;
        }
        int wanted = table.room(faucet.pouring, FLOW);
        int poured = wanted <= 0 ? 0 : smeltery.drain(faucet.pouring, wanted);
        if (poured <= 0) {
            faucet.set("");
            return;
        }
        table.fill(faucet.pouring, poured);
    }

    private void set(String metal) {
        if (metal.equals(pouring)) return;
        pouring = metal;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pouring = input.getStringOr("pouring", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("pouring", pouring);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
