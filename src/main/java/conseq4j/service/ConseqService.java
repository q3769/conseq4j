/*
 * The MIT License Copyright 2022 Qingtian Wang. Permission is hereby granted, free of charge, to
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

import lombok.Data;
import lombok.ToString;
import lombok.extern.java.Log;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * @author Qingtian Wang
 */
@Log @ToString public final class ConseqService implements ConcurrentSequencerService {

    private static final boolean VALIDATE_EXECUTOR_ON_RETURN_TO_POOL = true;
    private final ConcurrentMap<Object, RunningTasksCountingExecutorService> servicingSequentialExecutors =
            new ConcurrentHashMap<>();
    private final ObjectPool<RunningTasksCountingExecutorService> executorPool;

    private ConseqService(Builder builder) {
        this.executorPool = new GenericObjectPool<>(pooledExecutorFactory(builder), executorPoolConfig(builder));
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    private static PooledSingleThreadExecutorFactory pooledExecutorFactory(Builder builder) {
        return new PooledSingleThreadExecutorFactory(builder.executorTaskQueueCapacity);
    }

    private static GenericObjectPoolConfig<RunningTasksCountingExecutorService> executorPoolConfig(Builder builder) {
        final GenericObjectPoolConfig<RunningTasksCountingExecutorService> genericObjectPoolConfig =
                new GenericObjectPoolConfig<>();
        genericObjectPoolConfig.setMaxTotal(builder.globalConcurrency);
        genericObjectPoolConfig.setTestOnReturn(VALIDATE_EXECUTOR_ON_RETURN_TO_POOL);
        return genericObjectPoolConfig;
    }

    private static void logExecutionError(RunningTasksCountingExecutorService executor, Collection<?> tasks,
            Exception ex) {
        log.log(Level.SEVERE, "error executing tasks " + tasks + " by executor " + executor + " - " + ex.getClass()
                .getCanonicalName(), ex);
    }

    @Override public void execute(Object sequenceKey, Runnable runnable) {
        servicingSequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            RunningTasksCountingExecutorService computedExecutor = computeExecutor(presentSequenceKey, presentExecutor);
            computedExecutor.execute(runnable);
            return computedExecutor;
        });
    }

    private RunningTasksCountingExecutorService computeExecutor(Object presentSequenceKey,
            RunningTasksCountingExecutorService servicingExecutor) {
        if (servicingExecutor != null) {
            return servicingExecutor;
        }
        final RunningTasksCountingExecutorService pooledExecutor;
        try {
            pooledExecutor = executorPool.borrowObject();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to borrow executor from pool " + executorPool, ex);
        }
        pooledExecutor.addListener(
                new SweepingExecutorServiceListener(presentSequenceKey, servicingSequentialExecutors, executorPool));
        return pooledExecutor;
    }

    @Override public <T> Future<T> submit(Object sequenceKey, Callable<T> task) {
        FutureHolder<T> futureHolder = new FutureHolder<>();
        servicingSequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            RunningTasksCountingExecutorService computedExecutor = computeExecutor(presentSequenceKey, presentExecutor);
            futureHolder.setFuture(computedExecutor.submit(task));
            return computedExecutor;
        });
        return futureHolder.getFuture();
    }

    @Override public <T> Future<T> submit(Object sequenceKey, Runnable task, T result) {
        FutureHolder<T> futureHolder = new FutureHolder<>();
        servicingSequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            RunningTasksCountingExecutorService computedExecutor = computeExecutor(presentSequenceKey, presentExecutor);
            futureHolder.setFuture(computedExecutor.submit(task, result));
            return computedExecutor;
        });
        return futureHolder.getFuture();
    }

    @Override public Future<?> submit(Object sequenceKey, Runnable task) {
        FutureHolder<?> futureHolder = new FutureHolder<>();
        servicingSequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            RunningTasksCountingExecutorService computedExecutor = computeExecutor(presentSequenceKey, presentExecutor);
            futureHolder.setFutureUnbounded(computedExecutor.submit(task));
            return computedExecutor;
        });
        return futureHolder.getFutureUnbounded();
    }

    @Override public <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        FuturesHolder<T, InterruptedException> futuresHolder = new FuturesHolder<>();
        servicingSequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            RunningTasksCountingExecutorService computedExecutor = computeExecutor(presentSequenceKey, presentExecutor);
            try {
                final List<Future<T>> invokeAll = computedExecutor.invokeAll(tasks);
                futuresHolder.setFutures(invokeAll);
            } catch (InterruptedException ex) {
                logExecutionError(computedExecutor, tasks, ex);
                futuresHolder.setExecutionError(ex);
                Thread.currentThread().interrupt();
            }
            return computedExecutor;
        });
        return futuresHolder.get();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks, Duration timeout)
            throws InterruptedException {
        FuturesHolder<T, InterruptedException> futuresHolder = new FuturesHolder<>();
        servicingSequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            RunningTasksCountingExecutorService computedExecutor = computeExecutor(presentSequenceKey, presentExecutor);
            try {
                futuresHolder.setFutures(computedExecutor.invokeAll(tasks, timeout.toNanos(), TimeUnit.NANOSECONDS));
            } catch (InterruptedException ex) {
                logExecutionError(computedExecutor, tasks, ex);
                futuresHolder.setExecutionError(ex);
                Thread.currentThread().interrupt();
            }
            return computedExecutor;
        });
        return futuresHolder.get();
    }

    @Override public <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        ResultHolder<T, Exception> resultHolder = new ResultHolder<>();
        servicingSequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            RunningTasksCountingExecutorService computedExecutor = computeExecutor(presentSequenceKey, presentExecutor);
            try {
                resultHolder.setResult(computedExecutor.invokeAny(tasks));
            } catch (InterruptedException ex) {
                logExecutionError(computedExecutor, tasks, ex);
                resultHolder.setExecutionError(ex);
                Thread.currentThread().interrupt();
            } catch (ExecutionException ex) {
                logExecutionError(computedExecutor, tasks, ex);
                resultHolder.setExecutionError(ex);
            }
            return computedExecutor;
        });
        try {
            return resultHolder.get();
        } catch (InterruptedException | ExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutionException("unexpected error: " + e.getMessage(), e);
        }
    }

    @Override public <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        ResultHolder<T, Exception> resultHolder = new ResultHolder<>();
        servicingSequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            RunningTasksCountingExecutorService computedExecutor = computeExecutor(presentSequenceKey, presentExecutor);
            try {
                resultHolder.setResult(computedExecutor.invokeAny(tasks, timeout.toNanos(), TimeUnit.NANOSECONDS));
            } catch (InterruptedException ex) {
                logExecutionError(computedExecutor, tasks, ex);
                resultHolder.setExecutionError(ex);
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException ex) {
                logExecutionError(computedExecutor, tasks, ex);
                resultHolder.setExecutionError(ex);
            }
            return computedExecutor;
        });
        try {
            return resultHolder.get();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutionException("unexpected error: " + e.getMessage(), e);
        }
    }

    @ToString @Log public static class Builder {

        public static final int DEFAULT_GLOBAL_CONCURRENCY = Integer.MAX_VALUE;
        public static final int DEFAULT_TASK_QUEUE_CAPACITY = Integer.MAX_VALUE;

        private int executorTaskQueueCapacity = DEFAULT_TASK_QUEUE_CAPACITY;
        private int globalConcurrency = DEFAULT_GLOBAL_CONCURRENCY;

        public ConseqService build() {
            log.log(Level.INFO, "building conseq service using {0}", this);
            final ConseqService conseqService = new ConseqService(this);
            log.log(Level.FINEST, () -> "built " + conseqService);
            return conseqService;
        }

        public Builder executorTaskQueueCapacity(int executorTaskQueueCapacity) {
            if (executorTaskQueueCapacity != DEFAULT_TASK_QUEUE_CAPACITY) {
                log.log(Level.WARNING,
                        "may not be a good idea to limit task queue capacity. unless you intend to reject and fail all excessive tasks that the executor task queue cannot hold, consider using the default/unbounded capacity instead");
            }
            this.executorTaskQueueCapacity = executorTaskQueueCapacity;
            return this;
        }

        public Builder globalConcurrency(int globalConcurrency) {
            this.globalConcurrency = globalConcurrency;
            return this;
        }
    }

    @Data private static class FutureHolder<T> {

        Future<T> future;
        Future<?> futureUnbounded;
    }

    @Data private static class FuturesHolder<T, E extends Throwable> {

        List<Future<T>> futures;
        E executionError;

        List<Future<T>> get() throws E {
            if (executionError != null) {
                throw executionError;
            }
            return futures;
        }
    }

    @Data private static class ResultHolder<T, E extends Throwable> {

        T result;
        E executionError;

        public T get() throws E {
            if (executionError != null) {
                throw executionError;
            }
            return result;
        }
    }
}
