package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Section 71.1.10 in practice: breaking a block and harvesting it are two different things.
 *
 * <p>The speed rule lives in the player mixin, because the client has to agree with it. What is
 * decided here is everything only the server can decide — what comes out of the block, what it cost
 * to break, and what it did to the tool.
 */
public final class ProgressionEvents {
    /** How often hands bring something loose out of the ground they dug (section 71.1.2). */
    public static final double HANDFUL_CHANCE = 0.75;
    /** How often stripping leaves by hand yields usable fibre — the first link of the chain. */
    public static final double FIBRE_CHANCE = 0.12;
    /** How often river gravel gives up a grain of tin ore. Bronze is worked for, not mined. */
    public static final double CASSITERITE_CHANCE = 0.05;

    private static boolean initialized;

    private ProgressionEvents() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || serverPlayer.isCreative()) return InteractionResult.PASS;
            if (BlockBreaking.verdict(serverPlayer.getMainHandItem(), state).breaks()) {
                return InteractionResult.PASS;
            }
            return InteractionResult.FAIL;
        });
        // Section 71.1.3: timber is hewn. Holding the use button against a log repeats this
        // interaction, and every repetition is one stroke of the axe.
        // Section 71: crouching against a log with a joiner's hatchet cuts a bench out of it.
        // Crouching against a log never strips it, whatever is in hand: the bark coming off
        // mid-cut looks like a bug, and stripping is what the same axe does when not crouching.
        UseBlockCallback.EVENT.register(ProgressionEvents::workTimber);
        PlayerBlockBreakEvents.BEFORE.register(ProgressionEvents::decideYield);
        PlayerBlockBreakEvents.AFTER.register(ProgressionEvents::afterBreak);
    }

    /**
     * Section 71: crouching against a log with a joiner's hatchet cuts a bench out of it.
     *
     * <p>Crouching against a log never strips it, whatever is in hand. This runs on both sides on
     * purpose: the client predicts an interaction before the server answers, so a rule that only
     * existed on the server would strip the log locally and then visibly snap it back.
     */
    private static InteractionResult workTimber(Player player, Level level,
                                                net.minecraft.world.InteractionHand hand,
                                                net.minecraft.world.phys.BlockHitResult hit) {
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (!LogWorking.isTimber(state)) return InteractionResult.PASS;
        // Nothing in hand that can cut a working surface: nothing happens, and in particular the
        // bark stays on.
        if (LogWorking.strokesNeeded(player.getMainHandItem(), state) == Integer.MAX_VALUE) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            // The client only has to agree that the interaction was handled; the work is the
            // server's, and its result arrives with the block change.
            return InteractionResult.SUCCESS;
        }
        LogWorking.strike(serverPlayer, serverLevel, hit.getBlockPos(), state);
        return InteractionResult.SUCCESS;
    }

    /**
     * Takes the block over from vanilla wherever what comes out of it is not what vanilla would
     * drop: a handful of loose ground, a block ruined by the wrong tool, or nothing at all.
     *
     * <p>Cancelling and destroying it here rather than letting vanilla finish is what keeps "it
     * broke" and "you got something" apart. What vanilla would have done otherwise — the break
     * itself, the wear on the tool, the effort — is done explicitly.
     */
    private static boolean decideYield(Level level, Player player, BlockPos pos, BlockState state,
                                       BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        if (serverPlayer.isCreative()) return true;
        ItemStack held = serverPlayer.getMainHandItem();
        BreakingVerdict verdict = BlockBreaking.verdict(held, state);
        if (!verdict.breaks()) return false;
        if (!verdict.interceptsDrops()) return true;

        // A block prised out with the wrong tool sometimes survives. When it does, vanilla drops it
        // exactly as it always would, and nothing here has to be involved.
        if (verdict.yield() == BreakingVerdict.Yield.DAMAGED
                && serverLevel.getRandom().nextDouble() >= BreakingVerdict.DAMAGED_LOSS_CHANCE) {
            return true;
        }

        ItemStack yield = verdict.yield() == BreakingVerdict.Yield.HANDFUL
                && serverLevel.getRandom().nextDouble() < HANDFUL_CHANCE
                ? BlockBreaking.handful(state) : ItemStack.EMPTY;

        serverLevel.destroyBlock(pos, false, serverPlayer);
        if (!yield.isEmpty()) Block.popResource(serverLevel, pos, yield);
        if (verdict == BreakingVerdict.SHATTERS) {
            serverLevel.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        wearTool(serverPlayer, held, verdict);
        spendEffort(serverPlayer, state, verdict);
        return false;
    }

    /**
     * What is left to do after a block really broke the ordinary way: the effort it cost, and the
     * fibre that comes off leaves stripped by hand.
     */
    private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state,
                                   BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (serverPlayer.isCreative()) return;
        ItemStack held = serverPlayer.getMainHandItem();
        // Section 71.1.8: the first link of the chain has to be free. Leaves stripped by hand give
        // the fibre a primitive tool is bound with, so a player with nothing can always start again.
        if (state.is(BlockTags.LEAVES) && !BlockBreaking.isTool(held)
                && serverLevel.getRandom().nextDouble() < FIBRE_CHANCE) {
            Block.popResource(serverLevel, pos, new ItemStack(ModItems.LEAF_STRING));
        }
        // Sections 55 and 56: tin is not mined out of a seam, it is washed out of river gravel.
        // That is where the bronze age got it, and it needs no ore of its own in the ground.
        if (state.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                && serverLevel.getRandom().nextDouble() < CASSITERITE_CHANCE) {
            Block.popResource(serverLevel, pos, new ItemStack(
                    de.ipnats.hardwrought.metallurgy.ModMetals.raw(
                            de.ipnats.hardwrought.metallurgy.Metal.TIN)));
        }
        BreakingVerdict verdict = BlockBreaking.verdict(held, state);
        // Anything the yield rule took over already paid for itself before it cancelled the break.
        if (verdict.interceptsDrops()) return;
        wearTool(serverPlayer, held, verdict);
        spendEffort(serverPlayer, state, verdict);
    }

    /** Section 71.1: a tool used for what it was not made for wears out faster. */
    private static void wearTool(ServerPlayer player, ItemStack held, BreakingVerdict verdict) {
        if (verdict.toolDamage() <= 0 || held.isEmpty() || !held.isDamageableItem()) return;
        held.hurtAndBreak(verdict.toolDamage(), player, EquipmentSlot.MAINHAND);
    }

    /** Section 71.1.9: breaking costs stamina, and a poor tool costs several times as much. */
    private static void spendEffort(ServerPlayer player, BlockState state, BreakingVerdict verdict) {
        var runtime = CoreLifecycle.find(player.level().getServer());
        if (runtime == null) return;
        double cost = BlockBreaking.staminaCost(state, verdict);
        if (cost > 0) runtime.survival().spendStamina(player, cost);
    }

}
