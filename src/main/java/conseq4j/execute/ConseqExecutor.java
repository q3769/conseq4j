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

import lombok.NonNull;
import lombok.ToString;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Relies on the JDK {@link CompletableFuture} as the sequential executor of the tasks under the same sequence key.
 *
 * @author Qingtian Wang
 */
@ThreadSafe
@ToString
public final class ConseqExecutor implements SequentialExecutor {
    private static final int DEFAULT_CONCURRENCY = Math.max(16, Runtime.getRuntime().availableProcessors());
    private static final int DEFAULT_WORK_QUEUE_CAPACITY = Integer.MAX_VALUE;
    private static final int FOREVER = Integer.MAX_VALUE;
    private static final ThreadPoolExecutor.AbortPolicy DEFAULT_REJECTED_HANDLER = new ThreadPoolExecutor.AbortPolicy();
    private static final Builder.WorkQueueType DEFAULT_WORK_QUEUE_TYPE = Builder.WorkQueueType.LINKED;

    private final Map<Object, CompletableFuture<?>> sequentialExecutors = new ConcurrentHashMap<>();
    private final ExecutorService adminThread = Executors.newSingleThreadExecutor();
    /**
     * The worker thread pool facilitates the overall async execution, independent of the submitted tasks. Any thread
     * from the pool can be used to execute any task, regardless of sequence keys. The pool capacity decides the overall
     * max parallelism of task execution.
     */
    private final ThreadPoolExecutor workerThreadPool;

    private ConseqExecutor(@Nonnull Builder builder) {
        this(new ThreadPoolExecutor(builder.concurrency,
                builder.concurrency,
                0,
                TimeUnit.MILLISECONDS,
                builder.workQueueType == Builder.WorkQueueType.ARRAY ?
                        new ArrayBlockingQueue<>(builder.workQueueCapacity) :
                        new LinkedBlockingQueue<>(builder.workQueueCapacity),
                Executors.defaultThreadFactory(),
                builder.rejectedExecutionHandler));
    }

    private ConseqExecutor(ThreadPoolExecutor workerThreadPool) {
        this.workerThreadPool = workerThreadPool;
    }

    /**
     * @return conseq executor with default concurrency
     */
    public static @Nonnull ConseqExecutor newInstance() {
        return new Builder().build();
    }

    /**
     * @param concurrency
     *         max number of tasks that can be run in parallel by the returned executor instance. This is set as the max
     *         capacity of the {@link #workerThreadPool}
     * @return conseq executor with given concurrency
     */
    public static @Nonnull ConseqExecutor newInstance(int concurrency) {
        return new Builder().concurrency(concurrency).build();
    }

    /**
     * @param workerThreadPool
     *         the worker thread pool that facilitates the overall async execution, independent of the submitted tasks.
     * @return new ConseqExecutor instance backed by the specified workerThreadPool
     */
    public static ConseqExecutor from(ThreadPoolExecutor workerThreadPool) {
        return new ConseqExecutor(workerThreadPool);
    }

    private static <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    /**
     * @param command
     *         the command to run asynchronously in proper sequence
     * @param sequenceKey
     *         the key under which this task should be sequenced
     * @return future result of the command, not downcast-able from the basic {@link Future} interface.
     * @see ConseqExecutor#submit(Callable, Object)
     */
    @Override
    public CompletableFuture<Void> execute(@NonNull Runnable command, @NonNull Object sequenceKey) {
        return submit(Executors.callable(command, null), sequenceKey);
    }

