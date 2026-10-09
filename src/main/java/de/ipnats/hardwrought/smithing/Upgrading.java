package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.core.networking.UpgradePayloads;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Working a piece up a tier at the smithing table: tungsten steel to netherite, netherite to mithril,
 * mithril to adamant.
 *
 * <p>The piece is held in the main hand, the template and the bar are in the inventory, and the table
 * is used. What follows is the smith's own work: the template's pattern lies over the piece and has
 * to be traced (see the upgrade screen). The upgrade always succeeds — which piece becomes which is
 * an ordinary smithing recipe, and its enchantments and everything else go with it — but how truly
 * the pattern was followed decides how well made the new piece is: a clean trace keeps the piece's
 * craftsmanship or betters it, a careless one costs some of it.
 *
 * <p>Anything else at a smithing table — armor trims, or a hand that holds nothing to work up — opens
 * vanilla's own screen, as before.
 */
public final class Upgrading {
    /** How well made a piece counts as that nobody forged: a little under a good smith's work. */
    public static final float UNFORGED = 0.65f;
    /** The trace that changes nothing: better than this improves the piece, worse costs it. */
    public static final float FAIR = 0.75f;
    /** Craftsmanship a whole point of accuracy is worth, and the most a trace can cost or add. */
    static final float WORTH = 0.6f, MOST_LOST = 0.30f, MOST_GAINED = 0.15f;
    private static final double REACH = 8.0;

    /** What a player is working up, where, and with what. */
    private record Work(BlockPos table, Item template, Item bar) { }

    /** An upgrade that could be done now: the recipe, and the two things in the inventory it takes. */
    public record Match(RecipeHolder<SmithingRecipe> recipe, SmithingRecipeInput input, int pattern) { }

    private static final Map<UUID, Work> works = new HashMap<>();

    private Upgrading() { }

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !level.getBlockState(hit.getBlockPos()).is(Blocks.SMITHING_TABLE)
                    || player.isSpectator() || player.getMainHandItem().isEmpty()) {
                return InteractionResult.PASS;
            }
            // Only the server knows the recipes; the client waits to be told whether this is an upgrade.
            if (!(player instanceof ServerPlayer smith)) {
                return holdsTemplate(player.getInventory()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            Match match = find((ServerLevel) level, smith.getInventory(), smith.getMainHandItem());
            if (match == null) return InteractionResult.PASS;
            works.put(smith.getUUID(), new Work(hit.getBlockPos().immutable(), match.input().template().getItem(),
                    match.input().addition().getItem()));
            if (ServerPlayNetworking.canSend(smith, UpgradePayloads.Open.TYPE)) {
                ServerPlayNetworking.send(smith, new UpgradePayloads.Open(hit.getBlockPos(), match.pattern(),
                        BuiltInRegistries.ITEM.getKey(match.recipe().value().assemble(match.input()).getItem())));
            }
            return InteractionResult.SUCCESS;
        });
        ServerPlayNetworking.registerGlobalReceiver(UpgradePayloads.Finish.TYPE,
                (payload, context) -> finish(context.player(), payload.table(), payload.accuracy()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> works.remove(handler.player.getUUID()));
    }

    private static boolean holdsTemplate(Inventory inventory) {
        List<Item> templates = UpgradeEquipment.templates();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (templates.contains(inventory.getItem(slot).getItem())) return true;
        }
        return false;
    }

    /**
     * The upgrade this piece can be given with what is in the inventory, or null. The highest
     * template is tried first, so a smith carrying two templates gets the upgrade the piece is due.
     */
    public static Match find(ServerLevel level, Inventory inventory, ItemStack piece) {
        List<Item> templates = UpgradeEquipment.templates();
        for (int pattern = templates.size() - 1; pattern >= 0; pattern--) {
            ItemStack template = first(inventory, templates.get(pattern));
            if (template.isEmpty()) continue;
            Set<Item> tried = new HashSet<>();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack bar = inventory.getItem(slot);
                if (bar.isEmpty() || bar == piece || !tried.add(bar.getItem())) continue;
                SmithingRecipeInput input = new SmithingRecipeInput(template, piece, bar);
                var recipe = level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level);
                if (recipe.isPresent()) return new Match(recipe.get(), input, pattern);
            }
        }
        return null;
    }

    private static ItemStack first(Inventory inventory, Item item) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) return inventory.getItem(slot);
        }
        return ItemStack.EMPTY;
    }

    /**
     * How well made a piece is after being worked up, from how well made it was and how truly the
     * pattern was traced, 0 to 1. Treatment and polish stay as they were: the hardening is in the
     * steel the piece began as, and the new metal is laid over it.
     */
    public static ForgeQuality worked(ForgeQuality before, float accuracy) {
        float was = before == null ? UNFORGED : before.craftsmanship();
        float change = Math.clamp((Math.clamp(accuracy, 0f, 1f) - FAIR) * WORTH, -MOST_LOST, MOST_GAINED);
        return new ForgeQuality(Math.clamp(was + change, 0f, 1f),
                before == null ? ForgeQuality.Treatment.AIR : before.treatment(),
                before == null ? 0 : before.polish(), before == null ? 0 : before.passes());
    }

    private static void finish(ServerPlayer smith, BlockPos table, float accuracy) {
        Work work = works.remove(smith.getUUID());
        ServerLevel level = (ServerLevel) smith.level();
        if (work == null || !work.table().equals(table) || !level.getBlockState(table).is(Blocks.SMITHING_TABLE)
                || smith.distanceToSqr(table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5) > REACH * REACH) {
            return;
        }
        ItemStack piece = smith.getMainHandItem();
        Match match = find(level, smith.getInventory(), piece);
        // The same upgrade that was begun, with the same template and bar: nothing swapped under way.
        if (match == null || !match.input().template().is(work.template()) || !match.input().addition().is(work.bar())) return;
        ItemStack made = match.recipe().value().assemble(match.input());
        if (made.isEmpty()) return;
        made.set(ModDataComponents.FORGE_QUALITY, worked(ForgeQuality.of(piece), accuracy));
        if (!smith.isCreative()) {
            match.input().template().shrink(1);
            match.input().addition().shrink(1);
        }
        smith.setItemInHand(InteractionHand.MAIN_HAND, made);
        level.playSound(null, table, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0f, 0.9f + accuracy * 0.3f);
    }
}
