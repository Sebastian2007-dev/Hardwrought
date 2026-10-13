package de.ipnats.hardwrought.machinery;

import net.minecraft.util.StringRepresentable;

/**
 * What a pipe is on one of its sides. A side towards another pipe of its kind, or towards a block
 * it can fill, is {@link #CONNECTED}; a side towards a block it draws from is {@link #EXTRACT}. A
 * pipe wrench chooses between them, and can also cut a side off, which leaves it {@link #NONE}.
 */
public enum PipeLink implements StringRepresentable {
    NONE("none"),
    CONNECTED("connected"),
    EXTRACT("extract");

    private final String name;

    PipeLink(String name) {
        this.name = name;
    }

    public boolean joined() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
