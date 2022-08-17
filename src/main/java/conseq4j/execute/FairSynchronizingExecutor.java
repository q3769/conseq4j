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

import lombok.NonNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Synchronizes calls to the wrapped executor, with fairness option being true as in
 * {@link ReentrantLock#ReentrantLock(boolean)}. The fairness setup ensures the tasks are executed in the same order as
 * they are received from the API client's submission. Performance-wise the synchronization should not be a problem
 * because, although synchronized, no call should be blocking on the task's actual execution which happens on a
 * different thread; only the submission portion of the call is blocking on the calling thread.
 */
final class FairSynchronizingExecutor implements ConcurrentSequencingExecutor {

    /**
     * Earliest-submitted task gets executed first
     */
    public static final boolean FAIR_ON_CONTENTION = true;
    private final ConcurrentSequencingExecutor delegate;
    private final Lock fairLock = new ReentrantLock(FAIR_ON_CONTENTION);

    public FairSynchronizingExecutor(@NonNull ConcurrentSequencingExecutor delegate) {
        this.delegate = delegate;
    }

    @Override public void execute(@NonNull Runnable command, @NonNull Object sequenceKey) {
        fairLock.lock();
        try {
            delegate.execute(command, sequenceKey);
        } finally {
            fairLock.unlock();
        }
    }

    @Override public <T> Future<T> submit(@NonNull Callable<T> task, @NonNull Object sequenceKey) {
        fairLock.lock();
        try {
            return delegate.submit(task, sequenceKey);
        } finally {
            fairLock.unlock();
        }
    }
}
