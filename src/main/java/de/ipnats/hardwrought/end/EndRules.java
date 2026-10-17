package de.ipnats.hardwrought.end;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The End's own rules (endgame § 4).
 *
 * <ul>
 *   <li>Nobody arrives on an island: the way in sets every traveller down at {@link #ARRIVAL}, out in
 *   the void, on a floor of light ({@link VoidLight}) that holds for {@link #PLATFORM_TICKS} and then is
 *   gone. Whoever cannot fly by then falls. There is no obsidian platform.</li>
 *   <li>No block is placed, and no bucket emptied, anywhere in the End while its dragon lives: the only
 *   way to stay up is to fly. The client is told, so that it does not show a block the server refuses.</li>
 * </ul>
 */
public final class EndRules {
    /** Where the way in sets a traveller down: well out from the main island, looking towards it. */
    public static final Vec3 ARRIVAL = new Vec3(150.5, 90, 0.5);
    public static final float ARRIVAL_YAW = 90;
    /** How long the floor of light holds, and how far it reaches from its middle. */
    public static final int PLATFORM_TICKS = 600, PLATFORM_RADIUS = 2;
    /** The last seconds of the floor are counted down to whoever stands on it. */
    static final int COUNTDOWN_SECONDS = 10;

    public static final Block VOID_LIGHT = Registry.register(BuiltInRegistries.BLOCK, key("void_light"),
            new VoidLight(BlockBehaviour.Properties.of().setId(key("void_light")).mapColor(MapColor.NONE).strength(-1.0f, 3600000.0f)
                    .noLootTable().noOcclusion().lightLevel(state -> 12).pushReaction(PushReaction.IMMOVEABLE)
                    .isValidSpawn((state, level, pos, type) -> false)));
    public static final BlockEntityType<VoidLight.Entity> VOID_LIGHT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hardwrought.id("void_light"), new BlockEntityType<>(VoidLight.Entity::new, Set.of(VOID_LIGHT)));

    /** Server to client: whether building is forbidden where its player is. */
    public record State(boolean noBuilding) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Hardwrought.id("end_rules_v1"));
        public static final StreamCodec<ByteBuf, State> CODEC = ByteBufCodecs.BOOL.map(State::new, State::noBuilding);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Set by the client: what the server last said. */
    public static boolean clientNoBuilding;
    private static final Map<UUID, Boolean> TOLD = new HashMap<>();

    private EndRules() { }

    private static ResourceKey<Block> key(String name) {
        return ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
    }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(EndRules::tick);
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
            if (to.dimension() == Level.END) arrived(player);
        });
    }

    // ------------------------------------------------------------------------------------ the dragon

    /** Whether the End's dragon lives: until it is first killed, and whenever one has been raised again. */
    public static boolean dragonAlive(ServerLevel level) {
        if (level.dimension() != Level.END) return false;
        var fight = level.getDragonFight();
        if (fight != null && !fight.hasPreviouslyKilledDragon()) return true;
        return level.getDragons().stream().anyMatch(LivingEntity::isAlive);
    }

    /** Whether this player may not build where they are, on either side. */
    public static boolean buildingForbidden(Player player) {
        if (player == null || player.isCreative() || player.isSpectator()) return false;
        Level level = player.level();
        if (level.dimension() != Level.END) return false;
        return level instanceof ServerLevel server ? dragonAlive(server) : clientNoBuilding;
    }

    /** Tells a player why the block does not go down. */
    public static void refuse(Player player) {
        if (player instanceof ServerPlayer server && server.connection != null) {
            server.sendOverlayMessage(Component.translatable("message.hardwrought.end.no_building").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    // ------------------------------------------------------------------------------------ the way in

    /** Where the way in sets a traveller down instead of vanilla's platform. */
    public static TeleportTransition arrival(TeleportTransition vanilla) {
        return vanilla.withPosition(ARRIVAL).withRotation(ARRIVAL_YAW, 0);
    }

    /** A floor of light at the arrival, or the one there made to hold again from now. */
    public static void lightPlatform(ServerLevel end) {
        BlockPos middle = BlockPos.containing(ARRIVAL).below();
        long until = end.getGameTime() + PLATFORM_TICKS;
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-PLATFORM_RADIUS, 0, -PLATFORM_RADIUS), middle.offset(PLATFORM_RADIUS, 0, PLATFORM_RADIUS))) {
            if (!end.getBlockState(pos).isAir() && !end.getBlockState(pos).is(VOID_LIGHT)) continue;
            if (!end.getBlockState(pos).is(VOID_LIGHT)) end.setBlockAndUpdate(pos, VOID_LIGHT.defaultBlockState());
            if (end.getBlockEntity(pos) instanceof VoidLight.Entity light) {
                light.until = until;
                light.setChanged();
            }
        }
        Vec3 top = Vec3.atBottomCenterOf(middle.above());
        Fx.play(end, FxEffect.END_PLATFORM, top, top.add(0, PLATFORM_TICKS, 0), 0, PLATFORM_RADIUS + 0.5f, null);
    }

    private static void arrived(ServerPlayer player) {
        if (player.connection == null) return;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(15, 70, 25));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.hardwrought.end.title").withStyle(ChatFormatting.DARK_PURPLE)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.hardwrought.end.subtitle").withStyle(ChatFormatting.GRAY)));
        tell(player, true);
    }

    // ------------------------------------------------------------------------------------ every tick

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tell(player, false);
            countdown(player);
        }
        TOLD.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static void tell(ServerPlayer player, boolean always) {
        if (player.connection == null) return;
        boolean forbidden = player.level().dimension() == Level.END && dragonAlive(player.level());
        Boolean told = TOLD.put(player.getUUID(), forbidden);
        if (always || told == null || told != forbidden) ServerPlayNetworking.send(player, new State(forbidden));
    }

    /** The floor's last seconds, to whoever stands on it. */
    private static void countdown(ServerPlayer player) {
        ServerLevel level = player.level();
        if (level.dimension() != Level.END || player.connection == null) return;
        if (!(level.getBlockEntity(player.blockPosition().below()) instanceof VoidLight.Entity light)) return;
        long seconds = (light.until - level.getGameTime() + 19) / 20;
        if (seconds > COUNTDOWN_SECONDS || seconds <= 0) return;
        player.sendOverlayMessage(Component.translatable("message.hardwrought.end.fading", seconds).withStyle(ChatFormatting.LIGHT_PURPLE));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f,
                0.6f + 0.06f * (COUNTDOWN_SECONDS - seconds));
    }
}
