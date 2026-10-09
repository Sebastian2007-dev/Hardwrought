package de.ipnats.hardwrought.client.atlas;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.atlas.Atlas;
import de.ipnats.hardwrought.client.HardwroughtKeys;
import de.ipnats.hardwrought.core.networking.AtlasPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The atlas as this client has heard of it: the chunks drawn and the places marked, dimension by
 * dimension. It is only a copy — the server draws the atlas and keeps it — filled chunk by chunk as
 * the bearer walks, and whole when the book is opened.
 */
public final class AtlasClient {
    public static final KeyMapping OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.atlas", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, HardwroughtKeys.CATEGORY));

    private static final Map<Identifier, Map<Long, byte[]>> tiles = new HashMap<>();
    private static final Map<Identifier, List<Atlas.Marker>> markers = new HashMap<>();

    private AtlasClient() { }

    public static void initialize() {
        Atlas.Book.opener = AtlasClient::open;
        ClientPlayNetworking.registerGlobalReceiver(AtlasPayloads.Tiles.TYPE, (payload, context) -> {
            Map<Long, byte[]> page = tiles.computeIfAbsent(payload.dimension(), dimension -> new HashMap<>());
            if (payload.fresh()) page.clear();
            for (int i = 0; i < payload.chunks().length; i++) {
                byte[] tile = new byte[Atlas.TILE];
                System.arraycopy(payload.tiles(), i * Atlas.TILE, tile, 0, Atlas.TILE);
                page.put(payload.chunks()[i], tile);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(AtlasPayloads.Markers.TYPE,
                (payload, context) -> markers.put(payload.dimension(), payload.markers()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            tiles.clear();
            markers.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN.consumeClick()) open();
        });
    }

    /** Opens the book, for whoever carries one; a word to whoever does not. */
    public static void open() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui.screen() != null) return;
        if (!Atlas.carries(client.player)) {
            client.player.sendOverlayMessage(Component.translatable("gui.hardwrought.atlas.none"));
            return;
        }
        if (ClientPlayNetworking.canSend(AtlasPayloads.Request.TYPE)) ClientPlayNetworking.send(new AtlasPayloads.Request());
        client.gui.setScreen(new AtlasScreen());
    }

    /** The chunks drawn of a dimension, by their place (see {@link Atlas#key}). */
    public static Map<Long, byte[]> tiles(Identifier dimension) {
        return tiles.getOrDefault(dimension, Map.of());
    }

    public static List<Atlas.Marker> markers(Identifier dimension) {
        return markers.getOrDefault(dimension, List.of());
    }
}
