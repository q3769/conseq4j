/*
 * MIT License
 *
 * Copyright (c) 2022. Qingtian Wang
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

import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Level;

import static java.lang.Math.floorMod;

/**
 * <p>Default implementation of {@code ConcurrentSequencer}.</p>
 *
 * @author Qingtian Wang
 */
@ToString @Log public final class Conseq implements ConcurrentSequencer {

    /**
     * Default global concurrency is set to {@code Integer.MAX_VALUE}
     */
    public static final int DEFAULT_GLOBAL_CONCURRENCY = Integer.MAX_VALUE;
    /**
     * Default task queue size for an executor set to {@code Integer.MAX_VALUE}
     */
    public static final int DEFAULT_EXECUTOR_QUEUE_SIZE = Integer.MAX_VALUE;
    private static final int SINGLE_THREAD_COUNT = 1;
    private static final long KEEP_ALIVE_SAME_THREAD = 0L;
    private final ConcurrentMap<Object, ExecutorService> sequentialExecutors;
    private final int globalConcurrency;
    private final int executorTaskQueueSize;

    private Conseq(Builder builder) {
        this.globalConcurrency = builder.globalConcurrency;
        this.executorTaskQueueSize = builder.executorTaskQueueSize;
        this.sequentialExecutors = new ConcurrentHashMap<>();
    }

    /**
     * <p>To get a new fluent builder.</p>
     *
     * @return a new {@link conseq4j.Conseq.Builder} instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    private static ThreadPoolExecutor newSingleThreadExecutor(int executorTaskQueueSize) {
        return new ThreadPoolExecutor(SINGLE_THREAD_COUNT, SINGLE_THREAD_COUNT, KEEP_ALIVE_SAME_THREAD,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(executorTaskQueueSize));
    }

    /**
     * @return a single-thread executor that does not support any shutdown action
     */
    @Override public ExecutorService getSequentialExecutor(Object sequenceKey) {
        return this.sequentialExecutors.compute(bucketOf(sequenceKey), (bucket, executor) -> {
            if (executor != null) {
                return executor;
            }
            return new ShutdownDisabledExecutorService(newSequentialExecutorService());
        });
    }

    private ExecutorService newSequentialExecutorService() {
        if (this.executorTaskQueueSize == DEFAULT_EXECUTOR_QUEUE_SIZE) {
            return Executors.newSingleThreadExecutor();
        }
        return newSingleThreadExecutor(this.executorTaskQueueSize);
    }

    private int bucketOf(Object sequenceKey) {
        return floorMod(Objects.hash(sequenceKey), this.globalConcurrency);
    }

    public static final class Builder {

        private int globalConcurrency = DEFAULT_GLOBAL_CONCURRENCY;
        private int executorTaskQueueSize = DEFAULT_EXECUTOR_QUEUE_SIZE;

        private Builder() {
        }

        /**
         * @param globalConcurrency max global concurrency i.e. the max number of sequential executors.
         */
        public Builder globalConcurrency(int globalConcurrency) {
            if (globalConcurrency <= 0)
                throw new IllegalArgumentException(
                        "global concurrency has to be greater than zero, but given: " + globalConcurrency);
            this.globalConcurrency = globalConcurrency;
            return this;
        }

        /**
         * @param executorTaskQueueSize max task queue capacity for each sequential executor.
         */
        public Builder executorTaskQueueSize(int executorTaskQueueSize) {
            if (executorTaskQueueSize <= 0)
                throw new IllegalArgumentException(
                        "executor task queue size has to be greater than zero, but given: " + executorTaskQueueSize);
            else
                log.log(Level.WARNING,
                        "may not be a good idea to limit the size of an executor''s task queue; at runtime any excessive task beyond the given queue size {0} will be rejected unless you set up custom handler. consider using the default/unbounded size instead",
                        executorTaskQueueSize);
            this.executorTaskQueueSize = executorTaskQueueSize;
            return this;
        }

        public Conseq build() {
            return new Conseq(this);
        }
    }
}
