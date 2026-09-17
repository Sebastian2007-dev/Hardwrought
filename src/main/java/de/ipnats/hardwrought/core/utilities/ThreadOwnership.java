package de.ipnats.hardwrought.core.utilities;

/** Prevents a retained service reference from bypassing the runtime's thread guard. */
public final class ThreadOwnership {
    private final Thread owner = Thread.currentThread();

    public void require() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Service accessed from a foreign thread");
    }
}
