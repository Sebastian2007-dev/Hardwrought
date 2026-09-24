package de.ipnats.hardwrought.machinery;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The speed a turning part runs at, and on the client how far round it has got.
 *
 * <p>The speed is saved and sent to the client whenever it changes; the angle is the client's own,
 * accumulated tick by tick so that a part starts from where it stood and stops where it got to.
 */
public class KineticBlockEntity extends BlockEntity implements KineticHolder {
    /** Degrees per tick for each turn per minute: 360 degrees over 1200 ticks. */
    public static final float DEGREES_PER_TICK_PER_RPM = 0.3f;

    private float speed;
    /** Client side only. */
    private float angle;

    public KineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public float kineticSpeed() {
        return speed;
    }

    @Override
    public void setKineticSpeed(float speed) {
        if (Math.abs(this.speed - speed) < 1e-4f) return;
        this.speed = speed;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Where the part stands, in degrees. */
    public float angle() {
        return angle;
    }

    /** Degrees it travels in one tick, signed. */
    public float degreesPerTick() {
        return speed * DEGREES_PER_TICK_PER_RPM;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, KineticBlockEntity part) {
        if (part.speed == 0.0f) return;
        part.angle = (part.angle + part.degreesPerTick()) % 360.0f;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        speed = input.getFloatOr("speed", 0.0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("speed", speed);
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
