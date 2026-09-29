package de.ipnats.hardwrought.building;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The two blocks that share one cell: the half block that was there and the thin one set into it. */
public class SharedCellBlockEntity extends BlockEntity {
    private BlockState host = Blocks.AIR.defaultBlockState();
    private BlockState insert = Blocks.AIR.defaultBlockState();

    public SharedCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHARED_CELL, pos, state);
    }

    public BlockState host() { return host; }

    public BlockState insert() { return insert; }

    /** Replaces both parts and tells the clients; a no-op when nothing changed. */
    public void set(BlockState host, BlockState insert) {
        if (host.equals(this.host) && insert.equals(this.insert)) return;
        this.host = host;
        this.insert = insert;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        host = input.read("host", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
        insert = input.read("insert", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("host", BlockState.CODEC, host);
        output.store("insert", BlockState.CODEC, insert);
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
