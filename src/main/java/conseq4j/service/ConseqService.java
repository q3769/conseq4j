/*
 * The MIT License Copyright 2021 Qingtian Wang. Permission is hereby granted, free of charge, to
 * any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
 * KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package conseq4j.service;

import lombok.ToString;
import lombok.extern.java.Log;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * @author Qingtian Wang
 */
@Log @ToString public final class ConseqService implements ConcurrentSequencerService {

    private static final boolean FIFO_ON_CONCURRENCY_CONTENTION = true;
    private static final boolean VALIDATE_ON_RETURN_TO_POOL = true;

    public static Builder newBuilder() {
        return new Builder();
    }

    private static PooledSingleThreadExecutorFactory pooledExecutorFactory(Builder builder) {
        return new PooledSingleThreadExecutorFactory(
                new Semaphore(builder.globalConcurrency, FIFO_ON_CONCURRENCY_CONTENTION),
                builder.executorTaskQueueCapacity);
    }

    private static GenericObjectPoolConfig<GlobalConcurrencyBoundedRunningTasksCountingExecutorService> executorPoolConfig(
            Builder builder) {
        final GenericObjectPoolConfig<GlobalConcurrencyBoundedRunningTasksCountingExecutorService>
                genericObjectPoolConfig = new GenericObjectPoolConfig<>();
        genericObjectPoolConfig.setMaxTotal(builder.globalConcurrency);
        genericObjectPoolConfig.setTestOnReturn(VALIDATE_ON_RETURN_TO_POOL);
        return genericObjectPoolConfig;
    }

    private static String taskSubmissionErrorMessage(Object sequenceKey, Collection<?> tasks, Exception ex) {
        return ex.getClass().getSimpleName() + " running tasks " + tasks + " on sequence key " + sequenceKey + ": "
                + ex.getMessage();
    }

    private final ConcurrentMap<Object, GlobalConcurrencyBoundedRunningTasksCountingExecutorService>
            sequentialExecutors;
    private final ObjectPool<GlobalConcurrencyBoundedRunningTasksCountingExecutorService> executorPool;

    private ConseqService(Builder builder) {
        this.sequentialExecutors = Objects.requireNonNull(builder.sequentialExecutors);
        this.executorPool = new GenericObjectPool<>(pooledExecutorFactory(builder), executorPoolConfig(builder));
    }

