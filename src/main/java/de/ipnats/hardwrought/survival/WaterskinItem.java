package de.ipnats.hardwrought.survival;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.water.WaterAmounts;
import de.ipnats.hardwrought.water.WaterFlow;
import de.ipnats.hardwrought.water.WaterQuality;
import de.ipnats.hardwrought.water.WaterStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;

/**
 * Eight-use hydration source. Since Milestone 4 it remembers what was poured into it: the same
 * waterskin is worth a full drink filled at a spring and worse than nothing filled at sea.
 */
public final class WaterskinItem extends Item {
    public static final int CAPACITY = 8;
    /** Hydration one drink of clean water is worth, before the quality factor. */
    public static final double DRINK_HYDRATION = 24.0;

    public WaterskinItem(Properties properties) { super(properties); }

    public static int drinksRemaining(ItemStack stack) {
        return Math.max(0, CAPACITY - stack.getDamageValue());
    }

    public static boolean takeDrink(ItemStack stack) {
        if (drinksRemaining(stack) == 0) return false;
        stack.setDamageValue(stack.getDamageValue() + 1);
        return true;
    }

    public static void refill(ItemStack stack) {
        refill(stack, WaterQuality.FRESH);
    }

    public static void refill(ItemStack stack, WaterQuality quality) {
        stack.setDamageValue(0);
        stack.set(ModDataComponents.WATER_QUALITY, quality);
    }

    /** What is in the skin right now. An empty or unmarked skin is treated as clean water. */
    public static WaterQuality quality(ItemStack stack) {
        WaterQuality quality = stack.get(ModDataComponents.WATER_QUALITY);
        return quality == null ? WaterQuality.FRESH : quality;
    }

    /**
     * Section 23.2: boiling makes water drinkable. Using the skin on a lit fire boils what is in it.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!isLitFire(level.getBlockState(pos))) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        if (drinksRemaining(stack) == 0) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        WaterQuality before = quality(stack);
        WaterQuality after = before.boiled();
        Player player = context.getPlayer();
        if (after == before) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.hardwrought.waterskin_boil_useless")
                        .withStyle(ChatFormatting.GOLD));
            }
            return InteractionResult.CONSUME;
        }
        stack.set(ModDataComponents.WATER_QUALITY, after);
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.7f, 1.0f);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.waterskin_boiled"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) return refillFromWorld(level, player, stack);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (!takeDrink(stack)) {
                player.sendSystemMessage(Component.translatable("message.hardwrought.waterskin_empty"));
                return InteractionResult.FAIL;
            }
            var runtime = CoreLifecycle.find(serverPlayer.level().getServer());
            if (runtime != null) runtime.survival().drink(serverPlayer, DRINK_HYDRATION, quality(stack));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Section 23.1: water has volume, so filling the skin takes that volume out of the world. A full
     * skin is eight drinks of a hundred millibuckets; a puddle that holds less fills it part way
     * rather than refusing.
     */
    private InteractionResult refillFromWorld(Level level, Player player, ItemStack stack) {
        var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        if (!WaterStorage.isFreeWater(level.getBlockState(pos))) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

        int available = WaterStorage.amount(serverLevel, pos);
        int wanted = WaterAmounts.WATERSKIN - drinksRemaining(stack) * WaterAmounts.DRINK;
        int taken = Math.min(wanted, available - available % WaterAmounts.DRINK);
        if (taken <= 0) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.waterskin_too_little"));
            return InteractionResult.FAIL;
        }
        var runtime = CoreLifecycle.find(serverLevel.getServer());
        WaterQuality quality = runtime == null
                ? WaterQuality.FRESH : runtime.water().qualityAt(serverLevel, pos);
        WaterStorage.setAmount(serverLevel, pos, available - taken);
        WaterFlow.disturb(serverLevel, pos);

        int drinks = Math.min(CAPACITY, drinksRemaining(stack) + taken / WaterAmounts.DRINK);
        stack.setDamageValue(CAPACITY - drinks);
        stack.set(ModDataComponents.WATER_QUALITY, quality);
        player.sendSystemMessage(Component.translatable("message.hardwrought.waterskin_refilled",
                Component.translatable("water_quality.hardwrought." + quality.serializedName())));
        return InteractionResult.SUCCESS;
    }

    private static boolean isLitFire(BlockState state) {
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA)) return true;
        if (state.getBlock() instanceof CampfireBlock || state.is(Blocks.FURNACE)
                || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) {
            return state.getOptionalValue(BlockStateProperties.LIT).orElse(false);
        }
        return state.is(BlockTags.CAMPFIRES);
    }
}
