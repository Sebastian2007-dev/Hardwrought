package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.core.networking.SeasonPayload;
import de.ipnats.hardwrought.environment.Season;
import de.ipnats.hardwrought.environment.SeasonState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * The client's end of the calendar: taking the server's word for what season it is, and repainting
 * the world when that changes.
 *
 * <p>Biome colour is baked into chunk meshes when they are built, so a season that changed the
 * colours would otherwise only show on the chunks a player happened to walk away from and back to.
 * A full rebuild of the world's geometry fixes that. It is an expensive thing to ask for, and it is
 * asked for six times across each turn of the year — or once more when somebody sets the season
 * with the debug command, which is a good deal of what that command is for.
 */
public final class SeasonClient {
    /**
     * How many steps the turn between two seasons is repainted in.
     *
     * <p>The colours live in the chunk meshes, so following the turn smoothly would mean rebuilding
     * the world every frame, which is not on offer. Six steps across three game days is one rebuild
     * every ten minutes or so and still reads as a gradual change rather than a switch. The light,
     * which is baked into nothing, does follow it smoothly — see {@link SeasonColors#skyTint}.
     */
    private static final int BLEND_STEPS = 6;

    private static int repaints;
    private static Season painted;
    private static int paintedStep = -1;

    private SeasonClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(SeasonPayload.TYPE, (payload, context) -> {
            Season[] seasons = Season.values();
            Season season = seasons[Math.floorMod(payload.season(), seasons.length)];
            SeasonState.adopt(season, payload.fog(), payload.dayInSeason(), payload.blend());
            repaintIfTurned(season, payload.blend());
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            painted = null;
            paintedStep = -1;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            painted = null;
            paintedStep = -1;
            SeasonState.adopt(Season.SPRING, 0, 1, 0);
        });
    }

    private static void repaintIfTurned(Season season, float blend) {
        int step = Math.round(Math.max(0, Math.min(1, blend)) * BLEND_STEPS);
        if (painted == season && paintedStep == step) return;
        // The first packet after joining is not a turn of the year; the chunks are being built with
        // the right colour anyway, and a re-render here would only throw that work away.
        boolean firstWord = painted == null;
        painted = season;
        paintedStep = step;
        if (firstWord) return;
        repaint();
    }

    /**
     * Rebuild the world's geometry, so the new colours actually appear.
     *
     * <p>{@code levelExtractor.allChanged()} is the call the game itself makes when a video setting
     * changes the colour of everything, and it has to be that call and not its parts. It clears the
     * level's tint caches, builds a fresh section-update tracker, repositions it on the camera and
     * only then flags the compiled geometry to be thrown away — all on the render thread, at the
     * point in the frame where that is safe.
     *
     * <p>Two earlier attempts were wrong in instructive ways, and both are worth remembering.
     * Marking a box of sections around the player dirty left a visible square of new colour in a
     * field of old: a global change has no radius at which it is correct. Calling
     * {@code invalidateCompiledGeometry} by hand instead threw the sections away without building
     * the tracker that queues them to be compiled again, and the world simply stopped rendering.
     */
    private static void repaint() {
        Minecraft client = Minecraft.getInstance();
        // Through the client's own queue, not straight from here: this is reached from a packet,
        // and the renderer must be torn down by the thread that draws it.
        client.execute(() -> {
            if (client.level == null) return;
            client.levelExtractor.allChanged();
            repaints++;
        });
    }

    /** How many times the world has been repainted for a turn of the year. For the tests. */
    public static int repaints() {
        return repaints;
    }
}
