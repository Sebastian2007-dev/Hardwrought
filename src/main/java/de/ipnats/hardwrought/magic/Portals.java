package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gates: the caster steps through a circle along an arrow. A short gate is a step the way the arrow
 * points ({@link SpellEffects}); a far gate — circle, arrow, circle — opens here and waits for the
 * caster to name where it leads (magic specification section 24).
 *
 * <p>The runes drawn across the arrow's shaft are its anchors. Without any, a far gate lands wide of
 * where it was meant to and leaves its traveller reeling. Earth holds it truest; every other rune
 * holds it a little and says how the caster arrives: Dark unseen, Wind softly and further, Water
 * able to breathe, Fire unburnt, Light seeing in the dark, Lightning quick, Earth firm.
 */
public final class Portals {
    /** How long a far gate waits for its destination. */
    static final int OPEN_TICKS = 20 * 30;
    /** A far gate without anchors lands this share of its distance off target, at worst. */
    static final double UNSTEADY = 0.12;
    /** A caster who walks further than this from an open gate leaves it behind. */
    static final double LEAVE = 6;

    private static final Map<UUID, Open> OPEN = new HashMap<>();

    /** A far gate standing open for one caster. */
    record Open(ResourceKey<Level> dimension, Vec3 origin, double range, double scatter, List<Rune> anchors,
                double duration, long until) { }

    private Portals() { }

    /** Opens a far gate for its caster and asks them where it should lead. */
    static void open(ServerPlayer player, Spell spell, CastQuality quality, CastQuality.Outcome outcome, double power) {
        ServerLevel level = player.level();
        double range = spell.reach() * Math.max(0.5, Math.min(1.2, power));
        double scatter = scatter(spell.anchors()) * (1.5 - Math.max(0, Math.min(1, quality.stability())));
        if (outcome == CastQuality.Outcome.WEAKENED) range *= 0.7;
        if (outcome == CastQuality.Outcome.ASTRAY) scatter *= 3;
        if (outcome == CastQuality.Outcome.SURGE) range *= 1.3;
        Vec3 at = player.position();
        OPEN.put(player.getUUID(), new Open(level.dimension(), at, range, scatter, spell.anchors(), spell.duration(),
                level.getGameTime() + OPEN_TICKS));
        Fx.play(level, FxEffect.TELEPORT, at, at.add(0, 2, 0), 0xB07CFF, 1.3f, null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.35f, 1.6f);
        if (ServerPlayNetworking.canSend(player, GatePayload.TYPE)) {
            ServerPlayNetworking.send(player, new GatePayload((int) range, OPEN_TICKS / 20, !spell.anchors().isEmpty()));
        }
    }

    /** How far off a far gate may land, as a share of its distance: Earth holds it truest. */
    static double scatter(List<Rune> anchors) {
        double scatter = UNSTEADY;
        for (Rune anchor : anchors) scatter *= anchor == Rune.EARTH ? 0.1 : 0.5;
        return scatter;
    }

    /** Whether a far gate stands open for a player. */
    public static boolean isOpen(ServerPlayer player) {
        Open open = OPEN.get(player.getUUID());
        return open != null && open.until() >= player.level().getGameTime();
    }

    /** The caster has named where their open gate leads; steps them through it, if it is still open. */
    public static boolean travel(ServerPlayer player, int x, int y, int z) {
        Open open = OPEN.remove(player.getUUID());
        ServerLevel level = player.level();
        if (open == null || open.until() < level.getGameTime() || open.dimension() != level.dimension()
                || player.position().distanceTo(open.origin()) > LEAVE || player.isSpectator()) {
            player.sendOverlayMessage(Component.translatable("magic.hardwrought.gate.closed").withStyle(ChatFormatting.GRAY));
            return false;
        }
        Vec3 from = player.position();
        Vec3 target = new Vec3(x + 0.5, Math.max(level.getMinY(), Math.min(level.getMaxY(), y)), z + 0.5);
        Vec3 way = target.subtract(from);
        double distance = way.length();
        if (distance > open.range()) {
            // It reaches as far as it reaches, and no further.
            target = from.add(way.scale(open.range() / distance));
            distance = open.range();
            player.sendSystemMessage(Component.translatable("magic.hardwrought.gate.too_far", (int) open.range())
                    .withStyle(ChatFormatting.YELLOW));
        }
        double off = open.scatter() * distance * player.getRandom().nextDouble();
        double angle = player.getRandom().nextDouble() * Math.PI * 2;
        target = target.add(Math.cos(angle) * off, 0, Math.sin(angle) * off);
        BlockPos column = BlockPos.containing(target);
        if (!level.getWorldBorder().isWithinBounds(column)) {
            player.sendOverlayMessage(Component.translatable("magic.hardwrought.gate.closed").withStyle(ChatFormatting.GRAY));
            return false;
        }
        Vec3 to = safe(level, player, column);
        Fx.play(level, FxEffect.TELEPORT, from, from.add(0, 2, 0), 0xB07CFF, 1.4f, null);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.8f);
        player.teleportTo(level, to.x, to.y, to.z, Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
        player.resetFallDistance();
        Fx.play(level, FxEffect.TELEPORT, to.add(0, 2, 0), to, 0xB07CFF, 1.4f, null);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.1f);
        arrive(player, open.anchors(), open.duration());
        if (open.anchors().isEmpty()) {
            // Nothing held the way: the traveller arrives reeling.
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 140));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
        }
        return true;
    }

    /** What the anchors give the traveller on arrival, near or far. */
    static void arrive(ServerPlayer player, List<Rune> anchors, double duration) {
        for (Rune anchor : anchors.stream().distinct().toList()) {
            int ticks = (int) (200 * Math.max(1, duration));
            switch (anchor) {
                case DARK -> player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks));
                case WIND -> player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks / 2));
                case WATER -> {
                    player.clearFire();
                    player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, ticks * 2));
                }
                case FIRE -> player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ticks));
                case LIGHT -> player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks * 2));
                case LIGHTNING -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1));
                case EARTH -> player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks / 2));
            }
        }
    }

    /**
     * Somewhere to stand near a point: two blocks of room over solid ground, searched up and down
     * from it, never in lava; failing that, the surface of the column.
     */
    static Vec3 safe(ServerLevel level, ServerPlayer player, BlockPos at) {
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        for (int d = 0; d <= 32; d++) {
            for (int sign : d == 0 ? new int[] {1} : new int[] {1, -1}) {
                BlockPos feet = at.above(d * sign);
                if (feet.getY() <= level.getMinY() || feet.getY() + 1 >= level.getMaxY()) continue;
                if (standable(level, player, feet)) return Vec3.atBottomCenterOf(feet);
            }
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        return Vec3.atBottomCenterOf(new BlockPos(at.getX(), Math.max(top, level.getMinY() + 1), at.getZ()));
    }

    private static boolean standable(ServerLevel level, ServerPlayer player, BlockPos feet) {
        BlockPos below = feet.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
        if (level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(feet.above()).is(FluidTags.LAVA)) return false;
        if (level.getFluidState(below).is(FluidTags.LAVA)) return false;
        Vec3 bottom = Vec3.atBottomCenterOf(feet);
        return level.noCollision(player, player.getBoundingBox().move(bottom.subtract(player.position())));
    }
}
