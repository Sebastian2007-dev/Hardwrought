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
    /** How far up a bucket looks for the surface of the water it is being poured into. */
    private static final int SURFACE_REACH = 16;

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
            ItemStack bottle = CauldronWater.bottle(quality);
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
        UseItemCallback.EVENT.register(WaterEvents::dipBowl);
        UseItemCallback.EVENT.register(WaterEvents::useBucket);
        CauldronWater.initialize();
    }


    /**
     * Everything a bucket does with water, in one place.
     *
     * <p>One handler rather than two, and it never hands a water bucket back to vanilla. That is
     * not tidiness: vanilla's bucket places a full source block whatever the bucket is carrying and
     * returns an empty one, so every case this used to pass on — a full bucket clicked without
     * sneaking, a pour with nowhere to go — quietly minted a thousand millibuckets out of a bucket
     * holding four hundred, and then looked like the water had been poured and picked straight back
     * up. A measured bucket cannot share an item with an unmeasured one and let either of them
     * answer.
     *
     * <p>Which of the two it does:
     *
     * <ul>
     *   <li>Aimed at water with room to spare — <b>dip</b>. Takes what is there, up to a bucketful.</li>
     *   <li>Sneaking — <b>pour</b>, always. That is the only way to add water to water.</li>
     *   <li>Full — <b>pour</b>, sneaking or not. A full bucket has nothing to dip for, so asking
     *       for the sneak key as well would be asking for nothing.</li>
     * </ul>
     */
    private static InteractionResult useBucket(Player player, Level level, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !BucketWater.isBucket(stack)
                || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        // A bucket with room, aimed at water, and no sneak: that is a dip.
        if (!player.isShiftKeyDown() && BucketWater.room(stack) > 0) {
            BlockPos water = lookedAtWater(level, player);
            if (water != null) return dip(serverPlayer, serverLevel, hand, stack, water);
        }
        if (BucketWater.held(stack) <= 0) {
            // An empty bucket with nothing to dip. Vanilla may still want it for lava or milk.
            return InteractionResult.PASS;
        }
        return pour(serverPlayer, serverLevel, hand, stack);
    }

    /**
     * Takes what is there, up to a bucketful, and tops up a bucket that already has something in
     * it. It never refuses for want of water: a puddle holding a third of a block gives a third of
     * a bucket, which is the whole reason the amount is written on the item.
     */
    private static InteractionResult dip(ServerPlayer player, ServerLevel level, InteractionHand hand,
                                         ItemStack stack, BlockPos pos) {
        // Read before drawing: the block dipped into is what the bucket tastes of, even when the
        // water around it made up the rest.
        var runtime = CoreLifecycle.find(level.getServer());
        WaterQuality quality = runtime == null
                ? WaterQuality.FRESH : runtime.water().qualityAt(level, pos);
        int drawn = WaterDraw.drawUpTo(level, pos, BucketWater.room(stack));
        if (drawn <= 0) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.water_too_little"));
            return InteractionResult.FAIL;
        }
        ItemStack filled = BucketWater.with(stack, BucketWater.held(stack) + drawn,
                BucketWater.mixed(stack, drawn, quality));
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (stack.getCount() == 1) {
            player.setItemInHand(hand, filled);
        } else {
            stack.shrink(1);
            if (!player.getInventory().add(filled)) player.spawnAtLocation(level, filled);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Pours what fits and keeps the rest, onto the surface of whatever is already there.
     *
     * <p>Onto the <em>surface</em>: a cell under water may be pressed to four times a block, which
     * is how a closed pipe pushes water uphill and entirely the wrong thing to do with a bucket.
     * Pouring into the cell a player was pointing at compressed the pool instead of raising it, so
     * emptying a bucket into a pond did nothing anybody could see.
     */
    private static InteractionResult pour(ServerPlayer player, ServerLevel level, InteractionHand hand,
                                          ItemStack stack) {
        int carried = BucketWater.held(stack);
        BlockPos aimed = pourTarget(level, player);
        if (aimed == null) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.water_no_room"));
            return InteractionResult.FAIL;
        }
        BlockPos target = surfaceAbove(level, aimed);
        // In the Nether, outside the Ash Grove, it boils off the moment it leaves the bucket.
        if (de.ipnats.hardwrought.nether.AshGrove.evaporates(level, target)) {
            de.ipnats.hardwrought.nether.AshGrove.fizz(level, target);
            if (!player.isCreative()) player.setItemInHand(hand, BucketWater.with(stack, 0, null));
            return InteractionResult.SUCCESS;
        }
        int present = WaterStorage.amount(level, target);
        int poured = Math.min(carried, Math.max(0, WaterAmounts.BLOCK - present));
        if (poured <= 0) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.water_no_room"));
            return InteractionResult.FAIL;
        }
        WaterStorage.setAmount(level, target, present + poured);
        // What was in the bucket goes into the world with it. An unmarked bucket — a creative one,
        // or one from another mod — pours ordinary water rather than claiming to be clean.
        WaterQuality carriedQuality = stack.get(ModDataComponents.WATER_QUALITY);
        if (carriedQuality != null) {
            WaterQualityStorage.add(level, target, carriedQuality, present, poured);
        }
        WaterFlow.disturbImmediately(level, target);
        level.playSound(null, target, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (!player.isCreative()) {
            player.setItemInHand(hand, BucketWater.with(stack, carried - poured, carriedQuality));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Section 23.1: sneak and use an empty hand at the water to drink straight out of it.
     *
     * <p>A player who has lost their waterskin should not die of thirst standing in a river. It is a
     * worse drink than one from a skin — most of a handful runs out between the fingers — and it is
     * unboiled, so a river can still make the drinker ill. That is the trade, and it is the point.
     *
     * <p>Vanilla sends nothing for an empty hand unless a block was hit, and water is no block to the aim:
     * a river with nothing behind it in reach could not be drunk from. So the client asks for it itself
     * ({@code DrinkByHandPayload}, sent from {@code MinecraftDrinkMixin} before the game tries the
     * hands at all, which is also what keeps whatever is in the other hand from being set down), and the
     * water is found here again: what is in sight, with a ray that sees fluids, or what the player stands in.
     */
    public static InteractionResult drinkByHand(ServerPlayer player) {
        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;
        // An empty hand only: sneaking with something in hand already means placing it or using it,
        // and a waterskin has its own answer to the same gesture.
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!(player.level() instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
        ServerPlayer serverPlayer = player;
        Level level = serverLevel;
        BlockPos pos = drinkableWater(serverLevel, player);
        if (pos == null) return InteractionResult.PASS;

        var runtime = CoreLifecycle.find(serverLevel.getServer());
        if (runtime == null) return InteractionResult.PASS;
        if (!runtime.survival().mayDrinkByHand(serverPlayer)) return InteractionResult.CONSUME;

        int available = WaterStorage.amount(serverLevel, pos);
        if (available < WaterAmounts.DRINK) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.water_too_little"));
            return InteractionResult.FAIL;
        }
        WaterQuality quality = runtime.water().qualityAt(serverLevel, pos);
        WaterStorage.setAmount(serverLevel, pos, available - WaterAmounts.DRINK);
        WaterFlow.disturb(serverLevel, pos);
        runtime.survival().drinkByHand(serverPlayer, quality);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.6f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * The water this player can reach with their hands: what they are looking at, or failing that,
     * what they are standing in.
     */
    public static BlockPos drinkableWater(Level level, Player player) {
        BlockPos looked = lookedAtWater(level, player);
        if (looked != null) return looked;
        BlockPos feet = player.blockPosition();
        if (WaterStorage.containsWater(level.getBlockState(feet))) return feet;
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        return WaterStorage.containsWater(level.getBlockState(eye)) ? eye : null;
    }

    /**
     * Where a bucket poured at this cell should actually land: the top of the water standing there.
     *
     * <p>Water is poured onto a surface, not injected into a depth. Walking up through whatever is
     * already full means a bucket emptied anywhere into a pond raises the pond, which is the only
     * thing a player can see happening and therefore the only behaviour that reads as working.
     */
    private static BlockPos surfaceAbove(ServerLevel level, BlockPos aimed) {
        BlockPos surface = aimed;
        for (int step = 0; step < SURFACE_REACH; step++) {
            if (WaterStorage.amount(level, surface) < WaterAmounts.BLOCK) return surface;
            BlockPos above = surface.above();
            if (!WaterStorage.canHold(level, above)) return surface;
            surface = above;
        }
        return surface;
    }

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

    /** An empty bowl dipped into water takes a bowlful of it, of whatever kind it is there. */
    private static InteractionResult dipBowl(Player player, Level level, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.BOWL)) return InteractionResult.PASS;
        BlockPos pos = lookedAtWater(level, player);
        if (pos == null) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        int available = WaterStorage.amount(serverLevel, pos);
        if (available < WaterBowlItem.MILLIBUCKETS) return InteractionResult.PASS;
        var runtime = CoreLifecycle.find(serverLevel.getServer());
        WaterQuality quality = runtime == null ? WaterQuality.FRESH : runtime.water().qualityAt(serverLevel, pos);
        WaterStorage.setAmount(serverLevel, pos, available - WaterBowlItem.MILLIBUCKETS);
        WaterFlow.disturb(serverLevel, pos);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0f, 0.8f);
        ItemStack bowl = WaterBowlItem.of(de.ipnats.hardwrought.core.registry.ModItems.WATER_BOWL, quality);
        stack.shrink(1);
        if (!serverPlayer.getInventory().add(bowl)) serverPlayer.spawnAtLocation(serverLevel, bowl);
        return InteractionResult.SUCCESS;
    }

    /** What a drink from this container is worth; anything unmarked counts as clean. */
    public static WaterQuality qualityOf(ItemStack stack) {
        WaterQuality quality = stack.get(ModDataComponents.WATER_QUALITY);
        return quality == null ? WaterQuality.FRESH : quality;
    }
}
