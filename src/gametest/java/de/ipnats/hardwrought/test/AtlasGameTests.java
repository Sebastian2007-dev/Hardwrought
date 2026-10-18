package de.ipnats.hardwrought.test;

import com.mojang.serialization.JsonOps;
import de.ipnats.hardwrought.atlas.Atlas;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.MapColor;

/** The atlas: what it draws, that it draws a place only once, what it marks, and that it is kept. */
public final class AtlasGameTests {
    @GameTest
    public void bearersSeeOneAnotherOnlyWhileNear(GameTestHelper helper) {
        @SuppressWarnings("removal") ServerPlayer one = helper.makeMockServerPlayerInLevel();
        @SuppressWarnings("removal") ServerPlayer two = helper.makeMockServerPlayerInLevel();
        two.teleportTo(one.getX() + 20, one.getY(), one.getZ());
        var near = Atlas.others(one, java.util.List.of(one, two));
        helper.assertTrue(near.size() == 1 && near.getFirst().name().equals(two.getGameProfile().name()),
                "Another bearer twenty blocks off is in one's atlas, and oneself is not: " + near);
        two.teleportTo(one.getX() + Atlas.BEARER_RANGE + 10, one.getY(), one.getZ());
        helper.assertTrue(Atlas.others(one, java.util.List.of(one, two)).isEmpty(), "but not one far off");
        helper.succeed();
    }

    @GameTest
    public void theAtlasDrawsWhatItsBearerWalksThroughAndKeepsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer bearer = helper.makeMockServerPlayerInLevel();
        helper.assertFalse(Atlas.carries(bearer), "Nobody carries an atlas they were not given");
        bearer.getInventory().setItem(5, new ItemStack(Atlas.ITEM));
        helper.assertTrue(Atlas.carries(bearer), "Anywhere in the inventory counts as carrying it");

        int before = Atlas.page(bearer).chunks();
        int drawn = Atlas.record(bearer);
        helper.assertTrue(drawn > 0 && drawn <= 49 && Atlas.page(bearer).chunks() == before + drawn,
                "The chunks round the bearer are drawn in, as far as they are loaded: " + drawn);
        helper.assertTrue(Atlas.record(bearer) == 0, "and a place is drawn only once");
        byte[] here = Atlas.page(bearer).tile(bearer.getBlockX() >> 4, bearer.getBlockZ() >> 4);
        helper.assertTrue(here != null && here.length == Atlas.TILE, "The chunk the bearer stands in is among them");
        boolean anything = false;
        for (byte square : here) anything |= MapColor.getColorFromPackedId(square & 0xFF) != 0;
        helper.assertTrue(anything, "and it shows the ground, not nothing");
        byte[] surveyed = Atlas.survey(level, bearer.getBlockX() >> 4, bearer.getBlockZ() >> 4);
        helper.assertTrue(java.util.Arrays.equals(here, surveyed), "The same land is drawn the same way twice");

        int marks = Atlas.page(bearer).markers().size();
        helper.assertTrue(Atlas.mark(bearer, 10, -20, "  Zuhause  ", 0), "A place can be marked");
        var marked = Atlas.page(bearer).markers().getLast();
        helper.assertTrue(marked.x() == 10 && marked.z() == -20 && marked.name().equals("Zuhause") && marked.kind() == 0,
                "under its name, tidied: " + marked);
        helper.assertTrue(new Atlas.Marker(0, 0, "x".repeat(60), 0).name().length() == Atlas.MAX_NAME, "A name is cut to length");
        helper.assertTrue(Atlas.mark(bearer, 4, 4, "Mine", 0, 2) && Atlas.page(bearer).markers().getLast().icon() == 2,
                "A mark keeps the symbol it was made with");
        helper.assertTrue(new Atlas.Marker(0, 0, "", 0, Atlas.ICONS + 1).icon() == 1, "and a symbol out of range comes round again");
        Atlas.unmark(bearer, Atlas.page(bearer).markers().size() - 1);

        // Saved with the world and read back the same.
        var data = level.getDataStorage().computeIfAbsent(Atlas.Data.TYPE);
        var saved = Atlas.Data.TYPE.codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var loaded = Atlas.Data.TYPE.codec().parse(JsonOps.INSTANCE, saved).getOrThrow();
        var again = loaded.getClass();
        helper.assertTrue(again == Atlas.Data.class, "The atlas is saved and read back");
        var reread = Atlas.Data.TYPE.codec().encodeStart(JsonOps.INSTANCE, loaded).getOrThrow();
        helper.assertTrue(saved.equals(reread), "and nothing of it is lost on the way");

        helper.assertTrue(Atlas.unmark(bearer, Atlas.page(bearer).markers().size() - 1)
                        && Atlas.page(bearer).markers().size() == marks && !Atlas.unmark(bearer, 999),
                "A mark can be taken out again, and only one that is there");
        helper.succeed();
    }
}
