package de.ipnats.hardwrought.mobs;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mob specification § 11: a spider in a fight spins a web where its target stands, slowing it and
 * closing the way. Webbing is limited so forests and bases do not fill up with it: a spider spins
 * at most every {@value #COOLDOWN_TICKS} ticks, and a web it spun comes away again after
 * {@value #WEB_TICKS} ticks unless something else has replaced it.
 */
public class SpiderWebGoal extends Goal {
    static final int COOLDOWN_TICKS = 400;
    static final int WEB_TICKS = 1200;
    static final double RANGE = 6;

    private static final Map<ServerLevel, Long2LongOpenHashMap> webs = new WeakHashMap<>();

    private final Spider spider;
    private long readyAt;

    public SpiderWebGoal(Spider spider) {
        this.spider = spider;
    }

    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(SpiderWebGoal::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> webs.clear());
    }

    @Override
    public boolean canUse() {
        if (!(spider.level() instanceof ServerLevel level) || level.getGameTime() < readyAt) return false;
        LivingEntity target = spider.getTarget();
        return target != null && target.onGround() && spider.distanceTo(target) <= RANGE
                && spider.hasLineOfSight(target) && level.getGameRules().get(GameRules.MOB_GRIEFING)
                && level.getBlockState(target.blockPosition()).isAir();
    }

    @Override
    public void start() {
        ServerLevel level = (ServerLevel) spider.level();
        BlockPos at = spider.getTarget().blockPosition();
        level.setBlockAndUpdate(at, Blocks.COBWEB.defaultBlockState());
        level.playSound(null, at, SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.0f, 1.4f);
        webs.computeIfAbsent(level, ignored -> new Long2LongOpenHashMap()).put(at.asLong(), level.getGameTime() + WEB_TICKS);
        readyAt = level.getGameTime() + COOLDOWN_TICKS;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    public static boolean spunBySpider(ServerLevel level, BlockPos pos) {
        Long2LongOpenHashMap spun = webs.get(level);
        return spun != null && spun.containsKey(pos.asLong());
    }

    private static void tick(ServerLevel level) {
        Long2LongOpenHashMap spun = webs.get(level);
        if (spun == null || spun.isEmpty() || level.getGameTime() % 20 != 0) return;
        long now = level.getGameTime();
        spun.long2LongEntrySet().removeIf(entry -> {
            if (entry.getLongValue() > now) return false;
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.COBWEB)) level.removeBlock(pos, false);
            return true;
        });
    }
}
