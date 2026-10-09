package de.ipnats.hardwrought.electricity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Section 76: a needle in a coil. Held to any point of a network it says what is there: the voltage,
 * the current, how hard the wires on that point are worked, what the dynamos give and how much of it
 * never arrives. It is insulated — measuring is the one safe way to touch a live line.
 */
public class MeterItem extends Item {
    public MeterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        net.minecraft.core.BlockPos point = Electricity.point(context.getLevel(), context.getClickedPos());
        if (point == null) return InteractionResult.PASS;
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() != null) {
            context.getPlayer().sendOverlayMessage(Electricity.describe(level, point));
        }
        return InteractionResult.SUCCESS;
    }
}
