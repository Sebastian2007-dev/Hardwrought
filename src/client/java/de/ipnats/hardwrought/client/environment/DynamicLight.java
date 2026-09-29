package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.environment.SafetyLampItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Section 20: a carried light source should actually light the way — for everyone who can see it.
 * The light blocks only ever exist in each client's own copy of the world, so nothing is written to
 * the save file and no block update reaches the server. Every client lights the torches it can see
 * being carried, its own player's and everyone else's, so the light is shared without a single packet.
 *
 * <p>The brightness comes from the item itself — a block item lights as brightly as its block — so
 * no table has to be synchronized to the client.
 */
public final class DynamicLight {
    private static final int SAFETY_LAMP_LIGHT = 12;
    /** Carriers farther away than this are not lit; beyond it their light barely reaches the viewer. */
    private static final double RANGE = 48;
    /** Each moving light costs a light-engine update; past this many, the nearest ones win. */
    private static final int MAX_LIGHTS = 24;

    /** Every light block this client placed, with its level. */
    private static final Map<BlockPos, Integer> placed = new HashMap<>();
    private static BlockPos own;

    private DynamicLight() { }

    /** Where the local player's own carried light is, or null. */
    public static BlockPos placedAt() { return own; }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(DynamicLight::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            placed.clear();
            own = null;
        });
    }

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null || !Hardwrought.config().dynamicLight()) {
            clearAll(level);
            return;
        }
        Map<BlockPos, Integer> wanted = new HashMap<>();
        BlockPos ownTarget = null;
        List<LivingEntity> carriers = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RANGE),
                entity -> entity == player || !entity.isInvisible() && !entity.isSpectator());
        carriers.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)));
        for (LivingEntity carrier : carriers) {
            if (wanted.size() >= MAX_LIGHTS) break;
            int light = heldLight(carrier);
            if (light <= 0) continue;
            BlockPos at = BlockPos.containing(carrier.getEyePosition());
            wanted.merge(at, light, Math::max);
            if (carrier == player) ownTarget = at;
        }

        // Take away what no longer belongs, first, so a light that only changed level can be replaced.
        placed.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(wanted.get(entry.getKey()))
                    && isOurLight(level, entry.getKey(), entry.getValue())) return false;
            remove(level, entry.getKey());
            return true;
        });
        wanted.forEach((pos, light) -> {
            if (placed.containsKey(pos)) return;
            // Never overwrite a real block: only empty space can carry the carried light.
            if (!level.getBlockState(pos).isAir()) return;
            level.setBlock(pos, Blocks.LIGHT.defaultBlockState()
                    .setValue(LightBlock.LEVEL, light), Block.UPDATE_CLIENTS);
            placed.put(pos, light);
        });
        own = ownTarget != null && placed.containsKey(ownTarget) ? ownTarget : null;
    }

    private static void clearAll(ClientLevel level) {
        if (level != null) placed.keySet().forEach(pos -> remove(level, pos));
        placed.clear();
        own = null;
    }

    private static void remove(ClientLevel level, BlockPos pos) {
        // Only remove the block if it is still ours; the server may have replaced it meanwhile.
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static boolean isOurLight(ClientLevel level, BlockPos pos, int light) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.LIGHT) && state.getValue(LightBlock.LEVEL) == light;
    }

    public static int heldLight(LivingEntity carrier) {
        return Math.max(lightOf(carrier.getMainHandItem()), lightOf(carrier.getOffhandItem()));
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
