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

package conseq4j.execute;

import elf4j.Logger;
import lombok.Data;
import lombok.NonNull;
import lombok.ToString;

import java.util.concurrent.*;

/**
 * Relies on the JDK {@link CompletableFuture} as the sequential executor of the tasks under the same sequence key.
 *
 * @author Qingtian Wang
 */
@ToString
final class StagingExecutor implements SequencingExecutor {
    private final Logger warn = Logger.instance(StagingExecutor.class).atWarn();
    private final ConcurrentMap<Object, CompletableFuture<?>> sequentialExecutors = new ConcurrentHashMap<>();

    private final ExecutorService executionThreadPool;

    /**
     * @param executionThreadPool the custom thread pool to facilitate the global async execution
     */
    public StagingExecutor(@NonNull ExecutorService executionThreadPool) {
        this.executionThreadPool = executionThreadPool;
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

    /**
     * Sequential execution of tasks under the same/equal sequence key is achieved by linearly processing the
     * completion-stages of the {@link CompletableFuture} on the same key; i.e. the "main-line" execution.
     * <p>
     * A {@link ConcurrentMap} is employed to keep track of each sequence key's pending tasks. Each map entry represents
     * an active sequential executor in-service for all the tasks under the same sequence/entry key; the entry's value
     * is to hold the most recently added task (completion stage), i.e. the tail of the FIFO task queue of the active
     * executor. With this executor map, an active executor can be located by its sequence key so that further
     * tasks/stages of the same key can be queued behind the previous task(s) of the same executor. If no active
     * executor exists in the map for the submitted task's sequence key, a new entry/executor will be created. Each
     * submitted task will create a new corresponding main-line completion stage which is always put on the executor map
     * - either as the head (and tail) of a new executor's task queue, or the tail of an existing executor's task queue.
     * This new stage will not start executing before the previous stage completes, and, will have to complete execution
     * before the next task's completion stage can start executing. Such linear progression of the main-line
     * tasks/stages ensures the sequential-ness of task execution under the same sequence key.
     * <p>
     * A separate maintenance/cleanup stage is set up to run after the completion of each main-line task/stage. This
     * maintenance stage will sweep the executor entry off of the map if all tasks of the executor are completed. It
     * ensures that every executor ever put on the map is eventually removed after all its tasks are done executing;
     * i.e. no entry will forever linger in the executor map. Unlike a main-line task/stage, a maintenance stage is
     * never put in a task queue or the executor map, and has no effect on the overall sequential-ness of the main-line
     * executions.
     *
     * @param command     the command to run asynchronously in proper sequence
     * @param sequenceKey the key that the command should be queued behind such that all commands of the same key are
     *                    sequentially executed
     */
    @Override
    public Future<Void> execute(@NonNull Runnable command, @NonNull Object sequenceKey) {
        RunFutureHolder runFutureHolder = new RunFutureHolder();
        CompletableFuture<?> commandStage =
                this.sequentialExecutors.compute(sequenceKey, (sameSequenceKey, currentExecutionStage) -> {
                    CompletableFuture<Void> nextExecutionStage = (currentExecutionStage == null) ?
                            CompletableFuture.runAsync(command, this.executionThreadPool) :
                            currentExecutionStage.handleAsync((currentResult, currentException) -> {
                                if (currentException != null) {
                                    warn.log("[{}] occurred in [{}] before executing command [{}]",
                                            currentException,
                                            currentExecutionStage,
                                            command);
                                }
                                command.run();
                                return null;
                            }, this.executionThreadPool);
                    runFutureHolder.setFuture(nextExecutionStage);
                    return nextExecutionStage;
                });
        sweepExecutorIfAllTasksComplete(sequenceKey, commandStage);
        return new SimpleFuture<>(runFutureHolder.getFuture());
    }

    /**
     * @param task        the task to be called asynchronously with proper sequence
     * @param sequenceKey the key under which this task should be sequenced
     * @return future result of the task. Not downcast-able.
     * @see StagingExecutor#execute(Runnable, Object)
     */
    @Override
    public <T> Future<T> submit(@NonNull Callable<T> task, @NonNull Object sequenceKey) {
        SubmitFutureHolder<T> submitFutureHolder = new SubmitFutureHolder<>();
        CompletableFuture<?> taskStage =
                this.sequentialExecutors.compute(sequenceKey, (sameSequenceKey, currentExecutionStage) -> {
                    CompletableFuture<T> nextExecutionStage = (currentExecutionStage == null) ?
                            CompletableFuture.supplyAsync(() -> call(task), this.executionThreadPool) :
                            currentExecutionStage.handleAsync((currentResult, currentException) -> {
                                if (currentException != null) {
                                    warn.log("[{}] occurred in [{}] before executing task [{}]",
                                            currentException,
                                            currentExecutionStage,
                                            task);
                                }
                                return call(task);
                            }, this.executionThreadPool);
                    submitFutureHolder.setFuture(nextExecutionStage);
                    return nextExecutionStage;
                });
        sweepExecutorIfAllTasksComplete(sequenceKey, taskStage);
        return new SimpleFuture<>(submitFutureHolder.getFuture());
    }

    /**
     * The thread pool to conduct the executor sweeping maintenance is the default {@link ForkJoinPool#commonPool()},
     * and cannot be customized.
     *
     * @param sequenceKey the key whose tasks are sequentially executed
     * @param triggerTask the task/stage that triggers a check and possible sweep of the executor from the map if
     *                    executor's tail task in queue is done at the time of checking
     */
    private void sweepExecutorIfAllTasksComplete(Object sequenceKey, CompletableFuture<?> triggerTask) {
        triggerTask.whenCompleteAsync((anyResult, anyException) -> sequentialExecutors.computeIfPresent(sequenceKey,
                (sameSequenceKey, tailTask) -> tailTask.isDone() ? null : tailTask));
    }

    int getActiveExecutorCount() {
        return this.sequentialExecutors.size();
    }

    String getExecutionThreadPoolTypeName() {
        return this.executionThreadPool.getClass().getName();
    }

    private static class UncheckedExecutionException extends RuntimeException {

        public UncheckedExecutionException(Exception e) {
            super(e);
        }
    }

    @Data
    private static class RunFutureHolder {

        Future<Void> future;
    }

    @Data
    private static class SubmitFutureHolder<T> {

        Future<T> future;
    }

    /**
     * Making it impossible to downcast this wrapper's instances any further from {@link Future}
     *
     * @param <V> type of result held by the Future
     */
    private static final class SimpleFuture<V> implements Future<V> {

        private final Future<V> future;

        SimpleFuture(@lombok.NonNull Future<V> future) {
            this.future = future;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return this.future.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return this.future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return this.future.isDone();
        }

        @Override
        public V get() throws InterruptedException, java.util.concurrent.ExecutionException {
            return this.future.get();
        }

        @Override
        public V get(long timeout, @lombok.NonNull TimeUnit unit)
                throws InterruptedException, java.util.concurrent.ExecutionException,
                java.util.concurrent.TimeoutException {
            return this.future.get(timeout, unit);
        }
    }
}
