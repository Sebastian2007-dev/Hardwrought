package de.ipnats.hardwrought.core.utilities;

import net.minecraft.server.MinecraftServer;

public final class ServerThread {
    private ServerThread() { }

    public static void require(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Hardwrought state can only be accessed on the logical server thread");
        }
    }
}
