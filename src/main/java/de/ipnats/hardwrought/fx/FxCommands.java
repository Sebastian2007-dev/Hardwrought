package de.ipnats.hardwrought.fx;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Trying out the FX module until the magic system uses it:
 * <ul>
 *   <li>{@code /hardwrought fx play <effect> [colour] [scale]} aims the effect where the player looks,</li>
 *   <li>{@code /hardwrought fx at <pos> <effect> [colour] [scale]} places it at a position,</li>
 *   <li>{@code /hardwrought fx showcase} plays every effect in turn around the player,</li>
 *   <li>{@code /hardwrought fx list} and {@code /hardwrought fx clear}.</li>
 * </ul>
 * A colour is a name such as {@code arcane} or six hex digits such as {@code ff8800}.
 */
public final class FxCommands {
    /** How far a player's look reaches when aiming an effect. */
    private static final double REACH = 48;

    static final Map<String, Integer> COLORS = new LinkedHashMap<>();

    static {
        COLORS.put("default", 0);
        COLORS.put("white", 0xFFFFFF);
        COLORS.put("red", 0xFF3030);
        COLORS.put("orange", 0xFF8A20);
        COLORS.put("yellow", 0xFFE040);
        COLORS.put("green", 0x50FF60);
        COLORS.put("cyan", 0x40F0FF);
        COLORS.put("blue", 0x4A7DFF);
        COLORS.put("purple", 0xA050FF);
        COLORS.put("pink", 0xFF50C8);
        COLORS.put("fire", 0xFF6A1E);
        COLORS.put("frost", 0xA8E8FF);
        COLORS.put("arcane", 0xB45CFF);
        COLORS.put("holy", 0xFFE9A0);
        COLORS.put("void", 0x6A2CFF);
        COLORS.put("nature", 0x7CFF8A);
        COLORS.put("blood", 0xC0102A);
    }

    private static final SimpleCommandExceptionType UNKNOWN_EFFECT =
            new SimpleCommandExceptionType(Component.literal("Unbekannter Effekt."));
    private static final SimpleCommandExceptionType UNKNOWN_COLOR =
            new SimpleCommandExceptionType(Component.literal("Unbekannte Farbe: ein Name oder sechs Hex-Ziffern."));

    private FxCommands() { }

    static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                literal("hardwrought")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(literal("fx")
                                .then(literal("play").then(effectArgument(false)))
                                .then(literal("at").then(argument("pos", Vec3Argument.vec3()).then(effectArgument(true))))
                                .then(literal("showcase").executes(context ->
                                        run(context, FxEffect.SHOWCASE, false, 0, 1)))
                                .then(literal("clear").executes(context -> run(context, FxEffect.CLEAR, false, 0, 1)))
                                .then(literal("list").executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "Effekte: " + String.join(", ", FxEffect.ids())
                                                    + "\nFarben: " + String.join(", ", COLORS.keySet())), false);
                                    return 1;
                                })))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> effectArgument(boolean at) {
        return argument("effect", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(FxEffect.ids(), builder))
                .executes(context -> run(context, effect(context), at, 0, 1))
                .then(argument("color", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(COLORS.keySet(), builder))
                        .executes(context -> run(context, effect(context), at, color(context), 1))
                        .then(argument("scale", FloatArgumentType.floatArg(0.1f, 8f))
                                .executes(context -> run(context, effect(context), at, color(context),
                                        FloatArgumentType.getFloat(context, "scale")))));
    }

    private static FxEffect effect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return FxEffect.byId(StringArgumentType.getString(context, "effect").toLowerCase(Locale.ROOT))
                .orElseThrow(UNKNOWN_EFFECT::create);
    }

    private static int color(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String text = StringArgumentType.getString(context, "color").toLowerCase(Locale.ROOT);
        Integer named = COLORS.get(text);
        if (named != null) return named;
        if (text.matches("[0-9a-f]{6}")) return Integer.parseInt(text, 16);
        throw UNKNOWN_COLOR.create();
    }

    private static int run(CommandContext<CommandSourceStack> context, FxEffect effect, boolean at, int color, float scale)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Entity entity = source.getEntity();
        Vec3 given = at ? Vec3Argument.getVec3(context, "pos") : null;

        Vec3 pos, target;
        Entity follow = null;
        if (effect.aim() == FxEffect.Aim.SELF || entity == null) {
            pos = given != null ? given : source.getPosition();
            target = pos;
            if (given == null) follow = entity;
        } else {
            Vec3 eye = entity.getEyePosition();
            Vec3 look = entity.getViewVector(1);
            HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(REACH)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            Vec3 aimed = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(REACH * 0.5)) : hit.getLocation();
            if (effect.aim() == FxEffect.Aim.AREA) {
                pos = given != null ? given : aimed;
                target = pos;
            } else {
                // From the right hand, a little below the eyes, so the effect does not start inside the view.
                Vec3 right = look.cross(new Vec3(0, 1, 0));
                right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
                pos = given != null ? given : eye.add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.25, 0);
                target = aimed;
            }
        }
        Fx.play(level, effect, pos, target, color, scale, follow);
        source.sendSuccess(() -> Component.literal("FX: " + effect.id()), false);
        return 1;
    }
}
