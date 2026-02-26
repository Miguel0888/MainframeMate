package de.bund.zrb.command.request.helper;

import de.bund.zrb.api.WDCommand;

import java.util.concurrent.atomic.AtomicInteger;

public class WDCommandImpl<T extends WDCommand.Params> implements WDCommand {
    /**
     * Global, thread-safe command ID generator.
     * Uses AtomicInteger to guarantee unique IDs across all instances without locking.
     * Previously used a static int with instance-level synchronized, which was broken:
     * synchronized(this) does NOT protect a static field across different instances,
     * leading to duplicate IDs under concurrency and response correlation errors.
     */
    private static final AtomicInteger COMMAND_COUNTER = new AtomicInteger(0);

    private final int id;
    private final String method;
    protected T params;

    // Retry-Metadaten:
    private final long firstTimestamp = System.currentTimeMillis();
    private int retryCount = 0;

    public WDCommandImpl(String method, T params) {
        this.method = method;
        this.params = params;
        this.id = nextCommandId();
    }

    @Override
    public String getName() {
        return method;
    }

    public Integer getId() {
        return id;
    }

    @Override
    public long getFirstTimestamp() {
        return firstTimestamp;
    }

    @Override
    public int getRetryCount() {
        return retryCount;
    }

    @Override
    public void incrementRetryCount() {
        retryCount++;
    }

    /**
     * Returns a globally unique command ID using lock-free atomic increment.
     */
    private static int nextCommandId() {
        return COMMAND_COUNTER.incrementAndGet();
    }
}
