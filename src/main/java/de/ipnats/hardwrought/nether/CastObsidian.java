package de.ipnats.hardwrought.nether;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Cast obsidian: what lava becomes when water reaches it. It is obsidian cooled in a moment rather than
 * over an age — glassy, full of cracks with the heat still in them — and too brittle to hold a portal
 * (see docs/endgame.md). Only real obsidian makes a frame; cast obsidian becomes real obsidian when it
 * is tempered in the smeltery: held at {@link #TEMPER_CELSIUS} for {@link #TEMPER_TICKS}.
 *
 * <p>Mining it wants what obsidian wants — steel or better — so casting a frame in place and carrying
 * it off is no shortcut either.
 */
public final class CastObsidian {
    /** The smeltery heat that tempers it: coal with bellows, or lava. */
    public static final int TEMPER_CELSIUS = 1600;
    /** A minute at that heat per block. */
    public static final int TEMPER_TICKS = 60 * 20;

    public static final Block BLOCK = register();
    public static final Item ITEM = item();

    private CastObsidian() { }

    /** How it comes about, for the book: water reaching lava. */
    public static java.util.List<de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe> worldRecipes() {
        return java.util.List.of(new de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe(
                de.ipnats.hardwrought.Hardwrought.id("cast_obsidian"),
                de.ipnats.hardwrought.knowledge.WorldRecipes.one(
                        de.ipnats.hardwrought.knowledge.FluidIcons.liquid(net.minecraft.world.level.material.Fluids.LAVA, 1000),
                        de.ipnats.hardwrought.knowledge.FluidIcons.liquid(net.minecraft.world.level.material.Fluids.WATER, 1000)),
                new ItemStack(ITEM), ItemStack.EMPTY));
    }

    public static void initialize() {
        // Lighting a frame of it: the fire takes on top, but no portal will — and the frame says why.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!(level instanceof ServerLevel server) || !(held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE))) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            if (!level.getBlockState(pos).is(BLOCK)) return InteractionResult.PASS;
            var at = hit.getLocation();
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.1);
            server.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.01);
            server.playSound(null, pos, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 0.8f, 1.6f);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("message.hardwrought.cast_obsidian.brittle")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return InteractionResult.PASS;
        });
    }

    /** Whether this is cast obsidian, which the smeltery tempers rather than melts. */
    public static boolean tempers(ItemStack stack) {
        return stack.is(ITEM);
    }

    private static Block register() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id("cast_obsidian"));
        return Registry.register(BuiltInRegistries.BLOCK, key,
                new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).setId(key)));
    }

    private static Item item() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id("cast_obsidian"));
        BlockItem item = new BlockItem(BLOCK, new Item.Properties().setId(key).useBlockDescriptionPrefix());
        item.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
