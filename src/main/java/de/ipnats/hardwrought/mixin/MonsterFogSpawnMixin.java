package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.environment.SeasonFogRules;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Monsters in the fog. Specification section 24.
 *
 * <p>The light check is what normally confines the surface to the night. Under a thick autumn fog
 * it is lifted, so a foggy morning is genuinely dangerous rather than merely inconvenient — the
 * same morning the sun stops burning the undead (see {@link MobFogSunMixin}) is the morning new
 * ones turn up in it.
 *
 * <p>Only the light rule gives way. Everything else a spawn has to satisfy — the block beneath,
 * the room to stand, the caps, the distance from a player — is untouched, so this makes the fog
 * dangerous rather than making it a flood.
 *
 * <p>An arc floodlight is stronger than the fog: within its reach nothing monstrous spawns at all
 * (docs/electricity-roadmap.md, stage E3).
 */
@Mixin(Monster.class)
public abstract class MonsterFogSpawnMixin {
    @Inject(method = "isDarkEnoughToSpawn", at = @At("HEAD"), cancellable = true)
    private static void hardwrought$fogIsDarkEnough(ServerLevelAccessor level, BlockPos pos,
                                                    RandomSource random,
                                                    CallbackInfoReturnable<Boolean> callback) {
        if (de.ipnats.hardwrought.haven.Floodlights.spawnBarred(level.getLevel(), pos)) {
            callback.setReturnValue(false);
            return;
        }
        if (SeasonFogRules.darkEnoughForMonsters()) callback.setReturnValue(true);
    }
}
