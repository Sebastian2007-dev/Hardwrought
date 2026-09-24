package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.progression.SpawnLoot;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A chest or barrel near the world spawn forgets its loot table the moment it would be filled,
 * so it opens empty. See {@link SpawnLoot}.
 */
@Mixin(RandomizableContainer.class)
public interface RandomizableContainerMixin {
    @Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true)
    private void hardwrought$noLootNearSpawn(Player player, CallbackInfo callback) {
        RandomizableContainer container = (RandomizableContainer) this;
        if (container.getLootTable() == null) return;
        if (!SpawnLoot.emptyHere(container.getLevel(), container.getBlockPos())) return;
        container.setLootTable(null);
        callback.cancel();
    }
}
