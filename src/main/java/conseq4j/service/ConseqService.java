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
import lombok.extern.java.Log;
import net.jcip.annotations.NotThreadSafe;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * The default implementation of the {@link ConcurrentSequencerService} API. Relies on the JDK {@link CompletableFuture}
 * as the sequential executor of the tasks under the same sequence key.
 *
 * @author Qingtian Wang
 */
@NotThreadSafe @Log @ToString public final class ConseqService implements ConcurrentSequencerService {

    private static final ExecutorService DEFAULT_THREAD_POOL = ForkJoinPool.commonPool();

    private final ConcurrentMap<Object, CompletableFuture<?>> sequentialExecutors = new ConcurrentHashMap<>();

    private final ExecutorService executionThreadPool;

    /**
     * Default constructor sets the global execution thread pool to be the default JDK
     * {@link ForkJoinPool#commonPool()}.
     */
    public ConseqService() {
        this(DEFAULT_THREAD_POOL);
    }

    /**
     * @param executionThreadPool the custom thread pool to facilitate the global async execution
     */
    public ConseqService(@NonNull ExecutorService executionThreadPool) {
        this.executionThreadPool = executionThreadPool;
        log.fine(() -> "constructed " + this);
    }

    private static <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (Exception e) {
            log.log(Level.WARNING, "error executing user provided task " + task, e);
            throw new UncheckedExecutionException(e);
        }
    }

    /**
     * Sequential execution of tasks under the same/equal sequence key is achieved by linearly processing the
     * completion-stages of the {@link CompletableFuture} of the same key; i.e. the "main-line" execution.
     * <p>
     * A {@link ConcurrentMap} is employed to keep track of each sequence key's pending task execution stages. Keyed on
     * the sequence key, the corresponding value is to hold the latest main-line execution stage of the sequence key -
     * the tail of the FIFO task execution queue for the same sequence key if you will. Each submitted task will create
     * a new execution stage which is stacked on top of the previous task's execution stage. As part of the same atomic
     * transaction of the stage stacking, the newly-created execution stage also replaces the previous stage as the new
     * value under the same sequence key in the map. The new stage will not start executing before the previous
     * execution stage completes, and, will have completed its execution before the next task's execution stage can
     * start executing. Such linear progression of the main-line execution stages ensure the sequential-ness of task
     * execution under the same sequence key.
     * <p>
     * Outside the main-line progression, a separate maintenance stage is stacked upon each main-line execution stage.
     * After the execution stage completes, this maintenance stage checks on the completion status of the latest
     * main-line execution stage (may not be the same one that triggered this maintenance check) under the same sequence
     * key, and removes the checked stage from the map if its execution has completed. Never set/served as a value on
     * the execution map, the maintenance stage does not alter the sequential nature of the main-line stage progression,
     * so it does not disturb the sequential-ness of the main-line executions. Meanwhile, as each completed main-line
     * execution is triggering an "off-of-band" maintenance/cleanup check, collectively, this ensures that every
     * execution stage ever put on the execution map is eventually checked for completion and removal; i.e. no main-line
     * execution stage will forever linger in the execution map.
     */
    @Override public void execute(Runnable command, Object sequenceKey) {
        Objects.requireNonNull(command, "Runnable command cannot be NULL");
        Objects.requireNonNull(sequenceKey, "sequence key cannot be NULL");
        this.sequentialExecutors.compute(sequenceKey, (k, currentExecutionStage) -> {
            CompletableFuture<Void> nextExecutionStage =
                    (currentExecutionStage == null) ? CompletableFuture.runAsync(command, this.executionThreadPool) :
                            currentExecutionStage.handleAsync((currentResult, currentException) -> {
                                if (currentException != null)
                                    log.log(Level.WARNING, currentException + " occurred in " + currentExecutionStage
                                            + " before executing next " + command);
                                command.run();
                                return null;
                            }, this.executionThreadPool);
            sweepExecutorWhenComplete(nextExecutionStage, sequenceKey);
            return nextExecutionStage;
        });
    }

    /**
     * The thread pool to conduct the sweeping maintenance is the default {@link ForkJoinPool#commonPool()}, and cannot
     * be customized. The executor sweeper runs after the completion of the stage's execution. This ensures this stage
     * executor under the same sequence key will always be checked and cleaned up if it has not been swept off by
     * earlier sweeps for the same sequence key; thus, no executor can linger forever after its completion.
     *
     * @param executionStage the executionStage to check and sweep if its execution is done
     * @param sequenceKey    the key whose tasks are sequentially executed by the executionStage
     */
    private void sweepExecutorWhenComplete(CompletableFuture<?> executionStage, Object sequenceKey) {
        executionStage.whenCompleteAsync((executionResult, executionException) -> new ExecutorSweeper(sequenceKey,
                this.sequentialExecutors).sweepIfDone());
    }

    /**
     * @see ConseqService#execute(Runnable, Object)
     */
    @Override public <T> Future<T> submit(Callable<T> task, Object sequenceKey) {
        Objects.requireNonNull(task, "Callable task cannot be NULL");
        Objects.requireNonNull(sequenceKey, "sequence key cannot be NULL");
        FutureHolder<T> resultHolder = new FutureHolder<>();
        this.sequentialExecutors.compute(sequenceKey, (k, currentExecutionStage) -> {
            CompletableFuture<T> nextExecutionStage = (currentExecutionStage == null) ?
                    CompletableFuture.supplyAsync(() -> call(task), this.executionThreadPool) :
                    currentExecutionStage.handleAsync((currentResult, currentException) -> {
                        if (currentException != null)
                            log.log(Level.WARNING, currentException + " occurred in " + currentExecutionStage
                                    + " before executing next " + task);
                        return call(task);
                    }, this.executionThreadPool);
            resultHolder.setFuture(nextExecutionStage);
            sweepExecutorWhenComplete(nextExecutionStage, sequenceKey);
            return nextExecutionStage;
        });
        return new MinimalFuture<>(resultHolder.getFuture());
    }

    int getActiveExecutorCount() {
        return this.sequentialExecutors.size();
    }

    String getExecutionThreadPoolTypeName() {
        return this.executionThreadPool.getClass().getName();
    }

    private static final class ExecutorSweeper {

        final Object sequenceKey;
        final ConcurrentMap<Object, CompletableFuture<?>> sequentialExecutors;

        private ExecutorSweeper(Object sequenceKey, ConcurrentMap<Object, CompletableFuture<?>> sequentialExecutors) {
            this.sequenceKey = sequenceKey;
            this.sequentialExecutors = sequentialExecutors;
        }

        public void sweepIfDone() {
            this.sequentialExecutors.compute(this.sequenceKey, (k, executionStage) -> {
                if (executionStage == null) {
                    log.log(Level.FINE, () -> "executionStage for sequence key " + this.sequenceKey
                            + " already swept off of active service map");
                    return null;
                }
                boolean done = executionStage.isDone();
                if (done) {
                    log.log(Level.FINE,
                            () -> "sweeping executionStage " + executionStage + " off of active service map");
                    return null;
                }
                log.log(Level.FINE, () -> "keeping executionStage " + executionStage + " in active service map");
                return executionStage;
            });
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

        private final Future<V> future;

        private MinimalFuture(@NonNull Future<V> future) {
            if (future instanceof MinimalFuture)
                throw new IllegalStateException("already a minimalized Future:" + future);
            this.future = future;
        }

        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            return this.future.cancel(mayInterruptIfRunning);
        }

        @Override public boolean isCancelled() {
            return this.future.isCancelled();
        }

        @Override public boolean isDone() {
            return this.future.isDone();
        }

        @Override public V get() throws InterruptedException, ExecutionException {
            return this.future.get();
        }

        @Override public V get(long timeout, @NonNull TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return this.future.get(timeout, unit);
        }
    }
}
