package de.ipnats.hardwrought;

import net.fabricmc.api.ModInitializer;
import de.ipnats.hardwrought.core.config.HardwroughtConfig;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.core.registry.MaterialDefinitions;
import de.ipnats.hardwrought.core.registry.ItemWeightDefinitions;
import de.ipnats.hardwrought.core.registry.FoodNutritionDefinitions;
import de.ipnats.hardwrought.combat.ArmorProfiles;
import de.ipnats.hardwrought.combat.CombatSystem;
import de.ipnats.hardwrought.combat.ShieldProfiles;
import de.ipnats.hardwrought.combat.WeaponProfiles;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.water.WaterEvents;
import de.ipnats.hardwrought.water.WaterQualityProfiles;
import de.ipnats.hardwrought.water.WaterStorage;
import de.ipnats.hardwrought.core.networking.CoreNetworking;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.debug.DebugCommands;
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;

import net.minecraft.resources.Identifier;
import de.ipnats.hardwrought.survival.SurvivalSystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hardwrought implements ModInitializer {
	private static HardwroughtConfig config;
	public static final String MOD_ID = "hardwrought";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		config = HardwroughtConfig.load();
		ModDataComponents.initialize();
		WaterStorage.initialize();
		ModItems.initialize();
		DataResourceLoader.get().registerReloadListener(id("materials"), new MaterialDefinitions());
		DataResourceLoader.get().registerReloadListener(id("item_weights"), new ItemWeightDefinitions());
		DataResourceLoader.get().registerReloadListener(id("food_nutrition"), new FoodNutritionDefinitions());
		DataResourceLoader.get().registerReloadListener(id("weapon_profiles"), new WeaponProfiles());
		DataResourceLoader.get().registerReloadListener(id("armor_profiles"), new ArmorProfiles());
		DataResourceLoader.get().registerReloadListener(id("shield_profiles"), new ShieldProfiles());
		DataResourceLoader.get().registerReloadListener(id("water_quality"), new WaterQualityProfiles());
		CoreNetworking.initialize();
		SurvivalSystem.initializeEvents();
		CombatSystem.initializeEvents();
		WaterEvents.initialize();
		CoreLifecycle.initialize();
		DebugCommands.initialize();
		LOGGER.info("Hardwrought initialized (debug logging: {}).", config.debugLogging());
	}

	public static HardwroughtConfig config() {
		if (config == null) {
			throw new IllegalStateException("Hardwrought has not been initialized yet");
		}
		return config;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
