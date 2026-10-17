package de.ipnats.hardwrought.enchanting;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.nether.NetherCrossing;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;

/**
 * The soulbound rune (endgame § 4): a piece that carries it stays with its owner through a death —
 * not dropped where they fell, but back in its slot when they rise again — and the rune is spent
 * doing it. It is written only in Wither essence ({@link RuneWork#inkSuits}), so each one costs a
 * fight with the Wither, and a death costs it again.
 *
 * <p>Where nothing is dropped on death anyway (keep inventory), the rune saves nothing and stays.
 */
public final class Soulbound {
    public static final ResourceKey<Enchantment> RUNE = ResourceKey.create(Registries.ENCHANTMENT, RuneWork.SOULBOUND);

    /** What a death did not take from a player, waiting for them to rise again. */
    public static final AttachmentType<List<NetherCrossing.KeptStack>> KEPT = AttachmentRegistry.create(Hardwrought.id("soulbound"),
            builder -> builder.persistent(NetherCrossing.KeptStack.CODEC.listOf()).copyOnDeath());

    private Soulbound() { }

    public static void initialize() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) giveBack(newPlayer);
        });
    }

    /** Whether this piece carries the soulbound rune. */
    public static boolean bound(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) return false;
        Holder<Enchantment> rune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(RUNE).orElse(null);
        return rune != null && stack.getEnchantments().getLevel(rune) > 0;
    }

    /**
     * A player is dying and about to drop what they carry: every soulbound piece is taken out first,
     * its rune spent, and kept for them.
     */
    public static void keep(ServerPlayer player, ServerLevel level) {
        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) return;
        Inventory inventory = player.getInventory();
        List<NetherCrossing.KeptStack> kept = new ArrayList<>(player.getAttachedOrElse(KEPT, List.of()));
        int before = kept.size();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!bound(level, stack)) continue;
            ItemStack piece = inventory.removeItemNoUpdate(slot);
            spend(piece);
            kept.add(new NetherCrossing.KeptStack(slot, piece));
        }
        if (kept.size() == before) return;
        player.setAttached(KEPT, kept);
        // Seen to go with the soul: souls rising off the body, a toll as the runes burn out.
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.04);
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.6f);
    }

    /** Burns the rune off a piece: its enchantment, and its mark among the piece's runes, so the room is free again. */
    static void spend(ItemStack piece) {
        EnchantmentHelper.updateEnchantments(piece, enchantments -> enchantments.removeIf(enchantment -> enchantment.is(RUNE)));
        RuneWork.Marks marks = piece.get(ModDataComponents.RUNES);
        if (marks == null) return;
        List<RuneWork.Placement> rest = marks.runes().stream().filter(rune -> !rune.rune().equals(RuneWork.SOULBOUND)).toList();
        if (rest.isEmpty()) piece.remove(ModDataComponents.RUNES);
        else piece.set(ModDataComponents.RUNES, new RuneWork.Marks(rest));
    }

    /** Risen again: what the soul kept, back where it was — armour on, the rest in its slot or wherever there is room. */
    static void giveBack(ServerPlayer player) {
        List<NetherCrossing.KeptStack> kept = player.getAttached(KEPT);
        if (kept == null || kept.isEmpty()) return;
        Inventory inventory = player.getInventory();
        for (NetherCrossing.KeptStack piece : kept) {
            ItemStack stack = piece.stack().copy();
            if (piece.slot() < inventory.getContainerSize() && inventory.getItem(piece.slot()).isEmpty()) inventory.setItem(piece.slot(), stack);
            else if (!inventory.add(stack)) player.spawnAtLocation(player.level(), stack);
        }
        player.removeAttached(KEPT);
        player.containerMenu.broadcastChanges();
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.04);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 1.2f);
        player.sendSystemMessage(Component.translatable("message.hardwrought.soulbound.returned", kept.size()).withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Takes what this player carries as if they had died; for tests. */
    public static void keepForTesting(ServerPlayer player, ServerLevel level) {
        keep(player, level);
    }

    /** Gives back what was kept, as on rising again; for tests. */
    public static void giveBackForTesting(ServerPlayer player) {
        giveBack(player);
    }
}
