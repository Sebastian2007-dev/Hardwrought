package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** What counts as an anvil, how good the work on it can be, and what the work does to it. */
public final class Anvils {
    /** Iron gives nothing back. Wood gives a little, and the work shows it. */
    public static final float WOOD_CAP = 0.85f;
    /** A chipped or damaged iron anvil still rings true, nearly. */
    private static final float CHANCE_TO_DAMAGE_IRON = 0.03f;

    private Anvils() { }

    public static boolean isAnvil(BlockState state) {
        return craftsmanshipCap(state) > 0;
    }

    /** The best craftsmanship a piece worked on this anvil can reach; 0 where this is no anvil. */
    public static float craftsmanshipCap(BlockState state) {
        if (state.is(ModBlocks.WOODEN_ANVIL)) return WOOD_CAP;
        if (state.is(Blocks.ANVIL)) return 1.0f;
        if (state.is(Blocks.CHIPPED_ANVIL)) return 0.97f;
        if (state.is(Blocks.DAMAGED_ANVIL)) return 0.93f;
        return 0f;
    }

    /** Wear a finished piece puts on a wooden anvil. */
    private static final int WOOD_WEAR_PER_PIECE = 2;
    /** Chance that a single blow puts a point of wear on a wooden anvil. */
    private static final float WOOD_WEAR_PER_BLOW = 1f / 12f;

    /** A blow on a wooden anvil now and then wears it too; iron shrugs single blows off. */
    public static void strikeWear(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.WOODEN_ANVIL) && level.getRandom().nextFloat() < WOOD_WEAR_PER_BLOW) {
            wearWood(level, pos, state, 1);
        }
    }

    /**
     * One finished piece of wear. A wooden anvil counts towards splitting; an iron one now and then
     * takes a chip, the way vanilla's own anvil does under use.
     */
    public static void wear(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.WOODEN_ANVIL)) {
            wearWood(level, pos, state, WOOD_WEAR_PER_PIECE);
        } else if (state.getBlock() instanceof AnvilBlock && level.getRandom().nextFloat() < CHANCE_TO_DAMAGE_IRON) {
            BlockState damaged = AnvilBlock.damage(state);
            if (damaged == null) {
                level.removeBlock(pos, false);
                level.levelEvent(1029, pos, 0);
            } else {
                level.setBlockAndUpdate(pos, damaged);
                level.levelEvent(1030, pos, 0);
            }
        }
    }

    private static void wearWood(ServerLevel level, BlockPos pos, BlockState state, int amount) {
        int before = state.getValue(WoodenAnvilBlock.USES);
        int uses = before + amount;
        if (uses >= WoodenAnvilBlock.MAX_USES) {
            level.destroyBlock(pos, false);
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.1);
            return;
        }
        level.setBlockAndUpdate(pos, state.setValue(WoodenAnvilBlock.USES, uses));
        boolean newStage = (before < WoodenAnvilBlock.CRACKED && uses >= WoodenAnvilBlock.CRACKED)
                || (before < WoodenAnvilBlock.SPLIT && uses >= WoodenAnvilBlock.SPLIT);
        level.playSound(null, pos, newStage ? SoundEvents.WOOD_BREAK : SoundEvents.WOOD_HIT, SoundSource.BLOCKS,
                newStage ? 0.9f : 0.5f, newStage ? 0.8f : 0.7f);
        if (newStage) {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.05);
        }
    }
}
