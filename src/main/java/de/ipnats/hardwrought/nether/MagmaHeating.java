package de.ipnats.hardwrought.nether;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.smithing.Heat;
import de.ipnats.hardwrought.smithing.Smithing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * The Nether's forge fire: metal thrown onto a magma block there heats up where it lies, hot enough to
 * forge iron (see docs/endgame.md). There is no clay in the Nether for a brick furnace, but there is
 * this.
 *
 * <p>Each piece heads for its own working heat, never past {@link #MAGMA_CELSIUS}: iron to the top of
 * its range, copper, bronze and gold to the top of theirs — so magma brings them up to the anvil and
 * never spoils them. What melts hotter than iron stays out of reach; that is still the forge's work.
 */
public final class MagmaHeating {
    /** As hot as a magma block gets a piece: the top of iron's working heat. */
    public static final double MAGMA_CELSIUS = 1300;
    /** How often a piece on magma takes heat, and how much of the way to its goal each time. */
    static final int INTERVAL = 10;
    static final double SHARE = 0.12;

    private MagmaHeating() { }

    /** One look at a dropped item: on magma in the Nether, and metal, it warms. Called from its tick. */
    public static void heatOnMagma(ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % INTERVAL != 0
                || level.dimension() != Level.NETHER || !entity.onGround()) {
            return;
        }
        warm(level, entity);
    }

    /** One step of warming a dropped item, if it lies on magma and is metal; the Nether left to the caller. */
    public static void warm(ServerLevel level, ItemEntity entity) {
        BlockPos under = BlockPos.containing(entity.getX(), entity.getY() - 0.05, entity.getZ());
        if (!level.getBlockState(under).is(Blocks.MAGMA_BLOCK)) return;
        ItemStack stack = entity.getItem();
        if (!Smithing.isForgeMetal(stack)) return;
        var runtime = CoreLifecycle.find(level.getServer());
        if (runtime == null) return;
        double[] range = Smithing.workingRange(stack.getItem(), runtime.materials());
        if (range == null) return;
        double goal = Math.min(MAGMA_CELSIUS, range[1]);
        long now = level.getGameTime();
        double current = Heat.of(stack, now);
        if (current >= goal - 1) return;
        ItemStack heated = stack.copy();
        Smithing.heat(heated, Math.min(goal, current + Math.max(15, (goal - current) * SHARE)), now, runtime.materials());
        entity.setItem(heated);
        // It is seen and heard taking the heat: smoke and sparks off it, and a hiss now and then.
        level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 0.15, entity.getZ(), 2, 0.1, 0.02, 0.1, 0.01);
        if (level.getRandom().nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.SMALL_FLAME, entity.getX(), entity.getY() + 0.1, entity.getZ(), 1, 0.1, 0.02, 0.1, 0.005);
        }
        if (level.getRandom().nextInt(6) == 0) {
            level.playSound(null, entity.blockPosition(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.4f, 1.2f);
        }
    }
}
