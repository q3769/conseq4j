package conseq4j.service;

import lombok.NonNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes calls to the wrapped service, with possible fairness option. Note that, although calls are synchronized,
 * the caller thread most likely does not wait and block on the worker thread's execution; this depends on the wrapped
 * service's implementation.
 */
final class SerializedConcurrentSequencerService implements ConcurrentSequencerService {

    private final ConcurrentSequencerService delegate;
    private final Lock lock;

    SerializedConcurrentSequencerService(@NonNull ConcurrentSequencerService delegate, boolean fair) {
        this.delegate = delegate;
        this.lock = new ReentrantLock(fair);
    }

    @Override public void execute(@NonNull Runnable command, @NonNull Object sequenceKey) {
        lock.lock();
        try {
            delegate.execute(command, sequenceKey);
        } finally {
            lock.unlock();
        }
    }

    @Override public <T> Future<T> submit(@NonNull Callable<T> task, @NonNull Object sequenceKey) {
        lock.lock();
        try {
            return delegate.submit(task, sequenceKey);
        } finally {
            lock.unlock();
        }
    }
}
