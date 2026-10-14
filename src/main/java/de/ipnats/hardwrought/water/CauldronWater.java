package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * A cauldron keeps what was poured into it. Vanilla's cauldron holds water and nothing more, so
 * seawater poured in came back out of it clean; here the four ways water goes in and out of a cauldron
 * by hand — a bucket or a bottle, in or out — carry the water's quality with them, and the cauldron
 * remembers it ({@link WaterQualityStorage#cauldron}). Poured into what is already there, the two mix
 * the way water in the world does.
 */
public final class CauldronWater {
    /** What a level of a cauldron is worth, in millibuckets: three levels make a bucket. */
    private static final int LEVEL = WaterAmounts.BUCKET / LayeredCauldronBlock.MAX_FILL_LEVEL;

    private CauldronWater() { }

    public static void initialize() {
        UseBlockCallback.EVENT.register(CauldronWater::use);
    }

    private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        boolean empty = state.is(Blocks.CAULDRON);
        boolean water = state.is(Blocks.WATER_CAULDRON);
        if (!empty && !water) return InteractionResult.PASS;
        int filled = water ? state.getValue(LayeredCauldronBlock.LEVEL) : 0;
        ItemStack stack = player.getItemInHand(hand);

        if (stack.is(Items.WATER_BUCKET) && BucketWater.isFull(stack)) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            WaterQuality poured = WaterEvents.qualityOf(stack);
            set(server, pos, LayeredCauldronBlock.MAX_FILL_LEVEL,
                    filled == 0 ? poured : WaterQuality.mix(held(server, pos), filled * LEVEL, poured, WaterAmounts.BUCKET));
            give(player, hand, stack, new ItemStack(Items.BUCKET));
            sound(level, pos, player, SoundEvents.BUCKET_EMPTY);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.BUCKET) && filled == LayeredCauldronBlock.MAX_FILL_LEVEL) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            WaterQuality quality = held(server, pos);
            set(server, pos, 0, null);
            give(player, hand, stack, BucketWater.with(new ItemStack(Items.BUCKET), WaterAmounts.BUCKET, quality));
            sound(level, pos, player, SoundEvents.BUCKET_FILL);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.GLASS_BOTTLE) && filled > 0) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            WaterQuality quality = held(server, pos);
            set(server, pos, filled - 1, quality);
            give(player, hand, stack, bottle(quality));
            sound(level, pos, player, SoundEvents.BOTTLE_FILL);
            return InteractionResult.SUCCESS;
        }
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (stack.is(Items.POTION) && potion != null && potion.is(Potions.WATER) && filled < LayeredCauldronBlock.MAX_FILL_LEVEL) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            WaterQuality poured = WaterEvents.qualityOf(stack);
            set(server, pos, filled + 1,
                    filled == 0 ? poured : WaterQuality.mix(held(server, pos), filled * LEVEL, poured, LEVEL));
            give(player, hand, stack, new ItemStack(Items.GLASS_BOTTLE));
            sound(level, pos, player, SoundEvents.BOTTLE_EMPTY);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** A bottle of water of this kind, coloured as the kind is. */
    public static ItemStack bottle(WaterQuality quality) {
        ItemStack bottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        if (quality == null) return bottle;
        bottle.set(ModDataComponents.WATER_QUALITY, quality);
        if (quality != WaterQuality.FRESH) {
            bottle.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.of(Potions.WATER),
                    Optional.of(0xFF000000 | quality.color()), List.of(), Optional.empty()));
        }
        return bottle;
    }

    private static WaterQuality held(ServerLevel level, BlockPos pos) {
        return WaterQualityStorage.cauldron(level, pos);
    }

    private static void set(ServerLevel level, BlockPos pos, int filled, WaterQuality quality) {
        BlockState state = filled <= 0 ? Blocks.CAULDRON.defaultBlockState()
                : Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, filled);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        level.gameEvent(null, filled <= 0 ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, pos);
        WaterQualityStorage.setCauldron(level, pos, filled <= 0 ? null : quality);
    }

    private static void give(Player player, InteractionHand hand, ItemStack used, ItemStack result) {
        player.setItemInHand(hand, ItemUtils.createFilledResult(used, player, result));
    }

    private static void sound(Level level, BlockPos pos, Player player, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
    }
}
