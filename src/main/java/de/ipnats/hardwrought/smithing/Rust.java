package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Plain steel rusts. Carried or worn in rain or water, a steel tool, weapon or piece of armor takes on
 * rust; rust makes a tool dig slower, a weapon hit softer, and everything wear faster. A grindstone
 * takes it off again, at the cost of a little of the edge. Stainless steel, titanium and tungsten steel
 * never rust — which is half of why they are worth making.
 *
 * <p>Rust is a value from 0 to 1 on the item. From clean to fully rusted takes about
 * {@value #MINUTES_TO_FULL} minutes of being wet.
 */
public final class Rust {
    static final int MINUTES_TO_FULL = 4;
    /** Rust gained per second of being wet. */
    static final float PER_SECOND = 1.0f / (MINUTES_TO_FULL * 60);
    /** At full rust, a tool digs at this share of its speed and a weapon hits at this share of its damage. */
    static final double RUSTED_SPEED = 0.6, RUSTED_DAMAGE = 0.7;
    /** A grindstone takes this share of the item's durability with the rust. */
    static final double GRINDING_WEAR = 0.02;

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Rust() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(Rust::tick);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !level.getBlockState(hit.getBlockPos()).is(Blocks.GRINDSTONE)) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getMainHandItem();
            if (of(stack) <= 0) return InteractionResult.PASS;
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            grind(stack, server, (ServerPlayer) player);
            return InteractionResult.SUCCESS;
        });
    }

    /** Whether this item rusts at all: plain steel equipment and parts. */
    public static boolean rusts(ItemStack stack) {
        AlloyEquipment.Tier tier = AlloyEquipment.tierOf(stack.getItem());
        if (tier != null) return tier.rusts();
        return ToolParts.metalOf(stack.getItem()) == ToolParts.SmithMetal.STEEL;
    }

    public static float of(ItemStack stack) {
        Float rust = stack.get(ModDataComponents.RUST);
        return rust == null ? 0 : rust;
    }

    public static void add(ItemStack stack, float amount) {
        if (!rusts(stack)) return;
        float next = Mth.clamp(of(stack) + amount, 0, 1);
        if (next != of(stack)) stack.set(ModDataComponents.RUST, next);
    }

    public static double speedFactor(ItemStack stack) {
        return 1 - (1 - RUSTED_SPEED) * of(stack);
    }

    public static double damageFactor(ItemStack stack) {
        return 1 - (1 - RUSTED_DAMAGE) * of(stack);
    }

    /** Wear on a rusty item: each point of it may cost one more, as likely as it is rusted. */
    public static int wear(ItemStack stack, int amount, RandomSource random) {
        float rust = of(stack);
        if (rust <= 0 || amount <= 0) return amount;
        int extra = 0;
        for (int i = 0; i < amount; i++) if (random.nextFloat() < rust) extra++;
        return amount + extra;
    }

    static void grind(ItemStack stack, ServerLevel level, ServerPlayer player) {
        stack.remove(ModDataComponents.RUST);
        if (stack.isDamageableItem()) {
            stack.hurtAndBreak(Math.max(1, (int) Math.round(stack.getMaxDamage() * GRINDING_WEAR)), level, player, item -> { });
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.sendOverlayMessage(Component.translatable("message.hardwrought.rust_ground"));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator() || !player.isInWaterOrRain()) continue;
            for (EquipmentSlot slot : SLOTS) add(player.getItemBySlot(slot), PER_SECOND);
        }
    }
}
