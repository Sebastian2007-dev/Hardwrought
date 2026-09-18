package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.environment.SafetyLampItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Section 20: a carried light source should actually light the way. The light block only ever exists
 * in the client's own copy of the world, so nothing is written to the save file and no block update
 * reaches the server or any other player. A real, shared dynamic light belongs to the lighting
 * progression together with the lamp items it describes.
 *
 * <p>The brightness comes from the item itself — a block item lights as brightly as its block — so
 * no table has to be synchronized to the client.
 */
public final class DynamicLight {
    private static final int SAFETY_LAMP_LIGHT = 12;
    private static BlockPos placed;

    private DynamicLight() { }

    public static BlockPos placedAt() { return placed; }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(DynamicLight::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> placed = null);
    }

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null || !Hardwrought.config().dynamicLight()) {
            clear(level);
            return;
        }
        int light = heldLight(player);
        if (light <= 0) {
            clear(level);
            return;
        }
        BlockPos target = BlockPos.containing(player.getEyePosition());
        if (target.equals(placed) && isOurLight(level, target, light)) return;
        clear(level);
        // Never overwrite a real block: only empty space can carry the carried light.
        if (!level.getBlockState(target).isAir()) return;
        level.setBlock(target, Blocks.LIGHT.defaultBlockState()
                .setValue(LightBlock.LEVEL, light), Block.UPDATE_CLIENTS);
        placed = target;
    }

    private static void clear(ClientLevel level) {
        BlockPos previous = placed;
        placed = null;
        if (previous == null || level == null) return;
        // Only remove the block if it is still ours; the server may have replaced it meanwhile.
        if (level.getBlockState(previous).is(Blocks.LIGHT)) {
            level.setBlock(previous, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static boolean isOurLight(ClientLevel level, BlockPos pos, int light) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.LIGHT) && state.getValue(LightBlock.LEVEL) == light;
    }

    public static int heldLight(LocalPlayer player) {
        return Math.max(lightOf(player.getMainHandItem()), lightOf(player.getOffhandItem()));
    }

    public static int lightOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.getItem() instanceof SafetyLampItem) return SAFETY_LAMP_LIGHT;
        if (stack.getItem() instanceof BlockItem blockItem) {
            return Math.min(LightBlock.MAX_LEVEL, blockItem.getBlock().defaultBlockState().getLightEmission());
        }
        return 0;
    }
}
