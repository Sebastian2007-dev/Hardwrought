package de.ipnats.hardwrought.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the mantle's hover keep its wearer from being taken for someone flying who may not (see {@code MantleFlight}). */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerAccessor {
    @Accessor("aboveGroundTickCount")
    void hardwrought$setAboveGroundTickCount(int ticks);
}
