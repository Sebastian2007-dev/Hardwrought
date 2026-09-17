package de.ipnats.hardwrought.client;

import net.fabricmc.api.ClientModInitializer;

public class HardwroughtClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		de.ipnats.hardwrought.client.debug.DebugHud.initialize();
		de.ipnats.hardwrought.client.survival.SurvivalHud.initialize();
	}
}
