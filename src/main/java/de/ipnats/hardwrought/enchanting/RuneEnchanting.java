package de.ipnats.hardwrought.enchanting;

import de.ipnats.hardwrought.core.networking.RunePayloads;
import de.ipnats.hardwrought.metallurgy.OrePowders;
import de.ipnats.hardwrought.smithing.Mask;
import de.ipnats.hardwrought.smithing.ToolParts;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enchanting, as Hardwrought has it: the runes of an enchantment are written on the piece itself,
 * with powdered lapis lazuli, at an enchanting table. Written in powdered mithril or adamant instead,
 * a rune is overcharged: one or two levels beyond what the enchantment has otherwise.
 *
 * <p>The piece is held in the main hand and the table used. Which runes then lie ready is fixed by the
 * bookshelves round the table and the level the writer has on sitting down (see {@link RuneWork#tier});
 * how many of them the piece takes, by how readily its material takes enchantment and by how well
 * the piece is made (see {@link RuneWork#forCraftsmanship}). The writer lays
 * them on the piece (see {@link RuneWork}) and works them in, which costs powder for every stroke and
 * a little experience for every rune. The runes that lay ready stay ready for the whole sitting, even
 * as the writing takes the writer's levels down.
 *
 * <p>It is better done to the parts than to the finished tool: a rune works itself fully into a part
 * and goes with it into the tool it becomes, and reaches one level less on a tool already made.
 * A piece remembers the runes written on it and where they lie. It can be brought back to the table
 * as often as wanted — as a part, as the tool it became, as the same tool worked up to mithril — to
 * write more beside them while there is room and the piece takes more, or to wipe one off and write
 * it again, in a finer ink.
 *
 * <p>The vanilla way is gone with it: the table's own screen never opens, librarians sell no
 * enchanted books, and such books are a rare find — which is why treasure enchantments have runes too. Enchanted things that villagers sell, and books
 * laid on at an anvil, are as they were.
 */
public final class RuneEnchanting {
    /** How far from the table what was written still counts. */
    private static final double REACH = 8.0;
    /** The share of enchanted books in loot that is still found. */
    public static final float BOOK_LOOT_SHARE = 0.25f;

    /** One sitting: at which table, and which runes lay ready. */
    private record Sitting(BlockPos table, List<Identifier> runes, int capacity, boolean part, int coarser) { }

    private static final Map<UUID, Sitting> sittings = new HashMap<>();

    private RuneEnchanting() { }

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !level.getBlockState(hit.getBlockPos()).is(Blocks.ENCHANTING_TABLE)
                    || player.isSpectator()) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer writer) open(writer, hit.getBlockPos());
            return InteractionResult.SUCCESS;
        });
        ServerPlayNetworking.registerGlobalReceiver(RunePayloads.Write.TYPE,
                (payload, context) -> write(context.player(), payload.table(), Mask.of(payload.piece()), payload.runes(),
                        payload.wiped()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> sittings.remove(handler.player.getUUID()));
        // An enchanted book is a find now, not a commodity: most of those a chest, a fishing line or
        // anything else would have given are simply not there. (Librarians do not sell them at all;
        // their trade lists are in data/minecraft/tags/villager_trade/librarian.)
        net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY_DROPS.register((table, context, drops) ->
                drops.removeIf(stack -> stack.is(Items.ENCHANTED_BOOK) && context.getRandom().nextFloat() >= BOOK_LOOT_SHARE));
    }

    /** The powder runes are ordinarily written with. */
    public static Item powder() {
        return powder(RuneWork.Ink.LAPIS);
    }

    /** The powder an ink is. */
    public static Item powder(RuneWork.Ink ink) {
        return switch (ink) {
            case LAPIS -> OrePowders.powder(OrePowders.VanillaOre.LAPIS_LAZULI);
            case MITHRIL -> OrePowders.powder(de.ipnats.hardwrought.metallurgy.Metal.MITHRIL);
            case ADAMANT -> OrePowders.powder(de.ipnats.hardwrought.metallurgy.Metal.ADAMANTIUM);
        };
    }

    /** Whether a piece is a part, into which a rune works itself fully. */
    public static boolean isPart(ItemStack piece) {
        return ToolParts.partOf(piece.getItem()) != null;
    }

    /**
     * What a piece counts as when asking which enchantments suit it: itself, or for a part the kind
     * of tool it is the making of.
     */
    public static ItemStack kind(ItemStack piece) {
        ToolParts.Part part = ToolParts.partOf(piece.getItem());
        if (part == null) return piece;
        return new ItemStack(switch (part) {
            case PICKAXE_HEAD -> Items.IRON_PICKAXE;
            case AXE_HEAD, HALBERD_HEAD -> Items.IRON_AXE;
            case SHOVEL_HEAD -> Items.IRON_SHOVEL;
            case HOE_HEAD -> Items.IRON_HOE;
            case SWORD_BLADE, DAGGER_BLADE, GREATSWORD_BLADE -> Items.IRON_SWORD;
            case HAMMER_HEAD -> Items.MACE;
            case HELMET_SHELL -> Items.IRON_HELMET;
            case CUIRASS -> Items.IRON_CHESTPLATE;
            case GREAVES -> Items.IRON_LEGGINGS;
            case SABATONS -> Items.IRON_BOOTS;
        });
    }

    /** How readily a piece takes enchantment; nought for what takes none. */
    public static int enchantability(ItemStack piece) {
        ToolParts.SmithMetal metal = ToolParts.metalOf(piece.getItem());
        if (metal != null) {
            return switch (metal) {
                case GOLD -> 22;
                case COPPER -> 16;
                case IRON, BRONZE, TITANIUM -> 14;
                case STEEL, STAINLESS_STEEL -> 12;
                case TUNGSTEN_STEEL -> 10;
            };
        }
        Enchantable enchantable = piece.get(DataComponents.ENCHANTABLE);
        return enchantable == null ? 0 : enchantable.value();
    }

    /** Whether runes can be written on this at all, and why not where they cannot. */
    private static Component refusal(ItemStack piece) {
        if (piece.isEmpty()) return Component.translatable("message.hardwrought.runes.no_piece");
        if (enchantability(piece) <= 0) return Component.translatable("message.hardwrought.runes.not_enchantable");
        return null;
    }

    /** Bookshelves round a table, counted as vanilla counts them. */
    public static int shelves(Level level, BlockPos table) {
        int shelves = 0;
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(level, table, offset)) shelves++;
        }
        return shelves;
    }

    /**
     * Every enchantment that has a rune and suits this piece, the commonest first: what vanilla's
     * table gave, and what it kept for treasure — there being no librarian to buy mending from any
     * more — but no curse.
     */
    public static List<Holder<Enchantment>> suited(ServerLevel level, ItemStack piece) {
        ItemStack kind = kind(piece);
        List<Holder<Enchantment>> suited = new ArrayList<>();
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (var tag : List.of(EnchantmentTags.IN_ENCHANTING_TABLE, EnchantmentTags.TREASURE)) {
            registry.get(tag).ifPresent(runes -> runes.forEach(rune -> {
                if (!rune.is(EnchantmentTags.CURSE) && !suited.contains(rune) && rune.value().canEnchant(kind)) suited.add(rune);
            }));
        }
        suited.sort(Comparator.<Holder<Enchantment>>comparingInt(RuneWork::tier)
                .thenComparing(rune -> id(rune).toString()));
        return suited;
    }

    /** Whether a rune lies ready at a table with this many shelves, for a writer of this level. */
    public static boolean ready(Holder<Enchantment> rune, int shelves, int level) {
        int tier = RuneWork.tier(rune);
        return shelves >= RuneWork.shelvesFor(tier) && level >= RuneWork.levelFor(tier);
    }

    /** The runes a piece carries, where they lie; none for a piece nothing was written on. */
    public static List<RuneWork.Placement> carried(ItemStack piece) {
        RuneWork.Marks marks = piece.get(de.ipnats.hardwrought.core.registry.ModDataComponents.RUNES);
        return marks == null ? List.of() : marks.runes();
    }

    /** How many enchantments a piece has that are not among these runes: found that way, or laid on at an anvil. */
    private static int loose(ItemStack piece, List<RuneWork.Placement> runes) {
        int loose = 0;
        for (Holder<Enchantment> enchantment : piece.getEnchantments().keySet()) {
            Identifier id = id(enchantment);
            if (runes.stream().noneMatch(rune -> rune.rune().equals(id))) loose++;
        }
        return loose;
    }

    private static Identifier id(Holder<Enchantment> rune) {
        return rune.unwrapKey().orElseThrow().identifier();
    }

    private static void open(ServerPlayer writer, BlockPos table) {
        ItemStack piece = writer.getMainHandItem();
        Component refusal = refusal(piece);
        if (refusal != null) {
            writer.sendOverlayMessage(refusal);
            return;
        }
        ServerLevel level = (ServerLevel) writer.level();
        int shelves = shelves(level, table);
        // In creative every rune lies ready, as everything else does.
        int levels = writer.isCreative() ? 99 : writer.experienceLevel;
        int counted = writer.isCreative() ? 99 : shelves;
        List<Identifier> runes = new ArrayList<>();
        int locked = 0;
        for (Holder<Enchantment> rune : suited(level, piece)) {
            if (ready(rune, counted, levels) && runes.size() < RunePayloads.MAX_RUNES) runes.add(id(rune));
            else locked++;
        }
        if (runes.isEmpty()) {
            writer.sendOverlayMessage(Component.translatable(locked > 0 ? "message.hardwrought.runes.none_ready"
                    : "message.hardwrought.runes.none_suit"));
            return;
        }
        var made = de.ipnats.hardwrought.smithing.ForgeQuality.of(piece);
        int capacity = RuneWork.capacity(enchantability(piece)) + (made == null ? 0 : RuneWork.forCraftsmanship(made.total()));
        boolean part = isPart(piece);
        int coarser = coarser(piece);
        sittings.put(writer.getUUID(), new Sitting(table.immutable(), List.copyOf(runes), capacity, part, coarser));
        if (ServerPlayNetworking.canSend(writer, RunePayloads.Open.TYPE)) {
            List<RuneWork.Placement> carried = carried(piece);
            ServerPlayNetworking.send(writer, new RunePayloads.Open(table, shelves, writer.experienceLevel, capacity, part,
                    runes, locked, carried, loose(piece, carried), coarser));
        }
    }

    /** What writing these runes on this piece comes to, or why it cannot be done. */
    public record Judged(Map<Holder<Enchantment>, Integer> levels, int[] powder, int experience, String refusal) {
        static Judged refused(String why) {
            return new Judged(Map.of(), new int[RuneWork.Ink.values().length], 0, why);
        }

        /** Measures of this ink it takes. */
        public int powder(RuneWork.Ink ink) {
            return powder[ink.ordinal()];
        }

        public boolean allowed() {
            return refusal == null;
        }
    }

    /**
     * Judges what was written: every rune one that lay ready, none of them twice,
     * no two over each other, each enough on the piece to work, and no more of them than the piece takes.
     */
    public static Judged judge(ServerLevel level, Mask piece, List<RuneWork.Placement> runes, List<Identifier> ready,
                               int capacity, boolean part) {
        if (runes.isEmpty()) return Judged.refused("nothing");
        return judge(level, piece, runes, ready, capacity, part, List.of(), java.util.Set.of());
    }

    /** As the longest {@code judge}, on adamant, which takes the finest strokes. */
    public static Judged judge(ServerLevel level, Mask piece, List<RuneWork.Placement> runes, List<Identifier> ready,
                               int capacity, boolean part, List<RuneWork.Placement> kept,
                               java.util.Set<Holder<Enchantment>> present) {
        return judge(level, piece, runes, ready, capacity, part, kept, present, 0);
    }

    /**
     * How much larger than on adamant runes come out on this piece: not at all on adamant, a square
     * on mithril, two on everything else (see {@link RuneWork#size(int, int)}).
     */
    public static int coarser(ItemStack piece) {
        var tier = de.ipnats.hardwrought.smithing.UpgradeEquipment.tierOf(piece.getItem());
        return tier == de.ipnats.hardwrought.smithing.UpgradeEquipment.Tier.ADAMANT ? 0
                : tier == de.ipnats.hardwrought.smithing.UpgradeEquipment.Tier.MITHRIL ? 1 : 2;
    }

    /**
     * As {@link #judge(ServerLevel, Mask, List, List, int, boolean)}, on a piece that already carries
     * something: the runes that stay on it keep their room and count towards what the piece takes,
     * and nothing the piece already has is written on it a second time.
     *
     * @param kept    the runes that stay on the piece
     * @param present every enchantment that stays on the piece, written as a rune or not
     * @param coarser how much larger than on adamant runes come out on this piece
     */
    public static Judged judge(ServerLevel level, Mask piece, List<RuneWork.Placement> runes, List<Identifier> ready,
                               int capacity, boolean part, List<RuneWork.Placement> kept,
                               java.util.Set<Holder<Enchantment>> present, int coarser) {
        List<RuneWork.Placement> all = new ArrayList<>(kept);
        all.addAll(runes);
        if (present.size() + runes.size() > RuneWork.capacity(capacity, all)) return Judged.refused("too_many");
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Map<Holder<Enchantment>, Integer> levels = new java.util.LinkedHashMap<>();
        List<List<int[]>> laid = new ArrayList<>();
        for (RuneWork.Placement old : kept) {
            registry.get(ResourceKey.create(Registries.ENCHANTMENT, old.rune())).ifPresent(rune ->
                    laid.add(RuneWork.squares(RuneWork.shape(old.rune(), RuneWork.tier(rune), coarser), old)));
        }
        int[] powder = new int[RuneWork.Ink.values().length];
        int experience = 0;
        for (RuneWork.Placement placement : runes) {
            if (!ready.contains(placement.rune())) return Judged.refused("not_ready");
            Holder<Enchantment> rune = registry.get(ResourceKey.create(Registries.ENCHANTMENT, placement.rune())).orElse(null);
            if (rune == null) return Judged.refused("not_ready");
            // Vanilla's rule that this enchantment shuts out that one is not kept: protection goes
            // with fire protection, sharpness with smite. Only the same rune twice is refused.
            if (levels.containsKey(rune) || present.contains(rune)) return Judged.refused("clash");
            int tier = RuneWork.tier(rune);
            List<int[]> squares = RuneWork.squares(RuneWork.shape(placement.rune(), tier, coarser), placement);
            for (List<int[]> other : laid) if (RuneWork.overlap(squares, other)) return Judged.refused("overlapping");
            int written = RuneWork.written(piece, squares);
            int given = RuneWork.level(written, squares.size(),
                    RuneWork.highest(rune.value().getMaxLevel(), placement.ink()), part);
            if (given < 1) return Judged.refused("too_little");
            laid.add(squares);
            levels.put(rune, given);
            powder[placement.ink().ordinal()] += RuneWork.powder(written);
            experience += RuneWork.experienceFor(tier);
        }
        return new Judged(levels, powder, experience, null);
    }

    private static void write(ServerPlayer writer, BlockPos table, Mask shape, List<RuneWork.Placement> runes,
                              List<Integer> wiped) {
        Sitting sitting = sittings.get(writer.getUUID());
        ServerLevel level = (ServerLevel) writer.level();
        ItemStack piece = writer.getMainHandItem();
        if (sitting == null || !sitting.table().equals(table) || !level.getBlockState(table).is(Blocks.ENCHANTING_TABLE)
                || writer.distanceToSqr(table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5) > REACH * REACH
                || refusal(piece) != null || isPart(piece) != sitting.part()) {
            return;
        }
        // What stays on the piece: the runes that were not wiped off, and every enchantment but theirs.
        List<RuneWork.Placement> carried = carried(piece), kept = new ArrayList<>(), gone = new ArrayList<>();
        for (int i = 0; i < carried.size(); i++) (wiped.contains(i) ? gone : kept).add(carried.get(i));
        if (runes.isEmpty() && gone.isEmpty()) {
            writer.sendOverlayMessage(Component.translatable("message.hardwrought.runes.refused.nothing"));
            return;
        }
        java.util.Set<Holder<Enchantment>> present = new java.util.HashSet<>(piece.getEnchantments().keySet());
        present.removeIf(enchantment -> gone.stream().anyMatch(rune -> rune.rune().equals(id(enchantment))));
        Judged judged = judge(level, shape, runes, sitting.runes(), sitting.capacity(), sitting.part(), kept, present, sitting.coarser());
        if (!judged.allowed()) {
            writer.sendOverlayMessage(Component.translatable("message.hardwrought.runes.refused." + judged.refusal()));
            return;
        }
        if (!writer.isCreative()) {
            for (RuneWork.Ink ink : RuneWork.Ink.values()) {
                if (count(writer.getInventory(), powder(ink)) < judged.powder(ink)) {
                    writer.sendOverlayMessage(Component.translatable("message.hardwrought.runes.no_powder", judged.powder(ink),
                            new ItemStack(powder(ink)).getHoverName()));
                    return;
                }
            }
            if (writer.experienceLevel < judged.experience()) {
                writer.sendOverlayMessage(Component.translatable("message.hardwrought.runes.no_experience", judged.experience()));
                return;
            }
            for (RuneWork.Ink ink : RuneWork.Ink.values()) take(writer.getInventory(), powder(ink), judged.powder(ink));
            writer.giveExperienceLevels(-judged.experience());
        }
        if (!gone.isEmpty()) {
            net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(piece, enchantments ->
                    enchantments.removeIf(enchantment -> gone.stream().anyMatch(rune -> rune.rune().equals(id(enchantment)))));
        }
        judged.levels().forEach(piece::enchant);
        kept.addAll(runes);
        if (kept.isEmpty()) piece.remove(de.ipnats.hardwrought.core.registry.ModDataComponents.RUNES);
        else piece.set(de.ipnats.hardwrought.core.registry.ModDataComponents.RUNES, new RuneWork.Marks(kept));
        sittings.remove(writer.getUUID());
        level.playSound(null, table, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, level.getRandom().nextFloat() * 0.1f + 0.9f);
        writer.sendOverlayMessage(Component.translatable("message.hardwrought.runes.written"));
    }

    private static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) count += inventory.getItem(slot).getCount();
        }
        return count;
    }

    private static void take(Inventory inventory, Item item, int amount) {
        for (int slot = 0; slot < inventory.getContainerSize() && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) continue;
            int taken = Math.min(amount, stack.getCount());
            stack.shrink(taken);
            amount -= taken;
        }
    }
}
