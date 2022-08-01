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

import lombok.NonNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes calls to the wrapped service, with possible fairness option as with
 * {@link ReentrantLock#ReentrantLock(boolean)}. This is just in case the calling API client is
 * un-synchronized/multithreaded, which is not recommended. Performance-wise this should not be a problem because,
 * although synchronized, no call will be blocking on the task's execution; only the submission portion of the call is
 * blocking.
 */
final class SynchronizingConcurrentSequencerService implements ConcurrentSequencerService {

    private final ConcurrentSequencerService delegate;
    private final Lock lock;

    public SynchronizingConcurrentSequencerService(@NonNull ConcurrentSequencerService delegate, boolean fair) {
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
