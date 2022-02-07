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
package conseq4j;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author q3769
 */
public interface ConcurrentSequencer {

    void execute(Object sequenceKey, Runnable runnable);

    <T> Future<T> submit(Object sequenceKey, Callable<T> task);

    <T> Future<T> submit(Object sequenceKey, Runnable task, T result);

    Future<?> submit(Object sequenceKey, Runnable task);

    <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks)
            throws InterruptedException;

    <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks, long timeout,
            TimeUnit unit) throws InterruptedException;

    <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks) throws InterruptedException,
            ExecutionException;

    <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException;

}
