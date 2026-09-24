package de.ipnats.hardwrought.client.survival;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.client.HardwroughtKeys;
import de.ipnats.hardwrought.survival.Crawling;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/** The key that gets the player down to crawl, and up again. */
public final class CrawlClient {
    public static final KeyMapping TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.crawl", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, HardwroughtKeys.CATEGORY));

    private CrawlClient() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE.consumeClick()) {
                if (!(client.player instanceof Crawling.Crawler crawler)) continue;
                boolean crawling = !crawler.hardwrought$crawling();
                crawler.hardwrought$setCrawling(crawling);
                if (ClientPlayNetworking.canSend(Crawling.Toggle.TYPE)) {
                    ClientPlayNetworking.send(new Crawling.Toggle(crawling));
                }
                client.player.sendOverlayMessage(Component.translatable(
                        crawling ? "message.hardwrought.crawl_on" : "message.hardwrought.crawl_off"));
            }
        });
    }
}
