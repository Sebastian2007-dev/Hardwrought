package de.ipnats.hardwrought;

import net.fabricmc.api.ModInitializer;
import de.ipnats.hardwrought.core.config.HardwroughtConfig;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.core.registry.ModCreativeTabs;
import de.ipnats.hardwrought.core.registry.MaterialDefinitions;
import de.ipnats.hardwrought.core.registry.ItemWeightDefinitions;
import de.ipnats.hardwrought.core.registry.FoodNutritionDefinitions;
import de.ipnats.hardwrought.combat.ArmorProfiles;
import de.ipnats.hardwrought.combat.CombatSystem;
import de.ipnats.hardwrought.combat.ShieldProfiles;
import de.ipnats.hardwrought.combat.WeaponProfiles;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.geology.GeologyEvents;
import de.ipnats.hardwrought.progression.ProgressionEvents;
import de.ipnats.hardwrought.geology.RockProfiles;
import de.ipnats.hardwrought.water.AquiferProfiles;
import de.ipnats.hardwrought.water.WaterEvents;
import de.ipnats.hardwrought.water.WaterQualityProfiles;
import de.ipnats.hardwrought.water.WaterQualityStorage;
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
		de.ipnats.hardwrought.water.WaterCurrent.initialize();
		WaterQualityStorage.initialize();
		de.ipnats.hardwrought.water.FrozenWater.initialize();
		de.ipnats.hardwrought.environment.LightBurn.initialize();
		de.ipnats.hardwrought.environment.Torches.initialize();
		de.ipnats.hardwrought.environment.GasIgnition.initialize();
		de.ipnats.hardwrought.water.BucketMachinery.initialize();
		de.ipnats.hardwrought.oil.ModFluids.initialize();
		ModBlocks.initialize();
		de.ipnats.hardwrought.ritual.RitualContent.initialize();
		net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.CRUDE_OIL,
				de.ipnats.hardwrought.oil.CrudeOilBlock.CATCH_CHANCE, de.ipnats.hardwrought.oil.CrudeOilBlock.BURN_CHANCE);
		de.ipnats.hardwrought.oil.OilLakes.initialize();
		de.ipnats.hardwrought.metallurgy.ModMetals.initialize();
		de.ipnats.hardwrought.metallurgy.OrePowders.initialize();
		de.ipnats.hardwrought.magic.VoidCrystal.initialize();
		ModItems.initialize();
		// After the items: the still's bottles become containers of the refinery's liquids.
		de.ipnats.hardwrought.refining.RefineryFluids.initialize();
		de.ipnats.hardwrought.metallurgy.Alloys.initialize();
		de.ipnats.hardwrought.metallurgy.MetalBlocks.initialize();
		de.ipnats.hardwrought.smeltery.SmelteryBlocks.initialize();
		de.ipnats.hardwrought.electricity.ElectricBlocks.initialize();
		de.ipnats.hardwrought.haven.HavenBlocks.initialize();
		de.ipnats.hardwrought.carried.CarriedItems.initialize();
		de.ipnats.hardwrought.carried.CarriedPower.initialize();
		de.ipnats.hardwrought.automation.AutomationBlocks.initialize();
		de.ipnats.hardwrought.tesla.TeslaBlocks.initialize();
		de.ipnats.hardwrought.storage.StorageBlocks.initialize();
		de.ipnats.hardwrought.magic.Magic.initialize();
		de.ipnats.hardwrought.smithing.ToolParts.initialize();
		de.ipnats.hardwrought.smithing.AlloyEquipment.initialize();
		de.ipnats.hardwrought.smithing.UpgradeEquipment.initialize();
		de.ipnats.hardwrought.smithing.NoDiamondGear.initialize();
		de.ipnats.hardwrought.smithing.MetalStock.initialize();
		de.ipnats.hardwrought.core.registry.ModBlockEntities.initialize();
		de.ipnats.hardwrought.fluids.FluidBlocks.initialize();
		de.ipnats.hardwrought.chemistry.SaltPan.initialize();
		de.ipnats.hardwrought.health.Diseases.initialize();
		de.ipnats.hardwrought.survival.Overeating.initialize();
		de.ipnats.hardwrought.health.Herbs.initialize();
		de.ipnats.hardwrought.food.Spoilage.initialize();
		de.ipnats.hardwrought.health.Ailments.initialize();
		de.ipnats.hardwrought.survival.GoldenApples.initialize();
		de.ipnats.hardwrought.survival.Adrenaline.initialize();
		de.ipnats.hardwrought.knowledge.MultiblockKnowledge.initialize();
		de.ipnats.hardwrought.knowledge.MultiblockPlacing.initialize();
		de.ipnats.hardwrought.fun.StampiEasterEgg.initialize();
		de.ipnats.hardwrought.nether.NetherCrossing.initialize();
		de.ipnats.hardwrought.nether.Ashcork.initialize();
		de.ipnats.hardwrought.nether.AshGrove.initialize();
		de.ipnats.hardwrought.nether.CastObsidian.initialize();
		de.ipnats.hardwrought.nether.NetherOres.initialize();
		de.ipnats.hardwrought.nether.NetherLoot.initialize();
		de.ipnats.hardwrought.nether.PortalStabilizers.initialize();
		de.ipnats.hardwrought.wither.SoulSkulls.initialize();
		de.ipnats.hardwrought.wither.WitherPowers.initialize();
		de.ipnats.hardwrought.underworld.Underworld.initialize();
		de.ipnats.hardwrought.underworld.UnderworldGate.initialize();
		de.ipnats.hardwrought.underworld.UnderworldAtmosphere.initialize();
		de.ipnats.hardwrought.underworld.UnderworldRuins.initialize();
		de.ipnats.hardwrought.underworld.GraveUndead.initialize();
		de.ipnats.hardwrought.underworld.Graveyard.initialize();
		de.ipnats.hardwrought.witchking.WitchKingContent.initialize();
		de.ipnats.hardwrought.end.EndRules.initialize();
		de.ipnats.hardwrought.end.BrokenIsland.initialize();
		de.ipnats.hardwrought.end.dragon.VoidDragon.initialize();
		de.ipnats.hardwrought.enchanting.Soulbound.initialize();
		de.ipnats.hardwrought.water.WaterTreatmentBlocks.initialize();
		de.ipnats.hardwrought.gasline.GasLines.initialize();
		de.ipnats.hardwrought.refining.RefineryBlocks.initialize();
		de.ipnats.hardwrought.refining.RefineryMenu.initialize();
		de.ipnats.hardwrought.parts.Parts.initialize();
		de.ipnats.hardwrought.knowledge.FluidIcons.initialize();
		de.ipnats.hardwrought.power.PowerBlocks.initialize();
		de.ipnats.hardwrought.steam.SteamBlocks.initialize();
		de.ipnats.hardwrought.materials.Materials.initialize();
		de.ipnats.hardwrought.materials.MaterialBlocks.initialize();
		de.ipnats.hardwrought.multiblock.BigMachines.initialize();
		de.ipnats.hardwrought.nuclear.Nuclear.initialize();
		de.ipnats.hardwrought.building.BuildingPhysics.initialize();
		de.ipnats.hardwrought.building.SharedCell.initialize();
		de.ipnats.hardwrought.environment.SeasonState.initialize();
		de.ipnats.hardwrought.environment.SeasonCommands.initialize();
		de.ipnats.hardwrought.mobs.MobSystem.initialize();
		de.ipnats.hardwrought.mobs.SleepDebt.initialize();
		de.ipnats.hardwrought.smithing.Rust.initialize();
		de.ipnats.hardwrought.smithing.ForgeMenu.initialize();
		de.ipnats.hardwrought.chemistry.StillMenu.initialize();
		de.ipnats.hardwrought.core.registry.ModEffects.initialize();
		ModCreativeTabs.initialize();
		DataResourceLoader.get().registerReloadListener(id("materials"), new MaterialDefinitions());
		DataResourceLoader.get().registerReloadListener(id("item_weights"), new ItemWeightDefinitions());
		DataResourceLoader.get().registerReloadListener(id("food_nutrition"), new FoodNutritionDefinitions());
		DataResourceLoader.get().registerReloadListener(id("weapon_profiles"), new WeaponProfiles());
		DataResourceLoader.get().registerReloadListener(id("armor_profiles"), new ArmorProfiles());
		DataResourceLoader.get().registerReloadListener(id("shield_profiles"), new ShieldProfiles());
		DataResourceLoader.get().registerReloadListener(id("water_quality"), new WaterQualityProfiles());
		DataResourceLoader.get().registerReloadListener(id("aquifer"), new AquiferProfiles());
		DataResourceLoader.get().registerReloadListener(id("rock"), new RockProfiles());
		DataResourceLoader.get().registerReloadListener(id("structure"), new de.ipnats.hardwrought.building.StructuralProfiles());
		CoreNetworking.initialize();
		de.ipnats.hardwrought.fx.Fx.initialize();
		SurvivalSystem.initializeEvents();
		CombatSystem.initializeEvents();
		WaterEvents.initialize();
		de.ipnats.hardwrought.metallurgy.MetalWorldgen.initialize();
		de.ipnats.hardwrought.geology.OreBodies.initialize();
		de.ipnats.hardwrought.magic.VoidCrystal.initializeWorldgen();
		GeologyEvents.initialize();
		ProgressionEvents.initialize();
		de.ipnats.hardwrought.knowledge.KnowledgeEvents.initialize();
		de.ipnats.hardwrought.knowledge.WorldRecipes.initialize();
		de.ipnats.hardwrought.smithing.SmithingEvents.initialize();
		de.ipnats.hardwrought.smithing.Forging.initialize();
		de.ipnats.hardwrought.smithing.Grinding.initialize();
		de.ipnats.hardwrought.enchanting.RuneEnchanting.initialize();
		de.ipnats.hardwrought.smithing.Upgrading.initialize();
		de.ipnats.hardwrought.atlas.Atlas.initialize();
		de.ipnats.hardwrought.machinery.KineticReadout.initialize();
		de.ipnats.hardwrought.progression.Weaving.initialize();
		de.ipnats.hardwrought.progression.StructureLoot.initialize();
		de.ipnats.hardwrought.survival.Crawling.initialize();
		de.ipnats.hardwrought.survival.Ultra.initialize();
		CoreLifecycle.initialize();
		de.ipnats.hardwrought.party.Parties.initialize();
		de.ipnats.hardwrought.server.Afk.initialize();
		de.ipnats.hardwrought.survival.SleeperSync.initialize();
		de.ipnats.hardwrought.server.ServerCommands.initialize();
		de.ipnats.hardwrought.encounter.Encounters.initialize();
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
