package de.ipnats.hardwrought.client.underworld;

/** Stage 3 of the Underworld: what it sounds like, and the unease; see docs/underworld.md. */
public final class UnderworldClient {
    private UnderworldClient() { }

    public static void initialize() {
        ShaftRenderer.initialize();
        GraveUndeadClient.initialize();
        UnderworldReverb.initialize();
        UnderworldEcho.initialize();
        UnderworldAmbience.initialize();
        WatchingEyes.initialize();
    }
}
