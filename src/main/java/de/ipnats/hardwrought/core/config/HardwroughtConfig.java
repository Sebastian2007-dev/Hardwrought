package de.ipnats.hardwrought.core.config;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Local startup settings. Gameplay settings must be evaluated on the server. */
public record HardwroughtConfig(boolean debugLogging) {
    public static HardwroughtConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("hardwrought.properties");
        Properties properties = new Properties();
        properties.setProperty("debugLogging", "false");
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
            return new HardwroughtConfig(false);
        }
        String debug = properties.getProperty("debugLogging").trim();
        if (!debug.equalsIgnoreCase("true") && !debug.equalsIgnoreCase("false")) {
            Hardwrought.LOGGER.warn("Invalid debugLogging value '{}'; using false.", debug);
        }
        return new HardwroughtConfig(Boolean.parseBoolean(debug));
    }
}
