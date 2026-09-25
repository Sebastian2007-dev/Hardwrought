package de.ipnats.hardwrought.client;

import net.fabricmc.api.ClientModInitializer;

public class HardwroughtClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		de.ipnats.hardwrought.client.debug.DebugHud.initialize();
		de.ipnats.hardwrought.client.survival.SurvivalHud.initialize();
		de.ipnats.hardwrought.client.survival.CrawlClient.initialize();
		de.ipnats.hardwrought.client.survival.WeightTooltip.initialize();
		de.ipnats.hardwrought.client.survival.MeltingPointTooltip.initialize();
		de.ipnats.hardwrought.client.survival.NutritionScreen.initialize();
		de.ipnats.hardwrought.client.chemistry.ChemistryClient.initialize();
		de.ipnats.hardwrought.client.smithing.SmithingClient.initialize();
		de.ipnats.hardwrought.client.combat.CombatHud.initialize();
		de.ipnats.hardwrought.client.environment.EnvironmentHud.initialize();
		de.ipnats.hardwrought.client.environment.DynamicLight.initialize();
		de.ipnats.hardwrought.client.environment.CarrierWater.initialize();
		de.ipnats.hardwrought.client.environment.LeafOverlay.initialize();
		net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
				de.ipnats.hardwrought.core.registry.ModBlockEntities.DRYING_RACK,
				de.ipnats.hardwrought.client.environment.DryingRackRenderer::new);
		de.ipnats.hardwrought.client.knowledge.CompendiumClient.initialize();
		net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(
				context -> new de.ipnats.hardwrought.client.knowledge.MultiblockViewRenderer());
		de.ipnats.hardwrought.client.machinery.MachineryClient.initialize();
	}
}
