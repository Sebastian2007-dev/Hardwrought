package de.ipnats.hardwrought.magic;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A sigil at work: the program its drawing describes, kept with the block, and carried out on the
 * server tick by tick. With its arrows cancelling out, the element hovers above the sigil and acts on
 * whatever comes into it; with a direction, the sigil sends its element off that way, again and
 * again. With a square it waits for someone to come near, and only so many times.
 *
 * <p>The drawing itself is kept too, so the clients can lay it on the ground as it was drawn.
 */
public class SigilBlockEntity extends BlockEntity {
    /** On the outer circle's scale: how far the drawing reaches from the middle of the block. */
    public static final float RADIUS = 1.4f;
    /** How high above the sigil a hovering element stands. */
    public static final double HOVER = 1.3;
    /** How long a sigil keeps working, in ticks: twenty minutes for one that works all the time. */
    static final int LIFE = 20 * 60 * 20;
    /** And an hour for one that waits, which also only goes off so many times. */
    static final int WAITING_LIFE = 20 * 60 * 60;
    static final int CHARGES = 8;
    /** How close someone must come to a waiting sigil, in blocks. */
    static final double NEAR = 2.5;

    private static final Codec<List<List<Float>>> LINES = Codec.list(Codec.list(Codec.FLOAT));

    /** Set by the client: keeps the sigil's look up to date, and takes it away. */
    public static Consumer<SigilBlockEntity> clientTicker = sigil -> { };
    public static Consumer<SigilBlockEntity> clientRemoved = sigil -> { };

    Rune element;
    boolean inverted;
    /** Where it sends its element, level, or nothing for an element that hovers. */
    float dirX, dirZ;
    /** Which way was ahead for the one who inscribed it: the drawing's up. */
    float forwardX = 0, forwardZ = 1;
    SigilDesign.Shape shape = SigilDesign.Shape.POINT;
    boolean presence;
    boolean pulse;
    float power = 1;
    float area = 1.2f;
    UUID owner;
    long until;
    int charges = CHARGES;
    /** When an unstable sigil gives way, or -1. */
    long unstableAt = -1;
    List<float[]> lines = List.of();
    private long nextAction;
    private boolean wasNear;

    public SigilBlockEntity(BlockPos pos, BlockState state) {
        super(Magic.SIGIL_ENTITY, pos, state);
    }

    // --- What it is ------------------------------------------------------------------------------

    public Rune element() {
        return element;
    }

    public boolean inverted() {
        return inverted;
    }

    public boolean directed() {
        return dirX * dirX + dirZ * dirZ > 1e-4;
    }

    public boolean waiting() {
        return presence;
    }

    public SigilDesign.Shape shape() {
        return shape;
    }

    public float forwardX() {
        return forwardX;
    }

    public float forwardZ() {
        return forwardZ;
    }

    /** The drawing, its outer circle of radius 1, up the page being ahead; x and y alternating per line. */
    public List<float[]> lines() {
        return lines;
    }

    /** The colour it glows in: its element's, paler when it is turned around. */
    public int color() {
        if (element == null) return 0xD8D0C0;
        int c = element.color();
        if (!inverted) return c;
        return (((255 - (c >> 16 & 255)) / 2 + 100) << 16) | (((255 - (c >> 8 & 255)) / 2 + 100) << 8) | ((255 - (c & 255)) / 2 + 100);
    }

