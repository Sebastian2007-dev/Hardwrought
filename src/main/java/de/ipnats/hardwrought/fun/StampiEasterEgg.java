package de.ipnats.hardwrought.fun;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.mixin.MannequinAccessor;
import de.ipnats.hardwrought.smeltery.SmelteryMenu;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An easter egg. Typing SCHMELZERIE (or SCHMELZERY, or SCHMELZERI) into the smeltery's screen calls up
 * one of its makers, StampiCraft or StampiGames. He joins the game like any player would — "joined the
 * game" in chat, his own skin, his name over his head and nothing else — and comes for the player. There
 * is a moment to run, but he catches up in the end, strikes them dead, and leaves the game again. Each
 * brother is called up only by the other — and by anyone else, one of the two at random — and each kills
 * in his own way: StampiCraft for annoying the creator, StampiGames for copyright infringement.
 *
 * <p>In hardcore nobody really dies of it: the death is acted out — the death screen, with nothing on it
 * to press, so the world cannot be deleted from it — and a few seconds later, "lol not really", the player
 * is back where they stood.
 */
public final class StampiEasterEgg {
    public static final String CRAFT = "StampiCraft";
    public static final String GAMES = "StampiGames";
    /** The words that call one of them up. */
    public static final List<String> WORDS = List.of("SCHMELZERIE", "SCHMELZERY", "SCHMELZERI");