    /**
     * Tasks of different sequence keys execute in parallel, pending thread availability from the backing
     * {@link #workerThreadPool}.
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
     * @param task
     *         the task to be called asynchronously with proper sequence
     * @param sequenceKey
     *         the key under which this task should be sequenced
     * @return future result of the task, not downcast-able from the basic {@link Future} interface.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> submit(@NonNull Callable<T> task, @NonNull Object sequenceKey) {
        CompletableFuture<?> taskFifoQueueTail = sequentialExecutors.compute(sequenceKey,
                (k, presentTail) -> (presentTail == null) ?
                        CompletableFuture.supplyAsync(() -> call(task), workerThreadPool) :
                        presentTail.handleAsync((r, e) -> call(task), workerThreadPool));
        taskFifoQueueTail.whenCompleteAsync((r, e) -> sequentialExecutors.computeIfPresent(sequenceKey,
                        (k, checkedTaskFifoQueueTail) -> checkedTaskFifoQueueTail.isDone() ? null : checkedTaskFifoQueueTail),
                adminThread);
        return (CompletableFuture<T>) taskFifoQueueTail.thenApply(r -> r);
    }

    @Override
    public void shutdown() {
        ExecutorService shutdownThread = Executors.newSingleThreadExecutor();
        shutdownThread.execute(() -> {
            this.workerThreadPool.shutdown();
            while (true) {
                try {
                    if (this.workerThreadPool.awaitTermination(FOREVER, TimeUnit.SECONDS)) {
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            this.adminThread.shutdown();
        });
        shutdownThread.shutdown();
    }

    @Override
    public boolean isTerminated() {
        return this.workerThreadPool.isTerminated() && this.adminThread.isTerminated();
    }

    @Override
    public boolean isActive() {
        return workerThreadPool.getActiveCount() > 0;
    }

    int estimateActiveExecutorCount() {
        return this.sequentialExecutors.size();
    }

    /**
     * {@code ConseqExecutor} builder static inner class.
     */
    public static final class Builder {
        private int concurrency;
        private int workQueueCapacity;
        private WorkQueueType workQueueType;
        private RejectedExecutionHandler rejectedExecutionHandler;

        /**
         *
         */
        public Builder() {
            concurrency = DEFAULT_CONCURRENCY;
            workQueueCapacity = DEFAULT_WORK_QUEUE_CAPACITY;
            rejectedExecutionHandler = DEFAULT_REJECTED_HANDLER;
            workQueueType = DEFAULT_WORK_QUEUE_TYPE;
        }

        /**
         * Sets the {@code concurrency} and returns a reference to this Builder enabling method chaining.
         *
         * @param concurrency
         *         the {@code concurrency} to set
         * @return a reference to this Builder
         */
        public Builder concurrency(int concurrency) {
            this.concurrency = concurrency;
            return this;
        }

        /**
         * Sets the {@code workQueueCapacity} and returns a reference to this Builder enabling method chaining.
         *
         * @param workQueueCapacity
         *         the {@code workQueueCapacity} to set
         * @return a reference to this Builder
         */
        public Builder workQueueCapacity(int workQueueCapacity) {
            this.workQueueCapacity = workQueueCapacity;
            return this;
        }

        /**
         * @param rejectedExecutionHandler
         *         handler executed by the caller thread if a task is rejected by the conseq executor, maybe e.g.
         *         because of work queue is full. Default is {@link ThreadPoolExecutor.AbortPolicy}
         * @return a reference to this Builder
         */
        public Builder rejectedExecutionHandler(RejectedExecutionHandler rejectedExecutionHandler) {
            this.rejectedExecutionHandler = rejectedExecutionHandler;
            return this;
        }

        /**
         * Returns a {@code ConseqExecutor} built from the parameters previously set.
         *
         * @return a {@code ConseqExecutor} built with parameters of this {@code ConseqExecutor.Builder}
         */
        public @Nonnull ConseqExecutor build() {
            return new ConseqExecutor(this);
        }

        /**
         * @param workQueueType
         *         {@link WorkQueueType#LINKED} meaning {@link LinkedBlockingQueue}, or  {@link WorkQueueType#ARRAY}
         *         meaning {@link ArrayBlockingQueue}, default to {@link WorkQueueType#LINKED}
         * @return a reference to this Builder
         */
        public Builder workQueueType(WorkQueueType workQueueType) {
            this.workQueueType = workQueueType;
            return this;
        }

        /**
         *
         */
        public enum WorkQueueType {
            /**
             *
             */
            LINKED,
            /**
             *
             */
            ARRAY
        }
    }
}
