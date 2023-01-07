/*
 * MIT License
 *
 * Copyright (c) 2023 Qingtian Wang
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

package conseq4j.execute;

import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.Delegate;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.*;

/**
 * Relies on the JDK {@link CompletableFuture} as the sequential executor of the tasks under the same sequence key.
 *
 * @author Qingtian Wang
 */
@ThreadSafe
@ToString
public final class ConseqExecutor implements ConcurrentSequencingExecutor {
    private static final ExecutorService ADMIN_THREAD_POOL = Executors.newCachedThreadPool();
    private static final int DEFAULT_MINIMUM_CONCURRENCY = 16;
    private final ConcurrentMap<Object, CompletableFuture<?>> sequentialExecutors = new ConcurrentHashMap<>();
    private final ExecutorService workerThreadPool;

    private ConseqExecutor(int concurrency) {
        this.workerThreadPool = Executors.newFixedThreadPool(concurrency);
    }

    /**
     * @return conseq executor with default concurrency
     */
    public static ConseqExecutor newInstance() {
        return new ConseqExecutor(Math.max(Runtime.getRuntime().availableProcessors(), DEFAULT_MINIMUM_CONCURRENCY));
    }

    /**
     * @param concurrency max number of tasks that can be run in parallel by the returned executor instance
     * @return conseq executor with given concurrency
     */
    public static ConseqExecutor newInstance(int concurrency) {
        return new ConseqExecutor(concurrency);
    }

    private static <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UncheckedExecutionException(e);
        }
    }

    int estimateActiveExecutorCount() {
        return this.sequentialExecutors.size();
    }

    /**
     * @param command     the command to run asynchronously in proper sequence
     * @param sequenceKey the key under which this task should be sequenced
     * @see ConseqExecutor#submit(Callable, Object)
     */
    @Override
    public Future<Void> execute(@NonNull Runnable command, @NonNull Object sequenceKey) {
        return submit(Executors.callable(command, null), sequenceKey);
    }

    /**
     * Sequential execution of tasks under the same/equal sequence key is achieved by linearly chaining/queuing the
     * task/work stages of the same key, leveraging the {@link CompletableFuture} API.
     * <p>
     * A {@link ConcurrentMap} is employed to keep track of each sequence key's pending tasks status. Each map entry
     * represents an active sequential executor in-service for all the tasks under the same sequence key; the entry's
     * value is to hold the most recently added task as the tail of the FIFO task queue of the active executor. With
     * this executor map, an active executor can be located by its sequence key so that a subsequent task of the same
     * key can be queued behind the previous task. If no active executor exists in the map for the submitted task's
     * sequence key, a new entry/executor will be created. Before added to the tail of the task queue, each task will be
     * converted to a new corresponding work stage (of type {@link CompletableFuture}). If there already exists an
     * entry/work stage of the same sequence key in the map, the current work stage will be set to execute behind the
     * existing work stage and replace the existing stage as the tail of the task queue in the map entry. This new work
     * stage will not start executing before the previous stage completes, and will have to finish executing before the
     * next task's work stage can start executing. Such linear progression of the work tasks/stages ensures the
     * sequential-ness of task execution under the same sequence key.
     * <p>
     * A separate maintenance task/stage is set up to run after the completion of each work task/stage. Under the same
     * sequence key, this maintenance stage will locate the executor entry in the map, and sweep the entire executor
     * entry off of the map if the entry's work stage is complete; otherwise if the work stage is not complete, the
     * maintenance stage does nothing; the incomplete work stage stays in the map under the same sequence key, ready to
     * be trailed by the next work stage. This clean up maintenance ensures that every work stage ever put on the map is
     * eventually removed as long as every work stage runs to complete. Unlike a work task/stage, a maintenance
     * task/stage is never added in the task queue or the executor map, and has no effect on the overall sequential-ness
     * of the work stage executions.
     *
     * @param task        the task to be called asynchronously with proper sequence
     * @param sequenceKey the key under which this task should be sequenced
     * @return future result of the task, not downcast-able.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Future<T> submit(@NonNull Callable<T> task, @NonNull Object sequenceKey) {
        CompletableFuture<?> taskWorkStage = sequentialExecutors.compute(sequenceKey,
                (sameSequenceKey, existingWorkStage) -> (existingWorkStage == null) ?
                        CompletableFuture.supplyAsync(() -> call(task), workerThreadPool) :
                        existingWorkStage.handleAsync((completionResult, completionException) -> call(task),
                                workerThreadPool));
        sweepExecutorIfTailTaskComplete(sequenceKey, taskWorkStage);
        return new MinimalFuture<>((Future<T>) taskWorkStage);
    }

    /**
     * When trigger task is complete, check and de-list the executor entry if all is complete
     *
     * @param sequenceKey  the key whose tasks are sequentially executed
     * @param sweepTrigger the task/stage that triggers a check and possible sweep of the executor from the map if
     *                     executor's tail task in queue is done at the time of checking
     */
    private void sweepExecutorIfTailTaskComplete(Object sequenceKey, @NonNull CompletableFuture<?> sweepTrigger) {
        sweepTrigger.whenCompleteAsync((anyResult, anyException) -> sequentialExecutors.computeIfPresent(sequenceKey,
                (sameSequenceKey, tailWorkStage) -> tailWorkStage.isDone() ? null : tailWorkStage), ADMIN_THREAD_POOL);
    }

    /**
     * Making it impossible to downcast the wrapped instance any further from the minimum implementation of
     * {@link Future}
     *
     * @param <V> type of result held by the Future
     */
    private static final class MinimalFuture<V> implements Future<V> {
        @Delegate private final Future<V> future;

        MinimalFuture(@NonNull Future<V> future) {
            this.future = future;
        }
    }

    private static class UncheckedExecutionException extends RuntimeException {

        public UncheckedExecutionException(Exception e) {
            super(e);
        }
    }
}
