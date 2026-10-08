package de.ipnats.hardwrought.magic;

import com.mojang.brigadier.arguments.StringArgumentType;
import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The magic system, Milestone M1: runes, wands, drawing, casting, failing. See
 * {@code docs/magic-1.md} and the specification in {@code runeconzept/Hardwrought_Magic_System.md}.
 */
public final class Magic {
    public static final MagicTableBlock MAGIC_TABLE = blockOnly("magic_table", MagicTableBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE).noOcclusion());
    public static final RuneStoneBlock RUNE_STONE = block("rune_stone", RuneStoneBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS));
    public static final WandItem WOODEN_WAND = wand("wooden_wand", WandTier.WOOD);
    public static final WandItem IRON_WAND = wand("iron_wand", WandTier.IRON);
    public static final WandItem GOLDEN_WAND = wand("golden_wand", WandTier.GOLD);
    /** A sigil inscribed on the ground; never held, only drawn. */
    public static final SigilBlock SIGIL = blockOnly("sigil", SigilBlock::new,
            BlockBehaviour.Properties.of().noCollision().noOcclusion().instabreak().noLootTable()
                    .sound(net.minecraft.world.level.block.SoundType.AMETHYST));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<SigilBlockEntity> SIGIL_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("sigil"),
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(SigilBlockEntity::new, java.util.Set.of(SIGIL)));

    private Magic() { }

    public static void initialize() {
        RuneKnowledge.initialize();
        MagicTasks.initialize();
        MagicShields.initialize();
        MagicFields.initialize();
        RuneRuins.initialize();
        PayloadTypeRegistry.serverboundPlay().register(CastSpellPayload.TYPE, CastSpellPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShieldPayload.TYPE, ShieldPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GatePayload.TYPE, GatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GateTargetPayload.TYPE, GateTargetPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CastSpellPayload.TYPE,
                (payload, context) -> SpellCaster.cast(context.player(), payload.strokes(), payload.size(), payload.action()));
        ServerPlayNetworking.registerGlobalReceiver(GateTargetPayload.TYPE,
                (payload, context) -> Portals.travel(context.player(), payload.x(), payload.y(), payload.z()));
        registerCommands();
    }

    /** Everything a player can hold, for the creative tab: the wands, and a stone of every rune. */
    public static List<ItemStack> items() {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(WOODEN_WAND));
        items.add(new ItemStack(IRON_WAND));
        items.add(new ItemStack(GOLDEN_WAND));
        for (Glyph glyph : Glyph.values()) items.add(runeStone(glyph));
        return items;
    }

    /** A rune stone item that places the stone of one rune. */
    public static ItemStack runeStone(Glyph glyph) {
        ItemStack stack = new ItemStack(RUNE_STONE);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(RuneStoneBlock.GLYPH, glyph));
        return stack;
    }

    /**
     * {@code /hardwrought magic learn <player> <rune|all>} and {@code forget <player>}, for operators
     * and for trying spells out.
     */
    private static void registerCommands() {
        List<String> names = new ArrayList<>(Arrays.stream(Glyph.values()).map(Glyph::getSerializedName).toList());
        names.add("all");
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                literal("hardwrought")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(literal("magic")
                                .then(literal("learn").then(argument("player", EntityArgument.player())
                                        .then(argument("rune", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(names, builder))
                                                .executes(context -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                                    String which = StringArgumentType.getString(context, "rune").toLowerCase(Locale.ROOT);
                                                    int taught = 0;
                                                    for (Glyph glyph : Glyph.values()) {
                                                        if ((which.equals("all") || glyph.getSerializedName().equals(which))
                                                                && RuneKnowledge.learn(player, glyph)) {
                                                            taught++;
                                                        }
                                                    }
                                                    int count = taught;
                                                    context.getSource().sendSuccess(() -> Component.literal(
                                                            count + " Rune(n) gelehrt: " + player.getName().getString()), true);
                                                    return count;
                                                }))))
                                .then(literal("forget").then(argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                            RuneKnowledge.forget(player);
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    "Runenwissen vergessen: " + player.getName().getString()), true);
                                            return 1;
                                        }))))));
    }

    private static WandItem wand(String name, WandTier tier) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new WandItem(tier, new Item.Properties().setId(key)));
    }

    private static <T extends Block> T block(String name, Function<BlockBehaviour.Properties, T> factory,
                                             BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        T block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    private static <T extends Block> T blockOnly(String name, Function<BlockBehaviour.Properties, T> factory,
                                                 BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }
}
