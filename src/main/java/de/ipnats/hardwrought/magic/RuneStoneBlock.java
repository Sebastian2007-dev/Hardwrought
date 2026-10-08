package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A stone with a rune cut into its face (magic specification section 4.1): found in old ruins,
 * where exploring is how a player learns magic. Studying the face teaches the rune — an element rune
 * or an auxiliary one.
 */
public class RuneStoneBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<Glyph> GLYPH = EnumProperty.create("glyph", Glyph.class);

    public RuneStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(GLYPH, Glyph.FIRE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, GLYPH);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        study(serverLevel, serverPlayer, state.getValue(GLYPH), Vec3.atCenterOf(pos).add(state.getValue(FACING).getUnitVec3().scale(0.6)));
        return InteractionResult.SUCCESS;
    }

    /** Teaches a rune from a carving, with a word and a glimmer, or says it is already known. */
    public static void study(ServerLevel level, ServerPlayer player, Glyph glyph, Vec3 at) {
        Component name = Component.translatable(glyph.translationKey()).withColor(glyph.color());
        if (!RuneKnowledge.learn(player, glyph)) {
            player.sendOverlayMessage(Component.translatable("magic.hardwrought.rune_known", name));
            return;
        }
        player.sendSystemMessage(Component.translatable("magic.hardwrought.rune_learned", name)
                .withStyle(ChatFormatting.ITALIC));
        Fx.play(level, FxEffect.SPARKLE, at, at, glyph.color(), 1, null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1, 0.8f);
    }
}
