package de.ipnats.hardwrought.client.knowledge;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import de.ipnats.hardwrought.client.HardwroughtKeys;
import de.ipnats.hardwrought.knowledge.Multiblocks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A multiblock laid out in the world as ghost blocks, to build along (opened from its view in the
 * compendium). First it follows where the player looks: a right click sets it down there, a left
 * click lets it go. Set down, where a block is still missing its ghost shows; where the wrong block
 * stands the ghost shows red over it; where the right one stands there is nothing. All ghosts are
 * equally see-through. Once all of it stands it is gone, with a chime.
 *
 * <p>{@link #KEY} turns it a quarter round; set down, with sneak held, takes it away. What the keys do
 * is written above the hotbar for as long as it is there.
 */
public final class MultiblockProjection {
    public static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.multiblock_projection", InputConstants.Type.KEYBOARD, InputConstants.KEY_G,
            HardwroughtKeys.CATEGORY));
    private static final int FULL_BRIGHT = 0xF000F0;
    /** How see-through every ghost is, missing or wrong alike. */
    private static final float GHOST_ALPHA = 0.45f;
    private static final long CYCLE_MILLIS = 1000L;

    /** A position of the structure as it stands in the world, and how it is doing. */
    private record Spot(BlockPos pos, char symbol, int layer, State state) { }

    private enum State { MISSING, WRONG, RIGHT }

    private static Multiblocks.Multiblock shown;
    private static Set<Identifier> known = Set.of();
    private static BlockPos anchor;
    private static int turns;
    /** Following the look, not yet set down; and the quarter turns asked for while it does. */
    private static boolean placing;
    private static int extraTurns;
    /** A left click let it go: the held button breaks nothing until it is let up. */
    private static boolean cancelHeld;
    private static List<Spot> spots = List.of();
    /** Where the camera is this frame: the world is drawn around it. */
    private static Vec3 camera = Vec3.ZERO;

    private MultiblockProjection() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(MultiblockProjection::tick);
        LevelExtractionEvents.END_EXTRACTION.register(context -> camera = context.camera().position());
        LevelRenderEvents.COLLECT_SUBMITS.register(MultiblockProjection::submit);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
                de.ipnats.hardwrought.Hardwrought.id("multiblock_projection"), (graphics, delta) -> controls(graphics));
    }

    /** Takes the structure up to place: it follows the look until set down or let go. */
    public static void start(Multiblocks.Multiblock multiblock, Set<Identifier> studied) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        shown = multiblock;
        known = Set.copyOf(studied);
        placing = true;
        extraTurns = 0;
        follow(client);
        survey(client.level);
    }

    /** Where the player looks, turned to face them: the near edge of the structure at the block looked at. */
    private static void follow(Minecraft client) {
        Direction facing = client.player.getDirection();
        int facingTurns = switch (facing) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            default -> 3;
        };
        turns = (facingTurns + extraTurns) % 4;
        BlockPos looked;
        if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            looked = hit.getBlockPos().relative(hit.getDirection());
        } else {
            looked = BlockPos.containing(client.player.position().add(client.player.getLookAngle().multiply(4, 0, 4)));
        }
        int reach = (turns % 2 == 0 ? shown.depth() : shown.width()) / 2;
        anchor = looked.relative(facing, reach);
    }

    /** Whether it is still following the look, waiting to be set down. */
    public static boolean placing() {
        return shown != null && placing;
    }

    /** A right click: set down where it is. */
    public static void confirm() {
        placing = false;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 0.7f, 1.2f, false);
        }
    }

    /** A left click: let go. */
    public static void cancel() {
        clear();
        cancelHeld = true;
    }

    /** Whether the left button that let it go is still held, and should break nothing. */
    public static boolean justCancelled() {
        if (cancelHeld && !Minecraft.getInstance().options.keyAttack.isDown()) cancelHeld = false;
        return cancelHeld;
    }

    /** Above the hotbar, as the game says such things. */
    private static void say(Component message) {
        Minecraft.getInstance().gui.hud.setOverlayMessage(message, false);
    }

    public static void clear() {
        shown = null;
        placing = false;
        spots = List.of();
    }

    public static boolean active() {
        return shown != null;
    }

    private static void tick(Minecraft client) {
        while (KEY.consumeClick()) {
            if (shown == null || client.player == null) continue;
            if (placing) {
                extraTurns = (extraTurns + 1) % 4;
            } else if (client.player.isShiftKeyDown()) {
                clear();
                say(Component.translatable("message.hardwrought.projection.removed")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                turns = (turns + 1) % 4;
            }
        }
        if (shown == null || client.level == null || client.player == null) return;
        if (placing) {
            follow(client);
            survey(client.level);
            return;
        }
        survey(client.level);
        if (!spots.isEmpty() && spots.stream().allMatch(spot -> spot.state() == State.RIGHT)) finish(client);
    }

    /** Where every block of the structure goes now, and what stands there. */
    private static void survey(Level level) {
        List<Spot> next = new ArrayList<>();
        for (int y = 0; y < shown.height(); y++) {
            for (int z = 0; z < shown.depth(); z++) {
                for (int x = 0; x < shown.width(); x++) {
                    char symbol = shown.at(x, y, z);
                    if (symbol == ' ') continue;
                    BlockPos pos = place(x, y, z);
                    BlockState there = level.getBlockState(pos);
                    Identifier standing = BuiltInRegistries.BLOCK.getKey(there.getBlock());
                    State state = shown.legend().get(symbol).contains(standing) ? State.RIGHT
                            : there.isAir() || there.canBeReplaced() ? State.MISSING : State.WRONG;
                    next.add(new Spot(pos, symbol, y, state));
                }
            }
        }
        spots = next;
    }

    /** The world position of a structure position, the structure centred on the anchor and turned. */
    private static BlockPos place(int x, int y, int z) {
        int dx = x - shown.width() / 2, dz = z - shown.depth() / 2;
        for (int i = 0; i < turns; i++) {
            int t = dx;
            dx = -dz;
            dz = t;
        }
        return anchor.offset(dx, y, dz);
    }

    private static void finish(Minecraft client) {
        for (Spot spot : spots) {
            Vec3 at = Vec3.atCenterOf(spot.pos());
            client.level.addParticle(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.5, at.z, 0, 0.05, 0);
        }
        client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f, false);
        say(Component.translatable("message.hardwrought.projection.done",
                MultiblockScreen.name(shown)).withStyle(ChatFormatting.GREEN));
        clear();
    }

    /** What the keys do, written above the hotbar for as long as there is a projection. */
    private static void controls(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        if (shown == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() != null) return;
        Component line = placing
                ? Component.translatable("hud.hardwrought.projection.placing", KEY.getTranslatedKeyMessage())
                : Component.translatable("hud.hardwrought.projection.placed", KEY.getTranslatedKeyMessage());
        int x = graphics.guiWidth() / 2, y = graphics.guiHeight() - 72;
        graphics.centeredText(client.font, MultiblockScreen.name(shown), x, y - 11, 0xFFFFFFFF);
        graphics.centeredText(client.font, line, x, y, 0xFFC8C8C8);
    }

    /** The block a symbol shows as: one the player knows, cycling where several are. */
    private static Block ghostOf(char symbol) {
        List<Identifier> choices = new ArrayList<>();
        for (Identifier id : shown.legend().getOrDefault(symbol, List.of())) {
            if (known.contains(id)) choices.add(id);
        }
        if (choices.isEmpty()) choices.addAll(shown.legend().getOrDefault(symbol, List.of()));
        if (choices.isEmpty()) return null;
        return BuiltInRegistries.BLOCK.getValue(choices.get((int) ((System.currentTimeMillis() / CYCLE_MILLIS) % choices.size())));
    }

    private static void submit(LevelRenderContext context) {
        if (shown == null || spots.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        PoseStack pose = context.poseStack();
        RandomSource random = RandomSource.create(42L);
        var collector = context.submitNodeCollector().order(1);
        for (Spot spot : spots) {
            if (spot.state() == State.RIGHT) continue;
            Block block = ghostOf(spot.symbol());
            if (block == null) continue;
            int color = ((int) (GHOST_ALPHA * 255) << 24) | (spot.state() == State.WRONG ? 0xFF4040 : 0xFFFFFF);
            List<BlockStateModelPart> parts = new ArrayList<>();
            random.setSeed(42L);
            client.getModelManager().getBlockStateModelSet().get(block.defaultBlockState()).collectParts(random, parts);
            QuadInstance instance = new QuadInstance();
            instance.setColor(color);
            instance.setLightCoords(FULL_BRIGHT);
            instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
            pose.pushPose();
            pose.translate(spot.pos().getX() - camera.x, spot.pos().getY() - camera.y, spot.pos().getZ() - camera.z);
            // A wrong block is drawn a hair larger, over the one that stands there.
            if (spot.state() == State.WRONG) {
                pose.translate(0.5, 0.5, 0.5);
                pose.scale(1.02f, 1.02f, 1.02f);
                pose.translate(-0.5, -0.5, -0.5);
            }
            collector.submitCustomGeometry(pose, Sheets.translucentBlockItemSheet(), (at, vertices) -> {
                for (BlockStateModelPart part : parts) {
                    for (Direction side : Direction.values()) {
                        for (BakedQuad quad : part.getQuads(side)) vertices.putBakedQuad(at, quad, instance);
                    }
                    for (BakedQuad quad : part.getQuads(null)) vertices.putBakedQuad(at, quad, instance);
                }
            });
            pose.popPose();
        }
    }
}
