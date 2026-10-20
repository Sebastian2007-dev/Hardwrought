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
 *
 * <p>Stages S2 and S3 (docs/magic-roadmap.md, M4 and M5): its reach, size and life follow the Wind, Water
 * and Earth in its ring; Dark there chooses whom it acts on, Lightning delays it; two squares make it
 * wait for a foot on it; runes outside its frame are conditions. Linked to other sigils it sends them a
 * signal whenever it acts, and a sigil with a link into it acts only when signalled.
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
    private static final Codec<SigilDesign.Program> PROGRAM = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("range", 0).forGetter(SigilDesign.Program::range),
            Codec.INT.optionalFieldOf("size", 0).forGetter(SigilDesign.Program::size),
            Codec.INT.optionalFieldOf("duration", 0).forGetter(SigilDesign.Program::duration),
            Codec.STRING.optionalFieldOf("target", "ANYONE").xmap(SigilDesign.Target::valueOf, Enum::name).forGetter(SigilDesign.Program::target),
            Codec.INT.optionalFieldOf("delay", 0).forGetter(SigilDesign.Program::delay),
            Codec.BOOL.optionalFieldOf("contact", false).forGetter(SigilDesign.Program::contact),
            Codec.STRING.xmap(SigilDesign.Condition::valueOf, Enum::name).listOf().optionalFieldOf("conditions", List.of())
                    .forGetter(SigilDesign.Program::conditions)).apply(instance, SigilDesign.Program::new));

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
    SigilDesign.Program program = SigilDesign.Program.NONE;
    /** The sigils it signals, and those that signal it. */
    final List<BlockPos> links = new ArrayList<>();
    final List<BlockPos> sources = new ArrayList<>();
    private boolean signalled;
    private long nextAction;
    private boolean wasNear;

    /** How many sigils one may signal. */
    public static final int MAX_LINKS = 4;
    /** Ticks of delay for every Lightning in the ring. */
    public static final int DELAY_TICKS = 30;
    /** How far a directed sigil reaches without Wind. */
    public static final double REACH = 16;

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

    public SigilDesign.Program program() {
        return program;
    }

    public List<BlockPos> links() {
        return List.copyOf(links);
    }

    /** Whether something signals it, so that it acts only when signalled. */
    public boolean driven() {
        return !sources.isEmpty();
    }

    /** How far its element goes, directed. */
    public double reach() {
        return REACH * Math.pow(1.5, program.range());
    }

    /** A signal arrives: it acts once, at its next turn. */
    public void signal() {
        signalled = true;
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
        if (now % 40 == 0) sigil.showLinks(server);
        if (now < sigil.nextAction) return;
        // Driven by another sigil: it waits for the signal, and acts on it once.
        sigil.sources.removeIf(source -> server.isLoaded(source) && !(server.getBlockEntity(source) instanceof SigilBlockEntity));
        if (sigil.driven()) {
            if (!sigil.signalled) {
                sigil.nextAction = now + 2;
                return;
            }
            sigil.signalled = false;
            if (sigil.conditionsHold(server)) sigil.fire(server);
            sigil.nextAction = now + 10;
            return;
        }
        if (!sigil.conditionsHold(server)) {
            sigil.wasNear = false;
            sigil.nextAction = now + 20;
            return;
        }
        if (sigil.presence) {
            // Two squares wait for a foot on the sigil itself; one for anyone coming near.
            AABB watch = sigil.program.contact() ? new AABB(pos).inflate(0.2, 1.0, 0.2) : new AABB(pos).inflate(NEAR, 1.5, NEAR);
            boolean near = !server.getEntitiesOfClass(LivingEntity.class, watch,
                    entity -> entity.isAlive() && !entity.isSpectator() && sigil.targets(server, entity)).isEmpty();
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
        sigil.fire(server);
        int interval = sigil.directed() ? 30 : 20;
        sigil.nextAction = now + (sigil.pulse ? interval / 2 : interval);
    }

    /** Acts — after its delay, if it has one — and signals the sigils it is linked to. */
    private void fire(ServerLevel level) {
        if (program.delay() > 0) MagicTasks.later(level, program.delay() * DELAY_TICKS, () -> {
            if (!isRemoved()) act(level);
        });
        else act(level);
        for (BlockPos link : links) {
            if (level.isLoaded(link) && level.getBlockEntity(link) instanceof SigilBlockEntity other) other.signal();
        }
    }

    /** Whether everything drawn outside its frame holds now. */
    public boolean conditionsHold(ServerLevel level) {
        BlockPos above = worldPosition.above();
        for (SigilDesign.Condition condition : program.conditions()) {
            boolean holds = switch (condition) {
                case DARK -> level.getMaxLocalRawBrightness(above) <= 7;
                case LIGHT -> level.getMaxLocalRawBrightness(above) >= 8;
                case HOT -> temperature(level) >= 25;
                case COLD -> temperature(level) <= 5;
                case HOSTILE_NEAR -> !level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                        new AABB(worldPosition).inflate(8), LivingEntity::isAlive).isEmpty();
                case RAIN -> level.isRainingAt(above);
            };
            if (!holds) return false;
        }
        return true;
    }

    private double temperature(ServerLevel level) {
        var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(level.getServer());
        return runtime == null ? 15 : runtime.environment().outdoorTemperature(level, worldPosition);
    }

    /** Whether it acts on this one: anyone but its maker, or only monsters, or only its maker's side. */
    boolean targets(ServerLevel level, LivingEntity entity) {
        ServerPlayer maker = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        return switch (program.target()) {
            case ANYONE -> !entity.getUUID().equals(owner);
            case HOSTILE -> entity instanceof net.minecraft.world.entity.monster.Enemy;
            case FRIENDLY -> Allies.ally(maker, entity) && !(entity instanceof net.minecraft.world.entity.monster.Enemy);
        };
    }

    /** A faint thread along every link, now and then. */
    private void showLinks(ServerLevel level) {
        Vec3 from = Vec3.atBottomCenterOf(worldPosition).add(0, 0.2, 0);
        for (BlockPos link : links) {
            Vec3 to = Vec3.atBottomCenterOf(link).add(0, 0.2, 0);
            int steps = Math.max(2, (int) (from.distanceTo(to) * 2));
            for (int i = 0; i <= steps; i++) {
                Vec3 at = from.lerp(to, i / (double) steps);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Links this sigil to another, or unlinks them if they were. Returns whether they are linked now. */
    public boolean toggleLink(ServerLevel level, SigilBlockEntity other) {
        BlockPos to = other.getBlockPos();
        if (links.remove(to)) {
            other.sources.remove(worldPosition);
            setChanged();
            other.setChanged();
            return false;
        }
        if (links.size() >= MAX_LINKS) return false;
        links.add(to.immutable());
        if (!other.sources.contains(worldPosition)) other.sources.add(worldPosition.immutable());
        setChanged();
        other.setChanged();
        return true;
    }

    /** Struck by an explosion, it is damaged rather than gone (sigil §50): weaker, and turned aside. False where it gives way. */
    /** An endermite close by (docs/magic-roadmap.md, M6): the sigil loses a little strength each second. */
    void unsettle() {
        power = Math.max(0.05f, power * 0.97f);
        setChanged();
    }

    boolean damaged() {
        if (power < 0.4f) return false;
        power *= 0.6f;
        if (directed()) {
            float x = dirX;
            dirX = -dirZ;
            dirZ = x;
        }
        setChanged();
        return true;
    }

    private SpellEffects.Release release(ServerLevel level, Vec3 direction, double scale) {
        ServerPlayer maker = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (maker != null && maker.level() != level) maker = null;
        return new SpellEffects.Release(level, maker, element, inverted,
                directed() ? Spell.Placement.ARROW : Spell.Placement.FRONT, direction, reach(),
                shape == SigilDesign.Shape.BURST, false, false, false, power * scale, area, 1);
    }

    private void act(ServerLevel level) {
        Vec3 hover = hoverPoint();
        if (!directed()) {
            SpellEffects.Release r = release(level, new Vec3(0, 1, 0), 1);
            double radius = shape == SigilDesign.Shape.SPHERE ? area + 1 : shape == SigilDesign.Shape.RING ? area + 2 : 1.0;
            if (program.target() != SigilDesign.Target.ANYONE) {
                // Chosen targets only: the element goes to each of them, and nothing else is touched.
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(radius + 1),
                        e -> e.isAlive() && !e.isSpectator() && targets(level, e))) {
                    double distance = entity.position().distanceTo(hover);
                    if (distance > radius + 0.8 || shape == SigilDesign.Shape.RING && distance < radius - 1.5) continue;
                    SpellEffects.strike(r, entity.position().add(0, entity.getBbHeight() * 0.5, 0), 0.6, false);
                }
                return;
            }
            if (shape == SigilDesign.Shape.BURST) SpellEffects.burst(r, hover);
            else if (shape == SigilDesign.Shape.RING) {
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI / 5;
                    SpellEffects.strike(r, hover.add(Math.cos(a) * radius, -0.5, Math.sin(a) * radius), 1.0, false);
                }
            } else SpellEffects.strike(r, hover, radius, false);
            return;
        }
        Vec3 dir = new Vec3(dirX, 0, dirZ).normalize();
        SpellEffects.Release r = release(level, dir, 1);
        Vec3 end = hover.add(dir.scale(reach()));
        BlockHitResult block = level.clip(new ClipContext(hover, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        Vec3 stop = block.getType() == HitResult.Type.BLOCK ? block.getLocation().subtract(dir.scale(0.3)) : end;
        // The first living thing along the way, if any is nearer than the wall.
        LivingEntity first = null;
        double best = hover.distanceTo(stop);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(hover, stop).inflate(1),
                e -> e.isAlive() && !e.isSpectator() && (program.target() == SigilDesign.Target.ANYONE || targets(level, e)))) {
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
        this.program = design.program();
        float grown = (float) Math.pow(1.4, program.size());
        this.area = (shape == SigilDesign.Shape.SPHERE || shape == SigilDesign.Shape.RING ? 2.5f : 1.2f) * grown;
        this.owner = owner;
        this.until = now + (long) ((presence ? WAITING_LIFE : LIFE) * Math.pow(2, program.duration()));
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
        shape = SigilDesign.Shape.values()[Math.max(0, Math.min(SigilDesign.Shape.values().length - 1, input.getIntOr("shape", 0)))];
        program = input.read("program", PROGRAM).orElse(SigilDesign.Program.NONE);
        links.clear();
        links.addAll(input.read("links", BlockPos.CODEC.listOf()).orElse(List.of()));
        sources.clear();
        sources.addAll(input.read("sources", BlockPos.CODEC.listOf()).orElse(List.of()));
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
        output.store("program", PROGRAM, program);
        if (!links.isEmpty()) output.store("links", BlockPos.CODEC.listOf(), List.copyOf(links));
        if (!sources.isEmpty()) output.store("sources", BlockPos.CODEC.listOf(), List.copyOf(sources));
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
