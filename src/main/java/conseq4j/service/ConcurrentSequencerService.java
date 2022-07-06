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
     * @see java.util.concurrent.ExecutorService#execute(Runnable)
     */
    void execute(Runnable command, Object sequenceKey);

    /**
     * @see java.util.concurrent.ExecutorService#submit(Callable)
     */
    <T> Future<T> submit(Callable<T> task, Object sequenceKey);

    /**
     * @see java.util.concurrent.ExecutorService#submit(Runnable, Object)
     */
    <T> Future<T> submit(Runnable task, T result, Object sequenceKey);

    /**
     * @see java.util.concurrent.ExecutorService#submit(Runnable)
     */
    Future<?> submit(Runnable task, Object sequenceKey);

    /**
     * @see java.util.concurrent.ExecutorService#invokeAll(Collection)
     */
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, Object sequenceKey)
            throws InterruptedException;

    /**
     * @see java.util.concurrent.ExecutorService#invokeAll(Collection, long, TimeUnit)
     */
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, Duration timeout, Object sequenceKey)
            throws InterruptedException;

    /**
     * @see ExecutorService#invokeAny(Collection)
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks, Object sequenceKey)
            throws InterruptedException, ExecutionException;

    /**
     * @see ExecutorService#invokeAny(Collection, long, TimeUnit)
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks, Duration timeout, Object sequenceKey)
            throws InterruptedException, ExecutionException, TimeoutException;

}
