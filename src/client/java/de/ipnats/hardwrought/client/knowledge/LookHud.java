package de.ipnats.hardwrought.client.knowledge;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.client.HardwroughtKeys;
import de.ipnats.hardwrought.core.networking.LookPayloads;
import de.ipnats.hardwrought.knowledge.KnowledgeLevel;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The panel at the top of the screen that says what the player is looking at.
 *
 * <p>It answers the way the compendium does. A block never broken and never carried is a black
 * shadow named with question marks; one that has been held has its name; only one that has been
 * worked with says whether the tool in hand will do. Creatures follow the same three steps, with a
 * fight for holding and a kill for working.
 *
 * <p>What is in front of the player the client can see for itself. How well the player knows it is
 * asked of the server, one subject at a time, and asked again every so often for as long as there
 * is still something to learn about it: knowledge only ever grows, so an answer of "studied" is
 * kept for the rest of the session and anything less is worth a second look.
 *
 * <p>A chest, a furnace or a machine also shows what is in it, under its name. That too is the
 * server's to tell, and it is told with the same reserve: a thing in the chest the player has never
 * held is a shadow in the panel, and a container still nameless shows nothing of its insides.
 */
public final class LookHud {
    private static final KeyMapping TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.look_hud", InputConstants.Type.KEYBOARD, InputConstants.KEY_NUMPAD1,
            HardwroughtKeys.CATEGORY));

    /** How long an answer short of "studied" is trusted before it is asked for again, in ticks. */
    private static final int ASK_AGAIN = 20;
    private static final int TOP = 6;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int ICON = 16;
    /** How often the insides of a container are asked for while it is looked at, in ticks. */
    private static final int LOOK_INSIDE = 10;
    private static final int SLOT = 18;
    private static final int SLOTS_PER_ROW = 9;

    /** One thing knowledge is kept about: an item, or the type of a creature. */
    private record Subject(boolean entity, Identifier id) { }

    /**
     * What the crosshair rests on.
     *
     * @param subject what to ask the server about, or null for things nobody has to find out
     * @param icon    the item drawn beside the name, empty for none
     * @param pos     the block looked at, null for an entity
     * @param state   that block's state, null for an entity
     * @param entity  the entity looked at, null for a block
     */
    private record Target(Subject subject, ItemStack icon, Component name, String namespace,
                          BlockPos pos, BlockState state, Entity entity) { }

    private static final Map<Subject, KnowledgeLevel> KNOWN = new HashMap<>();
    private static final Map<String, String> MOD_NAMES = new HashMap<>();
    private static Target target;
    private static Subject asked;
    private static int sinceAsked;
    private static boolean visible = true;
    /** What the server last said is inside a block; only drawn while that block is still the target. */
    private static LookPayloads.Contents contents;
    private static BlockPos askedInside;
    private static int sinceInside;

    private LookHud() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(LookPayloads.Answer.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    int level = Math.clamp(payload.level(), 0, KnowledgeLevel.values().length - 1);
                    KNOWN.put(new Subject(payload.entity(), payload.subject()), KnowledgeLevel.byOrdinal(level));
                }));
        ClientPlayNetworking.registerGlobalReceiver(LookPayloads.Contents.TYPE, (payload, context) ->
                context.client().execute(() -> contents = payload));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE.consumeClick()) visible = !visible;
            target = visible ? find(client) : null;
            ask();
            askInside(client);
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hardwrought.id("look_hud"),
                (graphics, delta) -> render(graphics));
    }

    private static void reset() {
        KNOWN.clear();
        target = null;
        asked = null;
        contents = null;
        askedInside = null;
    }

    // ---------------------------------------------------------------- what is looked at

    private static Target find(Minecraft client) {
        if (client.player == null || client.level == null || client.gui.screen() != null) return null;
        HitResult hit = client.hitResult;
        if (hit instanceof EntityHitResult entityHit) return of(client, entityHit.getEntity());
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        if (state.isAir()) return null;
        // Knowledge is kept by item, so a block is known as the item it is placed from. Fire, a
        // portal, a pool of water have none, and nothing a player could ever hold to learn them.
        Item item = state.getBlock().asItem();
        Subject subject = item == Items.AIR ? null : new Subject(false, BuiltInRegistries.ITEM.getKey(item));
        return new Target(subject, item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item),
                state.getBlock().getName(), BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace(),
                pos.immutable(), state, null);
    }

    private static Target of(Minecraft client, Entity entity) {
        if (entity == null || entity.isInvisibleTo(client.player)) return null;
        Identifier type = EntityType.getKey(entity.getType());
        // Another player is nobody's research subject.
        if (entity instanceof Player) {
            return new Target(null, ItemStack.EMPTY, entity.getDisplayName(), type.getNamespace(), null, null, entity);
        }
        if (entity instanceof LivingEntity) {
            return new Target(new Subject(true, type), ItemStack.EMPTY, entity.getDisplayName(),
                    type.getNamespace(), null, null, entity);
        }
        // A boat or a minecart is a thing rather than a creature, and is known as the item it is.
        ItemStack item = entity.getPickResult();
        if (item == null) item = ItemStack.EMPTY;
        Subject subject = item.isEmpty() ? null : new Subject(false, BuiltInRegistries.ITEM.getKey(item.getItem()));
        return new Target(subject, item, entity.getDisplayName(), type.getNamespace(), null, null, entity);
    }

    private static KnowledgeLevel level(Target looked) {
        if (looked.subject() == null) return KnowledgeLevel.STUDIED;
        return KNOWN.getOrDefault(looked.subject(), KnowledgeLevel.UNKNOWN);
    }

    private static void ask() {
        sinceAsked++;
        if (target == null || target.subject() == null) return;
        Subject subject = target.subject();
        if (KNOWN.get(subject) == KnowledgeLevel.STUDIED) return;
        if (subject.equals(asked) && sinceAsked < ASK_AGAIN) return;
        if (!ClientPlayNetworking.canSend(LookPayloads.Request.TYPE)) return;
        ClientPlayNetworking.send(new LookPayloads.Request(subject.entity(), subject.id()));
        asked = subject;
        sinceAsked = 0;
    }

    /**
     * Asks what the block looked at holds, and keeps asking while it is looked at: a furnace works
     * and a hopper fills whether or not anybody opens them.
     */
    private static void askInside(Minecraft client) {
        sinceInside++;
        Target looked = target;
        if (looked == null || looked.pos() == null || client.level == null || !level(looked).named()
                || !holdsItems(client.level.getBlockEntity(looked.pos())) && !holdsFluids(client.level, looked.pos())) {
            askedInside = null;
            return;
        }
        if (looked.pos().equals(askedInside) && sinceInside < LOOK_INSIDE) return;
        if (!ClientPlayNetworking.canSend(LookPayloads.Inside.TYPE)) return;
        ClientPlayNetworking.send(new LookPayloads.Inside(looked.pos()));
        askedInside = looked.pos();
        sinceInside = 0;
    }

    /** Whether a pipe or a gas line would find a liquid or a gas store there, from any side. */
    private static boolean holdsFluids(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (level.getBlockEntity(pos) == null) return false;
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            if (net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level, pos, side) != null) return true;
            if (de.ipnats.hardwrought.gasline.GasStorage.SIDED.find(level, pos, side) != null) return true;
        }
        return false;
    }

    private static boolean holdsItems(net.minecraft.world.level.block.entity.BlockEntity entity) {
        return entity instanceof net.minecraft.world.Container
                || entity instanceof de.ipnats.hardwrought.progression.DryingRackBlockEntity
                || entity instanceof de.ipnats.hardwrought.progression.KeptGridBlockEntity
                || entity instanceof de.ipnats.hardwrought.machinery.ItemPipeBlockEntity;
    }

    // ---------------------------------------------------------------- drawing

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        Target looked = target;
        if (looked == null || client.player == null || client.gui.screen() != null) return;
        // Until the first answer is in, the panel would flash question marks over everything.
        if (looked.subject() != null && !KNOWN.containsKey(looked.subject())) return;
        KnowledgeLevel level = level(looked);

        List<Component> lines = new ArrayList<>();
        if (!level.named()) {
            lines.add(ShadowItem.UNKNOWN_NAME);
            lines.add(Component.translatable("gui.hardwrought.compendium.undiscovered")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(looked.name());
            if (level.detailed()) details(client, looked, lines);
            else lines.add(Component.translatable("hud.hardwrought.look.unstudied").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.literal(modName(looked.namespace())).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        }

        boolean icon = !looked.icon().isEmpty();
        int textWidth = 0;
        for (Component line : lines) textWidth = Math.max(textWidth, client.font.width(line));
        int textX = icon ? ICON + PADDING : 0;
        int head = Math.max(icon ? ICON : 0, lines.size() * LINE_HEIGHT - 1);

        LookPayloads.Contents inside = level.named() && contents != null && contents.pos().equals(looked.pos())
                ? contents : null;
        int kinds = inside == null ? 0 : inside.stacks().size();
        int rows = (kinds + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
        boolean working = inside != null && inside.progress() >= 0;
        Component more = inside == null || inside.more() <= 0 ? null
                : Component.literal("+" + inside.more()).withStyle(ChatFormatting.DARK_GRAY);
        List<LookPayloads.Contents.Tank> tanks = inside == null ? List.of() : inside.tanks();
        List<String> tankTexts = new java.util.ArrayList<>();
        int tankWidth = 0;
        for (LookPayloads.Contents.Tank tank : tanks) {
            String text = tank.name().getString() + "  " + amount(tank);
            tankTexts.add(text);
            tankWidth = Math.max(tankWidth, TANK_ICON + client.font.width(text));
        }
        int insideHeight = (rows > 0 ? PADDING + rows * SLOT : 0) + (more != null ? LINE_HEIGHT : 0)
                + (tanks.isEmpty() ? 0 : PADDING + tanks.size() * TANK_ROW)
                + (working ? PADDING + 3 : 0);

        int width = Math.max(Math.max(textX + textWidth, Math.min(kinds, SLOTS_PER_ROW) * SLOT), tankWidth);
        if (working) width = Math.max(width, 4 * SLOT);
        int height = head + insideHeight;
        int x = (graphics.guiWidth() - width) / 2;
        int y = TOP + PADDING;

        graphics.fill(x - PADDING, y - PADDING, x + width + PADDING, y + height + PADDING, 0xA6080B0E);
        graphics.fill(x - PADDING + 1, y - PADDING + 1, x + width + PADDING - 1, y - PADDING + 2, 0x805D6972);
        if (icon) ShadowItem.render(graphics, looked.icon(), x, y + (head - ICON) / 2, level.named());
        for (int index = 0; index < lines.size(); index++) {
            graphics.text(client.font, lines.get(index), x + textX, y + index * LINE_HEIGHT, 0xFFE8EDF0, true);
        }

        if (inside != null) {
            int below = y + head;
            if (rows > 0) {
                below += PADDING;
                for (int index = 0; index < kinds; index++) {
                    held(graphics, client, inside.stacks().get(index),
                            x + index % SLOTS_PER_ROW * SLOT, below + index / SLOTS_PER_ROW * SLOT);
                }
                below += rows * SLOT;
            }
            if (more != null) {
                graphics.text(client.font, more, x, below + 1, 0xFFE8EDF0, true);
                below += LINE_HEIGHT;
            }
            if (!tanks.isEmpty()) {
                below += PADDING;
                for (int index = 0; index < tanks.size(); index++) {
                    tank(graphics, client, tanks.get(index), tankTexts.get(index), x, below, width);
                    below += TANK_ROW;
                }
            }
            if (working) {
                below += PADDING;
                graphics.fill(x, below, x + width, below + 3, 0xFF30383D);
                graphics.fill(x, below, x + Math.round(width * inside.progress()), below + 3, 0xFFE0A84A);
            }
        }

        // How far along the block is, drawn along the foot of the panel as it gives way.
        if (looked.state() != null && client.gameMode != null && client.gameMode.isDestroying()) {
            double progress = Math.clamp((client.gameMode.getDestroyStage() + 1) / 10.0, 0.0, 1.0);
            int full = width + PADDING * 2;
            graphics.fill(x - PADDING, y + height + PADDING - 1,
                    x - PADDING + (int) Math.round(full * progress), y + height + PADDING, 0xFFE8EDF0);
        }
    }

    /** One kind of thing in a container: itself and how many, or its shadow and how many. */
    private static void held(GuiGraphicsExtractor graphics, Minecraft client, LookPayloads.Contents.Held held,
                             int x, int y) {
        boolean known = held.level() != KnowledgeLevel.UNKNOWN.ordinal();
        String count = held.count() == 1 ? null : compact(held.count());
        if (known) {
            graphics.item(held.stack(), x + 1, y + 1);
            graphics.itemDecorations(client.font, held.stack(), x + 1, y + 1, count);
            return;
        }
        ShadowItem.render(graphics, held.stack(), x + 1, y + 1, false);
        if (count != null) {
            graphics.text(client.font, count, x + 18 - client.font.width(count), y + 10, 0xFFFFFFFF, true);
        }
    }

    /** One row per liquid or gas: what it is drawn as itself, how much of how much, and a bar of it. */
    private static final int TANK_ROW = 20;
    private static final int TANK_ICON = 20;

    private static void tank(GuiGraphicsExtractor graphics, Minecraft client, LookPayloads.Contents.Tank tank,
                             String text, int x, int y, int width) {
        var ingredient = de.ipnats.hardwrought.knowledge.FluidIcons.of(tank.icon());
        if (ingredient != null) FluidIconRenderer.render(graphics, ingredient, x, y);
        graphics.text(client.font, text, x + TANK_ICON, y + 1, 0xFFE8EDF0, true);
        int barX = x + TANK_ICON, barWidth = width - TANK_ICON;
        double ratio = tank.capacity() <= 0 ? 0 : Math.clamp(tank.amount() / (double) tank.capacity(), 0.0, 1.0);
        graphics.fill(barX, y + 11, barX + barWidth, y + 14, 0xFF30383D);
        graphics.fill(barX, y + 11, barX + (int) Math.round(barWidth * ratio), y + 14, tank.gas() ? 0xFFD8C35A : 0xFF4A90D8);
    }

    /** How much of how much: millibuckets for a liquid, units for a gas. */
    private static String amount(LookPayloads.Contents.Tank tank) {
        return Component.translatable(tank.gas() ? "hud.hardwrought.look.gas_amount" : "hud.hardwrought.look.fluid_amount",
                String.format(Locale.ROOT, "%,d", tank.amount()), String.format(Locale.ROOT, "%,d", tank.capacity())).getString();
    }

    /** A number that still fits in the corner of a slot once a chest is full of one thing. */
    private static String compact(int count) {
        if (count < 1000) return Integer.toString(count);
        return count < 10000 ? String.format(Locale.ROOT, "%.1fk", count / 1000.0) : count / 1000 + "k";
    }

    /** What only a studied thing tells: whether this tool will do, or how much life is left in it. */
    private static void details(Minecraft client, Target looked, List<Component> lines) {
        if (looked.state() != null) {
            BlockState state = looked.state();
            if (state.getDestroySpeed(client.level, looked.pos()) < 0 || !state.requiresCorrectToolForDrops()) return;
            lines.add(client.player.hasCorrectToolForDrops(state)
                    ? Component.literal("✔ ").append(Component.translatable("hud.hardwrought.look.harvestable"))
                            .withStyle(ChatFormatting.GREEN)
                    : Component.literal("✘ ").append(Component.translatable("hud.hardwrought.look.wrong_tool"))
                            .withStyle(ChatFormatting.RED));
        } else if (looked.entity() instanceof LivingEntity living) {
            lines.add(Component.literal(String.format(Locale.ROOT, "❤ %.0f / %.0f",
                    Math.ceil(living.getHealth()), Math.ceil(living.getMaxHealth()))).withStyle(ChatFormatting.RED));
        }
    }

    private static String modName(String namespace) {
        return MOD_NAMES.computeIfAbsent(namespace, id -> FabricLoader.getInstance().getModContainer(id)
                .map(mod -> mod.getMetadata().getName()).orElse(id));
    }
}
