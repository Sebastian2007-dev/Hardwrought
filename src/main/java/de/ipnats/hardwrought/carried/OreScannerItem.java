package de.ipnats.hardwrought.carried;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An ore scanner (stage E4): a pulse coil and a circuit. Each pulse — a use — sends a field into the
 * rock {@link #REACH} blocks round and reads back what answers: every ore in that reach glows for its
 * holder for a few seconds, and the pulse says what it found and how much. A pulse costs
 * {@link #PULSE_JOULES}; it takes a moment to charge the coil again.
 */
public class OreScannerItem extends PoweredItem {
    public static final int REACH = 12;
    public static final double PULSE_JOULES = 600.0;
    public static final int RECHARGE_TICKS = 40;
    private static final DustParticleOptions MARK = new DustParticleOptions(0x4FF0FF, 1.2f);

    public OreScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer holder)) return InteractionResult.SUCCESS;
        if (!Cells.draw(stack, PULSE_JOULES)) {
            holder.sendOverlayMessage(Component.translatable("message.hardwrought.device.flat"));
            return InteractionResult.FAIL;
        }
        player.getCooldowns().addCooldown(stack, RECHARGE_TICKS);
        Map<Block, Integer> found = pulse(server, holder, player.blockPosition());
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.8f);
        holder.sendOverlayMessage(summary(found));
        return InteractionResult.SUCCESS;
    }

    /** Marks every ore within reach for this player and counts them by kind. */
    public static Map<Block, Integer> pulse(ServerLevel level, ServerPlayer player, BlockPos centre) {
        Map<Block, Integer> found = new LinkedHashMap<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dy = -REACH; dy <= REACH; dy++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    if (dx * dx + dy * dy + dz * dz > REACH * REACH) continue;
                    at.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.isLoaded(at)) continue;
                    BlockState state = level.getBlockState(at);
                    if (!ore(state)) continue;
                    found.merge(state.getBlock(), 1, Integer::sum);
                    if (player != null) {
                        level.sendParticles(player, MARK, true, true, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
                    }
                }
            }
        }
        return found;
    }

    /** Whether the scanner takes this for ore. */
    public static boolean ore(BlockState state) {
        // Every ore of the game and the mod is named so, deepslate and nether kinds alike.
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_ore") || path.equals("ancient_debris");
    }

    private static Component summary(Map<Block, Integer> found) {
        if (found.isEmpty()) return Component.translatable("message.hardwrought.ore_scanner.nothing");
        MutableComponent said = Component.translatable("message.hardwrought.ore_scanner.found").withStyle(ChatFormatting.AQUA);
        int shown = 0;
        for (var entry : found.entrySet()) {
            if (shown++ == 4) {
                said.append(Component.literal(" …"));
                break;
            }
            said.append(Component.literal(" " + entry.getValue() + "× ")).append(entry.getKey().getName());
        }
        return said;
    }
}
