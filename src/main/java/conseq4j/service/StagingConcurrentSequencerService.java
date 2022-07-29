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
import net.jcip.annotations.ThreadSafe;

import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * Relies on the JDK {@link CompletableFuture} as the sequential executor of the tasks under the same sequence key.
 *
 * @author Qingtian Wang
 */
@ThreadSafe @Log @ToString final class StagingConcurrentSequencerService implements ConcurrentSequencerService {

    private static final ExecutorService DEFAULT_THREAD_POOL = ForkJoinPool.commonPool();

    private final ConcurrentMap<Object, CompletableFuture<?>> sequentialExecutors = new ConcurrentHashMap<>();

    private final ExecutorService executionThreadPool;

    /**
     * Default constructor sets the global execution thread pool to be the default JDK
     * {@link ForkJoinPool#commonPool()}.
     */
    StagingConcurrentSequencerService() {
        this(null);
    }

    /**
     * @param executionThreadPool the custom thread pool to facilitate the global async execution
     */
    StagingConcurrentSequencerService(ExecutorService executionThreadPool) {
        this.executionThreadPool = executionThreadPool == null ? DEFAULT_THREAD_POOL : executionThreadPool;
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
     * each map entry represents a sequential executor for all the tasks under the same sequence/entry key; the entry's
     * value is to hold the most recently added main-line task (execution stage) i.e. the tail of the FIFO task queue
     * for the same sequence key. Each submitted task will create a new corresponding main-line execution stage which is
     * queued behind the previous task's execution stage. As part of the same atomic transaction, the newly-created
     * execution stage, as the tail of the FIFO task queue, also replaces the previous execution stage as the new value
     * under the same sequence key in the executor map. As the stages are queued, this new stage will not start
     * executing before the previous execution stage completes, and, will have to end executing before the next task's
     * execution stage can start executing. Such linear progression of the main-line execution stages ensures the
     * sequential-ness of task execution under the same sequence key.
     * <p>
     * A separate maintenance/cleanup stage is set up to run after the completion of each main-line execution stage in
     * the FIFO task queue. This maintenance stage checks on the completion status of the most recent main-line
     * execution stage under the same sequence key, and removes the checked stage (thus the entire executor entry) from
     * the executor map if the execution has completed. The checked execution stage is the tail of the task queue, and
     * may or may not be the same stage that triggered this maintenance check. Unlike the execution stage, the
     * maintenance stage is not part of the main-line execution task queue; it may clean up and remove a completed
     * execution stage from the map, but does not disturb the overall sequential-ness of the main-line executions.
     * Meanwhile, as each completed main-line execution is always triggering an "off-of-band" maintenance/cleanup check,
     * collectively, this ensures that every main-line execution stage ever put on the executor map is eventually
     * checked for completion and removal; i.e. no executor entry will forever linger in the map.
     */
    @Override public void execute(@NonNull Runnable command, @NonNull Object sequenceKey) {
        this.sequentialExecutors.compute(sequenceKey, (k, currentExecutionStage) -> {
            CompletableFuture<Void> nextExecutionStage =
                    (currentExecutionStage == null) ? CompletableFuture.runAsync(command, this.executionThreadPool) :
                            currentExecutionStage.handleAsync((currentResult, currentException) -> {
                                if (currentException != null)
                                    log.log(Level.WARNING, currentException + " occurred in " + currentExecutionStage
                                            + " before executing next command: " + command);
                                command.run();
                                return null;
                            }, this.executionThreadPool);
            sweepExecutorIfTailExecutionStageComplete(nextExecutionStage, sequenceKey);
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
    private void sweepExecutorIfTailExecutionStageComplete(CompletableFuture<?> executionStage, Object sequenceKey) {
        executionStage.whenCompleteAsync((executionResult, executionException) -> new ExecutorSweeper(sequenceKey,
                this.sequentialExecutors).sweepIfDone());
    }

    /**
     * @see StagingConcurrentSequencerService#execute(Runnable, Object)
     */
    @Override public <T> Future<T> submit(@NonNull Callable<T> task, @NonNull Object sequenceKey) {
        FutureHolder<T> resultHolder = new FutureHolder<>();
        this.sequentialExecutors.compute(sequenceKey, (k, currentExecutionStage) -> {
            CompletableFuture<T> nextExecutionStage = (currentExecutionStage == null) ?
                    CompletableFuture.supplyAsync(() -> call(task), this.executionThreadPool) :
                    currentExecutionStage.handleAsync((currentResult, currentException) -> {
                        if (currentException != null)
                            log.log(Level.WARNING, currentException + " occurred in " + currentExecutionStage
                                    + " before executing next task: " + task);
                        return call(task);
                    }, this.executionThreadPool);
            resultHolder.setFuture(nextExecutionStage);
            sweepExecutorIfTailExecutionStageComplete(nextExecutionStage, sequenceKey);
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
            this.sequentialExecutors.compute(this.sequenceKey, (k, currentStage) -> {
                if (currentStage == null) {
                    log.log(Level.FINER,
                            () -> "tail task stage for sequence key " + k + " already swept off of executor map");
                    return null;
                }
                boolean done = currentStage.isDone();
                if (done) {
                    log.log(Level.FINER, () -> "sweeping tail stage " + currentStage + " for sequence key " + k
                            + " off of executor map");
                    return null;
                }
                log.log(Level.FINER,
                        () -> "keeping tail stage " + currentStage + " for sequence key " + k + " in executor map");
                return currentStage;
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
