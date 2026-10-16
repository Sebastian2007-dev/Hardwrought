package de.ipnats.hardwrought.underworld;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.environment.LightBurn;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The parts of the Underworld's unease that change the world, and so happen on the server.
 *
 * <ul>
 *   <li>In the <b>Dripstone Depths</b> stalactites break loose by themselves now and then: a crack at the
 *   roof and dust trickling down, a moment, and the whole of it comes down — vanilla's own falling
 *   stalactite, which hurts what it lands on.</li>
 *   <li>Anywhere down here, now and then, <b>a light goes out behind the player</b>: a torch, a lantern,
 *   a candle somewhere at their back, by itself.</li>
 * </ul>
 *
 * The rest of it — the eyes, the footsteps, the breathing, the whispers, the sounds — is each player's
 * own and lives on the client.
 */
public final class UnderworldAtmosphere {
    public static final ResourceKey<Biome> DRIPSTONE_DEPTHS = ResourceKey.create(Registries.BIOME, Hardwrought.id("dripstone_depths"));
    /** On average, per player in the Dripstone Depths: one stalactite every so many ticks. */
    static final int STALACTITE_EVERY = 300;
    /** On average, per player: a light going out behind them every so many ticks. */
    static final int LIGHT_OUT_EVERY = 6000;
    /** From the crack to the fall. */
    static final int CRACK_TICKS = 25;

    private UnderworldAtmosphere() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(UnderworldAtmosphere::tick);
    }

    private static void tick(MinecraftServer server) {
        ServerLevel underworld = server.getLevel(Underworld.LEVEL);
        if (underworld == null) return;
        RandomSource random = underworld.getRandom();
        for (ServerPlayer player : underworld.players()) {
            if (player.isSpectator()) continue;
            if (random.nextInt(STALACTITE_EVERY) == 0 && underworld.getBiome(player.blockPosition()).is(DRIPSTONE_DEPTHS)) {
                BlockPos root = findStalactite(underworld, player.blockPosition(), 16, random);
                if (root != null) crack(underworld, root);
            }
            if (random.nextInt(LIGHT_OUT_EVERY) == 0) lightOutBehind(underworld, player, random);
        }
    }

    /**
     * The root of a stalactite hanging free somewhere above a position, within a radius, or null where a few
     * tries find none.
     */
    public static BlockPos findStalactite(ServerLevel level, BlockPos around, int radius, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos.MutableBlockPos pos = around.offset(random.nextInt(2 * radius + 1) - radius, 2, random.nextInt(2 * radius + 1) - radius).mutable();
            if (!level.hasChunkAt(pos)) continue;
            for (int up = 0; up < 28; up++, pos.move(Direction.UP)) {
                BlockState state = level.getBlockState(pos);
                if (stalactite(state)) {
                    if (!level.getBlockState(pos.below()).isAir()) break;
                    while (stalactite(level.getBlockState(pos.above()))) pos.move(Direction.UP);
                    return pos.immutable();
                }
                if (state.isSolidRender()) break;
            }
        }
        return null;
    }

    private static boolean stalactite(BlockState state) {
        return state.is(Blocks.POINTED_DRIPSTONE) && state.getValue(SpeleothemBlock.TIP_DIRECTION) == Direction.DOWN;
    }

    /** A crack at the roof and dust trickling down; the fall follows as vanilla's own. */
    public static void crack(ServerLevel level, BlockPos root) {
        level.playSound(null, root, SoundEvents.POINTED_DRIPSTONE_BREAK, SoundSource.BLOCKS, 2.0f, 0.5f);
        level.playSound(null, root, SoundEvents.DRIPSTONE_BLOCK_HIT, SoundSource.BLOCKS, 1.5f, 0.6f);
        var dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
        level.sendParticles(dust, root.getX() + 0.5, root.getY() + 0.9, root.getZ() + 0.5, 14, 0.3, 0.05, 0.3, 0);
        level.sendParticles(ParticleTypes.DRIPPING_DRIPSTONE_WATER, root.getX() + 0.5, root.getY(), root.getZ() + 0.5, 3, 0.15, 0, 0.15, 0);
        // A stalactite that is ticked falls; that is how vanilla brings one down when its roof goes.
        level.scheduleTick(root, level.getBlockState(root).getBlock(), CRACK_TICKS);
    }

    /** A light somewhere at the player's back, five to fourteen blocks off, goes out. */
    public static boolean lightOutBehind(ServerLevel level, ServerPlayer player, RandomSource random) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        BlockPos feet = player.blockPosition();
        for (int attempt = 0; attempt < 400; attempt++) {
            BlockPos pos = feet.offset(random.nextInt(29) - 14, random.nextInt(9) - 3, random.nextInt(29) - 14);
            Vec3 toward = Vec3.atCenterOf(pos).subtract(player.position());
            double distance = toward.horizontalDistance();
            if (distance < 5 || distance > 14 || toward.multiply(1, 0, 1).normalize().dot(look) > -0.25) continue;
            BlockState state = level.getBlockState(pos);
            if (!LightBurn.burns(state)) continue;
            LightBurn.burnOut(level, pos, state);
            return true;
        }
        return false;
    }
}
