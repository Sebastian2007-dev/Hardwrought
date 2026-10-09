package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.enchanting.RuneEnchanting;
import de.ipnats.hardwrought.metallurgy.Metal;
import de.ipnats.hardwrought.metallurgy.MetalBlocks;
import de.ipnats.hardwrought.metallurgy.ModMetals;
import de.ipnats.hardwrought.smeltery.MoltenMetals;
import de.ipnats.hardwrought.smithing.AlloyEquipment;
import de.ipnats.hardwrought.smithing.ForgeQuality;
import de.ipnats.hardwrought.smithing.UpgradeEquipment;
import de.ipnats.hardwrought.smithing.Upgrading;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.GameType;

import java.util.List;

/** The tiers beyond tungsten steel, worked up at the smithing table; and the blocks of every metal. */
public final class UpgradeGameTests {
    @GameTest
    public void aPieceIsWorkedUpTierByTierAndKeepsWhatItCarried(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer smith = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        var efficiency = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Enchantments.EFFICIENCY.identifier()));

        ItemStack pickaxe = new ItemStack(AlloyEquipment.tool(AlloyEquipment.Tier.TUNGSTEN_STEEL, AlloyEquipment.Tool.PICKAXE));
        pickaxe.enchant(efficiency, 4);
        pickaxe.set(ModDataComponents.FORGE_QUALITY, new ForgeQuality(0.8f, ForgeQuality.Treatment.TEMPERED));
        smith.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        helper.assertTrue(Upgrading.find(level, smith.getInventory(), pickaxe) == null, "Without template and bar nothing is worked up");

        var mithrilTemplate = UpgradeEquipment.template(UpgradeEquipment.Tier.MITHRIL);
        var adamantTemplate = UpgradeEquipment.template(UpgradeEquipment.Tier.ADAMANT);
        smith.getInventory().setItem(1, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        smith.getInventory().setItem(2, new ItemStack(Items.NETHERITE_INGOT));
        smith.getInventory().setItem(3, new ItemStack(mithrilTemplate));
        smith.getInventory().setItem(4, new ItemStack(ModMetals.ingot(Metal.MITHRIL)));
        smith.getInventory().setItem(5, new ItemStack(adamantTemplate));
        smith.getInventory().setItem(6, new ItemStack(ModMetals.ingot(Metal.ADAMANTIUM)));

        var toNetherite = Upgrading.find(level, smith.getInventory(), pickaxe);
        helper.assertTrue(toNetherite != null && toNetherite.pattern() == 0
                        && toNetherite.input().template().is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                "Tungsten steel is worked up to netherite, with netherite's template although finer ones are at hand");
        ItemStack netherite = toNetherite.recipe().value().assemble(toNetherite.input());
        helper.assertTrue(netherite.is(Items.NETHERITE_PICKAXE) && netherite.getEnchantments().getLevel(efficiency) == 4
                        && ForgeQuality.of(netherite) != null,
                "and what it becomes keeps its runes and its making: " + netherite);

        var toMithril = Upgrading.find(level, smith.getInventory(), netherite);
        helper.assertTrue(toMithril != null && toMithril.pattern() == 1 && toMithril.input().template().is(mithrilTemplate),
                "Netherite is worked up to mithril with mithril's template");
        ItemStack mithril = toMithril.recipe().value().assemble(toMithril.input());
        var mithrilPickaxe = UpgradeEquipment.tool(UpgradeEquipment.Tier.MITHRIL, UpgradeEquipment.Tool.PICKAXE);
        helper.assertTrue(mithril.is(mithrilPickaxe) && mithril.getEnchantments().getLevel(efficiency) == 4, "into a mithril pickaxe: " + mithril);

        var toAdamant = Upgrading.find(level, smith.getInventory(), mithril);
        helper.assertTrue(toAdamant != null && toAdamant.pattern() == 2, "and mithril to adamant with adamant's");
        ItemStack adamant = toAdamant.recipe().value().assemble(toAdamant.input());
        helper.assertTrue(adamant.is(UpgradeEquipment.tool(UpgradeEquipment.Tier.ADAMANT, UpgradeEquipment.Tool.PICKAXE))
                        && adamant.getMaxDamage() > mithril.getMaxDamage() && mithril.getMaxDamage() > netherite.getMaxDamage(),
                "Each tier lasts longer than the one below: " + netherite.getMaxDamage() + " / " + mithril.getMaxDamage()
                        + " / " + adamant.getMaxDamage());
        helper.assertTrue(Upgrading.find(level, smith.getInventory(), adamant) == null, "Beyond adamant there is nothing");
        helper.assertTrue(Upgrading.find(level, smith.getInventory(), new ItemStack(Items.STICK)) == null, "and a stick is worked up to nothing");

        // Armor goes the same way.
        var toMithrilHelmet = Upgrading.find(level, smith.getInventory(), new ItemStack(Items.NETHERITE_HELMET));
        helper.assertTrue(toMithrilHelmet != null && toMithrilHelmet.recipe().value().assemble(toMithrilHelmet.input())
                        .is(UpgradeEquipment.armor(UpgradeEquipment.Tier.MITHRIL, ArmorType.HELMET)), "A netherite helmet becomes a mithril one");
        helper.succeed();
    }

    @GameTest
    public void howTrulyThePatternIsTracedDecidesHowWellMadeThePieceIs(GameTestHelper helper) {
        ForgeQuality before = new ForgeQuality(0.7f, ForgeQuality.Treatment.TEMPERED, 0.2f, 5);
        ForgeQuality fine = Upgrading.worked(before, 1.0f), fair = Upgrading.worked(before, Upgrading.FAIR), poor = Upgrading.worked(before, 0.2f);
        helper.assertTrue(fine.craftsmanship() > before.craftsmanship() && Math.abs(fair.craftsmanship() - before.craftsmanship()) < 0.001f
                        && poor.craftsmanship() < before.craftsmanship(),
                "A true trace betters the piece, a fair one keeps it, a poor one costs it: " + fine.craftsmanship() + " / "
                        + fair.craftsmanship() + " / " + poor.craftsmanship());
        helper.assertTrue(before.craftsmanship() - poor.craftsmanship() <= 0.3001f && fine.craftsmanship() - before.craftsmanship() <= 0.1501f,
                "but neither without limit");
        helper.assertTrue(fine.treatment() == ForgeQuality.Treatment.TEMPERED && fine.polish() == 0.2f && fine.passes() == 5,
                "Its hardening and its polish stay as they were");
        ForgeQuality found = Upgrading.worked(null, Upgrading.FAIR);
        helper.assertTrue(found.craftsmanship() == Upgrading.UNFORGED && found.treatment() == ForgeQuality.Treatment.AIR,
                "A piece nobody forged counts as fair work to begin with");
        helper.succeed();
    }

    @GameTest
    public void theMetalsOfTheModTakeRunesAndStackIntoBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var efficiency = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Enchantments.EFFICIENCY.identifier()));
        for (ItemStack pickaxe : List.of(new ItemStack(AlloyEquipment.tool(AlloyEquipment.Tier.STEEL, AlloyEquipment.Tool.PICKAXE)),
                new ItemStack(UpgradeEquipment.tool(UpgradeEquipment.Tier.MITHRIL, UpgradeEquipment.Tool.PICKAXE)))) {
            helper.assertTrue(RuneEnchanting.suited(level, pickaxe).contains(efficiency) && RuneEnchanting.enchantability(pickaxe) > 0,
                    "A finished pickaxe of the mod's own metals takes a pickaxe's runes: " + pickaxe);
        }
        helper.assertTrue(MetalBlocks.materials().size() == 21 && MetalBlocks.block("tin") != null && MetalBlocks.block("iron") == null,
                "Every metal that had no block has one now; iron keeps vanilla's");
        CraftingRecipe nine = (CraftingRecipe) level.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("hardwrought", "tin_block"))).orElseThrow().value();
        ItemStack bar = new ItemStack(ModMetals.ingot(Metal.TIN));
        ItemStack block = nine.assemble(CraftingInput.of(3, 3, java.util.Collections.nCopies(9, bar)));
        helper.assertTrue(block.is(MetalBlocks.block("tin").asItem()), "Nine bars of tin make a block of tin: " + block);
        CraftingRecipe back = (CraftingRecipe) level.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("hardwrought", "tin_ingot_from_block"))).orElseThrow().value();
        ItemStack bars = back.assemble(CraftingInput.of(1, 1, List.of(block)));
        helper.assertTrue(bars.is(bar.getItem()) && bars.getCount() == 9, "and the block gives its nine back: " + bars);
        var melt = MoltenMetals.melt(block.getItem());
        helper.assertTrue(melt != null && melt.material().equals("tin") && melt.amount() == 9 * MoltenMetals.INGOT,
                "A block melts as nine bars");
        helper.succeed();
    }
}
