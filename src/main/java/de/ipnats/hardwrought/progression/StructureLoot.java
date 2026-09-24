package de.ipnats.hardwrought.progression;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * What the world's own buildings no longer come furnished with.
 *
 * <p>A village used to hand a new player a crafting table, furnaces, an anvil and more — every step
 * this mod makes them earn, standing ready in the first house they walked into. Structures are now
 * generated without them. A block that was part of a wall or a floor becomes the plain material it
 * sat among, so no building is left with holes; the rest simply is not there.
 */
public final class StructureLoot {
    private static final Map<Block, Block> STRIPPED = Map.ofEntries(
            Map.entry(Blocks.CRAFTING_TABLE, Blocks.OAK_PLANKS),
            Map.entry(Blocks.SMITHING_TABLE, Blocks.OAK_PLANKS),
            Map.entry(Blocks.FURNACE, Blocks.COBBLESTONE),
            Map.entry(Blocks.BLAST_FURNACE, Blocks.COBBLESTONE),
            Map.entry(Blocks.SMOKER, Blocks.COBBLESTONE),
            Map.entry(Blocks.ANVIL, Blocks.AIR),
            Map.entry(Blocks.CHIPPED_ANVIL, Blocks.AIR),
            Map.entry(Blocks.DAMAGED_ANVIL, Blocks.AIR),
            Map.entry(Blocks.ENCHANTING_TABLE, Blocks.AIR),
            Map.entry(Blocks.BREWING_STAND, Blocks.AIR),
            Map.entry(Blocks.GRINDSTONE, Blocks.AIR),
            Map.entry(Blocks.STONECUTTER, Blocks.AIR));

    private static boolean initialized;

    private StructureLoot() { }

    /**
     * A vanilla crafting table has no place in this world: one standing from before — generated in
     * an older world, or left over from a save — falls apart into sticks when anyone tries to use it.
     */
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            var pos = hit.getBlockPos();
            if (!level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) return net.minecraft.world.InteractionResult.PASS;
            if (level.isClientSide()) return net.minecraft.world.InteractionResult.SUCCESS;
            level.destroyBlock(pos, false);
            Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK, 4));
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                    "message.hardwrought.crafting_table_rotten"));
            return net.minecraft.world.InteractionResult.SUCCESS;
        });
    }

    /** What a structure places instead of this block; the block itself where nothing changes. */
    public static BlockState replacement(BlockState state) {
        Block instead = STRIPPED.get(state.getBlock());
        return instead == null ? state : instead.defaultBlockState();
    }

    public static boolean stripped(BlockState state) {
        return STRIPPED.containsKey(state.getBlock());
    }
}