    public Vec3 hoverPoint() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, HOVER, 0);
    }

    // --- Doing it ---------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, SigilBlockEntity sigil) {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (sigil.unstableAt >= 0 && now >= sigil.unstableAt) {
            sigil.giveWay(server);
            return;
        }
        if (now >= sigil.until || sigil.element == null) {
            Fx.play(server, FxEffect.SPARKLE, sigil.hoverPoint().add(0, -1, 0), sigil.hoverPoint(), 0x8A8A8A, 0.6f, null);
            server.removeBlock(pos, false);
            return;
        }
        if (now < sigil.nextAction) return;
        if (sigil.presence) {
            boolean near = !server.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(NEAR, 1.5, NEAR),
                    entity -> entity.isAlive() && !entity.isSpectator() && !entity.getUUID().equals(sigil.owner)).isEmpty();
            if (!near) {
                sigil.wasNear = false;
                sigil.nextAction = now + 5;
                return;
            }
            // Each time someone steps into it is one of its charges, however long they stay.
            if (!sigil.wasNear) {
                sigil.wasNear = true;
                if (--sigil.charges < 0) {
                    server.removeBlock(pos, false);
                    return;
                }
                sigil.setChanged();
            }
        }
        sigil.act(server);
        int interval = sigil.directed() ? 30 : 20;
        sigil.nextAction = now + (sigil.pulse ? interval / 2 : interval);
    }

    private SpellEffects.Release release(ServerLevel level, Vec3 direction, double scale) {
        ServerPlayer maker = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (maker != null && maker.level() != level) maker = null;
        return new SpellEffects.Release(level, maker, element, inverted,
                directed() ? Spell.Placement.ARROW : Spell.Placement.FRONT, direction, 16,
                shape == SigilDesign.Shape.BURST, false, false, false, power * scale, area, 1);
    }

    private void act(ServerLevel level) {
        Vec3 hover = hoverPoint();
        if (!directed()) {
            SpellEffects.Release r = release(level, new Vec3(0, 1, 0), 1);
            if (shape == SigilDesign.Shape.BURST) SpellEffects.burst(r, hover);
            else SpellEffects.strike(r, hover, shape == SigilDesign.Shape.SPHERE ? area + 1 : 1.0, false);
            return;
        }
        Vec3 dir = new Vec3(dirX, 0, dirZ).normalize();
        SpellEffects.Release r = release(level, dir, 1);
        Vec3 end = hover.add(dir.scale(16));
        BlockHitResult block = level.clip(new ClipContext(hover, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        Vec3 stop = block.getType() == HitResult.Type.BLOCK ? block.getLocation().subtract(dir.scale(0.3)) : end;
        // The first living thing along the way, if any is nearer than the wall.
        LivingEntity first = null;
        double best = hover.distanceTo(stop);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(hover, stop).inflate(1),
                e -> e.isAlive() && !e.isSpectator())) {
            var hit = entity.getBoundingBox().inflate(0.3).clip(hover, stop);
            if (hit.isPresent() && hover.distanceTo(hit.get()) < best) {
                best = hover.distanceTo(hit.get());
                first = entity;
            }
        }
        Vec3 at = first != null ? hover.add(dir.scale(best)) : stop;
        FxEffect travel = inverted ? FxEffect.ARCANE_BOLT : switch (element) {
            case FIRE -> FxEffect.FIREBALL;
            case LIGHTNING -> FxEffect.CHAIN_LIGHTNING;
            default -> FxEffect.ARCANE_BOLT;
        };
        Fx.play(level, travel, hover, at, color(), (float) Math.min(1.4, 0.6 + 0.3 * power), null);
        MagicTasks.later(level, travel == FxEffect.CHAIN_LIGHTNING ? 2 : (int) Math.ceil(hover.distanceTo(at) / 1.1),
                () -> SpellEffects.land(r, at));
    }

    /** An unstable sigil gives way: its element bursts out where it lies, and the sigil is gone. */
    private void giveWay(ServerLevel level) {
        Vec3 at = hoverPoint().add(0, -0.8, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1, 0.5f);
        SpellEffects.burst(release(level, new Vec3(0, 1, 0), 1.5), at);
        level.removeBlock(worldPosition, false);
    }

    // --- Keeping it -------------------------------------------------------------------------------

    /** Writes a freshly inscribed sigil's program and tells the clients. */
    void inscribe(SigilDesign design, Rune element, Vec3 forward, Vec3 direction, float power, UUID owner, long now,
                  long unstableAt) {
        this.element = element;
        this.inverted = design.inverted();
        this.forwardX = (float) forward.x;
        this.forwardZ = (float) forward.z;
        this.dirX = (float) direction.x;
        this.dirZ = (float) direction.z;
        this.shape = design.shape();
        this.presence = design.presence();
        this.pulse = design.pulse();
        this.power = power;
        this.area = shape == SigilDesign.Shape.SPHERE ? 2.5f : 1.2f;
        this.owner = owner;
        this.until = now + (presence ? WAITING_LIFE : LIFE);
        this.charges = CHARGES + 2 * design.strength();
        this.unstableAt = unstableAt;
        this.lines = design.lines();
        this.nextAction = now + 20;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        element = input.read("element", Rune.CODEC).orElse(null);
        inverted = input.getBooleanOr("inverted", false);
        dirX = input.getFloatOr("dir_x", 0);
        dirZ = input.getFloatOr("dir_z", 0);
        forwardX = input.getFloatOr("forward_x", 0);
        forwardZ = input.getFloatOr("forward_z", 1);
        shape = SigilDesign.Shape.values()[Math.max(0, Math.min(2, input.getIntOr("shape", 0)))];
        presence = input.getBooleanOr("presence", false);
        pulse = input.getBooleanOr("pulse", false);
        power = input.getFloatOr("power", 1);
        area = input.getFloatOr("area", 1.2f);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        until = input.getLongOr("until", 0);
        charges = input.getIntOr("charges", CHARGES);
        unstableAt = input.getLongOr("unstable_at", -1);
        List<float[]> read = new ArrayList<>();
        for (List<Float> line : input.read("lines", LINES).orElse(List.of())) {
            float[] values = new float[line.size()];
            for (int i = 0; i < values.length; i++) values[i] = line.get(i);
            read.add(values);
        }
        lines = read;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (element != null) output.store("element", Rune.CODEC, element);
        output.putBoolean("inverted", inverted);
        output.putFloat("dir_x", dirX);
        output.putFloat("dir_z", dirZ);
        output.putFloat("forward_x", forwardX);
        output.putFloat("forward_z", forwardZ);
        output.putInt("shape", shape.ordinal());
        output.putBoolean("presence", presence);
        output.putBoolean("pulse", pulse);
        output.putFloat("power", power);
        output.putFloat("area", area);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
        output.putLong("until", until);
        output.putInt("charges", charges);
        output.putLong("unstable_at", unstableAt);
        List<List<Float>> written = new ArrayList<>();
        for (float[] line : lines) {
            List<Float> values = new ArrayList<>(line.length);
            for (float v : line) values.add(v);
            written.add(values);
        }
        output.store("lines", LINES, written);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SigilBlockEntity sigil) {
        clientTicker.accept(sigil);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) clientRemoved.accept(this);
    }
}
