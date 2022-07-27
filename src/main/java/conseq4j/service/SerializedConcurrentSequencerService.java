package conseq4j.service;

import lombok.NonNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes calls to the wrapped service, with possible fairness option. Note that, although calls are synchronized,
 * the caller thread most likely does not wait and block on the worker thread's execution, which depends on the wrapped
 * service's implementation.
 */
class SerializedConcurrentSequencerService implements ConcurrentSequencerService {

    private final ConcurrentSequencerService delegate;
    private final Lock lock;

    SerializedConcurrentSequencerService(@NonNull ConcurrentSequencerService delegate, boolean fair) {
        this.delegate = delegate;
        this.lock = new ReentrantLock(fair);
    }

    @Override public void execute(Runnable command, Object sequenceKey) {
        lock.lock();
        try {
            delegate.execute(command, sequenceKey);
        } finally {
            lock.unlock();
        }
    }

    @Override public <T> Future<T> submit(Callable<T> task, Object sequenceKey) {
        lock.lock();
        try {
            return delegate.submit(task, sequenceKey);
        } finally {
            lock.unlock();
        }
    }
}
