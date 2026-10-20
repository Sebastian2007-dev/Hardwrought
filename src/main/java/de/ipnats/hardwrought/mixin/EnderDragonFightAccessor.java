package de.ipnats.hardwrought.mixin;

import net.minecraft.world.level.dimension.end.DragonRespawnStage;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where the raising of a dragon has got to, for {@code DragonRising}. */
@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccessor {
    @Accessor("respawnStage")
    DragonRespawnStage hardwrought$respawnStage();

    @Accessor("respawnTime")
    int hardwrought$respawnTime();
}
