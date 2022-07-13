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
import lombok.ToString;
import lombok.Value;
import lombok.extern.java.Log;

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

    private final ConcurrentMap<Object, CompletableFuture<?>> sequentialExecutors = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override public void execute(Runnable command, Object sequenceKey) {
        this.sequentialExecutors.compute(sequenceKey, (k, executor) -> {
            CompletableFuture<Void> replacementExecutor = (executor == null) ? CompletableFuture.runAsync(command) :
                    executor.handleAsync((executionResult, executionException) -> {
                        if (executionException != null)
                            log.log(Level.WARNING,
                                    executionException + " occurred in " + executor + " before executing next "
                                            + command);
                        command.run();
                        return null;
                    });
            sweepExecutorWhenDone(replacementExecutor, sequenceKey);
            return replacementExecutor;
        });
    }

    private void sweepExecutorWhenDone(CompletableFuture<?> executor, Object sequenceKey) {
        executor.handleAsync((executionResult, executionException) -> {
            new ExecutorSweeper(sequenceKey, this.sequentialExecutors).sweepIfDone();
            return null;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override public <T> Future<T> submit(Callable<T> task, Object sequenceKey) {
        FutureHolder<T> resultHolder = new FutureHolder<>();
        this.sequentialExecutors.compute(sequenceKey, (k, executor) -> {
            CompletableFuture<T> replacementExecutor =
                    (executor == null) ? CompletableFuture.supplyAsync(() -> call(task)) :
                            executor.handleAsync((executionResult, executionException) -> {
                                if (executionException != null)
                                    log.log(Level.WARNING,
                                            executionException + " occurred in " + executor + " before executing next "
                                                    + task);
                                return call(task);
                            });
            resultHolder.setFuture(replacementExecutor);
            sweepExecutorWhenDone(replacementExecutor, sequenceKey);
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
                CompletableFuture<?> sweepResult = executor.isDone() ? null : executor;
                logSweepAction(executor, sweepResult);
                return sweepResult;
            });
        }

        private void logSweepAction(CompletableFuture<?> executor, CompletableFuture<?> sweepResult) {
            if (sweepResult == null) {
                log.log(Level.FINE, () -> "sweeping executor " + executor + " off of active service map");
            } else {
                log.log(Level.FINE, () -> "keeping executor " + executor + " in active service map");
            }
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
     * Wrapper to hide intricacies of {@link CompletableFuture} and only expose contract methods on the {@link Future}
     * interface
     *
     * @param <V> result type held by the Future
     */
    @Value static class MinimalFuture<V> implements Future<V> {

        Future<V> toMinimalize;

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
