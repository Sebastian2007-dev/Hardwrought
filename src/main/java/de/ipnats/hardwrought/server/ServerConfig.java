package de.ipnats.hardwrought.server;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * What a server operator can tune without a datapack: the settings of parties, of players gone away
 * from the keyboard, and of boss fights in company. Read once when the server starts, from
 * {@code config/hardwrought-server.properties}, which is written with the defaults the first time.
 *
 * <p>Separate from {@link de.ipnats.hardwrought.core.config.HardwroughtConfig} because that one is
 * read on every client too, and these only mean anything to the logical server.
 *
 * @param partyMaxSize      how many players one party holds at most
 * @param shareKnowledge    whether what one member learns, every member learns
 * @param friendlyFire      whether a new party lets its members hurt each other; the leader can change it
 * @param afkMinutes        how long without moving or acting before a player counts as away; 0 turns it off
 * @param deathCoordinates  whether a dying player (and their party) is told where it happened
 */
public record ServerConfig(int partyMaxSize, boolean shareKnowledge, boolean friendlyFire, int afkMinutes,
                           boolean deathCoordinates) {
    public static final ServerConfig DEFAULTS = new ServerConfig(8, true, false, 5, true);
    private static ServerConfig current = DEFAULTS;

    /** The settings in force; the defaults before a server has started. */
    public static ServerConfig get() {
        return current;
    }

    /** For tests: puts these settings in force until the next {@link #load()}. */
    public static void set(ServerConfig config) {
        current = config == null ? DEFAULTS : config;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("hardwrought-server.properties");
        Properties properties = new Properties();
        properties.setProperty("partyMaxSize", Integer.toString(DEFAULTS.partyMaxSize));
        properties.setProperty("shareKnowledge", Boolean.toString(DEFAULTS.shareKnowledge));
        properties.setProperty("friendlyFire", Boolean.toString(DEFAULTS.friendlyFire));
        properties.setProperty("afkMinutes", Integer.toString(DEFAULTS.afkMinutes));
        properties.setProperty("deathCoordinates", Boolean.toString(DEFAULTS.deathCoordinates));
        try {
            if (Files.exists(path)) {
                try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            } else {
                Files.createDirectories(path.getParent());
                try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    properties.store(writer, "Hardwrought server settings - read when the server starts");
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            Hardwrought.LOGGER.warn("Cannot read server config {}; using defaults.", path, exception);
            current = DEFAULTS;
            return;
        }
        current = new ServerConfig(
                number(properties, "partyMaxSize", DEFAULTS.partyMaxSize, 2, 32),
                flag(properties, "shareKnowledge", DEFAULTS.shareKnowledge),
                flag(properties, "friendlyFire", DEFAULTS.friendlyFire),
                number(properties, "afkMinutes", DEFAULTS.afkMinutes, 0, 240),
                flag(properties, "deathCoordinates", DEFAULTS.deathCoordinates));
    }

    private static boolean flag(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key, Boolean.toString(fallback)).trim();
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        Hardwrought.LOGGER.warn("Invalid {} value '{}'; using {}.", key, value, fallback);
        return fallback;
    }

    private static int number(Properties properties, String key, int fallback, int min, int max) {
        String value = properties.getProperty(key, Integer.toString(fallback)).trim();
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= min && parsed <= max) return parsed;
        } catch (NumberFormatException ignored) {
            // Reported below, like an out-of-range value.
        }
        Hardwrought.LOGGER.warn("Invalid {} value '{}' (allowed {} to {}); using {}.", key, value, min, max, fallback);
        return fallback;
    }
}
