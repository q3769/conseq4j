package conseq4j.service;

import lombok.NonNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Making it impossible to downcast this wrapper's instances any further from {@link Future}
 *
 * @param <V> type of result held by the Future
 */
final class MinimalFuture<V> implements Future<V> {

    private final Future<V> future;

    MinimalFuture(@NonNull Future<V> future) {
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
