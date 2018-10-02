package mingxin.wang.common.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Copyright (c) 2017-2018 Mingxin Wang. All rights reserved.
 */
public abstract class TimedCirculation {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimedCirculation.class);
    private static final int RESERVATION_STATE_OFFSET = 32;
    private static final long IDLE_STATE_MASK = 0x000000007FFFFFFFL;
    private static final long RUNNING_FLAG_MASK = 0x0000000080000000L;
    private static final long NO_RESERVATION_STATE_MASK = IDLE_STATE_MASK | RUNNING_FLAG_MASK;

    private final AtomicLong state;
    private final ScheduledExecutorService executor;

    protected TimedCirculation(ScheduledExecutorService executor) {
        this.state = new AtomicLong();
        this.executor = executor;
    }

    public final void trigger() {
        trigger(Duration.ZERO);
    }

    public final void trigger(Duration delay) {
        executor.schedule(new Task(advanceVersion()), delay.toNanos(), TimeUnit.NANOSECONDS);
    }

    public final void suspend() {
        advanceVersion();
    }

    protected abstract Optional<Duration> runOneIteration();

    private long advanceVersion() {
        long s, v;
        do {
            s = state.get();
            v = s + 1 & IDLE_STATE_MASK;
        } while (!state.weakCompareAndSet(s, s & RUNNING_FLAG_MASK | v));
        return v;
    }

    private void schedule(Task task, Duration duration) {
        executor.schedule(task, duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    private final class Task implements Runnable {
        private long version;

        private Task(long version) {
            this.version = version;
        }

        @Override
        public void run() {
            long s;
            for (;;) {
                s = state.get();
                if ((s & IDLE_STATE_MASK) != version) {
                    return;
                }
                if ((s & RUNNING_FLAG_MASK) == 0) {  // No other instances are running
                    if (state.weakCompareAndSet(s, s | RUNNING_FLAG_MASK)) {
                        break;
                    }
                } else {
                    if (state.weakCompareAndSet(s, s & NO_RESERVATION_STATE_MASK | (s << RESERVATION_STATE_OFFSET))) {
                        return;
                    }
                }
            }
            for (;;) {
                Duration nextDelay;
                try {
                    Optional<Duration> nextDelayOptional = runOneIteration();
                    nextDelay = nextDelayOptional.orElse(null);
                } catch (Throwable t) {
                    nextDelay = null;
                    LOGGER.error("Unexpected exception was caught while executing scheduled task: {}", TimedCirculation.this, t);
                }
                for (;;) {
                    s = state.get();
                    if ((s & NO_RESERVATION_STATE_MASK) == (s >>> RESERVATION_STATE_OFFSET)) {
                        if (state.weakCompareAndSet(s, s & NO_RESERVATION_STATE_MASK)) {  // Reset reservation
                            version = s & IDLE_STATE_MASK;  // Update version
                            break;
                        }
                    } else {
                        if (state.weakCompareAndSet(s, s & IDLE_STATE_MASK)) {  // Reset running state
                            if (version == (s & IDLE_STATE_MASK) && nextDelay != null) {
                                schedule(this, nextDelay);
                            }
                            return;
                        }
                    }
                }
            }
        }
    }
}
