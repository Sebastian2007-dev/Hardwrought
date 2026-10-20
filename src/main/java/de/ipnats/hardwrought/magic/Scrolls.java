package de.ipnats.hardwrought.magic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrolls (full mechanics specification §86, docs/magic-roadmap.md stage M3): a spell drawn once with
 * care at a Mage Table and bound to parchment in rune ink, to be read later in a moment. The scroll
 * keeps the drawing and how well it was drawn — accuracy, stability, power — and reading it casts the
 * drawing with that quality, from the hotbar, without a wand. It is used up.
 *
 * <p>Binding takes a sheet of parchment and an ink: rune ink, or void ink, which binds it steadier.
 */
public final class Scrolls {
    /** What void ink adds to a scroll's stability. */
    public static final double VOID_INK_STEADINESS = 0.05;
    /** Ticks between one scroll and the next. */
    public static final int COOLDOWN = 20;

    /** One stroke as the scroll keeps it. */
    public record Line(List<Float> xs, List<Float> ys, int millis) {
        static final Codec<Line> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.listOf().fieldOf("x").forGetter(Line::xs),
                Codec.FLOAT.listOf().fieldOf("y").forGetter(Line::ys),
                Codec.INT.optionalFieldOf("ms", 0).forGetter(Line::millis)).apply(instance, Line::new));

        CastSpellPayload.Stroke stroke() {
            float[] x = new float[xs.size()], y = new float[ys.size()];
            for (int i = 0; i < x.length; i++) x[i] = xs.get(i);
            for (int i = 0; i < y.length; i++) y[i] = ys.get(i);
            return new CastSpellPayload.Stroke(x, y, millis);
        }

        static Line of(CastSpellPayload.Stroke stroke) {
            List<Float> x = new ArrayList<>(), y = new ArrayList<>();
            for (float v : stroke.xs()) x.add(v);
            for (float v : stroke.ys()) y.add(v);
            return new Line(x, y, stroke.millis());
        }
    }

    /** How one spell on the scroll was judged when it was bound. */
    public record Judged(double accuracy, double stability, double power, int outcome, int worst) {
        static final Codec<Judged> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("accuracy").forGetter(Judged::accuracy),
                Codec.DOUBLE.fieldOf("stability").forGetter(Judged::stability),
                Codec.DOUBLE.fieldOf("power").forGetter(Judged::power),
                Codec.INT.fieldOf("outcome").forGetter(Judged::outcome),
                Codec.INT.optionalFieldOf("worst", 0).forGetter(Judged::worst)).apply(instance, Judged::new));

        CastQuality quality() {
            CastQuality.Outcome[] outcomes = CastQuality.Outcome.values();
            return new CastQuality(accuracy, stability, power, outcomes[Math.clamp(outcome, 0, outcomes.length - 1)], worst);
        }
    }

    /** What is written on a scroll. */
    public record Writing(List<Line> lines, float size, List<Judged> judged, Component name) {
        static final Codec<Writing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Line.CODEC.listOf().fieldOf("lines").forGetter(Writing::lines),
                Codec.FLOAT.fieldOf("size").forGetter(Writing::size),
                Judged.CODEC.listOf().fieldOf("judged").forGetter(Writing::judged),
                ComponentSerialization.CODEC.fieldOf("name").forGetter(Writing::name)).apply(instance, Writing::new));

        public List<CastSpellPayload.Stroke> strokes() {
            return lines.stream().map(Line::stroke).toList();
        }

        public List<CastQuality> qualities() {
            return judged.stream().map(Judged::quality).toList();
        }
    }

    public static final DataComponentType<Writing> WRITING = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("scroll"), DataComponentType.<Writing>builder().persistent(Writing.CODEC).build());

    public static final Item PARCHMENT = item("parchment", Item::new, 64);
    public static final Item RUNE_INK = item("rune_ink", Item::new, 16);
    public static final Item SCROLL = item("scroll", ScrollItem::new, 16);

    private Scrolls() { }

    public static void initialize() {
        // A scroll is bound at the Mage Table, not crafted.
        de.ipnats.hardwrought.knowledge.LootSources.inWorld(() -> SCROLL);
    }

    public static List<Item> items() {
        return List.of(PARCHMENT, RUNE_INK, SCROLL);
    }

    /** Whether the player has a sheet of parchment and ink to bind with. */
    public static boolean hasMaterials(ServerPlayer player) {
        if (player.getAbilities().instabuild) return true;
        Inventory inventory = player.getInventory();
        return slotOf(inventory, PARCHMENT) >= 0 && (slotOf(inventory, VoidCrystal.INK) >= 0 || slotOf(inventory, RUNE_INK) >= 0);
    }

    /** Whether the ink used now is void ink. */
    private static boolean voidInk(ServerPlayer player) {
        return slotOf(player.getInventory(), VoidCrystal.INK) >= 0;
    }

    /** Uses one parchment and one ink, void ink first. */
    public static void useMaterials(ServerPlayer player) {
        if (player.getAbilities().instabuild) return;
        Inventory inventory = player.getInventory();
        int parchment = slotOf(inventory, PARCHMENT);
        if (parchment >= 0) inventory.getItem(parchment).shrink(1);
        int ink = slotOf(inventory, VoidCrystal.INK);
        if (ink < 0) ink = slotOf(inventory, RUNE_INK);
        if (ink >= 0) inventory.getItem(ink).shrink(1);
    }

    private static int slotOf(Inventory inventory, Item item) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(item)) return slot;
        return -1;
    }

    /** A scroll of this drawing, as it was judged; void ink in hand binds it steadier. */
    public static ItemStack write(List<CastSpellPayload.Stroke> strokes, float size, List<CastQuality> qualities, List<Component> names,
                                  boolean voidInk) {
        List<Judged> judged = new ArrayList<>();
        for (CastQuality q : qualities) {
            double stability = Math.min(1.0, q.stability() + (voidInk ? VOID_INK_STEADINESS : 0.0));
            judged.add(new Judged(q.accuracy(), stability, q.power(), q.outcome().ordinal(), q.worst()));
        }
        Component name = names.isEmpty() ? Component.empty() : names.size() == 1 ? names.getFirst()
                : names.getFirst().copy().append(Component.literal(" + …"));
        ItemStack scroll = new ItemStack(SCROLL);
        scroll.set(WRITING, new Writing(strokes.stream().map(Line::of).toList(), size, judged, name));
        return scroll;
    }

    /** Binds the drawing into a scroll and hands it to the player. Called before the materials are used. */
    static void give(ServerPlayer player, List<CastSpellPayload.Stroke> strokes, float size, List<CastQuality> qualities,
                     List<Component> names) {
        ItemStack scroll = write(strokes, size, qualities, names, voidInk(player));
        if (!player.getInventory().add(scroll)) player.spawnAtLocation(player.level(), scroll);
    }

    private static <T extends Item> T item(String name, java.util.function.Function<Item.Properties, T> factory, int stack) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().stacksTo(stack).setId(key)));
    }
}
