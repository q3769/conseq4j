/*
 * The MIT License Copyright 2021 Qingtian Wang. Permission is hereby granted, free of charge, to
 * any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
 * KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package conseq4j.service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * <p>Main API interface to submit task(s) with a sequence key. Task(s) submitted under the same/equal sequence key are
 * executed in the same order as submitted.</p>
 *
 * @author Qingtian Wang
 */
public interface ConcurrentSequencerService {

    /**
     * <p>execute.</p>
     *
     * @param sequenceKey an {@link java.lang.Object} whose hash code is used to locate a corresponding sequential
     *                    executor.
     * @param runnable    a {@link java.lang.Runnable} to be submitted under the {@code sequenceKey}.
     * @see java.util.concurrent.ExecutorService#execute(Runnable)
     */
    void execute(Object sequenceKey, Runnable runnable);

    /**
     * @see java.util.concurrent.ExecutorService#submit(Callable)
     */
    <T> Future<T> submit(Object sequenceKey, Callable<T> task);

    /**
     * @see java.util.concurrent.ExecutorService#submit(Runnable, Object)
     */
    <T> Future<T> submit(Object sequenceKey, Runnable task, T result);

    /**
     * @see java.util.concurrent.ExecutorService#submit(Runnable)
     */
    Future<?> submit(Object sequenceKey, Runnable task);

    /**
     * @see java.util.concurrent.ExecutorService#invokeAll(Collection)
     */
    <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks)
            throws InterruptedException;

    /**
     * @see java.util.concurrent.ExecutorService#invokeAll(Collection, long, TimeUnit)
     */
    <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks, Duration timeout)
            throws InterruptedException;

    /**
     * @see ExecutorService#invokeAny(Collection)
     */
    <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException;

    /**
     * @see ExecutorService#invokeAny(Collection, long, TimeUnit)
     */
    <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException;

}
