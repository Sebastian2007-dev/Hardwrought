package de.ipnats.hardwrought.electricity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One point of a network: the wires made fast to it, and what was last worked out for it.
 *
 * <p>A wire is remembered by both of its ends. By itself this is a plain support — an insulator; a
 * lamp and a dynamo are the same thing with something between the wire and the ground.
 */
public class ElectricBlockEntity extends BlockEntity {
    public static final int MAX_WIRES = 4;

    /**
     * A wire from here to another point.
     *
     * @param hot whether it is carrying more than it is good for; drawn glowing
     */
    public record Wire(BlockPos other, Gauge gauge, boolean hot) {
        static final Codec<Wire> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("to").forGetter(Wire::other),
                Gauge.CODEC.fieldOf("gauge").forGetter(Wire::gauge),
                Codec.BOOL.optionalFieldOf("hot", false).forGetter(Wire::hot)).apply(instance, Wire::new));
    }

    private final List<Wire> wires = new ArrayList<>();
    /** How far each wire this end keeps watch over has cooked, by its other end. Not saved: a wire cools while nobody is there. */
    private final Map<BlockPos, Float> heat = new HashMap<>();
    private float volts;
    private float amps;
    private boolean wet;

    public ElectricBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public List<Wire> wires() {
        return List.copyOf(wires);
    }

    public Wire wireTo(BlockPos other) {
        for (Wire wire : wires) if (wire.other().equals(other)) return wire;
        return null;
    }

    /** Where its wires are made fast, in the world. */
    public Vec3 terminal() {
        Vec3 offset = getBlockState().getBlock() instanceof ElectricBlock block
                ? block.terminal(getBlockState()) : new Vec3(0.5, 0.5, 0.5);
        return Vec3.atLowerCornerOf(worldPosition).add(offset);
    }

    void attach(BlockPos other, Gauge gauge) {
        if (wireTo(other) != null) return;
        wires.add(new Wire(other.immutable(), gauge, false));
        changed();
    }

    void detach(BlockPos other) {
        if (wires.removeIf(wire -> wire.other().equals(other))) {
            heat.remove(other);
            changed();
        }
    }

    void setHot(BlockPos other, boolean hot) {
        for (int i = 0; i < wires.size(); i++) {
            Wire wire = wires.get(i);
            if (wire.other().equals(other) && wire.hot() != hot) {
                wires.set(i, new Wire(wire.other(), wire.gauge(), hot));
                changed();
            }
        }
    }

    float heat(BlockPos other) {
        return heat.getOrDefault(other, 0.0f);
    }

    void setHeat(BlockPos other, float value) {
        if (value <= 0.0f) heat.remove(other);
        else heat.put(other.immutable(), value);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The voltage here against the ground, as last worked out. */
    public float volts() {
        return volts;
    }

    /** The current through whatever this is: out of a dynamo, into a lamp. None through a plain support. */
    public float amps() {
        return amps;
    }

    /** Whether water or rain is on it, and current creeps away to the ground. */
    public boolean wet() {
        return wet;
    }

    // --- what it is, electrically; a plain support is nothing ---

    /** The voltage it drives, where it is a source. */
    public double emf() {
        return 0.0;
    }

    /** A source's own resistance, in ohms. */
    public double sourceOhms() {
        return 1.0;
    }

    /**
     * Whether wire is made fast to this block itself. A dynamo, a battery and a machine say no: they
     * are joined to a line through a {@link CoilBlock} set against them.
     */
    public boolean takesWires() {
        return true;
    }

    /** Whether a source has anything to give. An empty battery has not. */
    public boolean canGive() {
        return true;
    }

    /** Whether a source may be driven backwards. A battery with room in it is charged that way; a dynamo cuts out. */
    public boolean canTake() {
        return false;
    }

    /** What a meter says about this in particular, after the figures; null for nothing. */
    public net.minecraft.network.chat.Component note() {
        return null;
    }

    /** What it lets through to the ground as a consumer, in siemens: one over its resistance. */
    public double conductance() {
        return 0.0;
    }

    /** Whether rain on it makes current creep. A bare support is made for the weather; a machine is not. */
    public boolean mindsRain() {
        return false;
    }

    /**
     * Told what the network worked out.
     *
     * @param seconds how much time this answer stands for; 0 where the network was only looked at again
     *                because something about it changed
     */
    protected void solved(ServerLevel level, double volts, double amps, boolean wet, double seconds) {
        this.volts = (float) volts;
        this.amps = (float) amps;
        this.wet = wet;
    }

    /** A point going away takes its wires with it: they drop, and the other ends are freed. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!(level instanceof ServerLevel server)) return;
        Vec3 here = terminal();
        for (Wire wire : List.copyOf(wires)) {
            if (level.getBlockEntity(wire.other()) instanceof ElectricBlockEntity partner) {
                double length = here.distanceTo(partner.terminal());
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(wire.gauge().item(), wire.gauge().coils(length)));
                partner.detach(pos);
            }
        }
        List<Wire> freed = List.copyOf(wires);
        wires.clear();
        for (Wire wire : freed) Electricity.update(server, wire.other());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        wires.clear();
        input.read("wires", Wire.CODEC.listOf()).ifPresent(wires::addAll);
        volts = input.getFloatOr("volts", 0.0f);
        amps = input.getFloatOr("amps", 0.0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!wires.isEmpty()) output.store("wires", Wire.CODEC.listOf(), List.copyOf(wires));
        output.putFloat("volts", volts);
        output.putFloat("amps", amps);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
