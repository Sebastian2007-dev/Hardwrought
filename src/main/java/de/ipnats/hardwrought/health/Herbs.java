package de.ipnats.hardwrought.health;

import de.ipnats.hardwrought.core.registry.ModItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Where the first medicines come from. Chamomile is an oxeye daisy dried on a drying rack; bark for a
 * fever comes off a birch log when an axe strips it. Either is steeped in a bowl of water and boiled on
 * a campfire into tea.
 */
public final class Herbs {
    /** The chance a stripped birch log gives a piece of bark. */
    static final double BARK_CHANCE = 0.5;

    private Herbs() { }

    public static void initialize() {
        // Before the axe strips the log, so the bark comes off with it; the stripping itself is vanilla's.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!(level instanceof ServerLevel server) || !held.is(ItemTags.AXES) || player.isSpectator()) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if ((state.is(Blocks.BIRCH_LOG) || state.is(Blocks.BIRCH_WOOD)) && server.getRandom().nextDouble() < BARK_CHANCE) {
                var drop = hit.getLocation();
                server.addFreshEntity(new ItemEntity(server, drop.x, drop.y, drop.z, new ItemStack(ModItems.WILLOW_BARK)));
            }
            return InteractionResult.PASS;
        });
    }

    /** The herbs, teas, bandages and medicines, for the creative tab and the medicine shelf. */
    public static List<Item> items() {
        return List.of(ModItems.DRIED_CHAMOMILE, ModItems.WILLOW_BARK, ModItems.CHAMOMILE_INFUSION,
                ModItems.WILLOW_INFUSION, ModItems.CHAMOMILE_TEA, ModItems.WILLOW_TEA, ModItems.ELECTROLYTE_DRINK,
                ModItems.BANDAGE, ModItems.DISINFECTED_BANDAGE, ModItems.MASH, ModItems.SPIRIT, ModItems.ANTISEPTIC,
                ModItems.FEVER_PILLS, ModItems.ANTIBIOTICS);
    }
}
