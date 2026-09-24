package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.networking.ForgingPayloads;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A session at the anvil, on the server.
 *
 * <p>Hammer in the main hand, the heated piece in the other, and the anvil used: that opens the
 * work. The client shows the piece and says where each blow lands; everything else is decided here,
 * and all of it is written onto the piece itself — its shape so far, its heat, the good and the bad
 * blows — so the work survives the player walking back to the fire, logging out, or dying.
 */
public final class Forging {
    /** Squares a job has to put right at the least, so two identical shapes cannot count as work. */
    public static final int MIN_JOB = 6;
    /** How far from the anvil the player may stand. */
    private static final double REACH_SQR = 36.0;
    private static final double STAMINA_PER_BLOW = 0.15;
    private static boolean initialized;

    private Forging() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !Hammers.isHammer(player.getMainHandItem())) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            if (!Anvils.isAnvil(level.getBlockState(pos))) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (player instanceof ServerPlayer worker) open(worker, pos);
            return InteractionResult.SUCCESS;
        });
        ServerPlayNetworking.registerGlobalReceiver(ForgingPayloads.Begin.TYPE,
                (payload, context) -> begin(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ForgingPayloads.Strike.TYPE,
                (payload, context) -> strike(context.player(), payload.anvil(), payload.x(), payload.y()));
        ServerPlayNetworking.registerGlobalReceiver(ForgingPayloads.Cancel.TYPE,
                (payload, context) -> cancel(context.player()));
    }

    /** Tells the client what the piece in the off hand can become at this anvil. */
    static void open(ServerPlayer player, BlockPos anvil) {
        ItemStack piece = player.getOffhandItem();
        List<Smithing.Recipe> recipes = Smithing.recipesFor(piece);
        if (recipes.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.forging_needs_metal"), true);
            return;
        }
        List<Identifier> results = new ArrayList<>();
        for (Smithing.Recipe recipe : recipes) results.add(BuiltInRegistries.ITEM.getKey(recipe.result()));
        if (ServerPlayNetworking.canSend(player, ForgingPayloads.Open.TYPE)) {
            ServerPlayNetworking.send(player, new ForgingPayloads.Open(anvil, results));
        }
    }

    /**
     * Starts the work on the piece in the off hand. A part takes several ingots, and they are all
     * taken now: they are hammered together into the one piece on the anvil.
     */
    public static boolean begin(ServerPlayer player, ForgingPayloads.Begin payload) {
        ServerLevel level = player.level();
        if (!atAnvil(player, payload.anvil())) return false;
        ItemStack held = player.getOffhandItem();
        if (held.has(ModDataComponents.FORGING_STATE)) return false;
        Item resultItem = BuiltInRegistries.ITEM.getValue(payload.result());
        Smithing.Recipe recipe = Smithing.recipeFor(held.getItem(), payload.result());
        if (recipe == null || resultItem == null) return false;
        Mask source = Mask.of(payload.source());
        Mask target = Mask.of(payload.target());
        if (source.count() == 0 || target.count() == 0 || source.mismatch(target) < MIN_JOB) return false;
        if (Hammers.reach(player.getMainHandItem()) < Hammers.reachNeeded(resultItem)) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.forging_needs_heavier_hammer",
                    new ItemStack(resultItem).getHoverName()), true);
            return false;
        }
        if (tooCold(player, held)) return false;
        if (held.getCount() < recipe.count()) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.forging_needs_more",
                    recipe.count(), held.getHoverName()), true);
            return false;
        }
        // Everything that goes into the piece stays together in the off hand while it is worked: three
        // bars for a pick head, one lump for a bar. The rest of the stack goes into the bag meanwhile.
        ItemStack piece = held.copyWithCount(recipe.count());
        held.shrink(recipe.count());
        float cap = Anvils.craftsmanshipCap(level.getBlockState(payload.anvil()));
        piece.set(ModDataComponents.FORGING_STATE, new ForgingState(payload.result(), source, target, cap));
        ItemStack rest = held.isEmpty() ? ItemStack.EMPTY : held.copy();
        player.setItemInHand(InteractionHand.OFF_HAND, piece);
        if (!rest.isEmpty() && !player.getInventory().add(rest)) player.spawnAtLocation(level, rest);
        return true;
    }

    /**
     * One blow. It puts right whatever is wrong where it lands — as much as the hammer covers, see
     * {@link Hammers} — takes a little
     * heat out of the piece, and is counted: a blow that found nothing to put right is a wasted one.
     * On metal below working heat a blow does nothing at all — cold iron does not move under a hammer
     * — and is not counted either; the piece has to go back to the fire. The last blow that makes the
     * shape right turns the piece into what it was becoming.
     */
    public static boolean strike(ServerPlayer player, BlockPos anvil, int x, int y) {
        ServerLevel level = player.level();
        if (!atAnvil(player, anvil) || x < 0 || y < 0 || x >= Mask.SIZE || y >= Mask.SIZE) return false;
        ItemStack piece = player.getOffhandItem();
        ForgingState state = piece.get(ModDataComponents.FORGING_STATE);
        if (state == null) return false;
        var runtime = CoreLifecycle.find(level.getServer());
        long now = level.getGameTime();

        double temperature = Heat.of(piece, now);
        if (tooCold(player, piece)) {
            level.playSound(null, anvil, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.2f, 1.6f);
            return false;
        }

        // A piece split apart in the inventory is no longer the metal the work began with.
        Smithing.Recipe recipe = Smithing.recipeFor(piece.getItem(), state.result());
        if (recipe != null && piece.getCount() != recipe.count()) {
            player.sendSystemMessage(Component.translatable("message.hardwrought.forging_needs_more",
                    recipe.count(), piece.getHoverName()), true);
            return false;
        }

        Mask before = state.currentMask();
        Mask after = before.strike(x, y, state.targetMask(), Hammers.reach(player.getMainHandItem()));
        boolean useful = after.mismatch(state.targetMask()) < before.mismatch(state.targetMask());
        float cap = Anvils.craftsmanshipCap(level.getBlockState(anvil));
        Anvils.strikeWear(level, anvil);
        ForgingState next = state.struck(after, useful, cap);
        piece.set(ModDataComponents.FORGING_STATE, next);
        if (temperature > Heat.AMBIENT) {
            piece.set(ModDataComponents.HEAT, new Heat((float) Math.max(Heat.AMBIENT,
                    temperature - Smithing.HEAT_PER_BLOW), now));
        }

        if (runtime != null) runtime.survival().spendStamina(player, STAMINA_PER_BLOW);
        ItemStack hammer = player.getMainHandItem();
        if (level.getRandom().nextInt(4) == 0) hammer.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        level.playSound(null, anvil, SoundEvents.ANVIL_USE, SoundSource.BLOCKS,
                0.35f, 0.9f + level.getRandom().nextFloat() * 0.3f);
        level.sendParticles(ParticleTypes.SMALL_FLAME, anvil.getX() + 0.5, anvil.getY() + 1.0,
                anvil.getZ() + 0.5, 3, 0.15, 0.02, 0.15, 0.02);

        if (next.finished()) finish(player, level, anvil, piece, next, runtime == null ? null : runtime);
        return true;
    }

    /**
     * Gives up on the piece in the off hand. Everything that went into it comes back as it went in —
     * three bars for a pick head, the raw lump for a bar — only as hot as the piece is now. The work
     * done on it is lost; the metal is not.
     */
    public static boolean cancel(ServerPlayer player) {
        ItemStack piece = player.getOffhandItem();
        ForgingState state = piece.get(ModDataComponents.FORGING_STATE);
        if (state == null) return false;
        ItemStack back = piece.copy();
        back.remove(ModDataComponents.FORGING_STATE);
        player.setItemInHand(InteractionHand.OFF_HAND, back);
        player.sendSystemMessage(Component.translatable("message.hardwrought.forging_cancelled"), true);
        return true;
    }

    private static void finish(ServerPlayer player, ServerLevel level, BlockPos anvil, ItemStack piece,
                               ForgingState state, de.ipnats.hardwrought.core.CoreRuntime runtime) {
        Item resultItem = BuiltInRegistries.ITEM.getValue(state.result());
        ItemStack result = new ItemStack(resultItem);
        Heat heat = piece.get(ModDataComponents.HEAT);
        if (heat != null) result.set(ModDataComponents.HEAT, heat);
        Smithing.Recipe recipe = Smithing.recipeFor(piece.getItem(), state.result());
        if (recipe != null && recipe.part()) {
            float craftsmanship = state.craftsmanship();
            result.set(ModDataComponents.FORGE_QUALITY,
                    new ForgeQuality(craftsmanship, ForgeQuality.Treatment.AIR));
            player.sendSystemMessage(Component.translatable("message.hardwrought.forged_part",
                    result.getHoverName(), Math.round(craftsmanship * 100)), true);
        } else {
            player.sendSystemMessage(Component.translatable("message.hardwrought.forged",
                    result.getHoverName()), true);
        }
        // The finished piece goes into the bag; the rest of the stack it came from goes back into the
        // off hand, so the next one can be begun straight away.
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        refillOffhand(player, piece.getItem());
        if (!player.getInventory().add(result)) {
            if (player.getOffhandItem().isEmpty()) player.setItemInHand(InteractionHand.OFF_HAND, result);
            else player.spawnAtLocation(level, result);
        }
        level.playSound(null, anvil, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.4f);
        if (runtime != null) runtime.knowledge().study(player, resultItem);
        Anvils.wear(level, anvil);
    }

    /**
     * Puts the rest of what the finished piece was made from back into the off hand: the hottest stack
     * of that metal in the bag, which is the one set aside when the work began.
     */
    private static void refillOffhand(ServerPlayer player, Item input) {
        var inventory = player.getInventory();
        long now = player.level().getGameTime();
        int best = -1;
        double bestHeat = -1;
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(input) || stack.has(ModDataComponents.FORGING_STATE)) continue;
            double heat = Heat.of(stack, now);
            if (heat > bestHeat) {
                best = slot;
                bestHeat = heat;
            }
        }
        if (best < 0) return;
        player.setItemInHand(InteractionHand.OFF_HAND, inventory.getItem(best));
        inventory.setItem(best, ItemStack.EMPTY);
    }

    /**
     * Whether the piece is below its working heat. Tells the player so, because a hammer bouncing off
     * cold metal without a word would look like a bug.
     */
    private static boolean tooCold(ServerPlayer player, ItemStack piece) {
        var runtime = CoreLifecycle.find(player.level().getServer());
        if (runtime == null) return false;
        double[] range = Smithing.workingRange(piece.getItem(), runtime.materials());
        if (range == null || Heat.of(piece, player.level().getGameTime()) >= range[0]) return false;
        player.sendSystemMessage(Component.translatable("message.hardwrought.forging_too_cold"), true);
        return true;
    }

    private static boolean atAnvil(ServerPlayer player, BlockPos anvil) {
        if (anvil == null || !Hammers.isHammer(player.getMainHandItem())) return false;
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(anvil)) > REACH_SQR) return false;
        return Anvils.isAnvil(player.level().getBlockState(anvil));
    }
}
