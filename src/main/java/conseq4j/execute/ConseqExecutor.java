/*
 * MIT License
 *
 * Copyright (c) 2021 Qingtian Wang
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

import static org.awaitility.Awaitility.await;

import conseq4j.Terminable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import lombok.NonNull;
import lombok.ToString;
import org.awaitility.core.ConditionFactory;

/**
 * Relies on the JDK {@link CompletableFuture} as the sequential executor of the tasks under the same sequence key.
 *
 * @author Qingtian Wang
 */
@ThreadSafe
@ToString
public final class ConseqExecutor implements SequentialExecutor, Terminable, AutoCloseable {

    private static final int DEFAULT_CONCURRENCY = Runtime.getRuntime().availableProcessors();
    private final Map<Object, CompletableFuture<?>> activeSequentialTasks = new ConcurrentHashMap<>();
    private final ExecutorService adminService = Executors.newSingleThreadExecutor();
    /**
     * The worker thread pool facilitates the overall async execution, independent of the submitted tasks. Any thread
     * from the pool can be used to execute any task, regardless of sequence keys. The pool capacity decides the overall
     * max parallelism of task execution.
     */
    private final ExecutorService workerExecutorService;

    private ConseqExecutor(ExecutorService workerExecutorService) {
        this.workerExecutorService = workerExecutorService;
    }

    /**
     * Returned executor uses {@link ForkJoinPool} of {@link Runtime#availableProcessors()} concurrency, with FIFO async
     * mode.
     *
     * @return conseq executor with default concurrency
     */
    public static @Nonnull ConseqExecutor instance() {
        return instance(DEFAULT_CONCURRENCY);
    }

    /**
     * Returned executor uses {@link ForkJoinPool} of specified concurrency to facilitate async operations, with FIFO
     * async mode.
     *
     * @param concurrency max number of tasks that can be run in parallel by the returned executor instance.
     * @return conseq executor with given concurrency
     */
    public static @Nonnull ConseqExecutor instance(int concurrency) {
        return instance(new ForkJoinPool(concurrency, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true));
    }

    /**
     * User can directly supply the (fully customized) worker thread pool to facilitate async operations of the returned
     * executor.
     *
     * @param workerExecutorService ExecutorService that backs the async operations of worker threads
     * @return instance of {@link ConseqExecutor}
     */
    public static @Nonnull ConseqExecutor instance(ExecutorService workerExecutorService) {
        return new ConseqExecutor(workerExecutorService);
    }

    private static <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private static ConditionFactory awaitForever() {
        return await().forever().pollDelay(Duration.ofMillis(10));
    }

    /**
     * @param command the command to run asynchronously in proper sequence
     * @param sequenceKey the key under which this task should be sequenced
     * @return future result of the command, not downcast-able from the basic {@link Future} interface.
     * @see ConseqExecutor#submit(Callable, Object)
     */
    @Override
    public CompletableFuture<Void> execute(@NonNull Runnable command, @NonNull Object sequenceKey) {
        return submit(Executors.callable(command, null), sequenceKey);
    }

    /**
     * Tasks of different sequence keys execute in parallel, pending thread availability from the backing
     * {@link #workerExecutorService}.
     * <p>
     * Sequential execution of tasks under the same/equal sequence key is achieved by linearly chaining/queuing the
     * task/work stages of the same key, leveraging the {@link CompletableFuture} API.
     * <p>
     * A {@link ConcurrentMap} is employed to keep track of each sequence key's pending tasks status. Each map entry
     * represents an active sequential executor in-service for all the tasks under the same sequence key; the entry's
     * value is to hold the most recently added task as the tail of the FIFO task queue of the active executor. With
     * this executor map, an active executor can be located by its sequence key so that a subsequent task of the same
     * key can be queued/chained behind the previous task. If no active executor exists in the map for the submitted
     * task's sequence key, a new entry/executor will be created. Before added to the tail of the task queue, each task
     * will be converted to a new corresponding work stage (of type {@link CompletableFuture}). If there already exists
     * an entry/work stage of the same sequence key in the map, the current work stage will be chained to execute behind
     * the existing work stage and replace the existing stage as the tail of the task queue in the map entry. This new
     * work stage will not start executing before the previous stage completes, and will have to finish executing before
     * the next task's work stage can start executing. Such linear progression of the work tasks/stages ensures the
     * sequential-ness of task execution under the same sequence key.
     * <p>
     * A separate administrative task/stage is triggered to run at the completion of each work task/stage. Under the
     * same sequence key, this admin task will locate the executor entry in the map, and remove the entry if its work
     * stage is complete. Otherwise, if the work stage is not complete, the admin task does nothing; the incomplete work
     * stage stays in the map under the same sequence key, ready to be trailed by the next work stage. This
     * administration ensures that every executor entry ever put on the map is eventually cleaned up and removed as long
     * as every work stage runs to complete. Although always chained after the completion of a work stage, the admin
     * task/stage is never added to the task work queue on the executor map and has no effect on the overall
     * sequential-ness of the work stage executions.
     *
     * @param task the task to be called asynchronously with proper sequence
     * @param sequenceKey the key under which this task should be sequenced
     * @return future result of the task, not downcast-able from the basic {@link Future} interface.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> submit(@NonNull Callable<T> task, @NonNull Object sequenceKey) {
        CompletableFuture<?> latestTask = activeSequentialTasks.compute(
                sequenceKey,
                (k, presentTask) -> (presentTask == null)
                        ? CompletableFuture.supplyAsync(() -> call(task), workerExecutorService)
                        : presentTask.handleAsync((r, e) -> call(task), workerExecutorService));
        CompletableFuture<?> copy = latestTask.thenApply(result -> result);
        latestTask.whenCompleteAsync(
                (r, e) -> activeSequentialTasks.computeIfPresent(
                        sequenceKey, (k, checkedTask) -> checkedTask.isDone() ? null : checkedTask),
                adminService);
        return (CompletableFuture<T>) copy;
    }

    /**
     * First wait until no more task pending, then orderly shutdown. For direct shutdown operations regardless of
     * pending tasks, use the {@link Terminable} methods instead.
     */
    @Override
    public void close() {
        awaitForever().until(this::noTaskPending);
        terminate();
        awaitForever().until(this::isTerminated);
    }

    boolean noTaskPending() {
        return activeSequentialTasks.isEmpty();
    }

    @Override
    public void terminate() {
        new Thread(() -> {
                    workerExecutorService.shutdown();
                    awaitForever().until(this::noTaskPending);
                    adminService.shutdown();
                })
                .start();
    }

    @Override
    public boolean isTerminated() {
        return workerExecutorService.isTerminated() && adminService.isTerminated();
    }

    @Override
    public @Nonnull List<Runnable> terminateNow() {
        List<Runnable> neverStartedTasks = workerExecutorService.shutdownNow();
        adminService.shutdownNow();
        return neverStartedTasks;
    }
}
