package de.ipnats.hardwrought.survival;

import de.ipnats.hardwrought.Hardwrought;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Ultra Survival and Ultra Hardcore: survival and hardcore with the mod's hardest rules switched on.
 *
 * <p>Chosen when the world is made, with the Ultra switch next to the game mode, and kept as a game
 * rule of the world. What it changes:
 * <ul>
 *   <li>nobody is handed a pack: the player's own grid is earned, a row at a time — woven pack,
 *       frame, leather;</li>
 *   <li>the difficulty is Hard, and locked there;</li>
 *   <li>health does not come back by itself. Only effects heal — a regeneration potion, a golden
 *       apple — or a night's sleep good enough to count, above {@link #HEALING_SLEEP} quality;</li>
 *   <li>nothing breaks at a touch: with the bare hand — or anything that is no tool for the block —
 *       even grass, flowers and sugar cane take {@link #HAND_TICKS} ticks, and the right tool takes at
 *       least {@link #TOOL_TICKS} (see {@link #slowBreaking}).</li>
 * </ul>
 *
 * <p>The client is told whether its world is Ultra ({@link State}), because it works out the breaking of a
 * block on its own as well, and a block it thinks broken at once would come back.
 */
public final class Ultra {
    /** Sleep better than this heals; anything less only rests. */
    public static final double HEALING_SLEEP = 0.75;
    /** Health a good sleep gives back per tick: a full bar over a few minutes of night. */
    public static final float SLEEP_HEAL_PER_TICK = 0.005f;
    /** The least a block takes to break, in ticks: by hand three seconds, with the right tool half of one. */
    public static final int HAND_TICKS = 60, TOOL_TICKS = 10;

    /** Server to client: whether this world is an Ultra one. */
    public record State(boolean on) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Hardwrought.id("ultra_state_v1"));
        public static final StreamCodec<ByteBuf, State> CODEC = ByteBufCodecs.BOOL.map(State::new, State::on);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** What the server last told this client. */
    public static volatile boolean clientActive;

    public static final GameRule<Boolean> RULE = GameRuleBuilder.forBoolean(false)
            .category(GameRuleCategory.PLAYER)
            .buildAndRegister(Hardwrought.id("ultra"));

    private static volatile boolean active;
    private static boolean initialized;

    private Ultra() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> tell(handler.player));
        ServerLifecycleEvents.SERVER_STARTED.register(Ultra::refresh);
        net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents.changeCallback(RULE)
                .register((value, server) -> refresh(server));
    }

    /** Reads the rule off the world and puts what follows from it in place. */
    public static void refresh(MinecraftServer server) {
        if (server == null) return;
        active = server.overworld().getGameRules().get(RULE);
        if (active) {
            server.overworld().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            server.setDifficulty(net.minecraft.world.Difficulty.HARD, true);
            server.setDifficultyLocked(true);
        }
        de.ipnats.hardwrought.knowledge.Journal.setUltra(active);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) tell(player);
    }

    private static void tell(ServerPlayer player) {
        if (player.connection != null && ServerPlayNetworking.canSend(player, State.TYPE)) {
            ServerPlayNetworking.send(player, new State(active));
        }
    }

    /** Whether the world this level belongs to is an Ultra one, on either side. */
    public static boolean active(Level level) {
        return level != null && (level.isClientSide() ? clientActive : active);
    }

    /**
     * How far one tick of work gets through a block, slowed for Ultra: never faster than a block in
     * {@link #HAND_TICKS} by hand or {@link #TOOL_TICKS} with the right tool. Creative breaks as ever.
     */
    public static float slowBreaking(float progress, net.minecraft.world.level.block.state.BlockState state, Player player) {
        if (progress <= 0 || player == null || player.isCreative() || !active(player.level())) return progress;
        return slowBreaking(progress, state, player.getMainHandItem());
    }

    /** The Ultra limit on one tick of work through a block, for what is in the hand. */
    public static float slowBreaking(float progress, net.minecraft.world.level.block.state.BlockState state,
                                     net.minecraft.world.item.ItemStack held) {
        if (progress <= 0) return progress;
        boolean tool = de.ipnats.hardwrought.progression.BlockBreaking.verdict(held, state)
                == de.ipnats.hardwrought.progression.BreakingVerdict.PROPER
                && held.getDestroySpeed(state) > 1.0f;
        return Math.min(progress, 1.0f / (tool ? TOOL_TICKS : HAND_TICKS));
    }

    /** Whether the running world is an Ultra one. */
    public static boolean active() {
        return active;
    }

    public static boolean active(MinecraftServer server) {
        return server != null && server.overworld() != null && server.overworld().getGameRules().get(RULE);
    }
}
