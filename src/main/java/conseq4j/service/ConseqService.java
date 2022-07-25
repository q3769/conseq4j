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
     * completion-stages of the {@link CompletableFuture} on the same key; i.e. the "main-line" execution.
     * <p>
     * A {@link ConcurrentMap} is employed to keep track of each sequence key's pending task execution stages. In a way,
     * each map entry represents a FIFO task execution queue of the entry's (sequence) key. The entry's value is to hold
     * the latest main-line execution stage - the tail of the FIFO task queue. Each submitted task will create a new
     * execution stage which is queued behind the previous task's execution stage. As part of the same atomic
     * transaction, the newly-enqueued execution stage also replaces the previous stage as the new value under the same
     * sequence key in the map. As the stages are queued, this new stage will not start executing before the previous
     * execution stage completes, and, will have to complete its execution before the next task's execution stage can
     * start executing. Such linear progression of the main-line execution stages ensures the sequential-ness of task
     * execution under the same sequence key.
     * <p>
     * A separate maintenance/cleanup stage is set up to run after the completion of each main-line execution stage.
     * This maintenance stage checks on the completion status of the latest main-line execution stage under the same
     * sequence key, and removes the checked stage from the execution queue map if the execution has completed. The
     * checked execution stage is the tail of the task execution queue, and may or may not be the same stage that
     * triggered this maintenance check. Unlike the execution stage, the maintenance stage is not part of the execution
     * queue; it may clean up and remove a completed execution stage from the queue/map, but does not disturb the
     * overall sequential-ness of the main-line executions. Meanwhile, as each completed main-line execution is always
     * triggering an "off-of-band" maintenance/cleanup check, collectively, this ensures that every main-line execution
     * stage ever put on the execution queue/map is eventually checked for completion and removal; i.e. no stage will
     * forever linger in the execution map.
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

}
