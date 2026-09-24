package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What hot metal does on its own: it burns a bare hand that holds it, it cools until it is plain
 * metal again, and it goes cold at once in water.
 *
 * <p>Bounded like everything else: one look at each player's two hands a second, one pass over
 * each inventory every two seconds, and nothing at all for anyone not carrying anything hot.
 */
public final class SmithingEvents {
    private static final int BURN_INTERVAL = 20;
    private static final int COOLING_PASS_INTERVAL = 40;
    private static final float BURN_DAMAGE = 2.0f;
    private static boolean initialized;

    private SmithingEvents() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(SmithingEvents::tick);
    }

    private static void tick(MinecraftServer server) {
        long ticks = server.getTickCount();
        boolean burnPass = ticks % BURN_INTERVAL == 0;
        boolean coolingPass = ticks % COOLING_PASS_INTERVAL == 0;
        if (!burnPass && !coolingPass) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long now = player.level().getGameTime();
            if (burnPass) burn(player, now);
            if (coolingPass) dropColdHeat(player, now);
        }
    }

    /**
     * Section 38's first rule of the forge, and the reason for the gloves: glowing metal cannot be
     * held in a bare hand. In the bag it is fine — the pack is leather — but in a hand it burns
     * every second it is held.
     */
    private static void burn(ServerPlayer player, long now) {
        if (player.isCreative() || player.isSpectator() || !exposed(player, now)) return;
        player.hurtServer(player.level(), player.level().damageSources().hotFloor(), BURN_DAMAGE);
        player.sendSystemMessage(Component.translatable("message.hardwrought.hot_metal_burns"), true);
    }

    /** Whether this player holds hot metal in a hand with no glove on it. Public for the tests. */
    public static boolean exposed(ServerPlayer player, long now) {
        if (!holdsHotMetal(player, now)) return false;
        var runtime = CoreLifecycle.find(player.level().getServer());
        return runtime == null || !runtime.equipment().wearsGloves(player);
    }

    public static boolean holdsHotMetal(ServerPlayer player, long now) {
        return Heat.of(player.getMainHandItem(), now) > Heat.BURNS_ABOVE
                || Heat.of(player.getOffhandItem(), now) > Heat.BURNS_ABOVE;
    }

    /**
     * A stack keeps its heat component until somebody notices it has gone cold. Dropping it then
     * is what lets a cooled ingot stack with the cold ingots beside it again.
     */
    private static void dropColdHeat(ServerPlayer player, long now) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.has(ModDataComponents.HEAT)) continue;
            if (Heat.of(stack, now) < Heat.COLD_BELOW) stack.remove(ModDataComponents.HEAT);
        }
    }

    /** What one quench boils off: one layer of water, an eighth of a block. */
    public static final int QUENCH_MILLIBUCKETS = de.ipnats.hardwrought.water.WaterAmounts.BLOCK / 8;

    /**
     * An item lying in water — open water or a water cauldron — is quenched: cold at once, with the
     * hiss and the steam of it, and iron that went in hot enough comes out hard. The steam is water
     * the quench took: a layer of it, or one level of the cauldron. Called from the item entity's
     * own tick.
     */
    public static void quenchIfInWater(ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        ItemStack stack = entity.getItem();
        if (!stack.has(ModDataComponents.HEAT)) return;
        net.minecraft.core.BlockPos pos = entity.blockPosition();
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        boolean cauldron = state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON);
        if (!cauldron && !entity.isInWater()) return;
        var runtime = CoreLifecycle.find(level.getServer());
        if (runtime == null) return;
        ItemStack quenched = stack.copy();
        double was = Smithing.quench(quenched, level.getGameTime(), runtime.materials());
        entity.setItem(quenched);
        if (was <= Heat.COLD_BELOW) return;
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                0.8f, 1.2f + level.getRandom().nextFloat() * 0.3f);
        level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.3, entity.getZ(),
                8, 0.15, 0.1, 0.15, 0.02);
        if (cauldron) {
            net.minecraft.world.level.block.LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        } else {
            boilOff(level, pos);
        }
    }

    /** Takes one layer out of the water the piece fell into, and lets the water around even it out. */
    private static void boilOff(ServerLevel level, net.minecraft.core.BlockPos pos) {
        // The piece sinks, so the water it lies in may be the block it is in or the one under it.
        net.minecraft.core.BlockPos water = pos;
        if (de.ipnats.hardwrought.water.WaterStorage.amount(level, water) <= 0) water = pos.below();
        int amount = de.ipnats.hardwrought.water.WaterStorage.amount(level, water);
        if (amount <= 0) return;
        de.ipnats.hardwrought.water.WaterStorage.setAmount(level, water, amount - QUENCH_MILLIBUCKETS);
        de.ipnats.hardwrought.water.WaterFlow.disturb(level, water);
    }
}