    public static final ResourceKey<DamageType> ANNOYING_THE_CREATOR =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("annoying_the_creator"));
    public static final ResourceKey<DamageType> COPYRIGHT_INFRINGEMENT =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("copyright_infringement"));

    /** How far off he joins, in blocks. */
    static final double JOIN_DISTANCE = 6;
    /** He strikes once in reach, but not before the player has had a moment to run — and at the latest then. */
    static final int GRACE_TICKS = 40;
    static final int CATCH_UP_TICKS = 120;
    static final double REACH = 1.8;
    /** Blocks a tick: a walk, so a sprinting player gets away from him — for a while. */
    static final double SPEED = 0.2;
    /** Ticks between the strike and his leaving the game. */
    static final int LEAVE_TICKS = 30;
    /** How long a hardcore player lies acted-dead before it turns out not to be so, in ticks. */
    public static final int NOT_REALLY_TICKS = 100;
    /** Marks a summoned maker, so one saved with the world before he could leave is gone when it loads. */
    static final String TAG = "hardwrought_stampi";
    /** One summoning per player every so often. */
    static final int COOLDOWN_TICKS = 200;

    /** The client says the word was typed into the smeltery's screen. */
    public record Summon() implements CustomPacketPayload {
        public static final Type<Summon> TYPE = new Type<>(Hardwrought.id("stampi_summon"));
        public static final StreamCodec<ByteBuf, Summon> CODEC = StreamCodec.unit(new Summon());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The server tells a hardcore player to act out their death, with the message it would have had. */
    public record FakeDeath(Component message) implements CustomPacketPayload {
        public static final Type<FakeDeath> TYPE = new Type<>(Hardwrought.id("stampi_fake_death"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FakeDeath> CODEC =
                ComponentSerialization.STREAM_CODEC.map(FakeDeath::new, FakeDeath::message);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record Pending(long at, Runnable action) { }

    /** A maker in the world, coming for the player who called him up. */
    private static final class Visit {
        final ServerPlayer player;
        final Mannequin mannequin;
        final String maker;
        int age;
        int struckAt = -1;

        Visit(ServerPlayer player, Mannequin mannequin, String maker) {
            this.player = player;
            this.mannequin = mannequin;
            this.maker = maker;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<Visit> VISITS = new ArrayList<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private StampiEasterEgg() { }

    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(Summon.TYPE, Summon.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FakeDeath.TYPE, FakeDeath.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Summon.TYPE, (payload, context) -> summon(context.player()));
        ServerTickEvents.END_SERVER_TICK.register(StampiEasterEgg::tick);
        // A maker still standing when the world was left goes as soon as it is loaded again.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Mannequin && entity.entityTags().contains(TAG)) entity.discard();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            VISITS.clear();
            PENDING.clear();
            LAST.clear();
        });
    }

    /** Which of the two answers this player: never the one typing, and for anyone else either. */
    public static String summoned(String typedBy, boolean coin) {
        if (CRAFT.equalsIgnoreCase(typedBy)) return GAMES;
        if (GAMES.equalsIgnoreCase(typedBy)) return CRAFT;
        return coin ? CRAFT : GAMES;
    }

    /** Whether a name is one of the two — how the client tells a maker from any other mannequin. */
    public static boolean isMaker(String name) {
        return CRAFT.equals(name) || GAMES.equals(name);
    }

    /** How the one called up kills. */
    public static ResourceKey<DamageType> killedBy(String maker) {
        return CRAFT.equals(maker) ? ANNOYING_THE_CREATOR : COPYRIGHT_INFRINGEMENT;
    }

    static void summon(ServerPlayer player) {
        if (!(player.containerMenu instanceof SmelteryMenu) || !player.isAlive()) return;
        ServerLevel level = player.level();
        long now = level.getServer().getTickCount();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) return;
        LAST.put(player.getUUID(), now);
        player.closeContainer();

        String maker = summoned(player.getGameProfile().name(), level.getRandom().nextBoolean());
        Mannequin mannequin = net.minecraft.world.entity.EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.EVENT);
        if (mannequin == null) return;
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 at = player.position().add(look.scale(JOIN_DISTANCE));
        mannequin.snapTo(at.x, player.getY(), at.z, player.getYRot() + 180, 0);
        mannequin.setYHeadRot(player.getYRot() + 180);
        ((MannequinAccessor) mannequin).hardwrought$setProfile(ResolvableProfile.createUnresolved(maker));
        // Only his name over his head, as over any player's: no "NPC" under it.
        ((MannequinAccessor) mannequin).hardwrought$setHideDescription(true);
        mannequin.setCustomName(Component.literal(maker));
        mannequin.setCustomNameVisible(true);
        mannequin.setPermanentlyInvulnerable(true);
        level.addFreshEntity(mannequin);
        // Tagged only once in the world: ENTITY_LOAD fires for fresh entities too, and would discard him at once.
        mannequin.addTag(TAG);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("multiplayer.player.joined", maker).withStyle(ChatFormatting.YELLOW), false);
        VISITS.add(new Visit(player, mannequin, maker));
    }

    /** One tick of a visit; false once he has left. */
    private static boolean visit(Visit visit) {
        visit.age++;
        Mannequin mannequin = visit.mannequin;
        ServerPlayer player = visit.player;
        boolean playerGone = player.isRemoved() || player.level() != mannequin.level();
        if (mannequin.isRemoved() || playerGone
                || visit.struckAt >= 0 && visit.age - visit.struckAt >= LEAVE_TICKS) {
            leave(visit);
            return false;
        }
        if (visit.struckAt >= 0) return true;

        Vec3 to = player.position().subtract(mannequin.position()).multiply(1, 0, 1);
        double distance = to.length();
        if (visit.age >= CATCH_UP_TICKS && distance > REACH) {
            // However far they ran, he is there.
            Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 at = player.position().add(look.scale(REACH - 0.3));
            mannequin.teleportTo(at.x, player.getY(), at.z);
            to = player.position().subtract(at).multiply(1, 0, 1);
            distance = to.length();
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        mannequin.setYRot(yaw);
        mannequin.setYHeadRot(yaw);
        mannequin.setYBodyRot(yaw);
        if (distance <= REACH) {
            mannequin.setDeltaMovement(0, mannequin.getDeltaMovement().y, 0);
            if (visit.age >= GRACE_TICKS) {
                visit.struckAt = visit.age;
                strike(player, visit.maker, mannequin);
            }
            return true;
        }
        Vec3 step = to.scale(SPEED / distance);
        double up = mannequin.getDeltaMovement().y;
        if (mannequin.horizontalCollision && mannequin.onGround()) up = 0.42;
        mannequin.setDeltaMovement(step.x, up, step.z);
        return true;
    }

    private static void leave(Visit visit) {
        Mannequin mannequin = visit.mannequin;
        if (!mannequin.isRemoved()) mannequin.discard();
        ((ServerLevel) mannequin.level()).getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("multiplayer.player.left", visit.maker).withStyle(ChatFormatting.YELLOW), false);
    }

    private static void strike(ServerPlayer player, String maker, Mannequin mannequin) {
        if (player.isRemoved() || !player.isAlive()) return;
        ServerLevel level = player.level();
        mannequin.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS, 1.0f, 0.8f);
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(killedBy(maker)));
        if (!level.getServer().isHardcore()) {
            player.hurtServer(level, source, Float.MAX_VALUE);
            return;
        }
        // Hardcore: the death is only acted. Nothing can touch the player while it lasts.
        Component message = source.getLocalizedDeathMessage(player);
        level.getServer().getPlayerList().broadcastSystemMessage(message, false);
        player.setPermanentlyInvulnerable(true);
        player.setHealth(player.getMaxHealth());
        ServerPlayNetworking.send(player, new FakeDeath(message));
        later(level.getServer(), NOT_REALLY_TICKS, () -> {
            if (player.isRemoved()) return;
            player.setPermanentlyInvulnerable(false);
            player.setHealth(player.getMaxHealth());
            player.level().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.hardwrought.stampi.not_really").withStyle(ChatFormatting.YELLOW), false);
        });
    }

    private static void later(MinecraftServer server, int ticks, Runnable action) {
        PENDING.add(new Pending(server.getTickCount() + ticks, action));
    }

    private static void tick(MinecraftServer server) {
        VISITS.removeIf(visit -> !visit(visit));
        if (PENDING.isEmpty()) return;
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (pending.at() <= server.getTickCount()) {
                due.add(pending.action());
                it.remove();
            }
        }
        due.forEach(Runnable::run);
    }
}
