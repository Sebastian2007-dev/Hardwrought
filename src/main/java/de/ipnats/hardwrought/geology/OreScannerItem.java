package de.ipnats.hardwrought.geology;

import de.ipnats.hardwrought.core.networking.OreDrillPayloads;
import de.ipnats.hardwrought.machinery.OreDrillBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A creative tool: used, it shows every ore the chunk the player stands in holds, how much of the
 * chunk each one is, and which drill tier reaches it — the reading an ore drill gives, without having
 * to build one. It names every ore, known or not; it is for building and testing worlds, not a way
 * round what a survival player has found out.
 */
public class OreScannerItem extends Item {
    public OreScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer
                && ServerPlayNetworking.canSend(serverPlayer, OreDrillPayloads.Info.TYPE)) {
            ServerPlayNetworking.send(serverPlayer, scan(serverPlayer));
        }
        return InteractionResult.SUCCESS;
    }

    /** The chunk the player stands in, read as a drill with no frame would read it. */
    public static OreDrillPayloads.Info scan(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return new OreDrillPayloads.Info(true, true, pos, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0, null,
                SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()),
                OreDrillBlockEntity.chunkOres(player, pos, null, null, true));
    }
}