    @Override public void execute(Object sequenceKey, Runnable runnable) {
        sequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService computed =
                    computeExecutor(presentSequenceKey, presentExecutor);
            computed.execute(runnable);
            return computed;
        });
    }

    private GlobalConcurrencyBoundedRunningTasksCountingExecutorService computeExecutor(Object presentSequenceKey,
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService presentExecutor) {
        if (presentExecutor != null) {
            return presentExecutor;
        }
        final GlobalConcurrencyBoundedRunningTasksCountingExecutorService computed;
        try {
            computed = executorPool.borrowObject();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to borrow executor from pool " + executorPool, ex);
        }
        computed.addListener(
                new SweepingExecutorServiceListener(presentSequenceKey, sequentialExecutors, executorPool));
        return computed;
    }

    @Override public <T> Future<T> submit(Object sequenceKey, Callable<T> task) {
        FutureHolder<T> futureHolder = new FutureHolder<>();
        sequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService computed =
                    computeExecutor(presentSequenceKey, presentExecutor);
            futureHolder.set(computed.submit(task));
            return computed;
        });
        return futureHolder.get();
    }

    @Override public <T> Future<T> submit(Object sequenceKey, Runnable task, T result) {
        FutureHolder<T> futureHolder = new FutureHolder<>();
        sequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService computed =
                    computeExecutor(presentSequenceKey, presentExecutor);
            futureHolder.set(computed.submit(task, result));
            return computed;
        });
        return futureHolder.get();
    }

    @Override public Future<?> submit(Object sequenceKey, Runnable task) {
        FutureHolder<Void> futureHolder = new FutureHolder<>();
        sequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService computedExecutor =
                    computeExecutor(presentSequenceKey, presentExecutor);
            futureHolder.set((RunnableFuture<Void>) computedExecutor.submit(task));
            return computedExecutor;
        });
        return futureHolder.get();
    }

    @Override public <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks) {
        FuturesHolder<T> futuresHolder = new FuturesHolder<>();
        sequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService computedExecutor =
                    computeExecutor(presentSequenceKey, presentExecutor);
            try {
                final List<Future<T>> invokeAll = computedExecutor.invokeAll(tasks);
                futuresHolder.set(invokeAll);
            } catch (InterruptedException ex) {
                log.log(Level.SEVERE, taskSubmissionErrorMessage(presentSequenceKey, tasks, ex), ex);
                Thread.currentThread().interrupt();
            }
            return computedExecutor;
        });
        return futuresHolder.get();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks, long timeout,
            TimeUnit unit) {
        FuturesHolder<T> futuresHolder = new FuturesHolder<>();
        sequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService computed =
                    computeExecutor(presentSequenceKey, presentExecutor);
            try {
                futuresHolder.set(computed.invokeAll(tasks, timeout, unit));
            } catch (InterruptedException ex) {
                log.log(Level.SEVERE, taskSubmissionErrorMessage(presentSequenceKey, tasks, ex), ex);
                Thread.currentThread().interrupt();
            }
            return computed;
        });
        return futuresHolder.get();
    }

    @Override public <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks) {
        ResultHolder<T> resultHolder = new ResultHolder<>();
        sequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService computed =
                    computeExecutor(presentSequenceKey, presentExecutor);
            try {
                resultHolder.set(computed.invokeAny(tasks));
            } catch (InterruptedException ex) {
                log.log(Level.WARNING, taskSubmissionErrorMessage(presentSequenceKey, tasks, ex), ex);
                Thread.currentThread().interrupt();
            } catch (ExecutionException ex) {
                throw new IllegalStateException(taskSubmissionErrorMessage(presentSequenceKey, tasks, ex), ex);
            }
            return computed;
        });
        return resultHolder.get();
    }

    @Override
    public <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        ResultHolder<T> resultHolder = new ResultHolder<>();
        sequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService computed =
                    computeExecutor(presentSequenceKey, presentExecutor);
            try {
                resultHolder.set(computed.invokeAny(tasks, timeout, unit));
            } catch (InterruptedException ex) {
                log.log(Level.WARNING, taskSubmissionErrorMessage(presentSequenceKey, tasks, ex), ex);
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException ex) {
                throw new IllegalStateException(taskSubmissionErrorMessage(presentSequenceKey, tasks, ex), ex);
            }
            return computed;
        });
        return resultHolder.get();
    }

    @ToString @Log public static class Builder {

        public static final int DEFAULT_TASK_QUEUE_CAPACITY = Integer.MAX_VALUE;
        public static final int DEFAULT_GLOBAL_CONCURRENCY = Integer.MAX_VALUE;

        private final ConcurrentMap<Object, GlobalConcurrencyBoundedRunningTasksCountingExecutorService>
                sequentialExecutors = new ConcurrentHashMap<>();
        private int executorTaskQueueCapacity = DEFAULT_TASK_QUEUE_CAPACITY;
        private int globalConcurrency = DEFAULT_GLOBAL_CONCURRENCY;

        public ConseqService build() {
            log.log(Level.INFO, "Building conseq with builder {0}", this);
            final ConseqService conseq = new ConseqService(this);
            log.log(Level.FINEST, () -> "Built " + conseq);
            return conseq;
        }

        public Builder executorTaskQueueCapacity(int executorTaskQueueCapacity) {
            this.executorTaskQueueCapacity = executorTaskQueueCapacity;
            return this;
        }

        public Builder globalConcurrency(int globalConcurrency) {
            this.globalConcurrency = globalConcurrency;
            return this;
        }
    }

    private static class FutureHolder<T> {

        private Future<T> future = null;

        public void set(Future<T> future) {
            this.future = future;
        }

        public Future<T> get() {
            return this.future;
        }
    }

    private static class FuturesHolder<T> {

        private List<Future<T>> futures = null;

        public List<Future<T>> get() {
            return futures;
        }

        public void set(List<Future<T>> futures) {
            this.futures = futures;
        }
    }

    private static class ResultHolder<T> {

        T result;

        public T get() {
            return result;
        }

        public void set(T result) {
            this.result = result;
        }
    }
}
