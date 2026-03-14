package de.bund.zrb.dosbox.core;

/**
 * Interface for all DOSBox hardware/software modules.
 * Each module registers itself with the machine during init.
 */
public interface Module {
    /** Called once after all modules are created, to wire up dependencies. */
    default void init() {}
    /** Called on machine reset. */
    default void reset() {}
    /** Called on shutdown. */
    default void destroy() {}
}

