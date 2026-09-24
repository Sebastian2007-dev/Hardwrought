package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.knowledge.WorldRecipes;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The first cloth: leaf cord woven on a needle of sharpened wood, and the pack sewn out of it.
 *
 * <pre>
 * stick ──rubbed on stone──▶ pointed stick ──+ cord──▶ sewing needle
 * sewing needle + 4 cord in the other hand ──held──▶ woven cloth
 * sewing needle + 4 woven cloth in the other hand ──held longer──▶ woven pack
 * </pre>
 *
 * <p>Outside Ultra a new player is handed the pack, so the book only shows how to sew one where the
 * player has to.
 */
public final class Weaving {
    /** Strokes on stone that bring a stick to a point. */
    public static final int SHARPEN_STROKES = 4;
    /** Cord a piece of cloth takes. */
    public static final int CORD_PER_WEAVE = 4;
    /** Cloth a pack takes. */
    public static final int CLOTH_PER_PACK = 4;
    /** Sewing a pack takes this many times as long as weaving a piece of cloth. */
    public static final int PACK_TIME_FACTOR = 2;

    /** Dry cord that can be woven: leaf cord or string. Green fibre is too soft and has to dry first. */
    public static final TagKey<Item> CORD = TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            de.ipnats.hardwrought.Hardwrought.id("weaving_cord"));

    private record Sharpening(BlockPos stone, int strokes) { }

    private static final Map<UUID, Sharpening> SHARPENING = new ConcurrentHashMap<>();
    private static boolean initialized;

    private Weaving() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().is(Items.STICK)) return InteractionResult.PASS;
            if (!isWhetstone(level.getBlockState(hit.getBlockPos()))) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (player instanceof ServerPlayer worker && level instanceof ServerLevel server) {
                sharpen(worker, server, hit.getBlockPos());
            }
            return InteractionResult.SUCCESS;
        });
        WorldRecipes.register(Weaving::worldRecipes);
    }

    /** Stone that will put a point on wood: any natural stone and the worked kinds made from it. */
    public static boolean isWhetstone(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.SMOOTH_STONE);
    }

    /**
     * One stroke of the stick in the main hand along this stone. Strokes must follow each other on
     * the same stone; the last one turns one stick into a pointed stick. Returns true when it did.
     */
    public static boolean sharpen(ServerPlayer player, ServerLevel level, BlockPos stone) {
        ItemStack stick = player.getMainHandItem();
        if (!stick.is(Items.STICK)) return false;
        Sharpening before = SHARPENING.get(player.getUUID());
        int strokes = before != null && before.stone().equals(stone) ? before.strokes() + 1 : 1;
        level.playSound(null, stone, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.6f, 1.4f + level.getRandom().nextFloat() * 0.2f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(stone)),
                stone.getX() + 0.5, stone.getY() + 1.0, stone.getZ() + 0.5, 3, 0.2, 0.05, 0.2, 0.02);
        if (strokes < SHARPEN_STROKES) {
            SHARPENING.put(player.getUUID(), new Sharpening(stone.immutable(), strokes));
            return false;
        }
        SHARPENING.remove(player.getUUID());
        if (!player.getAbilities().instabuild) stick.shrink(1);
        give(player, new ItemStack(ModItems.POINTED_STICK));
        level.playSound(null, stone, SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 0.5f, 1.6f);
        return true;
    }

    /** Whether the other hand holds enough cord for a piece of cloth. */
    public static boolean hasCord(Player player) {
        ItemStack cord = player.getOffhandItem();
        return cord.is(CORD) && cord.getCount() >= CORD_PER_WEAVE;
    }

    /** Whether the other hand holds enough cloth for a pack. */
    public static boolean hasCloth(Player player) {
        ItemStack cloth = player.getOffhandItem();
        return cloth.is(ModItems.WOVEN) && cloth.getCount() >= CLOTH_PER_PACK;
    }

    /**
     * Sews a woven pack out of the cloth in the other hand; the needle wears a little more than for
     * weaving. Returns false, changing nothing, when there is not cloth enough.
     */
    public static boolean sewPack(ServerPlayer player, ItemStack needle) {
        if (!hasCloth(player)) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.sewing_needs_cloth", CLOTH_PER_PACK));
            return false;
        }
        if (!player.getAbilities().instabuild) player.getOffhandItem().shrink(CLOTH_PER_PACK);
        needle.hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
        give(player, new ItemStack(ModItems.STARTER_BACKPACK));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.0f);
        return true;
    }

    /**
     * Weaves one piece of cloth: takes the cord from the other hand, wears the needle a little.
     * Returns false, changing nothing, when there is not cord enough.
     */
    public static boolean weave(ServerPlayer player, ItemStack needle) {
        if (!hasCord(player)) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.weaving_needs_cord", CORD_PER_WEAVE));
            return false;
        }
        if (!player.getAbilities().instabuild) player.getOffhandItem().shrink(CORD_PER_WEAVE);
        needle.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        give(player, new ItemStack(ModItems.WOVEN));
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.7f, 1.2f);
        return true;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.spawnAtLocation(player.level(), stack);
    }

    /** How the compendium shows the steps done by hand rather than in a grid. */
    public static List<WorldRecipes.WorldRecipe> worldRecipes() {
        List<WorldRecipes.WorldRecipe> recipes = new java.util.ArrayList<>(List.of(
                new WorldRecipes.WorldRecipe(de.ipnats.hardwrought.Hardwrought.id("pointed_stick"),
                        WorldRecipes.one(new ItemStack(Items.STICK)), new ItemStack(ModItems.POINTED_STICK),
                        new ItemStack(Items.STONE)),
                new WorldRecipes.WorldRecipe(de.ipnats.hardwrought.Hardwrought.id("woven"),
                        WorldRecipes.one(new ItemStack(ModItems.SEWING_NEEDLE),
                                new ItemStack(ModItems.LEAF_STRING, CORD_PER_WEAVE)),
                        new ItemStack(ModItems.WOVEN), ItemStack.EMPTY),
                new WorldRecipes.WorldRecipe(de.ipnats.hardwrought.Hardwrought.id("drying_fibre"),
                        WorldRecipes.one(new ItemStack(ModItems.GREEN_FIBRE)), new ItemStack(ModItems.LEAF_STRING),
                        new ItemStack(ModItems.DRYING_RACK))));
        if (de.ipnats.hardwrought.survival.Ultra.active()) {
            recipes.add(new WorldRecipes.WorldRecipe(de.ipnats.hardwrought.Hardwrought.id("woven_pack"),
                    WorldRecipes.one(new ItemStack(ModItems.SEWING_NEEDLE), new ItemStack(ModItems.WOVEN, CLOTH_PER_PACK)),
                    new ItemStack(ModItems.STARTER_BACKPACK), ItemStack.EMPTY));
        }
        return recipes;
    }

    /** Forgets what a player was sharpening, so a disconnect leaves nothing behind. */
    public static void forget(UUID player) {
        SHARPENING.remove(player);
    }
}
