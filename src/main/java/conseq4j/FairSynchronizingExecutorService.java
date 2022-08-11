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

package conseq4j;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Synchronizing calls to the decorated {@link ExecutorService} delegate. This is just in case the calling API client is
 * un-synchronized/multithreaded, which is not recommended. Performance-wise this should not be a problem because,
 * although synchronized, no call will be blocking on the task's execution; only the submission portion of the call is
 * blocking with a fairness option as with {@link ReentrantLock#ReentrantLock(boolean)}.
 */
final class FairSynchronizingExecutorService implements ExecutorService {

    /**
     * Earliest submission gets executed first
     */
    public static final boolean FAIR_ON_CONTENTION = true;
    private final ExecutorService delegate;
    private final Lock fairLock = new ReentrantLock(FAIR_ON_CONTENTION);

    public FairSynchronizingExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override public void shutdown() {
        fairLock.lock();
        try {
            delegate.shutdown();
        } finally {
            fairLock.unlock();
        }
    }

    @Override public List<Runnable> shutdownNow() {
        fairLock.lock();
        try {
            return delegate.shutdownNow();
        } finally {
            fairLock.unlock();
        }
    }

    @Override public boolean isShutdown() {
        fairLock.lock();
        try {
            return delegate.isShutdown();
        } finally {
            fairLock.unlock();
        }
    }

    @Override public boolean isTerminated() {
        fairLock.lock();
        try {
            return delegate.isTerminated();
        } finally {
            fairLock.unlock();
        }
    }

    @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        fairLock.lock();
        try {
            return delegate.awaitTermination(timeout, unit);
        } finally {
            fairLock.unlock();
        }
    }

    @Override public <T> Future<T> submit(Callable<T> task) {
        fairLock.lock();
        try {
            return delegate.submit(task);
        } finally {
            fairLock.unlock();
        }
    }

    @Override public <T> Future<T> submit(Runnable task, T result) {
        fairLock.lock();
        try {
            return delegate.submit(task, result);
        } finally {
            fairLock.unlock();
        }
    }

    @Override public Future<?> submit(Runnable task) {
        fairLock.lock();
        try {
            return delegate.submit(task);
        } finally {
            fairLock.unlock();
        }
    }

    @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        fairLock.lock();
        try {
            return delegate.invokeAll(tasks);
        } finally {
            fairLock.unlock();
        }
    }

    @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        fairLock.lock();
        try {
            return delegate.invokeAll(tasks, timeout, unit);
        } finally {
            fairLock.unlock();
        }
    }

    @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        fairLock.lock();
        try {
            return delegate.invokeAny(tasks);
        } finally {
            fairLock.unlock();
        }
    }

    @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        fairLock.lock();
        try {
            return delegate.invokeAny(tasks, timeout, unit);
        } finally {
            fairLock.unlock();
        }
    }

    @Override public void execute(Runnable command) {
        fairLock.lock();
        try {
            delegate.execute(command);
        } finally {
            fairLock.unlock();
        }
    }
}
