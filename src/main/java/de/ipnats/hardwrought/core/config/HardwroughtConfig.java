package de.ipnats.hardwrought.core.config;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Local startup settings. Gameplay settings must be evaluated on the server; the entries here only
 * control logging and purely client-side rendering.
 *
 * @param dynamicLight renders a carried light source in the client's own copy of the world only
 */
public record HardwroughtConfig(boolean debugLogging, boolean dynamicLight) {
    private static final HardwroughtConfig DEFAULTS = new HardwroughtConfig(false, true);

    public static HardwroughtConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("hardwrought.properties");
        Properties properties = new Properties();
        properties.setProperty("debugLogging", Boolean.toString(DEFAULTS.debugLogging));
        properties.setProperty("dynamicLight", Boolean.toString(DEFAULTS.dynamicLight));
        try {
            if (Files.exists(path)) {
                try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            } else {
                Files.createDirectories(path.getParent());
                try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    properties.store(writer, "Hardwrought - restart Minecraft after editing");
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            Hardwrought.LOGGER.warn("Cannot read config {}; using defaults.", path, exception);
            return DEFAULTS;
        }
        return new HardwroughtConfig(
                flag(properties, "debugLogging", DEFAULTS.debugLogging),
                flag(properties, "dynamicLight", DEFAULTS.dynamicLight));
    }

    private static boolean flag(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key, Boolean.toString(fallback)).trim();
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        Hardwrought.LOGGER.warn("Invalid {} value '{}'; using {}.", key, value, fallback);
        return fallback;
    }
}
