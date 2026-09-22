package de.ipnats.hardwrought.client;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.client.KeyMapping;

/** One heading in the controls screen for every key this mod binds. */
public final class HardwroughtKeys {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Hardwrought.id("survival"));

    private HardwroughtKeys() { }
}
