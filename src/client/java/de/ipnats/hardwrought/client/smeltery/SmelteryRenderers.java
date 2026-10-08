package de.ipnats.hardwrought.client.smeltery;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.ipnats.hardwrought.smeltery.CastingTableBlockEntity;
import de.ipnats.hardwrought.smeltery.FaucetBlock;
import de.ipnats.hardwrought.smeltery.FaucetBlockEntity;
import de.ipnats.hardwrought.smeltery.MoltenMetalBlock;
import de.ipnats.hardwrought.smeltery.MoltenMetals;
import de.ipnats.hardwrought.smeltery.SmelteryControllerBlockEntity;
import de.ipnats.hardwrought.smeltery.SmelteryTankBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Molten metal in the world: the layers in a smeltery's tank, the stream from a faucet, the pool in a
 * casting table. Each is the molten-metal block of its metal, stretched to the size it should have.
 */
public final class SmelteryRenderers {
    private SmelteryRenderers() { }

    /** A box of one molten metal, relative to the renderer's own block. */
    record Box(MovingBlockRenderState block, float x, float y, float z, float w, float h, float d) { }

    static MovingBlockRenderState molten(Level level, BlockPos pos, String material) {
        MovingBlockRenderState state = new MovingBlockRenderState();
        state.blockPos = pos;
        state.randomSeedPos = pos;
        state.blockState = MoltenMetalBlock.of(material);
        if (level instanceof ClientLevel client) {
            state.biome = client.getBiome(pos);
            state.cardinalLighting = client.cardinalLighting();
            state.lightEngine = client.getLightEngine();
        }
        return state;
    }

    static void submit(List<Box> boxes, PoseStack pose, SubmitNodeCollector collector) {
        for (Box box : boxes) {
            if (box.block.lightEngine == null || box.h <= 0) continue;
            pose.pushPose();
            pose.translate(box.x, box.y, box.z);
            pose.scale(box.w, box.h, box.d);
            collector.submitMovingBlock(pose, box.block, 0);
            pose.popPose();
        }
    }

    public static class BoxState extends BlockEntityRenderState {
        final List<Box> boxes = new ArrayList<>();
        final List<ItemStackRenderState> items = new ArrayList<>();
        final List<float[]> itemAt = new ArrayList<>();
    }

    /** The tank of a smeltery: its metals in layers, lowest first, filling it as they fill it. */
    public static class Controller implements BlockEntityRenderer<SmelteryControllerBlockEntity, BoxState> {
        public Controller(BlockEntityRendererProvider.Context context) { }

        @Override
        public BoxState createRenderState() {
            return new BoxState();
        }

        @Override
        public boolean shouldRenderOffScreen() {
            return true;
        }

        @Override
        public void extractRenderState(SmelteryControllerBlockEntity smeltery, BoxState state, float partialTick, Vec3 camera,
                                       ModelFeatureRenderer.CrumblingOverlay crumbling) {
            BlockEntityRenderer.super.extractRenderState(smeltery, state, partialTick, camera, crumbling);
            state.boxes.clear();
            BlockPos min = smeltery.tankMin(), max = smeltery.tankMax();
            if (min == null || max == null || smeltery.fluids().isEmpty()) return;
            BlockPos origin = smeltery.getBlockPos();
            float width = max.getX() - min.getX() + 1, depth = max.getZ() - min.getZ() + 1;
            float height = max.getY() - min.getY() + 1;
            double capacity = width * depth * height * SmelteryControllerBlockEntity.PER_BLOCK;
            float y = min.getY() - origin.getY() + 0.001f;
            for (Map.Entry<String, Integer> fluid : smeltery.fluids().entrySet()) {
                float h = (float) (fluid.getValue() / capacity * height);
                state.boxes.add(new Box(molten(smeltery.getLevel(), min, fluid.getKey()), min.getX() - origin.getX() + 0.001f, y,
                        min.getZ() - origin.getZ() + 0.001f, width - 0.002f, h, depth - 0.002f));
                y += h;
            }
        }

        @Override
        public void submit(BoxState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            SmelteryRenderers.submit(state.boxes, pose, collector);
        }
    }

    /** The stream from an open faucet down into the table. */
    public static class Faucet implements BlockEntityRenderer<FaucetBlockEntity, BoxState> {
        public Faucet(BlockEntityRendererProvider.Context context) { }

        @Override
        public BoxState createRenderState() {
            return new BoxState();
        }

