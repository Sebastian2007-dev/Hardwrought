package de.ipnats.hardwrought.environment;

import de.ipnats.hardwrought.core.networking.SeasonPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The season, where anything can reach it, and the autumn fog that goes with it.
 *
 * <p>{@link SeasonCycle} can work the season out from a day time, which is enough for everything
 * that runs during a simulation pass. Two things cannot use it. Vanilla's {@code Biome} decides
 * whether precipitation is rain or snow without being handed a world at all, and the client has to
 * draw a fog the server rolled. Hence a holder: one value, set by the server each tick and adopted
 * by the client when the packet arrives, which in single player is the same value written twice.
 *
 * <p>The fog is derived rather than stored — from the world seed and the day number, the way the
 * calendar is derived from the clock. Nothing to save, nothing to migrate, and the same morning is
 * foggy however many times the world is reloaded.
 */
public final class SeasonState {
    /** How often the server tells the client where the year is. Fog moves slowly; this is plenty. */
    private static final int BROADCAST_INTERVAL = 40;
    /** A change in fog or in the turn of the year smaller than this is not worth a packet. */
    private static final float FOG_EPSILON = 0.02f;
    private static final float BLEND_EPSILON = 0.01f;

    /**
     * How many of a season's last days the world spends looking like the next one.
     *
     * <p>A year that changed colour between two frames would read as a bug rather than as a season,
     * so the look of the world runs ahead of the calendar and arrives gradually. Three days of
     * twelve is enough to be unmistakable while watching and short enough that most of a season
     * still looks like itself. It is also how it happens outside: the trees turn before the
     * weather does.
     */
    private static final int BLEND_DAYS = 3;

    /** Fog comes up at first light and is gone by the afternoon, if it lasts that long. */
    private static final long FOG_SHORTEST = 2_500L;
    private static final long FOG_LONGEST = 9_000L;
    /** How long it takes to close in, and the longer time it takes to lift. */
    private static final long FOG_RISE = 600L;
    private static final long FOG_FALL = 1_400L;
    /** The thickest an autumn morning gets. Not 1: a player must always be able to see their feet. */
    private static final float FOG_MAX = 0.88f;

    private static volatile Season season = Season.SPRING;
    private static volatile float fog;
    private static volatile int dayInSeason = 1;
    private static volatile float blend;
    /**
     * A fog put there by hand instead of by the calendar, or null when the year is left to itself.
     *
     * <p>The only piece of mutable state in this class, and it exists for one reason: an autumn fog
     * is rolled at about one morning in three and a person testing one should not have to wait for
     * the dice. It is deliberately not saved — a forced fog is gone at the next restart, and at the
     * next dawn, because a debug tool that quietly outlives the session it was used in is a trap.
     */
    private static volatile Float forcedFog;
    private static volatile long forcedFogDay = Long.MIN_VALUE;
    /**
     * When the last broadcast went out, and whether one ever has.
     *
     * <p>The flag is not redundant. This used to lean on {@code lastBroadcast = Long.MIN_VALUE} as
     * a "never" marker, and {@code now - Long.MIN_VALUE} overflows to a negative number for every
     * game time there will ever be — so the interval was never up and the packet was never sent.
     * In single player that went unnoticed for a while, because both sides share the holder below
     * and the client was reading what the server had just written. On a dedicated server it meant
     * no seasons reached anybody at all.
     */
    private static long lastBroadcast;
    private static boolean everSent;
    private static Season lastSent;
    private static float lastSentFog = -1;
    private static float lastSentBlend = -1;

    private SeasonState() { }

    public static Season season() { return season; }

    /** How thick the fog is, 0 to 1. Zero in every season but autumn, and most autumn days too. */
    public static float fog() { return fog; }

    public static int dayInSeason() { return dayInSeason; }

    /** How far the look of the world has already turned towards the next season, 0 to 1. */
    public static float blend() { return blend; }

