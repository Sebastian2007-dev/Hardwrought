package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Section 76: a coil of bare copper, strung between two points of a network.
 *
 * <p>Used on one point it remembers it; used on a second it is strung between the two, where they
 * are within the gauge's span of each other and nothing stands between them. A coil strings
 * {@link Gauge#BLOCKS_PER_COIL} blocks, and a longer run takes more of them. Used anywhere else it
 * lets go of the first point.
 */
public class WireItem extends Item {
    private final Gauge gauge;

    public WireItem(Gauge gauge, Properties properties) {
        super(properties);
        this.gauge = gauge;
    }

    public Gauge gauge() {
        return gauge;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        BlockPos clicked = Electricity.point(level, context.getClickedPos());
        if (clicked == null) {
            stack.remove(ElectricBlocks.WIRE_START);
            return InteractionResult.PASS;
        }
        // A block that takes its wire through a coil is never an end of one, first or second: say so at
        // once, and remember nothing — a coil remembered here would refuse every point used after it.
        if (level.getBlockEntity(clicked) instanceof ElectricBlockEntity node && !node.takesWires()) {
            tell(player, Component.translatable("message.hardwrought.wire.needs_coil"));
            return InteractionResult.FAIL;
        }
        BlockPos start = stack.get(ElectricBlocks.WIRE_START);
        // A first point that is gone, or was remembered before such blocks stopped taking wire, is let go of.
        if (start != null && !(level.getBlockEntity(start) instanceof ElectricBlockEntity first && first.takesWires())) {
            start = null;
        }
        if (start == null || start.equals(clicked)) {
            stack.set(ElectricBlocks.WIRE_START, clicked.immutable());
            tell(player, Component.translatable("message.hardwrought.wire.started"));
            return InteractionResult.SUCCESS;
        }
        Electricity.Refusal refusal = Electricity.refusal(level, start, clicked, gauge);
        if (refusal != null) {
            if (refusal == Electricity.Refusal.NOT_A_POINT) stack.remove(ElectricBlocks.WIRE_START);
            tell(player, Component.translatable("message.hardwrought.wire." + refusal.name().toLowerCase(java.util.Locale.ROOT),
                    (int) gauge.span()));
            return InteractionResult.FAIL;
        }
        int coils = Electricity.coils(level, start, clicked, gauge);
        boolean free = player != null && player.getAbilities().instabuild;
        if (!free && stack.getCount() < coils) {
            tell(player, Component.translatable("message.hardwrought.wire.short", coils));
            return InteractionResult.FAIL;
        }
        stack.remove(ElectricBlocks.WIRE_START);
        if (!free) stack.shrink(coils);
        Electricity.connect(level, start, clicked, gauge);
        level.playSound(null, clicked, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.5f);
        tell(player, Component.translatable("message.hardwrought.wire.strung"));
        return InteractionResult.SUCCESS;
    }

    private static void tell(Player player, Component message) {
        if (player != null) player.sendOverlayMessage(message);
    }
}
