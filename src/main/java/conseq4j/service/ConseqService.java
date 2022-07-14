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
     * Sequential execution of tasks under the same/equal sequence key is achieved by linearly processing the
     * completion-stages of the {@link CompletableFuture} of the same key; i.e. the "main-line" execution.
     * <p/>
     * A {@link ConcurrentMap} keyed on the task's sequence key is employed. The corresponding value is to hold the
     * latest main-line stage - or the tail of the FIFO execution queue for the same sequence key if you will. Each
     * current task always creates a new main-line execution stage which is stacked on top of the previous stage. As an
     * atomic transaction to this stage stacking, the just-created current main-line stage also replaces the previous
     * stage as the new value under the same sequence key in the map. The current stage will not start executing before
     * the previous stage completes, and, will have completed its execution before the next task's main-line stage can
     * start executing. Such linear progression of the main-line stages ensure sequential execution of tasks under the
     * same sequence key.
     * <p/>
     * Outside the main-line progression, a separate cleanup task/stage is stacked upon each main-line stage. After the
     * main-line stage completes, this cleanup task/stage checks on the completion status of the latest main-line stage
     * (may not be the same one that triggered this cleanup check) under the same sequence key, and removes the checked
     * stage from the map if its execution has completed. The cleanup task/stage never alters the linear
     * progression/stacking of the main-line stages, so it does not disturb the sequential-ness of the main-line
     * executions. As each main-line stage after its completion is triggering an "off-of-band" cleanup check,
     * collectively, this ensures that every main-line stage is checked for completion and removal from the service map
     * at some point of time; thus, no main-line stage will forever linger in the service map.
     */
    @Override public void execute(Runnable command, Object sequenceKey) {
        Objects.requireNonNull(command, "Runnable command cannot be NULL");
        Objects.requireNonNull(sequenceKey, "sequence key cannot be NULL");
        this.sequentialExecutors.compute(sequenceKey, (k, presentStageExecutor) -> {
            CompletableFuture<Void> nextStageExecutor =
                    (presentStageExecutor == null) ? CompletableFuture.runAsync(command, this.executionThreadPool) :
                            presentStageExecutor.handleAsync((executionResult, executionException) -> {
                                if (executionException != null)
                                    log.log(Level.WARNING, executionException + " occurred in " + presentStageExecutor
                                            + " before executing next " + command);
                                command.run();
                                return null;
                            }, this.executionThreadPool);
            sweepExecutorWhenComplete(nextStageExecutor, sequenceKey);
            return nextStageExecutor;
        });
    }

    /**
     * The thread pool to conduct the sweeping maintenance is the default {@link ForkJoinPool#commonPool()}, and cannot
     * be customized. The executor sweeper runs after the completion of the stage's execution. This ensures this stage
     * executor under the same sequence key will always be checked and cleaned up if it has not been swept off by
     * earlier sweeps for the same sequence key; thus, no executor can linger forever after its completion.
     *
     * @param stageExecutor the stageExecutor to check and sweep if its execution is done
     * @param sequenceKey   the key whose tasks are sequentially executed by the stageExecutor
     */
    private void sweepExecutorWhenComplete(CompletableFuture<?> stageExecutor, Object sequenceKey) {
        stageExecutor.handleAsync((executionResult, executionException) -> {
            new ExecutorSweeper(sequenceKey, this.sequentialExecutors).sweepIfDone();
            return null;
        });
    }

    /**
     * @see {@link #execute(Runnable, Object)}'s Javadoc
     */
    @Override public <T> Future<T> submit(Callable<T> task, Object sequenceKey) {
        Objects.requireNonNull(task, "Callable task cannot be NULL");
        Objects.requireNonNull(sequenceKey, "sequence key cannot be NULL");
        FutureHolder<T> resultHolder = new FutureHolder<>();
        this.sequentialExecutors.compute(sequenceKey, (k, presentStageExecutor) -> {
            CompletableFuture<T> nextStageExecutor = (presentStageExecutor == null) ?
                    CompletableFuture.supplyAsync(() -> call(task), this.executionThreadPool) :
                    presentStageExecutor.handleAsync((executionResult, executionException) -> {
                        if (executionException != null)
                            log.log(Level.WARNING, executionException + " occurred in " + presentStageExecutor
                                    + " before executing next " + task);
                        return call(task);
                    }, this.executionThreadPool);
            resultHolder.setFuture(nextStageExecutor);
            sweepExecutorWhenComplete(nextStageExecutor, sequenceKey);
            return nextStageExecutor;
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
