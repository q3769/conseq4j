/*
 * The MIT License
 * Copyright 2022 Qingtian Wang.
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package conseq4j;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.ToString;
import lombok.extern.java.Log;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Level;

import static java.lang.Math.floorMod;

/**
 * @author Qingtian Wang
 */
@ToString @Log public final class Conseq implements ConcurrentSequencer {

    public static final int DEFAULT_GLOBAL_CONCURRENCY = Integer.MAX_VALUE;
    public static final int DEFAULT_EXECUTOR_QUEUE_SIZE = Integer.MAX_VALUE;
    private static final int SINGLE_THREAD_COUNT = 1;
    private static final long KEEP_ALIVE_SAME_THREAD = 0L;
    private final ConcurrentMap<Object, ListeningExecutorService> sequentialExecutors;
    private final int globalConcurrency;
    private final int executorTaskQueueSize;

    private Conseq(Builder builder) {
        this.globalConcurrency = builder.globalConcurrency;
        this.executorTaskQueueSize = builder.executorTaskQueueSize;
        this.sequentialExecutors = new ConcurrentHashMap<>();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    private static ThreadPoolExecutor newSingleThreadExecutor(BlockingQueue<Runnable> blockingTaskQueue) {
        return new ThreadPoolExecutor(SINGLE_THREAD_COUNT, SINGLE_THREAD_COUNT, KEEP_ALIVE_SAME_THREAD,
                TimeUnit.MILLISECONDS, blockingTaskQueue);
    }

    @Override public ListeningExecutorService getSequentialExecutor(Object sequenceKey) {
        int nonNegativeSequenceKeyHash = nonNegativeHashCodeOf(sequenceKey);
        return this.sequentialExecutors.compute(nonNegativeSequenceKeyHash, (sequenceKeyHashCode, executorService) -> {
            if (executorService != null) {
                return executorService;
            }
            return MoreExecutors.listeningDecorator(new IrrevocableExecutorService(newSequentialExecutorService()));
        });
    }

    private ExecutorService newSequentialExecutorService() {
        if (this.executorTaskQueueSize == DEFAULT_EXECUTOR_QUEUE_SIZE) {
            return Executors.newSingleThreadExecutor();
        }
        return newSingleThreadExecutor(new LinkedBlockingQueue<>(this.executorTaskQueueSize));
    }

    private int nonNegativeHashCodeOf(Object sequenceKey) {
        return floorMod(Objects.hashCode(sequenceKey), this.globalConcurrency);
    }

    public static final class Builder {

        private int globalConcurrency = DEFAULT_GLOBAL_CONCURRENCY;
        private int executorTaskQueueSize = DEFAULT_EXECUTOR_QUEUE_SIZE;

        private Builder() {
        }

        @Nonnull public Builder globalConcurrency(int globalConcurrency) {
            if (globalConcurrency <= 0)
                throw new IllegalArgumentException(
                        "global concurrency has to be greater than zero, but given: " + globalConcurrency);
            this.globalConcurrency = globalConcurrency;
            return this;
        }

        @Nonnull public Builder executorTaskQueueSize(int executorTaskQueueSize) {
            if (executorTaskQueueSize <= 0)
                throw new IllegalArgumentException(
                        "executor task queue size has to be greater than zero, but given: " + executorTaskQueueSize);
            else
                log.log(Level.WARNING,
                        "may not be a good idea to limit the size of an executor''s task queue, unless you intend to reject and fail any excessive task any time the task queue is full at the given size {0}, consider using the default/unbounded size",
                        executorTaskQueueSize);
            this.executorTaskQueueSize = executorTaskQueueSize;
            return this;
        }

        @Nonnull public Conseq build() {
            return new Conseq(this);
        }
    }
}
