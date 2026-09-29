package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.networking.OreDrillPayloads;
import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.geology.DrillTier;
import de.ipnats.hardwrought.geology.DrillYield;
import de.ipnats.hardwrought.geology.Geology;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The ore drill: a drill head with a frame round it, three by three and two high.
 *
 * <pre>
 *   layer 2   F F F        F  a drill frame, of any of the five metals
 *             F F F        D  the drill head, driven by a line from any side
 *             F F F        &gt;  the chute, on the side the head faces
 *   layer 1   F F F
 *             F D F &gt;
 *             F F F
 * </pre>
 *
 * <p>The frame's weakest block is the drill's tier (see {@link DrillTier}), and the tier decides which
 * of the ores in the chunk under it the drill can reach — the chunk's own mix, see {@link DrillYield}.
 * It works at a rate, not through an amount: one piece of ore every so often for as long as it is
 * turned, faster when turned faster, faster over a body of ore, and for ever.
 *
 * <p>It keeps nothing. Every piece goes out through the chute on the side the head faces: into
 * whatever takes items just outside it — a chest, a hopper, a pipe of any mod that offers item
 * storage — or, where nothing is there, onto the ground. A full chest stops it until there is room.
 *
 * <p>It is worked by rotation, from a line coming into any side of the head. An electric motor is one
 * more thing that turns a line, so a drill runs on power the moment there is a motor to drive it.
 */
public class OreDrillBlockEntity extends BlockEntity implements KineticHolder {
    /** Blocks in a complete frame: eight round the head, nine above it. */
    public static final int FRAME_BLOCKS = 17;
    /** The speed the drill is built for. */
    public static final float RATED_SPEED = 16.0f;
    /** Strength per turn per minute, rising with the tier: 160 at 16 RPM for bronze, a hand crank's worth. */
    public static final float BASE_IMPACT = 6.0f;
    public static final float IMPACT_PER_TIER = 4.0f;
    /** How often the frame is looked over, in ticks. */
    private static final int CHECK_INTERVAL = 40;
    /** How often a piece held up by a full chest tries again, in ticks. */
    private static final int RETRY_INTERVAL = 10;

    /** What the drill is doing, as its screen says it. */
    public enum Status {
        INCOMPLETE, BARREN, STILL, WORKING, BLOCKED
    }

    private float speed;
    private float progress;
    /** A piece brought up that the chest outside had no room for; nothing more comes up until it is out. */
    private final List<ItemStack> held = new ArrayList<>();
    /** The tier of the frame as last found, or null where the frame is not complete. */
    private DrillTier tier;
    /** Frame blocks missing when last looked. */
    private int missing = -1;
    private DrillYield yield;

