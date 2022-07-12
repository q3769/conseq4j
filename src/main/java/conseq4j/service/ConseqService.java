package conseq4j.service;

import lombok.Data;
import lombok.ToString;
import lombok.Value;
import lombok.extern.java.Log;

import java.util.concurrent.*;
import java.util.logging.Level;

@Log @ToString public final class ConseqService implements ConcurrentSequencerService {

    private final ConcurrentMap<Object, CompletableFuture<?>> sequentialExecutors = new ConcurrentHashMap<>();

    @Override public void execute(Runnable command, Object sequenceKey) {
        this.sequentialExecutors.compute(sequenceKey, (k, executor) -> {
            CompletableFuture<Void> replacementExecutor =
                    executor == null ? CompletableFuture.runAsync(command) : executor.thenRunAsync(command);
            sweepExecutorWhenDone(replacementExecutor, sequenceKey);
            return replacementExecutor;
        });
    }

    private void sweepExecutorWhenDone(CompletableFuture<?> executor, Object sequenceKey) {
        executor.handleAsync((executionResult, executionError) -> {
            new ExecutorSweeper(sequenceKey, this.sequentialExecutors).sweepIfDone();
            return null;
        });
    }

    @Override public <T> Future<T> submit(Callable<T> task, Object sequenceKey) {
        FutureHolder<T> resultHolder = new FutureHolder<>();
        this.sequentialExecutors.compute(sequenceKey, (k, executor) -> {
            CompletableFuture<T> replacementExecutor =
                    (executor == null) ? CompletableFuture.supplyAsync(() -> call(task)) :
                            executor.thenApplyAsync(executionResult -> call(task));
            resultHolder.setFuture(replacementExecutor);
            sweepExecutorWhenDone(replacementExecutor, sequenceKey);
            return replacementExecutor;
        });
        return resultHolder.getFuture();
    }

    int getActiveExecutorCount() {
        return this.sequentialExecutors.size();
    }

    private <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (Exception e) {
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
                            + " already swept off of active service");
                    return null;
                }
                CompletableFuture<?> sweepResult = executor.isDone() ? null : executor;
                logSweepAction(executor, sweepResult);
                return sweepResult;
            });
        }

        private void logSweepAction(CompletableFuture<?> executor, CompletableFuture<?> sweepResult) {
            if (sweepResult == null) {
                log.log(Level.FINE, () -> "sweeping executor " + executor + " off of active service");
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
}
