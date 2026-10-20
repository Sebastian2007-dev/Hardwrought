package de.ipnats.hardwrought.end.dragon;

import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import de.ipnats.hardwrought.mixin.EnderDragonFightAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.DragonRespawnStage;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The raising of the dragon, made a sight to see. Vanilla's sequence is kept as it is — four crystals on
 * the exit portal, the pillars called up one by one, the dragon made in the sky over the portal — and
 * each step of it is played as an effect for everyone near ({@code DragonRiseFx} on the client):
 *
 * <ol>
 *   <li><b>The call</b> (five seconds): the void stirs round the point the crystals shine at, a heart
 *   begins to beat in it, a sigil draws itself on the island, the sky begins to tear.</li>
 *   <li><b>The pillars</b> (two seconds each): lightning from that point to the pillar's top, a column
 *   of light out of the void beneath it, and when it stands again a burst and a peal of thunder.</li>
 *   <li><b>The summoning</b> (five seconds): every crystal pours into the point, rings close in on it,
 *   the void there swells black, the sky tears wide.</li>
 *   <li><b>The birth</b>: it bursts out of the void, with a wave over the whole island.</li>
 * </ol>
 *
 * Its stage is read after the fight has ticked, so a time read here is one past the one vanilla just acted on.
 */
public final class DragonRising {
    /** Where vanilla's crystals shine, and where the dragon is made. */
    public static final Vec3 FOCUS = new Vec3(0.5, 128, 0.5);
    static final int CALL_TICKS = 100, PILLAR_TICKS = 40, SUMMON_TICKS = 100;

    private static DragonRespawnStage last;

    private DragonRising() { }

    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(DragonRising::tick);
    }

    private static void tick(ServerLevel level) {
        if (level.dimension() != Level.END) return;
        var fight = level.getDragonFight();
        if (fight == null) return;
        EnderDragonFightAccessor access = (EnderDragonFightAccessor) fight;
        DragonRespawnStage stage = access.hardwrought$respawnStage();
        int time = access.hardwrought$respawnTime();
        DragonRespawnStage was = last;
        last = stage;
        if (stage == DragonRespawnStage.PREPARING_TO_SUMMON_PILLARS && was != stage) {
            Fx.play(level, FxEffect.DRAGON_RISE_CALL, FOCUS, FOCUS.add(0, CALL_TICKS, 0), 0, 1, null);
            title(level, "call");
        } else if (stage == DragonRespawnStage.SUMMONING_PILLARS) {
            List<EndSpikeFeature.EndSpike> spikes = EndSpikeFeature.getSpikesForLevel(level);
            if (time % PILLAR_TICKS == 1 && (time - 1) / PILLAR_TICKS < spikes.size()) {
                Vec3 top = top(spikes.get((time - 1) / PILLAR_TICKS));
                Fx.play(level, FxEffect.DRAGON_RISE_PILLAR, top, top.add(0, PILLAR_TICKS, 0), 0, 1, null);
            } else if (time % PILLAR_TICKS == 0 && time > 0 && time / PILLAR_TICKS - 1 < spikes.size()) {
                EndSpikeFeature.EndSpike spike = spikes.get(time / PILLAR_TICKS - 1);
                Fx.play(level, FxEffect.DRAGON_RISE_PILLAR_BURST, top(spike), top(spike), 0, spike.getRadius(), null);
            }
        } else if (stage == DragonRespawnStage.SUMMONING_DRAGON && was != stage) {
            Fx.play(level, FxEffect.DRAGON_RISE_SUMMON, FOCUS, FOCUS.add(0, SUMMON_TICKS, 0), 0, 1, null);
        } else if (stage == null && was == DragonRespawnStage.SUMMONING_DRAGON && !level.getDragons().isEmpty()) {
            Fx.play(level, FxEffect.DRAGON_RISE_BIRTH, FOCUS, FOCUS, 0, 1, level.getDragons().getFirst());
            title(level, "birth");
        }
    }

    private static Vec3 top(EndSpikeFeature.EndSpike spike) {
        return new Vec3(spike.getCenterX() + 0.5, spike.getHeight() + 1, spike.getCenterZ() + 0.5);
    }

    private static void title(ServerLevel level, String step) {
        for (ServerPlayer player : level.players()) {
            if (player.connection == null) continue;
            player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 70, 30));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.hardwrought.dragon.rise." + step)
                    .withStyle(ChatFormatting.DARK_PURPLE)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.hardwrought.dragon.rise." + step + ".subtitle")
                    .withStyle(ChatFormatting.GRAY)));
        }
    }
}
