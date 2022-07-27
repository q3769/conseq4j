/*
 * MIT License
 *
 * Copyright (c) 2022 Qingtian Wang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package conseq4j;

import lombok.ToString;
import lombok.extern.java.Log;
import net.jcip.annotations.ThreadSafe;

import java.util.Objects;
import java.util.concurrent.*;

import static java.lang.Math.floorMod;

/**
 * <p>Default implementation of {@code ConcurrentSequencer}.</p>
 *
 * @author Qingtian Wang
 */

@ThreadSafe @ToString @Log public final class Conseq implements ConcurrentSequencer {

    private static final int DEFAULT_GLOBAL_CONCURRENCY = Runtime.getRuntime().availableProcessors() + 1;
    private final ConcurrentMap<Object, ExecutorService> sequentialExecutors = new ConcurrentHashMap<>();
    private final int globalConcurrency;

    /**
     * Default constructor sets default global concurrency
     */
    public Conseq() {
        this(DEFAULT_GLOBAL_CONCURRENCY);
    }

    /**
     * @param globalConcurrency max count of "buckets"/executors, i.e. the max number of unrelated tasks that can be
     *                          concurrently executed at any given time
     */
    public Conseq(int globalConcurrency) {
        if (globalConcurrency <= 0)
            throw new IllegalArgumentException(
                    "expecting positive global concurrency, but given: " + globalConcurrency);
        this.globalConcurrency = globalConcurrency;
        log.fine(() -> "constructed " + this);
    }

    private static ExecutorService newFairSingleThreadExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new SerialEnqueueBlockingQueue<>(new LinkedBlockingQueue<>(), true));
    }

    /**
     * @return a single-thread executor that does not support any shutdown action. The executor's task queue is a
     *         {@link java.util.concurrent.LinkedBlockingQueue} as in {@link Executors#newSingleThreadExecutor()}. On
     *         producer side, under contention, enqueue operations are synchronized with "fairness" - longest-waiting
     *         element gets enqueued first; single-threaded on the consumer side, the task queue has no thread
     *         contention on the dequeue operations. This enables the returned sequential executor to provide execution
     *         "fairness" in that, under contention, tasks submitted first are favored to execute first.
     */
    @Override public ExecutorService getSequentialExecutor(Object sequenceKey) {
        return this.sequentialExecutors.computeIfAbsent(bucketOf(sequenceKey),
                k -> new ShutdownDisabledExecutorService(newFairSingleThreadExecutor()));
    }

    private int bucketOf(Object sequenceKey) {
        return floorMod(Objects.hash(sequenceKey), this.globalConcurrency);
    }

}
