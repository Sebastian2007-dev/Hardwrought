package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.CreakingWoodState;
import de.ipnats.hardwrought.mobs.HeartWood;
import net.minecraft.client.renderer.entity.state.CreakingRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CreakingRenderState.class)
public abstract class CreakingRenderStateMixin implements CreakingWoodState {
    @Unique
    private @Nullable HeartWood hardwrought$wood;

    @Override
    public @Nullable HeartWood hardwrought$wood() {
        return hardwrought$wood;
    }

    @Override
    public void hardwrought$setWood(@Nullable HeartWood wood) {
        hardwrought$wood = wood;
    }
}
