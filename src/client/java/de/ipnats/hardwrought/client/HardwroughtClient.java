package de.ipnats.hardwrought.client;

import net.fabricmc.api.ClientModInitializer;

public class HardwroughtClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		de.ipnats.hardwrought.client.debug.DebugHud.initialize();
		de.ipnats.hardwrought.client.survival.SurvivalHud.initialize();
		de.ipnats.hardwrought.client.combat.CombatHud.initialize();
		de.ipnats.hardwrought.client.environment.EnvironmentHud.initialize();
		de.ipnats.hardwrought.client.environment.DynamicLight.initialize();
	}
}
