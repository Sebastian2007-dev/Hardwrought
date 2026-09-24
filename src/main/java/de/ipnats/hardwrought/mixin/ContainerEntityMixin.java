package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.progression.SpawnLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A chest minecart near the world spawn opens empty, like a chest there does. See {@link SpawnLoot}. */
@Mixin(ContainerEntity.class)
public interface ContainerEntityMixin {
    @Inject(method = "unpackChestVehicleLootTable", at = @At("HEAD"), cancellable = true)
    private void hardwrought$noLootNearSpawn(Player player, CallbackInfo callback) {
        ContainerEntity container = (ContainerEntity) this;
        if (container.getContainerLootTable() == null) return;
        if (!SpawnLoot.emptyHere(container.level(), BlockPos.containing(container.position()))) return;
        container.setContainerLootTable(null);
        callback.cancel();
    }
}
