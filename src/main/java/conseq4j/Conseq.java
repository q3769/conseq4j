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

import lombok.ToString;
import lombok.extern.java.Log;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * <p>Default implementation of {@code ConcurrentSequencer}.</p>
 *
 * @author Qingtian Wang
 */
@ToString @Log public final class Conseq implements ConcurrentSequencer {

    private static final int DEFAULT_GLOBAL_CONCURRENCY = Integer.MAX_VALUE;
    private static final int DEFAULT_TASK_QUEUE_CAPACITY = Integer.MAX_VALUE;
    private static final boolean VALIDATE_EXECUTOR_ON_RETURN_TO_POOL = true;
    private final ConcurrentMap<Object, SingleThreadTaskExecutionAsyncListenedExecutor> servicingSequentialExecutors =
            new ConcurrentHashMap<>();
    private final ObjectPool<SingleThreadTaskExecutionAsyncListenedExecutor> executorPool;

    private Conseq(Builder builder) {
        this.executorPool = new GenericObjectPool<>(pooledExecutorFactory(builder), executorPoolConfig(builder));
    }

    private static PooledSingleThreadExecutorFactory pooledExecutorFactory(Builder builder) {
        return new PooledSingleThreadExecutorFactory(builder.executorTaskQueueCapacity);
    }

    private static GenericObjectPoolConfig<SingleThreadTaskExecutionAsyncListenedExecutor> executorPoolConfig(
            Builder builder) {
        final GenericObjectPoolConfig<SingleThreadTaskExecutionAsyncListenedExecutor> genericObjectPoolConfig =
                new GenericObjectPoolConfig<>();
        genericObjectPoolConfig.setMaxTotal(builder.globalConcurrency);
        genericObjectPoolConfig.setTestOnReturn(VALIDATE_EXECUTOR_ON_RETURN_TO_POOL);
        return genericObjectPoolConfig;
    }

    /**
     * <p>To get a new fluent builder.</p>
     *
     * @return a new {@link conseq4j.Conseq.Builder} instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@inheritDoc}
     */
    @Override public ExecutorService getSequentialExecutor(Object sequenceKey) {
        return this.servicingSequentialExecutors.compute(sequenceKey, (k, executor) -> {
            if (executor != null) {
                return executor;
            }
            final SingleThreadTaskExecutionAsyncListenedExecutor pooledExecutor = borrowPooledExecutor();
            pooledExecutor.addListener(
                    new ExecutorSweepingTaskExecutionListener(sequenceKey, servicingSequentialExecutors, executorPool));
            return pooledExecutor;
        });
    }

    private SingleThreadTaskExecutionAsyncListenedExecutor borrowPooledExecutor() {
        final SingleThreadTaskExecutionAsyncListenedExecutor pooledExecutor;
        try {
            pooledExecutor = executorPool.borrowObject();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to borrow executor from pool " + executorPool, ex);
        }
        return pooledExecutor;
    }

    @ToString @Log public static class Builder {

        private int executorTaskQueueCapacity = DEFAULT_TASK_QUEUE_CAPACITY;
        private int globalConcurrency = DEFAULT_GLOBAL_CONCURRENCY;

        public Conseq build() {
            log.log(Level.INFO, "building conseq using {0}", this);
            final Conseq conseq = new Conseq(this);
            log.log(Level.FINEST, () -> "built " + conseq);
            return conseq;
        }

        /**
         * @param executorTaskQueueCapacity for each sequential executor.
         */
        public Builder executorTaskQueueCapacity(int executorTaskQueueCapacity) {
            if (executorTaskQueueCapacity != DEFAULT_TASK_QUEUE_CAPACITY) {
                log.log(Level.WARNING,
                        "may not be a good idea to limit task queue capacity. unless you intend to reject and fail all excessive tasks that the executor task queue cannot hold, consider using the default/unbounded capacity instead");
            }
            this.executorTaskQueueCapacity = executorTaskQueueCapacity;
            return this;
        }

        /**
         * @param globalConcurrency enforced by the max number of executors that can be borrowed from the executor
         *                          pool.
         */
        public Builder globalConcurrency(int globalConcurrency) {
            this.globalConcurrency = globalConcurrency;
            return this;
        }
    }
}
