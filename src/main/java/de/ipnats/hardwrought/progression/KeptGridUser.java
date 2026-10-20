package de.ipnats.hardwrought.progression;

/**
 * An open menu working on the grid a bench keeps: the bench's own crafting menu, or a storage desk
 * the bench stands against. Whichever it is, it holds the bench, and nobody else may open the grid
 * meanwhile (see {@link KeptGridBlockEntity#claim}).
 */
public interface KeptGridUser {
    boolean isAt(KeptGridBlockEntity bench);
}
