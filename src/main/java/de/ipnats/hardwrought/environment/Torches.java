package de.ipnats.hardwrought.environment;

import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.equipment.BackpackContainer;
import de.ipnats.hardwrought.progression.LightingSticksItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/**
 * A torch is made unlit and has to be lit, and once lit it burns down wherever it is: in the hand, in
 * the bag, in the pack. A lit torch is the ordinary torch item carrying how long it has burnt as wear, so
 * its bar is the flame going down and it no longer stacks with others; at the end it is a stick with a
 * charred end. Set down, it burns on in the world for what it had left (see {@link LightBurn}); taken
 * down again, it comes off burning with what it has left. An unlit torch can also be set down as it is and
 * lit where it stands.
 *
 * <p>Lit from anything that burns — a campfire, a fire, lava, a lit furnace, a torch on the wall — or in
 * the hand, with flint and steel, the lighting sticks or another burning torch in the other hand. And a
 * burning torch lights a laid campfire, which is what a torch is for at the end of a day's march.
 *
 * <p>A torch with no wear on it at all is one nobody lit: a chest, a trade or the old recipe made it. In
 * a player's hands it is what it is, an unlit torch.
 */
public final class Torches {
    /** How long a lit torch burns, in seconds: a day, the same as one set on a wall. */
    public static final int BURN_SECONDS = 1200;

    private Torches() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) burn(player);
        });
        // A burning torch taken down comes off burning, with what it had left; one past its time, as a stick.
        // The loot table says unlit torch, and stays right for whatever else breaks one without a flame known.
        net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            if (!table.unwrapKey().map(key -> key.equals(net.minecraft.world.level.block.Blocks.TORCH.getLootTable().orElse(null)))
                    .orElse(false)) return;
            var origin = context.getOptional(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
            if (origin == null) return;
            ServerLevel level = context.getLevel();
            long since = LightBurn.litAt(level, BlockPos.containing(origin));
            int burnt = since == -1 ? 0 : (int) Math.max(0, (level.getGameTime() - since) / 20);
            drops.replaceAll(stack -> !stack.is(ModItems.UNLIT_TORCH) ? stack
                    : burnt >= BURN_SECONDS ? new ItemStack(Items.STICK, stack.getCount()) : lit(burnt));
        });
        // A burning torch held to a laid campfire lights it.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!isLit(held) || player.isSpectator()) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!LightingSticksItem.lightable(state)) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, true));
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5f, 1.4f);
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    /** Whether this is a burning torch: the torch item, with a flame that has a length. */
    public static boolean isLit(ItemStack stack) {
        return stack.is(Items.TORCH) && stack.has(DataComponents.MAX_DAMAGE);
    }

    /** A freshly lit torch. */
    public static ItemStack lit() {
        return lit(0);
    }

    /** A torch that has burnt this many seconds already. */
    public static ItemStack lit(int burnt) {
        ItemStack torch = new ItemStack(Items.TORCH);
        torch.set(DataComponents.MAX_STACK_SIZE, 1);
        torch.set(DataComponents.MAX_DAMAGE, BURN_SECONDS);
        torch.setDamageValue(Math.clamp(burnt, 0, BURN_SECONDS - 1));
        return torch;
    }

    /** How many seconds a lit torch has burnt. */
    public static int burnt(ItemStack stack) {
        return isLit(stack) ? stack.getDamageValue() : 0;
    }

    /** Whether something here burns well enough to light a torch at. */
    public static boolean flame(BlockState state) {
        if (state.getBlock() instanceof BaseFireBlock) return true;
        if (state.getFluidState().is(Fluids.LAVA) || state.getFluidState().is(Fluids.FLOWING_LAVA)) return true;
        if (state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT)) return true;
        return LightBurn.burns(state);
    }

    /**
     * Lights one torch out of the unlit stack in this hand. The lit one takes the hand if the stack is used
     * up, and otherwise goes into the bag, or to the ground if there is no room.
     *
     * @return what the hand holds afterwards, which an item's use has to hand back to the game
     */
    public static ItemStack lightOne(Player player, InteractionHand hand) {
        ItemStack unlit = player.getItemInHand(hand);
        if (!unlit.is(ModItems.UNLIT_TORCH)) return unlit;
        unlit.shrink(1);
        ItemStack lit = lit();
        if (unlit.isEmpty()) player.setItemInHand(hand, lit);
        else if (!player.getInventory().add(lit)) player.drop(lit, false, net.minecraft.util.Prediction.SERVER_ONLY);
        Level level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.4f, 1.6f);
        return player.getItemInHand(hand);
    }

    /** One second of burning for every torch a player carries; and a torch nobody lit is an unlit one. */
    public static void burn(ServerPlayer player) {
        if (player.isSpectator() || player.isCreative()) return;
        burnCarried(player);
    }

    /** The same, whatever the player's game mode: for tests, whose mock players are creative. */
    public static void burnCarried(ServerPlayer player) {
        burn(player, player.getInventory());
        BackpackContainer pack = BackpackContainer.of(player);
        pack.refresh();
        burn(player, pack);
    }

    private static void burn(ServerPlayer player, Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(Items.TORCH)) continue;
            if (!isLit(stack)) {
                container.setItem(slot, new ItemStack(ModItems.UNLIT_TORCH, stack.getCount()));
                continue;
            }
            int burnt = stack.getDamageValue() + 1;
            if (burnt < BURN_SECONDS) {
                ItemStack burning = stack.copy();
                burning.setDamageValue(burnt);
                container.setItem(slot, burning);
                continue;
            }
            // Burnt down to the hand: a stick with a charred end is all that is left of it.
            container.setItem(slot, new ItemStack(Items.STICK));
            if (player.level() instanceof ServerLevel level) {
                level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3f, 1.6f);
            }
        }
    }

    /** A lit torch was set down: its flame burns on in the world for only what it had left. */
    public static void placed(ServerLevel level, BlockPos pos, ItemStack from) {
        if (!isLit(from) || !LightBurn.burns(level.getBlockState(pos))) return;
        LightBurn.litSince(level, pos, level.getGameTime() - burnt(from) * 20L);
    }
}
