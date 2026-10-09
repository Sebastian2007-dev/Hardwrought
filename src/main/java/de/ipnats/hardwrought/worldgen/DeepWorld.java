package de.ipnats.hardwrought.worldgen;

/**
 * The numbers of the overworld's shape that code has to know. The shape itself is data, written by
 * {@code tools/worldgen_assets.py}: mountain ranges that use the height of the world, and caves of
 * every kind down to its floor.
 */
public final class DeepWorld {
    /** The floor and the top of the world, as the dimension type and the noise settings state them. */
    public static final int BOTTOM = -256, TOP = 1024;
    /**
     * The level of the lava that fills what is open below it. Vanilla's is -54, ten blocks above its
     * floor, and with the floor moved down to {@value #BOTTOM} that turned two hundred blocks of
     * caves into one lake of lava. It is twenty blocks above the floor here: the abyss has a sea of
     * lava under it, and everything above that is a cave that can be walked.
     *
     * <p>Lava still gathers higher up, in pockets, where vanilla's aquifers put it: a cave may have a
     * lava lake in it. It is no longer every cave.
     */
    public static final int LAVA_LEVEL = BOTTOM + 20;

    private DeepWorld() { }
}
