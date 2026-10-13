package de.ipnats.hardwrought.oil;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.ipnats.hardwrought.geology.Reservoir;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Debug: {@code /hardwrought oil rig oil|gas [depth]} lays a reservoir under the drilling rig the player
 * looks at (or the nearest within eight blocks), its top {@code depth} blocks under the rig — twelve
 * if not given, none to strike at once. {@code /hardwrought oil rig clear} takes it away again, and the
 * rig is over whatever the world holds there. The reservoir is kept with the rig.
 */
public final class RigDebug {
    private static final int DEFAULT_DEPTH = 12;
    private static final int SEARCH = 8;
    private static final int RADIUS = 30, HEIGHT = 8;

    private RigDebug() { }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("oil").then(literal("rig")
                .then(kind("oil", Reservoir.Kind.OIL))
                .then(kind("gas", Reservoir.Kind.GAS))
                .then(literal("clear").executes(context -> clear(context.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> kind(String name, Reservoir.Kind kind) {
        return literal(name).executes(context -> lay(context.getSource(), kind, DEFAULT_DEPTH))
                .then(argument("depth", IntegerArgumentType.integer(0, 256))
                        .executes(context -> lay(context.getSource(), kind, IntegerArgumentType.getInteger(context, "depth"))));
    }

    private static int lay(CommandSourceStack source, Reservoir.Kind kind, int depth) throws CommandSyntaxException {
        DrillingRigBlockEntity rig = rig(source);
        if (rig == null) return 0;
        BlockPos pos = rig.getBlockPos();
        // The top of the dome is over its middle: put the middle right under the rig.
        BlockPos centre = pos.below(1 + depth + HEIGHT);
        long seed = pos.asLong();
        Reservoir reservoir = new Reservoir(kind, 1_000_000_000, (int) (seed ^ (seed >>> 32)), centre, RADIUS, HEIGHT, 1.0);
        rig.setDebugReservoir(reservoir);
        source.sendSuccess(() -> Component.literal("Hardwrought: " + (kind == Reservoir.Kind.OIL ? "Öl" : "Gas")
                + " unter dem Bohrturm bei " + pos.toShortString() + ", " + depth + " Blöcke tief."), true);
        return 1;
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        DrillingRigBlockEntity rig = rig(source);
        if (rig == null) return 0;
        rig.setDebugReservoir(null);
        source.sendSuccess(() -> Component.literal("Hardwrought: der Bohrturm steht wieder über dem, was die Welt dort hat."), true);
        return 1;
    }

    /** The rig the player looks at, or the nearest one around them. */
    private static DrillingRigBlockEntity rig(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();
        HitResult hit = player.pick(SEARCH, 0, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && level.getBlockEntity(block.getBlockPos()) instanceof DrillingRigBlockEntity rig) {
            return rig;
        }
        DrillingRigBlockEntity nearest = null;
        double best = Double.MAX_VALUE;
        BlockPos at = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-SEARCH, -SEARCH, -SEARCH), at.offset(SEARCH, SEARCH, SEARCH))) {
            if (level.getBlockEntity(pos) instanceof DrillingRigBlockEntity rig && pos.distSqr(at) < best) {
                best = pos.distSqr(at);
                nearest = rig;
            }
        }
        if (nearest == null) source.sendFailure(Component.literal("Hardwrought: kein Bohrturm in Sicht oder in der Nähe."));
        return nearest;
    }
}
