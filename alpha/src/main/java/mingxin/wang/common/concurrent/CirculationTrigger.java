package mingxin.wang.common.concurrent;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Copyright (c) 2017-2018 Mingxin Wang. All rights reserved.
 */
public final class CirculationTrigger {
    private static final Logger LOGGER = LoggerFactory.getLogger(CirculationTrigger.class);
    private static final int RESERVATION_STATE_OFFSET = 32;
    private static final long IDLE_STATE_MASK = 0x000000007FFFFFFFL;
    private static final long RUNNING_FLAG_MASK = 0x0000000080000000L;
    private static final long NO_RESERVATION_STATE_MASK = IDLE_STATE_MASK | RUNNING_FLAG_MASK;

    private final AtomicLong state;
    private final TimedExecutor executor;
    private final CirculatingRunnable runnable;

    public static CirculationTrigger bind(TimedExecutor executor, CirculatingRunnable runnable) {
        Preconditions.checkNotNull(executor);
        Preconditions.checkNotNull(runnable);
        return new CirculationTrigger(new AtomicLong(), executor, runnable);
    }

    public final void fire() {
        fire(Instant.now());
    }

    public final void fire(Instant when) {
        Preconditions.checkNotNull(when);
        executor.execute(new TimedTask(executor), when);
    }

    public final void suspend() {
        advanceVersion();
    }

    private long advanceVersion() {
        long s, v;
        do {
            s = state.get();
            v = s + 1 & IDLE_STATE_MASK;
        } while (!state.weakCompareAndSet(s, s & RUNNING_FLAG_MASK | v));
        return v;
    }

    private final class TimedTask implements Runnable {
        private long version;
        private final TimedExecutor executor;

        private TimedTask(TimedExecutor executor) {
            this.version = advanceVersion();
            this.executor = executor;
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
            Optional<Duration> nextDuration;
            for (;;) {
                try {
                    nextDuration = runnable.run();
                } catch (Throwable t) {
                    nextDuration = Optional.empty();
                    LOGGER.error("Unexpected exception was caught while executing scheduled task: {}", CirculationTrigger.this, t);
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
                            if (version == (s & IDLE_STATE_MASK)) {
                                nextDuration.ifPresent(delay -> executor.execute(this, Instant.now().plus(delay)));
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    private CirculationTrigger(AtomicLong state, TimedExecutor executor, CirculatingRunnable runnable) {
        this.state = state;
        this.executor = executor;
        this.runnable = runnable;
    }
}
