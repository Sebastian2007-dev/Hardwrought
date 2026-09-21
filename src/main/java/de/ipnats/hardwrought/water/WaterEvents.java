package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Filling a glass bottle has to record what was filled into it, exactly as the waterskin does.
 * Without this a player could scoop seawater into a bottle and drink it as if it were clean, which
 * would leave the whole of section 23.2 with nothing to say.
 *
 * <p>Vanilla's own bottle filling is replaced here rather than patched, because the water quality
 * has to be read at the moment and the place the bottle is dipped.
 */
public final class WaterEvents {
    private static boolean initialized;

    private WaterEvents() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide() || !stack.is(Items.GLASS_BOTTLE)
                    || !(player instanceof ServerPlayer serverPlayer)
                    || !(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.PASS;
            }
            BlockPos pos = lookedAtWater(level, player);
            if (pos == null) return InteractionResult.PASS;
            int available = WaterStorage.amount(serverLevel, pos);
            if (available < WaterAmounts.BOTTLE) return InteractionResult.PASS;

            var runtime = CoreLifecycle.find(serverLevel.getServer());
            WaterQuality quality = runtime == null
                    ? WaterQuality.FRESH : runtime.water().qualityAt(serverLevel, pos);
            ItemStack bottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            bottle.set(ModDataComponents.WATER_QUALITY, quality);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0f, 1.0f);
            serverPlayer.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
            WaterStorage.setAmount(serverLevel, pos, available - WaterAmounts.BOTTLE);
            WaterFlow.disturb(serverLevel, pos);
            stack.shrink(1);
            if (!serverPlayer.getInventory().add(bottle)) {
                serverPlayer.spawnAtLocation(serverLevel, bottle);
            }
            return InteractionResult.SUCCESS;
        });
        UseItemCallback.EVENT.register(WaterEvents::fillBucket);
        UseItemCallback.EVENT.register(WaterEvents::emptyBucket);
    }

    /**
     * Filling a bucket is taken over from vanilla for the same two reasons the bottle was. Vanilla
     * takes the whole block whatever is in it, which destroys the surplus a cell under pressure
     * carries; and it records nothing, so seawater carried inland used to become clean on the way.
     * A bucket takes exactly one block of water and remembers where it was dipped.
     */
    private static InteractionResult fillBucket(Player player, Level level, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !stack.is(Items.BUCKET)
                || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = lookedAtWater(level, player);
        if (pos == null) return InteractionResult.PASS;
        int available = WaterStorage.amount(serverLevel, pos);
        if (available < WaterAmounts.BUCKET) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.water_too_little"));
            return InteractionResult.FAIL;
        }
        var runtime = CoreLifecycle.find(serverLevel.getServer());
        WaterQuality quality = runtime == null
                ? WaterQuality.FRESH : runtime.water().qualityAt(serverLevel, pos);
        ItemStack filled = new ItemStack(Items.WATER_BUCKET);
        filled.set(ModDataComponents.WATER_QUALITY, quality);
        WaterStorage.setAmount(serverLevel, pos, available - WaterAmounts.BUCKET);
        WaterFlow.disturb(serverLevel, pos);
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        serverPlayer.awardStat(Stats.ITEM_USED.get(Items.BUCKET));
        if (stack.getCount() == 1) {
            player.setItemInHand(hand, filled);
        } else {
            stack.shrink(1);
            if (!serverPlayer.getInventory().add(filled)) serverPlayer.spawnAtLocation(serverLevel, filled);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Emptying a bucket adds a thousand millibuckets to what is already there. Vanilla replaces the
     * block with a full one instead, which would quietly invent water when poured into a half-filled
     * cell and lose the bucket when poured into a full one. A cell that cannot take the whole bucket
     * refuses it, so nothing is ever lost.
     */
    private static InteractionResult emptyBucket(Player player, Level level, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !stack.is(Items.WATER_BUCKET)
                || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        BlockPos target = pourTarget(serverLevel, player);
        if (target == null) return InteractionResult.PASS;
        int present = WaterStorage.amount(serverLevel, target);
        if (WaterAmounts.pressureRoom(present) < WaterAmounts.BUCKET) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.water_no_room"));
            return InteractionResult.FAIL;
        }
        WaterStorage.setAmount(serverLevel, target, present + WaterAmounts.BUCKET);
        // What was in the bucket goes into the world with it. An unmarked bucket — a creative one,
        // or one from another mod — pours ordinary water rather than claiming to be clean.
        WaterQuality carried = stack.get(ModDataComponents.WATER_QUALITY);
        if (carried != null) {
            WaterQualityStorage.add(serverLevel, target, carried, present, WaterAmounts.BUCKET);
        }
        WaterFlow.disturbImmediately(serverLevel, target);
        level.playSound(null, target, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (!serverPlayer.isCreative()) {
            player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        }
        return InteractionResult.SUCCESS;
    }

    /** Where a bucket would pour: the block looked at, or the face in front of it. */
    private static BlockPos pourTarget(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getViewVector(1.0f).scale(5.0));
        HitResult hit = level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = blockHit.getBlockPos();
        if (WaterStorage.canHold(level, pos)) return pos;
        BlockPos face = pos.relative(blockHit.getDirection());
        return WaterStorage.canHold(level, face) ? face : null;
    }

    /**
     * The water the player is looking at, or null. Any water counts, not only a full block: a bottle
     * takes a tenth of a block, so a puddle is enough to fill one.
     */
    private static BlockPos lookedAtWater(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getViewVector(1.0f).scale(5.0));
        HitResult hit = level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY, player));
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = blockHit.getBlockPos();
        return WaterStorage.containsWater(level.getBlockState(pos)) ? pos : null;
    }

    /** What a drink from this container is worth; anything unmarked counts as clean. */
    public static WaterQuality qualityOf(ItemStack stack) {
        WaterQuality quality = stack.get(ModDataComponents.WATER_QUALITY);
        return quality == null ? WaterQuality.FRESH : quality;
    }
}
