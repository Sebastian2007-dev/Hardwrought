package de.ipnats.hardwrought.core.debug;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Future atmosphere/geology/statics systems supply authoritative measurements here. */
public final class DiagnosticRegistry {
    private final de.ipnats.hardwrought.core.utilities.ThreadOwnership ownership =
            new de.ipnats.hardwrought.core.utilities.ThreadOwnership();
    public enum Channel { ENVIRONMENT, GAS, TEMPERATURE, WATER, STRUCTURE, ORE, KNOWLEDGE }

    @FunctionalInterface
    public interface Probe {
        String inspect(ServerLevel level, BlockPos pos);
    }

    private record Entry(Channel channel, Probe probe) { }
    private final Map<String, Entry> probes = new LinkedHashMap<>();

    public void register(String id, Channel channel, Probe probe) {
        ownership.require();
        if (id == null || id.isBlank() || channel == null || probe == null) {
            throw new IllegalArgumentException("Invalid diagnostic probe");
        }
        if (probes.size() >= 20) throw new IllegalStateException("At most 20 probes per server");
        if (probes.putIfAbsent(id, new Entry(channel, probe)) != null) {
            throw new IllegalArgumentException("Duplicate diagnostic probe: " + id);
        }
    }

    public List<String> inspect(ServerLevel level, BlockPos pos) {
        ownership.require();
        List<String> result = new ArrayList<>();
        // Never load chunks to answer a debug query.
        if (!level.hasChunkAt(pos)) return List.of("Position: chunk not loaded");
        for (Channel channel : Channel.values()) {
            boolean found = false;
            for (var entry : probes.entrySet()) {
                if (entry.getValue().channel != channel) continue;
                found = true;
                try {
                    result.add(channel + " | " + entry.getValue().probe.inspect(level, pos));
                } catch (RuntimeException exception) {
                    result.add(channel + " | " + entry.getKey() + ": probe failed");
                    Hardwrought.LOGGER.debug("Diagnostic probe {} failed", entry.getKey(), exception);
                }
            }
            if (!found) result.add(channel + " | unavailable: simulation not implemented");
        }
        return List.copyOf(result);
    }
}