    /** Called by the client when the server's packet arrives. */
    public static void adopt(Season adopted, float adoptedFog, int day, float adoptedBlend) {
        season = adopted;
        fog = adoptedFog;
        dayInSeason = day;
        blend = adoptedBlend;
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(SeasonState::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
        // A player who has just joined is told at once rather than within the next two seconds.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld != null) refresh(overworld);
            send(handler.getPlayer());
        });
    }

    private static void reset() {
        season = Season.SPRING;
        fog = 0;
        dayInSeason = 1;
        blend = 0;
        forcedFog = null;
        forcedFogDay = Long.MIN_VALUE;
        lastBroadcast = 0;
        everSent = false;
        lastSent = null;
        lastSentFog = -1;
        lastSentBlend = -1;
    }

    private static void tick(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        refresh(overworld);
        long now = overworld.getGameTime();
        boolean due = !everSent || now - lastBroadcast >= BROADCAST_INTERVAL;
        boolean changed = season != lastSent || Math.abs(fog - lastSentFog) >= FOG_EPSILON
                || Math.abs(blend - lastSentBlend) >= BLEND_EPSILON;
        if (!due || !changed) return;
        lastBroadcast = now;
        everSent = true;
        lastSent = season;
        lastSentFog = fog;
        lastSentBlend = blend;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player);
    }

    private static void refresh(ServerLevel overworld) {
        long clock = overworld.getOverworldClockTime();
        season = SeasonCycle.at(clock);
        dayInSeason = SeasonCycle.dayInSeason(clock);
        blend = blendAt(clock);
        Float forced = forcedFog;
        boolean sameDay = Math.floorDiv(clock, SeasonCycle.TICKS_PER_DAY) == forcedFogDay;
        if (forced != null && sameDay) {
            fog = forced;
            return;
        }
        if (forced != null) forcedFog = null;
        fog = fogAt(clock, overworld.getSeed());
    }

    /**
     * Put a fog on the world by hand, or hand it back to the calendar with null. Lasts until the
     * next dawn and is never saved; see {@link #forcedFog}.
     */
    public static void force(ServerLevel overworld, Float thickness) {
        forcedFog = thickness;
        forcedFogDay = thickness == null ? Long.MIN_VALUE
                : Math.floorDiv(overworld.getOverworldClockTime(), SeasonCycle.TICKS_PER_DAY);
        refresh(overworld);
    }

    /** Whether the fog on the world at this moment was put there by hand. */
    public static boolean fogIsForced() {
        return forcedFog != null;
    }

    /** The thickest a natural fog gets, which is what a forced one defaults to. */
    public static float thickestFog() {
        return FOG_MAX;
    }

    private static void send(ServerPlayer player) {
        if (player == null || !ServerPlayNetworking.canSend(player, SeasonPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new SeasonPayload(season.ordinal(), fog, dayInSeason, blend));
    }

    /**
     * How far through the turn into the next season this moment is, 0 to 1.
     *
     * <p>Zero for most of a season, then a steady ramp across its last {@value #BLEND_DAYS} days.
     * It is continuous rather than stepped: the client decides for itself how finely to act on it,
     * because the light can follow it every frame while the chunk colours cannot.
     */
    public static float blendAt(long dayTime) {
        long intoSeason = Math.floorMod(dayTime, SeasonCycle.TICKS_PER_SEASON);
        long blending = BLEND_DAYS * SeasonCycle.TICKS_PER_DAY;
        long begins = SeasonCycle.TICKS_PER_SEASON - blending;
        if (intoSeason < begins) return 0;
        return (float) ((intoSeason - begins) / (double) blending);
    }

    /** The season the year is turning towards. */
    public static Season next(Season season) {
        Season[] seasons = Season.values();
        return seasons[(season.ordinal() + 1) % seasons.length];
    }

    /**
     * How thick the fog is at this moment, worked out rather than remembered.
     *
     * <p>One roll per day decides whether there is fog at all and how long it lasts; the rest is the
     * shape of the morning. It begins at first light, because that is when a real one does and
     * because a fog that could begin at any hour would be a fog nobody could plan around.
     */
    public static float fogAt(long dayTime, long seed) {
        if (SeasonCycle.at(dayTime).fogChance() <= 0) return 0;
        long day = Math.floorDiv(dayTime, SeasonCycle.TICKS_PER_DAY);
        long roll = mix(seed, day);
        double chance = (roll >>> 11 & 0xFFFF) / 65_536.0;
        if (chance >= SeasonCycle.at(dayTime).fogChance()) return 0;

        long duration = FOG_SHORTEST + Math.floorMod(roll >>> 27, FOG_LONGEST - FOG_SHORTEST);
        long sinceDawn = Math.floorMod(dayTime, SeasonCycle.TICKS_PER_DAY);
        if (sinceDawn >= duration) return 0;
        double rising = Math.min(1.0, sinceDawn / (double) FOG_RISE);
        double falling = Math.min(1.0, (duration - sinceDawn) / (double) FOG_FALL);
        return (float) (FOG_MAX * Math.min(rising, falling));
    }

    /** A cheap avalanche so that consecutive days do not give near-identical rolls. */
    private static long mix(long seed, long day) {
        long value = seed * 0x9E3779B97F4A7C15L + day * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        return value;
    }
}
