package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** The client-side angle ticker every plain turning part shares. */
final class KineticTickers {
    private KineticTickers() { }

    static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityTicker<T> client(
            Level level, BlockEntityType<T> type) {
        if (!level.isClientSide() || type != ModBlockEntities.KINETIC) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<KineticBlockEntity>) KineticBlockEntity::clientTick;
        return ticker;
    }
}
