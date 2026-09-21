package de.ipnats.hardwrought.geology;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a player actually does with geology: reading the rock (section 54) and being paid by the
 * grade of what they dig (section 53).
 */
public final class GeologyEvents {
    /** Any pickaxe reads the rock. The datapack decides which items those are. */
    public static final TagKey<Item> PROSPECTING_TOOLS =
            TagKey.create(Registries.ITEM, Hardwrought.id("prospecting_tools"));
    /** Iron and better also read direction, distance and depth off it. */
    public static final TagKey<Item> PRECISE_PROSPECTING_TOOLS =
            TagKey.create(Registries.ITEM, Hardwrought.id("precise_prospecting_tools"));

    /** How far a plain pickaxe notices anything at all. */
    public static final int TRACE_RANGE = 48;
    /** How far a proper prospecting tool reads, which is most of the way across a region. */
    public static final int SURVEY_RANGE = 160;
    /** Section 53: a rich body pays several times what a poor one does, up to this many extra. */
    public static final int MAX_BONUS_DROPS = 4;
    /** Grade needed per extra drop. A twelfth, so a tenth of a percent still pays nothing. */
    public static final double GRADE_PER_BONUS = 1.0 / 12.0;

    private static boolean initialized;

    private GeologyEvents() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || !player.isSecondaryUseActive()
                    || !(player instanceof ServerPlayer serverPlayer)
                    || !(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(PROSPECTING_TOOLS)) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            if (!isReadableRock(serverLevel.getBlockState(pos))) return InteractionResult.PASS;
            prospect(serverPlayer, serverLevel, pos, stack.is(PRECISE_PROSPECTING_TOOLS));
            return InteractionResult.SUCCESS;
        });
        PlayerBlockBreakEvents.AFTER.register(GeologyEvents::payTheGrade);
    }

    /** Only natural rock can be read. A wall of bricks says nothing about the ground it stands on. */
    public static boolean isReadableRock(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.GRAVEL);
    }

    /**
     * Section 54, the early methods: the colour of the rock and the traces in it. A plain pickaxe
     * names the rock and notices ore close by; a proper tool gives the bearing, the distance and the
     * depth, which is the difference between knowing something is there and knowing where to dig.
     */
    private static void prospect(ServerPlayer player, ServerLevel level, BlockPos pos, boolean precise) {
        long seed = level.getSeed();
        Map<Identifier, RockProfile> profiles = Geology.profiles(level.getServer());
        RockType rock = Geology.rockAt(seed, pos.getX(), pos.getZ());
        player.sendSystemMessage(Component.translatable("message.hardwrought.prospect_rock",
                Component.translatable("rock.hardwrought." + rock.serializedName())));

        OreDeposit here = Geology.depositAt(seed, profiles, pos.getX(), pos.getY(), pos.getZ());
        if (here != null) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.prospect_in_body",
                    oreName(here), String.format(Locale.ROOT, "%.1f", here.gradeAt(pos) * 100))
                    .withStyle(ChatFormatting.GOLD));
            level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.8f, 1.4f);
            return;
        }

        int range = precise ? SURVEY_RANGE : TRACE_RANGE;
        List<OreDeposit> near = Geology.nearby(seed, profiles, pos, range);
        if (near.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.prospect_nothing"));
            return;
        }
        OreDeposit closest = near.get(0);
        if (!precise) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.prospect_traces",
                    oreName(closest)));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.hardwrought.prospect_bearing",
                oreName(closest), Math.round(Geology.horizontalDistance(closest, pos)),
                Component.translatable("direction.hardwrought."
                        + bearing(pos, closest.centre()).getSerializedName()),
                closest.centre().getY()).withStyle(ChatFormatting.AQUA));
        level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.8f, 1.0f);
    }

    /** Which way to walk. Only the four cardinal directions: this is a hint, not a map. */
    public static Direction bearing(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Section 53: better rock pays better. Breaking ore inside a body yields extra of whatever the
     * block drops, in proportion to what the rock assays there — so the middle of a rich body is
     * worth working and its rim is barely worth the pick.
     */
    private static void payTheGrade(Level level, Player player, BlockPos pos, BlockState state,
                                    net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.isCreative() || !isOre(state)) return;
        double grade = Geology.gradeAt(serverLevel.getSeed(), Geology.profiles(serverLevel.getServer()),
                pos.getX(), pos.getY(), pos.getZ());
        int bonus = bonusDrops(grade);
        if (bonus <= 0) return;

        ItemStack tool = serverPlayer.getMainHandItem();
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, serverPlayer, tool);
        for (ItemStack drop : drops) {
            // Silk touch drops the ore block itself. Multiplying that would be a duplication bug,
            // not a rich seam: the grade pays in ore, not in stone.
            if (drop.is(state.getBlock().asItem())) return;
        }
        for (int extra = 0; extra < bonus; extra++) {
            for (ItemStack drop : drops) Block.popResource(serverLevel, pos, drop.copy());
        }
    }

    /** How many extra drops a grade is worth. Pure, so the payout curve is checkable. */
    public static int bonusDrops(double grade) {
        if (!Double.isFinite(grade) || grade <= 0) return 0;
        return Math.min(MAX_BONUS_DROPS, (int) (grade / GRADE_PER_BONUS));
    }

    /** Vanilla already keeps one tag with every ore block in it, whatever the ore is. */
    private static boolean isOre(BlockState state) {
        return state.is(BlockTags.ORES);
    }

    /** The block's own name, built from its id the way every vanilla translation key is. */
    private static Component oreName(OreDeposit deposit) {
        return Component.translatable("block." + deposit.ore().getNamespace()
                + "." + deposit.ore().getPath());
    }
}
