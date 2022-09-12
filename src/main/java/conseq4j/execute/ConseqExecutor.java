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
import lombok.ToString;
import lombok.extern.java.Log;
import net.jcip.annotations.ThreadSafe;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * The default implementation of the {@link ConcurrentSequencingExecutor} API. Task submission calls are thread-safe
 * (synchronized) and fair under contention.
 *
 * @author Qingtian Wang
 */
@ThreadSafe
@Log
@ToString
public final class ConseqExecutor implements ConcurrentSequencingExecutor {

    private static final ExecutorService DEFAULT_THREAD_POOL = ForkJoinPool.commonPool();
    private final ConcurrentSequencingExecutor delegate;

    /**
     * Default executor uses {@link ForkJoinPool#commonPool()} as async facility.
     */
    public ConseqExecutor() {
        this(DEFAULT_THREAD_POOL);
    }

    /**
     * @param executionThreadPool custom JDK thread pool to facilitate async execution of this conseq executor instance
     */
    public ConseqExecutor(ExecutorService executionThreadPool) {
        delegate = new FairSynchronizingExecutor(new StagingExecutor(executionThreadPool));
        log.fine(() -> "constructed " + this);
    }

    @Override
    public void execute(@NonNull Runnable command, @NonNull Object sequenceKey) {
        delegate.execute(command, sequenceKey);
    }

    @Override
    public <T> Future<T> submit(@NonNull Callable<T> task, @NonNull Object sequenceKey) {
        return delegate.submit(task, sequenceKey);
    }
}
