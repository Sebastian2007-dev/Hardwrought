package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.client.smeltery.SmelteryScreen;
import de.ipnats.hardwrought.smeltery.CastingTableBlockEntity;
import de.ipnats.hardwrought.smeltery.Casts;
import de.ipnats.hardwrought.smeltery.MoltenMetals;
import de.ipnats.hardwrought.smeltery.SmelteryBlocks;
import de.ipnats.hardwrought.smeltery.SmelteryControllerBlock;
import de.ipnats.hardwrought.smeltery.SmelteryControllerBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * Casting as it looks: the casts lying in their tables, metal standing in a cast in the cast's own
 * outline, a bar that has set — and the smeltery's screen with a tank so big that a bar in it would
 * be a hairline if it were drawn to scale. Photographed, because none of it is anything a server
 * test can see.
 */
public final class SmelteryClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            BlockPos controller = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) player.level();
                BlockPos base = player.blockPosition().south(12);
                for (int x = -4; x <= 6; x++) {
                    for (int z = -8; z <= 4; z++) {
                        for (int y = 0; y <= 5; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                    }
                }
                // Left to right: a blank, an empty cast, a cast half full, a cast with its bar set, a part setting.
                String[] metals = {"", "", "iron", "gold", "steel"};
                ItemStack[] casts = {new ItemStack(Casts.BLANK), cast("sword_blade"), cast("pickaxe_head"),
                        cast(Casts.INGOT), cast("axe_head")};
                int[] shares = {0, 0, 50, 100, 100};
                for (int i = 0; i < casts.length; i++) {
                    BlockPos pos = base.offset(i - 2, 0, 0);
                    level.setBlockAndUpdate(pos, SmelteryBlocks.CASTING_TABLE.defaultBlockState());
                    CastingTableBlockEntity table = (CastingTableBlockEntity) level.getBlockEntity(pos);
                    Casts.Cast shape = Casts.of(casts[i]);
                    table.placeCast(casts[i]);
                    if (shares[i] > 0) table.fill(metals[i], shape.amount() * shares[i] / 100);
                }
                // A smeltery of three by three, five high — more millibuckets than a short holds — with a few bars in it.
                BlockPos tank = base.offset(0, 0, -5);
                var bricks = SmelteryBlocks.SMELTERY_BRICKS.defaultBlockState();
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setBlockAndUpdate(tank.offset(x, 0, z), bricks);
                for (int y = 1; y <= 5; y++) {
                    for (int i = -1; i <= 1; i++) {
                        level.setBlockAndUpdate(tank.offset(i, y, -2), bricks);
                        level.setBlockAndUpdate(tank.offset(-2, y, i), bricks);
                        level.setBlockAndUpdate(tank.offset(2, y, i), bricks);
                        level.setBlockAndUpdate(tank.offset(i, y, 2), bricks);
                    }
                }
                BlockPos at = tank.offset(0, 1, 2);
                level.setBlockAndUpdate(at, SmelteryBlocks.CONTROLLER.defaultBlockState()
                        .setValue(SmelteryControllerBlock.FACING, Direction.SOUTH));
                SmelteryControllerBlockEntity smeltery = (SmelteryControllerBlockEntity) level.getBlockEntity(at);
                smeltery.addFluidForTesting("iron", MoltenMetals.INGOT);
                smeltery.addFluidForTesting("gold", MoltenMetals.INGOT);
                smeltery.addFluidForTesting("tungsten_steel", 3 * MoltenMetals.INGOT);
                // Somewhere to stand and look down into the casts from.
                for (int y = 0; y <= 1; y++) level.setBlockAndUpdate(base.offset(0, y, 2), Blocks.SMOOTH_STONE.defaultBlockState());
                player.teleportTo(level, base.getX() + 0.5, base.getY() + 2, base.getZ() + 2.5, Set.of(), 180.0f, 50.0f, false);
                return at;
            });
            world.getConnection().waitForChunksRender();
            // The gold has set after two seconds; the iron stays half poured.
            context.waitTicks(CastingTableBlockEntity.SETTING_TICKS + 20);
            context.takeScreenshot("hardwrought-casting");

            context.waitFor(client -> client.level.getBlockEntity(controller) instanceof SmelteryControllerBlockEntity smeltery
                    && smeltery.formed() && smeltery.fluids().size() == 3, 200);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                SmelteryControllerBlockEntity smeltery = (SmelteryControllerBlockEntity) player.level().getBlockEntity(controller);
                smeltery.selectForCasting("tungsten_steel");
                player.openMenu(smeltery);
            });
            context.waitFor(client -> client.gui.screen() instanceof SmelteryScreen, 100);
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-smeltery-thin-layers");
            context.runOnClient(client -> client.player.closeContainer());

            // A cast part in the hand at the grindstone, and — in survival, with armor on — the stamina
            // drops standing clear of the armor row.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ItemStack head = new ItemStack(de.ipnats.hardwrought.smithing.ToolParts.part(
                        de.ipnats.hardwrought.smithing.ToolParts.SmithMetal.IRON,
                        de.ipnats.hardwrought.smithing.ToolParts.Part.PICKAXE_HEAD));
                head.set(de.ipnats.hardwrought.core.registry.ModDataComponents.FORGE_QUALITY,
                        new de.ipnats.hardwrought.smithing.ForgeQuality(Casts.CAST_CRAFTSMANSHIP,
                                de.ipnats.hardwrought.smithing.ForgeQuality.Treatment.AIR, 0.08f, 2));
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, head);
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
                        new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            });
            context.waitTicks(30);
            context.takeScreenshot("hardwrought-stamina-above-armor");
            context.runOnClient(client -> client.gui.setScreen(
                    new de.ipnats.hardwrought.client.smithing.GrindingScreen(controller)));
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-grinding");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }

    private static ItemStack cast(String shape) {
        return new ItemStack(Casts.all().stream().filter(cast -> cast.shape().equals(shape)).findFirst().orElseThrow().fired());
    }
}
