package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.fun.StampiEasterEgg;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/** The smeltery's easter egg: who answers whom, and how they kill. */
public final class FunGameTests {
    @GameTest
    public void eachBrotherOnlyCallsUpTheOther(GameTestHelper helper) {
        for (boolean coin : new boolean[] {true, false}) {
            helper.assertTrue(StampiEasterEgg.summoned("StampiCraft", coin).equals(StampiEasterEgg.GAMES),
                    "StampiCraft only ever calls up StampiGames");
            helper.assertTrue(StampiEasterEgg.summoned("stampigames", coin).equals(StampiEasterEgg.CRAFT),
                    "and StampiGames only ever StampiCraft, however it is written");
        }
        helper.assertTrue(StampiEasterEgg.summoned("Steve", true).equals(StampiEasterEgg.CRAFT)
                        && StampiEasterEgg.summoned("Steve", false).equals(StampiEasterEgg.GAMES),
                "Anyone else gets either");
        helper.assertTrue(StampiEasterEgg.WORDS.containsAll(java.util.List.of("SCHMELZERIE", "SCHMELZERY", "SCHMELZERI")),
                "All three spellings call them up");
        helper.assertTrue(StampiEasterEgg.isMaker("StampiCraft") && StampiEasterEgg.isMaker("StampiGames")
                        && !StampiEasterEgg.isMaker("Steve"),
                "Only the two makers lose the NPC label");

        var types = helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        var player = helper.makeMockServerPlayerInLevel();
        for (String maker : new String[] {StampiEasterEgg.CRAFT, StampiEasterEgg.GAMES}) {
            DamageSource source = new DamageSource(types.getOrThrow(StampiEasterEgg.killedBy(maker)));
            helper.assertTrue(source.is(DamageTypeTags.BYPASSES_ARMOR) && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                            && source.is(DamageTypeTags.BYPASSES_RESISTANCE),
                    "Nothing stands between " + maker + " and the kill");
            String message = source.getLocalizedDeathMessage(player).getString();
            helper.assertTrue(message.endsWith(maker.equals(StampiEasterEgg.CRAFT)
                            ? "was killed by Annoying the Creator." : "was killed by copyright infringement."),
                    "Each kills in his own way: " + message);
        }
        helper.succeed();
    }
}
