package de.ipnats.hardwrought.machinery;

import net.minecraft.world.item.Item;

/**
 * The pipe wrench: put to a side of a pipe, it sets what that side does. Towards a chest, a tank or a
 * machine it turns the side from filling to drawing to cut off; towards another pipe, from joined to
 * cut off. See {@link ConnectedPipeBlock}.
 */
public class PipeWrenchItem extends Item {
    public PipeWrenchItem(Properties properties) {
        super(properties);
    }
}
