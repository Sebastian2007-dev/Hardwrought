package de.ipnats.hardwrought.witchking;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.mixin.ServerGamePacketListenerAccessor;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The mantle's hover (endgame § 4): at a key, its wearer stops gliding and hangs in the air, drifting
 * gently up and down, moving slowly every way, rising with jump and sinking with sneak.
 *
 * <p>Two things hold it in check. <b>Soul charge</b> runs down while hovering, about
 * {@value #HOVER_TICKS} ticks from full, and fills again while gliding or standing; run dry, the wearer
 * drops out of the hover. And a <b>ceiling</b>: whoever goes higher than {@link #ceiling}, the mantle
 * gives out — it stops carrying them, and for {@value #STALL_TICKS} ticks it neither glides nor hovers.
 *
 * <p>The client moves the hovering player, as it moves every player; the server decides whether they
 * may hover, keeps the charge, and tells the client ({@link State}). While hovering, the server does not
 * count the time in the air against them, nor the sinking as a fall.
 */
public final class MantleFlight {
    /** Ticks of hovering a full charge gives, and ticks of gliding or standing that fill it again. */
    public static final int HOVER_TICKS = 400, REFILL_TICKS = 160;
    /** The least charge a hover can start on. */
    static final float START_CHARGE = 0.15f;
    /** What sinking costs of the charge, against hanging still or climbing. */
    static final float SINK_COST = 0.2f;
    /** How long the mantle gives out for, past the ceiling. */
    public static final int STALL_TICKS = 100;
    /** In the End the ceiling stands at a fixed height over the void; elsewhere this far above the ground below. */
    public static final int END_CEILING = 160, OVER_GROUND = 64;
    /** How far below the ceiling the air is felt thinning: a warning. */
    static final int WARN_BELOW = 8;

    /** Client to server: hover, or stop. */
    public record Hover(boolean on) implements CustomPacketPayload {
        public static final Type<Hover> TYPE = new Type<>(Hardwrought.id("mantle_hover_v1"));
        public static final StreamCodec<ByteBuf, Hover> CODEC = ByteBufCodecs.BOOL.map(Hover::new, Hover::on);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: whether its player hovers, and their charge. */
    public record State(boolean hovering, float charge) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Hardwrought.id("mantle_state_v1"));
        public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, State::hovering, ByteBufCodecs.FLOAT, State::charge, State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final class Flight {
        boolean hovering;
        float charge = 1;
        /** What the client was last told. */
        boolean toldHovering;
        float toldCharge = 1;
        /** A few ticks after a hover in which a landing is not a fall. */
        int soft;
    }

    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    /** Set by the client: whether its own player hovers, as the server last said. */
    public static Predicate<Player> clientHovering = player -> false;

    private MantleFlight() { }

    static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(Hover.TYPE, Hover.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Hover.TYPE, (payload, context) -> request(context.player(), payload.on()));
        ServerTickEvents.END_SERVER_TICK.register(MantleFlight::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FLIGHTS.remove(handler.player.getUUID()));
    }

    /** Whether this player is hovering, on either side. */
    public static boolean hovering(Player player) {
        if (player.level().isClientSide()) return clientHovering.test(player);
        Flight flight = FLIGHTS.get(player.getUUID());
        return flight != null && flight.hovering;
    }

    /** Thrown by a blow: out of the hover, into a fall the wearer has to catch. */
    public static void knockOut(ServerPlayer player) {
        Flight flight = FLIGHTS.get(player.getUUID());
        if (flight == null || !flight.hovering) return;
        flight.hovering = false;
        tell(player, flight);
    }

    /** Whether the mantle on this player's back has given out for now. */
    public static boolean stalled(Player player) {
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        return worn.is(WitchKingContent.MANTLE) && player.getCooldowns().isOnCooldown(worn);
    }

    /** The highest the mantle carries its wearer here. */
    public static double ceiling(Player player) {
        Level level = player.level();
        if (level.dimension() == Level.END) return END_CEILING;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ()) + OVER_GROUND;
    }

    private static boolean wearing(ServerPlayer player) {
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        return worn.is(WitchKingContent.MANTLE) && LivingEntity.canGlideUsing(worn, EquipmentSlot.CHEST);
    }

    private static void request(ServerPlayer player, boolean on) {
        Flight flight = FLIGHTS.computeIfAbsent(player.getUUID(), id -> new Flight());
        if (!on) {
            flight.hovering = false;
        } else if (!flight.hovering && wearing(player) && !stalled(player) && !player.onGround() && !player.isInWater()
                && !player.isSpectator() && flight.charge >= START_CHARGE && player.getY() < ceiling(player)) {
            flight.hovering = true;
            player.stopFallFlying();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.PLAYERS, 0.9f, 0.7f);
        }
        tell(player, flight);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Flight flight = FLIGHTS.get(player.getUUID());
            boolean worn = wearing(player);
            if (flight == null) {
                if (!worn) continue;
                flight = new Flight();
                FLIGHTS.put(player.getUUID(), flight);
            }
            step(player, flight, worn);
            tell(player, flight);
        }
    }

    private static void step(ServerPlayer player, Flight flight, boolean worn) {
        ServerLevel level = player.level();
        if (flight.hovering && (!worn || player.onGround() || player.isInWater() || player.isSpectator() || !player.isAlive())) {
            flight.hovering = false;
        }
        boolean aloft = flight.hovering || player.isFallFlying();
        if (worn && aloft && !player.isCreative()) {
            double ceiling = ceiling(player);
            if (player.getY() > ceiling) {
                stall(player, flight);
                return;
            }
            if (player.getY() > ceiling - WARN_BELOW && player.tickCount % 20 == 0) {
                overlay(player, Component.translatable("message.hardwrought.mantle.thin_air").withStyle(ChatFormatting.AQUA));
            }
        }
        if (flight.hovering) {
            // Letting itself sink costs it little: it is the climbing and the hanging that take the souls' strength.
            var input = player.getLastClientInput();
            float cost = input.shift() && !input.jump() ? SINK_COST : 1;
            flight.charge = Math.max(0, flight.charge - cost / HOVER_TICKS);
            flight.soft = 10;
            player.resetFallDistance();
            if (player.connection != null) ((ServerGamePacketListenerAccessor) player.connection).hardwrought$setAboveGroundTickCount(0);
            if (player.tickCount % 2 == 0) {
                level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.25, 0.1, 0.25, 0.01);
            }
            if (player.tickCount % 5 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.2, 0.05, 0.2, 0.005);
            }
            if (flight.charge <= 0) {
                flight.hovering = false;
                overlay(player, Component.translatable("message.hardwrought.mantle.spent").withStyle(ChatFormatting.GRAY));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8f, 0.4f);
            }
        } else {
            if (flight.soft > 0) {
                flight.soft--;
                player.resetFallDistance();
            }
            if (player.isFallFlying() || player.onGround()) flight.charge = Math.min(1, flight.charge + 1f / REFILL_TICKS);
        }
    }

    /** Too high: the mantle gives out, and its wearer falls. */
    private static void stall(ServerPlayer player, Flight flight) {
        flight.hovering = false;
        player.stopFallFlying();
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        // Vanilla tells the client of a cooldown at once; a player without a connection is only ever a test's.
        if (player.connection != null) player.getCooldowns().addCooldown(worn, STALL_TICKS);
        ServerLevel level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 0.4f);
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
        overlay(player, Component.translatable("message.hardwrought.mantle.stalled").withStyle(ChatFormatting.RED));
    }

    private static void tell(ServerPlayer player, Flight flight) {
        if (player.connection == null) return;
        if (flight.hovering == flight.toldHovering && Math.abs(flight.charge - flight.toldCharge) < 0.01f
                && !(flight.charge >= 1 && flight.toldCharge < 1)) return;
        flight.toldHovering = flight.hovering;
        flight.toldCharge = flight.charge;
        ServerPlayNetworking.send(player, new State(flight.hovering, flight.charge));
    }

    private static void overlay(ServerPlayer player, Component message) {
        if (player.connection != null) player.sendOverlayMessage(message);
    }

    // ------------------------------------------------------------------------------------ for tests

    public static void requestForTesting(ServerPlayer player, boolean on) {
        request(player, on);
    }

    public static float chargeForTesting(ServerPlayer player) {
        Flight flight = FLIGHTS.get(player.getUUID());
        return flight == null ? 1 : flight.charge;
    }

    public static void stepForTesting(ServerPlayer player) {
        Flight flight = FLIGHTS.computeIfAbsent(player.getUUID(), id -> new Flight());
        step(player, flight, wearing(player));
    }
}
