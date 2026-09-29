package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.smithing.AlloyEquipment;
import de.ipnats.hardwrought.smithing.Rust;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The metals above iron: each outlasts the one below, and only plain steel rusts. */
public final class SteelTierGameTests {
    @GameTest
    public void eachTierOutlastsTheOneBelow(GameTestHelper helper) {
        int last = new ItemStack(Items.IRON_PICKAXE).getMaxDamage();
        for (AlloyEquipment.Tier tier : AlloyEquipment.Tier.values()) {
            int durability = new ItemStack(AlloyEquipment.tool(tier, AlloyEquipment.Tool.PICKAXE)).getMaxDamage();
            helper.assertTrue(durability > last, tier.id() + " pickaxe outlasts the tier below: " + durability + " vs " + last);
            last = durability;
        }
        helper.succeed();
    }

    @GameTest
    public void onlyPlainSteelRusts(GameTestHelper helper) {
        ItemStack steel = new ItemStack(AlloyEquipment.tool(AlloyEquipment.Tier.STEEL, AlloyEquipment.Tool.PICKAXE));
        ItemStack stainless = new ItemStack(AlloyEquipment.tool(AlloyEquipment.Tier.STAINLESS_STEEL, AlloyEquipment.Tool.PICKAXE));
        Rust.add(steel, 1);
        Rust.add(stainless, 1);
        helper.assertTrue(Rust.of(steel) == 1 && Rust.of(stainless) == 0, "Steel rusts, stainless steel does not");
        helper.assertTrue(Rust.speedFactor(steel) < 0.61 && Rust.damageFactor(steel) < 0.71,
                "A fully rusted pick digs slower and hits softer");
        helper.succeed();
    }
}