        @Override
        public void extractRenderState(FaucetBlockEntity faucet, BoxState state, float partialTick, Vec3 camera,
                                       ModelFeatureRenderer.CrumblingOverlay crumbling) {
            BlockEntityRenderer.super.extractRenderState(faucet, state, partialTick, camera, crumbling);
            state.boxes.clear();
            if (faucet.pouring().isEmpty()) return;
            Direction facing = faucet.getBlockState().getValue(FaucetBlock.FACING);
            float cx = 0.5f - facing.getStepX() * 0.25f, cz = 0.5f - facing.getStepZ() * 0.25f;
            state.boxes.add(new Box(molten(faucet.getLevel(), faucet.getBlockPos(), faucet.pouring()),
                    cx - 0.0625f, -1 / 16f, cz - 0.0625f, 0.125f, 6 / 16f, 0.125f));
        }

        @Override
        public void submit(BoxState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            SmelteryRenderers.submit(state.boxes, pose, collector);
        }
    }

    /**
     * A tank of lava in the wall: the lava behind its window, as high as the tank is full. Towards a
     * joined tank there is no wall, so the lava runs to the edge and meets the lava next door.
     */
    public static class Tank implements BlockEntityRenderer<SmelteryTankBlockEntity, BoxState> {
        public Tank(BlockEntityRendererProvider.Context context) { }

        @Override
        public BoxState createRenderState() {
            return new BoxState();
        }

        @Override
        public void extractRenderState(SmelteryTankBlockEntity tank, BoxState state, float partialTick, Vec3 camera,
                                       ModelFeatureRenderer.CrumblingOverlay crumbling) {
            BlockEntityRenderer.super.extractRenderState(tank, state, partialTick, camera, crumbling);
            state.boxes.clear();
            if (tank.lava() <= 0) return;
            var block = tank.getBlockState();
            float wall = 1f / 16f;
            float x0 = joined(block, Direction.WEST) ? 0 : wall, x1 = joined(block, Direction.EAST) ? 1 : 1 - wall;
            float z0 = joined(block, Direction.NORTH) ? 0 : wall, z1 = joined(block, Direction.SOUTH) ? 1 : 1 - wall;
            float y0 = joined(block, Direction.DOWN) ? 0 : wall, y1 = joined(block, Direction.UP) ? 1 : 1 - wall;
            float h = (y1 - y0) * tank.lava() / SmelteryTankBlockEntity.CAPACITY;
            state.boxes.add(new Box(molten(tank.getLevel(), tank.getBlockPos(), "lava"), x0, y0, z0, x1 - x0, h, z1 - z0));
        }

        private static boolean joined(net.minecraft.world.level.block.state.BlockState block, Direction side) {
            var property = net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(side);
            return block.hasProperty(property) && block.getValue(property);
        }

        @Override
        public void submit(BoxState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            SmelteryRenderers.submit(state.boxes, pose, collector);
        }
    }

    /** A casting table: its cast lying on it, the metal rising in the cast, and the finished bar. */
    public static class Table implements BlockEntityRenderer<CastingTableBlockEntity, BoxState> {
        private final ItemModelResolver resolver;

        public Table(BlockEntityRendererProvider.Context context) {
            this.resolver = context.itemModelResolver();
        }

        @Override
        public BoxState createRenderState() {
            return new BoxState();
        }

        @Override
        public void extractRenderState(CastingTableBlockEntity table, BoxState state, float partialTick, Vec3 camera,
                                       ModelFeatureRenderer.CrumblingOverlay crumbling) {
            BlockEntityRenderer.super.extractRenderState(table, state, partialTick, camera, crumbling);
            state.boxes.clear();
            state.items.clear();
            state.itemAt.clear();
            int seed = (int) table.getBlockPos().asLong();
            add(state, table.cast(), table, seed, 15.05f / 16f);
            if (!table.metal().isEmpty() && table.amount() > 0) {
                float h = 0.06f * table.amount() / MoltenMetals.INGOT;
                state.boxes.add(new Box(molten(table.getLevel(), table.getBlockPos(), table.metal()),
                        0.3f, 15.1f / 16f, 0.36f, 0.4f, h, 0.28f));
            }
            add(state, table.result(), table, seed + 1, 15.2f / 16f);
        }

        private void add(BoxState state, ItemStack stack, CastingTableBlockEntity table, int seed, float y) {
            if (stack.isEmpty()) return;
            ItemStackRenderState item = new ItemStackRenderState();
            resolver.updateForTopItem(item, stack, ItemDisplayContext.FIXED, table.getLevel(), null, seed);
            state.items.add(item);
            state.itemAt.add(new float[] {y});
        }

        @Override
        public void submit(BoxState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            for (int i = 0; i < state.items.size(); i++) {
                pose.pushPose();
                pose.translate(0.5f, state.itemAt.get(i)[0], 0.5f);
                // Item models face south in-hand.  Turning that face upward makes the cast cavity
                // and the ingot texture visible instead of showing their backs through the table.
                pose.rotateDegrees(Axis.XP, -90f);
                pose.scale(0.75f, 0.75f, 0.75f);
                state.items.get(i).submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
            SmelteryRenderers.submit(state.boxes, pose, collector);
        }
    }
}
