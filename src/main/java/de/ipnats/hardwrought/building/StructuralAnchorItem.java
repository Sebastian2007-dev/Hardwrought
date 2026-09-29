package de.ipnats.hardwrought.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * An iron tie bolted through a built block into whatever it touches. The block then holds on to any
 * neighbour that is held itself — terrain or a supported block — without paying for the span, up to
 * {@link Statics#ANCHOR_CAPACITY_N}. It is how a lintel, a balcony or an overhang stays up where its
 * material alone would not reach.
 *
 * <p>Natural terrain needs no anchor, and a block that carries nothing has nothing to tie. The anchor
 * stays with the block until the block is gone.
 */
public class StructuralAnchorItem extends Item {
    public StructuralAnchorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Only the server knows what was built; the client just swings.
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (BuiltBlocks.isAnchored(level, pos)) return refuse(player, "message.hardwrought.anchor.already");
        if (!BuiltBlocks.isBuilt(level, pos)) return refuse(player, "message.hardwrought.anchor.terrain");
        if (!BuildingPhysics.anchor(level, pos)) return refuse(player, "message.hardwrought.anchor.nothing");

        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                8, 0.35, 0.35, 0.35, 0.05);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
            player.sendOverlayMessage(Component.translatable("message.hardwrought.anchor.set"));
        }
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult refuse(Player player, String key) {
        if (player != null) player.sendOverlayMessage(Component.translatable(key));
        return InteractionResult.FAIL;
    }
}