    public OreDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORE_DRILL, pos, state);
    }

    /** Where the frame of a drill head at this position has to be: eight round it, nine above. */
    public static List<BlockPos> framePositions(BlockPos head) {
        List<BlockPos> positions = new ArrayList<>(17);
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dy == 0 && dx == 0 && dz == 0) continue;
                    positions.add(head.offset(dx, dy, dz));
                }
            }
        }
        return positions;
    }

    /** Where the head of the drill a frame block belongs to is, from the frame block and its part number. */
    public static BlockPos headOf(BlockPos frame, int part) {
        BlockPos offset = framePositions(BlockPos.ZERO).get(part - 1);
        return frame.subtract(offset);
    }

    /** The side the drill puts its ore out by: the way its head faces. */
    public static Direction outputSide(BlockState head) {
        return head.hasProperty(OreDrillBlock.FACING) ? head.getValue(OreDrillBlock.FACING) : Direction.NORTH;
    }

    /**
     * Draws the drill whole or takes it apart again: every frame block round the head is told its part
     * of the finished drill, or that it is a loose frame once more, the one in front of the head that it
     * carries the chute, and the head whether it is closed in.
     */
    public static void shape(Level level, BlockPos head, boolean formed) {
        BlockState headState = level.getBlockState(head);
        BlockPos chute = head.relative(outputSide(headState));
        List<BlockPos> frame = framePositions(head);
        for (int i = 0; i < frame.size(); i++) {
            BlockPos pos = frame.get(i);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DrillFrameBlock)) continue;
            int part = formed ? i + 1 : 0;
            boolean output = formed && pos.equals(chute);
            if (state.getValue(DrillFrameBlock.PART) != part || state.getValue(DrillFrameBlock.OUTPUT) != output) {
                level.setBlock(pos, state.setValue(DrillFrameBlock.PART, part).setValue(DrillFrameBlock.OUTPUT, output),
                        Block.UPDATE_CLIENTS);
            }
        }
        if (headState.getBlock() instanceof OreDrillBlock && headState.getValue(OreDrillBlock.FORMED) != formed) {
            level.setBlock(head, headState.setValue(OreDrillBlock.FORMED, formed), Block.UPDATE_CLIENTS);
        }
    }

    /** The tier a complete frame gives, or null where a block of it is missing. Counts what is missing. */
    public static DrillTier frameTier(Level level, BlockPos head, int[] missingOut) {
        DrillTier weakest = null;
        int missing = 0;
        for (BlockPos pos : framePositions(head)) {
            if (level.getBlockState(pos).getBlock() instanceof DrillFrameBlock frame) {
                if (weakest == null || frame.tier().level() < weakest.level()) weakest = frame.tier();
            } else {
                missing++;
            }
        }
        if (missingOut != null && missingOut.length > 0) missingOut[0] = missing;
        return missing == 0 ? weakest : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OreDrillBlockEntity drill) {
        if (!(level instanceof ServerLevel server)) return;
        if (drill.missing < 0 || level.getGameTime() % CHECK_INTERVAL == 0) drill.lookOverFrame(server, pos);
        if (!drill.held.isEmpty() && level.getGameTime() % RETRY_INTERVAL == 0) {
            drill.putOutAll(server, pos, List.copyOf(drill.held));
            drill.setChanged();
        }
        boolean working = drill.status() == Status.WORKING;
        if (working) {
            drill.progress += Math.abs(drill.speed) / RATED_SPEED;
            if (drill.progress >= drill.yield.intervalTicks()) {
                drill.progress = 0;
                drill.bringUp(server, pos);
            }
            drill.setChanged();
        }
        if (state.getValue(OreDrillBlock.RUNNING) != working) {
            level.setBlock(pos, state.setValue(OreDrillBlock.RUNNING, working), Block.UPDATE_CLIENTS);
        }
    }

    private void lookOverFrame(ServerLevel level, BlockPos pos) {
        int[] counted = new int[1];
        DrillTier found = frameTier(level, pos, counted);
        missing = counted[0];
        boolean wasFormed = getBlockState().getValue(OreDrillBlock.FORMED);
        shape(level, pos, found != null);
        if (found != tier || wasFormed != (found != null)) {
            tier = found;
            // A heavier frame is a heavier load on the line.
            Kinetics.update(level, pos);
            if (wasFormed != (found != null)) {
                // The frame carries the drive now, or no longer: lines that meet it from outside change too.
                for (BlockPos frame : framePositions(pos)) Kinetics.update(level, frame);
            }
        }
        yield = tier == null ? null : DrillYield.forChunk(level.getSeed(), Geology.profiles(level.getServer()),
                pos.getX(), pos.getZ(), tier);
    }

    public Status status() {
        if (tier == null) return Status.INCOMPLETE;
        if (yield == null || yield.barren()) return Status.BARREN;
        if (!held.isEmpty()) return Status.BLOCKED;
        if (speed == 0.0f) return Status.STILL;
        return Status.WORKING;
    }

    /**
     * Brings one piece of the chunk's ore up: what that ore gives when it is mined with a pickaxe — raw
     * iron, raw tin, coal, a handful of redstone — read off the ore's own loot table, never the block.
     */
    private void bringUp(ServerLevel level, BlockPos pos) {
        Identifier ore = yield.roll(level.getRandom());
        if (ore == null) return;
        Block block = BuiltInRegistries.BLOCK.getValue(ore);
        if (block == null || block.defaultBlockState().isAir()) return;
        // Cut as an iron pickaxe would: no silk touch, no fortune.
        List<ItemStack> pieces = Block.getDrops(block.defaultBlockState(), level, pos, null, null,
                new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        putOutAll(level, pos, pieces);
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.5f, 0.7f);
    }

    /** Puts every stack out, and keeps back what a full chest had no room for. */
    private void putOutAll(ServerLevel level, BlockPos pos, List<ItemStack> stacks) {
        held.clear();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            ItemStack left = putOut(level, pos, stack);
            if (!left.isEmpty()) held.add(left);
        }
    }

    /** Where the ore goes: whatever takes items just outside the chute, if anything does. */
    private static Storage<ItemVariant> outlet(ServerLevel level, BlockPos head) {
        Direction side = outputSide(level.getBlockState(head));
        return ItemStorage.SIDED.find(level, head.relative(side, 2), side.getOpposite());
    }

    /**
     * Puts a piece out through the chute. Into whatever takes items outside it, or onto the ground where
     * nothing does. Gives back what did not fit, which is only ever the case with a full chest.
     */
    private ItemStack putOut(ServerLevel level, BlockPos head, ItemStack piece) {
        Storage<ItemVariant> outlet = outlet(level, head);
        Direction side = outputSide(level.getBlockState(head));
        if (outlet == null) {
            Block.popResourceFromFace(level, head.relative(side), side, piece);
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long moved = outlet.insert(ItemVariant.of(piece), piece.getCount(), transaction);
            transaction.commit();
            return piece.copyWithCount(piece.getCount() - (int) moved);
        }
    }

    /** How strongly the drill loads its line per turn, by its tier. Nothing while its frame is incomplete. */
    public float impact() {
        return tier == null ? BASE_IMPACT : BASE_IMPACT + IMPACT_PER_TIER * tier.level();
    }

    /** Seconds between two pieces at the speed it is turned now, or 0 where it is not working. */
    public float secondsPerPiece() {
        if (status() != Status.WORKING) return 0.0f;
        return (float) (yield.intervalTicks() / 20.0 * RATED_SPEED / Math.abs(speed));
    }

    /**
     * Everything the drill's screen shows: its state, and every ore the chunk under it holds — the ones
     * this frame reaches with their share of what comes up, the others with the frame they would need.
     */
    public OreDrillPayloads.Info info(ServerPlayer player, boolean open) {
        ServerLevel level = player.level();
        BlockPos pos = getBlockPos();
        List<OreDrillPayloads.Ore> ores = chunkOres(player, pos, tier, yield, false);
        Storage<ItemVariant> outlet = outlet(level, pos);
        Direction side = outputSide(getBlockState());
        Identifier into = outlet == null ? null
                : BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.relative(side, 2)).getBlock());
        float progressShare = yield == null || yield.barren() ? 0.0f
                : Math.min(1.0f, progress / yield.intervalTicks());
        return new OreDrillPayloads.Info(open, false, pos, tier == null ? 0 : tier.level(), Math.max(0, missing),
                status().ordinal(), speed, secondsPerPiece(), progressShare, side.get3DDataValue(), into,
                SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ores);
    }

    /**
     * Every ore the chunk at this position holds, as a drill of this tier would see it: the ones it
     * reaches with their share of what it brings up, the others with the tier they need. With no tier,
     * nothing is reached. {@code revealAll} names every ore whether or not the player knows it.
     */
    public static List<OreDrillPayloads.Ore> chunkOres(ServerPlayer player, BlockPos pos, DrillTier tier,
                                                      DrillYield yield, boolean revealAll) {
        ServerLevel level = player.level();
        var profiles = Geology.profiles(level.getServer());
        Map<Identifier, Integer> needs = DrillYield.oreTiers(profiles);
        DrillTier best = DrillTier.values()[DrillTier.values().length - 1];
        DrillYield everything = DrillYield.forChunk(level.getSeed(), profiles, pos.getX(), pos.getZ(), best);
        var runtime = CoreLifecycle.find(level.getServer());
        int studied = de.ipnats.hardwrought.knowledge.KnowledgeLevel.STUDIED.ordinal();
        List<OreDrillPayloads.Ore> ores = new ArrayList<>();
        for (DrillYield.Entry entry : everything.entries()) {
            int tierNeeded = needs.getOrDefault(entry.ore(), best.level());
            boolean reached = tier != null && tier.reaches(tierNeeded) && yield != null && !yield.barren();
            int known = revealAll ? studied
                    : runtime == null ? 0 : runtime.knowledge().knowledge(player).level(entry.ore()).ordinal();
            ores.add(new OreDrillPayloads.Ore(entry.ore(), tierNeeded, reached ? (float) yield.share(entry.ore()) : -1.0f,
                    (float) everything.share(entry.ore()), known));
        }
        // What is reached first, most first; then what would need a better frame, most of it first.
        ores.sort(Comparator.comparing((OreDrillPayloads.Ore ore) -> ore.share() < 0)
                .thenComparing(ore -> -Math.max(ore.share(), 0))
                .thenComparing(ore -> -ore.presence()));
        return ores;
    }

    // ---------------------------------------------------------------- for tests

    public DrillTier tier() {
        return tier;
    }

    public DrillYield yield() {
        return yield;
    }

    /** Test hook: brings one piece up now, as if the drill had just finished it. */
    public void bringUpNow() {
        if (level instanceof ServerLevel server && yield != null && !yield.barren() && held.isEmpty()) {
            bringUp(server, worldPosition);
        }
    }

    /** What a full chest left in the drill, or nothing. */
    public List<ItemStack> held() {
        return List.copyOf(held);
    }

    /** Looks the frame over now rather than on the next check: when it is used, or a frame block comes or goes. */
    public void lookOverFrameNow() {
        if (level instanceof ServerLevel server) lookOverFrame(server, worldPosition);
    }

    // ---------------------------------------------------------------- kinetics, saving

    @Override
    public float kineticSpeed() {
        return speed;
    }

    @Override
    public void setKineticSpeed(float speed) {
        this.speed = speed;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        speed = input.getFloatOr("speed", 0.0f);
        progress = input.getFloatOr("progress", 0.0f);
        held.clear();
        input.read("held", ItemStack.CODEC.listOf()).ifPresent(held::addAll);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("speed", speed);
        output.putFloat("progress", progress);
        if (!held.isEmpty()) output.store("held", ItemStack.CODEC.listOf(), List.copyOf(held));
    }

    /** What the drill held when it is broken comes out with it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) held.forEach(stack -> Block.popResource(level, pos, stack));
        held.clear();
        super.preRemoveSideEffects(pos, state);
    }
}
