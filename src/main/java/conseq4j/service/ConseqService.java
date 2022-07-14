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

package conseq4j.service;

import lombok.Data;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.extern.java.Log;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * <p>
 * The default implementation of the main service API. Relies on the JDK {@code CompletableFuture} as sequential
 * executor of the tasks under the sequence key. For simplicity, the asynchronous execution facility is the default
 * {@link ForkJoinPool#commonPool()}, and cannot be customized by design.
 * </p>
 *
 * @author Qingtian Wang
 */
@Log @ToString public final class ConseqService implements ConcurrentSequencerService {

    private static final ExecutorService DEFAULT_THREAD_POOL = ForkJoinPool.commonPool();

    private final ConcurrentMap<Object, CompletableFuture<?>> sequentialExecutors = new ConcurrentHashMap<>();

    private final ExecutorService executionThreadPool;

    /**
     * Default constructor sets the global execution thread pool to be the default JDK
     * {@link ForkJoinPool#commonPool()}.
     */
    public ConseqService() {
        this.executionThreadPool = DEFAULT_THREAD_POOL;
    }

    /**
     * @param executionThreadPool the custom thread pool to facilitate the global async execution
     */
    public ConseqService(@NonNull ExecutorService executionThreadPool) {
        this.executionThreadPool = executionThreadPool;
    }

    /**
     * {@inheritDoc}
     */
    @Override public void execute(Runnable command, Object sequenceKey) {
        Objects.requireNonNull(command, "Runnable command cannot be NULL");
        Objects.requireNonNull(sequenceKey, "sequence key cannot be NULL");
        this.sequentialExecutors.compute(sequenceKey, (k, executor) -> {
            CompletableFuture<Void> replacementExecutor =
                    (executor == null) ? CompletableFuture.runAsync(command, this.executionThreadPool) :
                            executor.handleAsync((executionResult, executionException) -> {
                                if (executionException != null)
                                    log.log(Level.WARNING,
                                            executionException + " occurred in " + executor + " before executing next "
                                                    + command);
                                command.run();
                                return null;
                            }, this.executionThreadPool);
            sweepExecutorWhenComplete(replacementExecutor, sequenceKey);
            return replacementExecutor;
        });
    }

    /**
     * The thread pool to conduct the sweeping maintenance is the default {@link ForkJoinPool#commonPool()}, and cannot
     * be customized.
     *
     * @param executor    the executor to check and sweep if its execution is done
     * @param sequenceKey the key whose tasks are sequentially executed by the executor
     */
    private void sweepExecutorWhenComplete(CompletableFuture<?> executor, Object sequenceKey) {
        executor.handleAsync((executionResult, executionException) -> {
            new ExecutorSweeper(sequenceKey, this.sequentialExecutors).sweepIfDone();
            return null;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override public <T> Future<T> submit(Callable<T> task, Object sequenceKey) {
        Objects.requireNonNull(task, "Callable task cannot be NULL");
        Objects.requireNonNull(sequenceKey, "sequence key cannot be NULL");
        FutureHolder<T> resultHolder = new FutureHolder<>();
        this.sequentialExecutors.compute(sequenceKey, (k, executor) -> {
            CompletableFuture<T> replacementExecutor =
                    (executor == null) ? CompletableFuture.supplyAsync(() -> call(task), this.executionThreadPool) :
                            executor.handleAsync((executionResult, executionException) -> {
                                if (executionException != null)
                                    log.log(Level.WARNING,
                                            executionException + " occurred in " + executor + " before executing next "
                                                    + task);
                                return call(task);
                            }, this.executionThreadPool);
            resultHolder.setFuture(replacementExecutor);
            sweepExecutorWhenComplete(replacementExecutor, sequenceKey);
            return replacementExecutor;
        });
        return new MinimalFuture<>(resultHolder.getFuture());
    }

    int getActiveExecutorCount() {
        return this.sequentialExecutors.size();
    }

    private <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (Exception e) {
            log.log(Level.WARNING, "error executing user provided task " + task, e);
            throw new UncheckedExecutionException(e);
        }
    }

    String getExecutionThreadPoolTypeName() {
        return this.executionThreadPool.getClass().getName();
    }

    @Value private static class ExecutorSweeper {

        Object sequenceKey;
        ConcurrentMap<Object, CompletableFuture<?>> sequentialExecutors;

        public void sweepIfDone() {
            this.sequentialExecutors.compute(this.sequenceKey, (k, executor) -> {
                if (executor == null) {
                    log.log(Level.FINE, () -> "executor for sequence key " + this.sequenceKey
                            + " already swept off of active service map");
                    return null;
                }
                return sweeping(executor) ? null : executor;
            });
        }

        private boolean sweeping(CompletableFuture<?> executor) {
            boolean result = executor.isDone();
            if (result) {
                log.log(Level.FINE, () -> "sweeping executor " + executor + " off of active service map");
            } else {
                log.log(Level.FINE, () -> "keeping executor " + executor + " in active service map");
            }
            return result;
        }
    }

    private static class UncheckedExecutionException extends RuntimeException {

        public UncheckedExecutionException(Exception e) {
            super(e);
        }
    }

    @Data private static class FutureHolder<T> {

        Future<T> future;
    }

    /**
     * Making it impossible to downcast this wrapper's instances any further from {@link Future}
     *
     * @param <V> type of result held by the Future
     */
    private static final class MinimalFuture<V> implements Future<V> {

        private final Future<V> toMinimalize;

        private MinimalFuture(@NonNull Future<V> toMinimalize) {
            this.toMinimalize = toMinimalize;
        }

        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            return this.toMinimalize.cancel(mayInterruptIfRunning);
        }

        @Override public boolean isCancelled() {
            return this.toMinimalize.isCancelled();
        }

        @Override public boolean isDone() {
            return this.toMinimalize.isDone();
        }

        @Override public V get() throws InterruptedException, ExecutionException {
            return this.toMinimalize.get();
        }

        @Override public V get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return this.toMinimalize.get(timeout, unit);
        }
    }
}
