/*
 * MIT License
 *
 * Copyright (c) 2021 Qingtian Wang
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

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Main API of conseq executor, bypassing the intermediate ({@link java.util.concurrent.ExecutorService}) API, to
 * service the submitted task per its sequence key.
 * <p>
 * A public implementation should be thread-safe. In the context of asynchronous concurrency and sequencing,
 * thread-safety goes beyond the concerns of data corruption due to concurrent modification, into that of execution
 * order across multiple tasks. By definition, though, there is no such thing as order or sequence among tasks submitted
 * concurrently by different threads. Such multi-thread submitted tasks can be executed in any order, regardless of
 * sequence key. However, tasks submitted by a single thread - or, by each single thread in a multi-threading scenario -
 * should be executed sequentially in the same order of submission if they have the same sequence key; otherwise, such
 * single-thread submitted tasks should be managed to execute concurrently by multiple threads if they have different
 * sequence keys.
 *
 * @author Qingtian Wang
 */
public interface SequentialExecutor {
    /**
     * @param command
     *         the Runnable task to run sequentially with others under the same sequence key
     * @param sequenceKey
     *         the key under which all tasks are executed sequentially
     * @return future holding run status of the submitted command
     */
    Future<Void> execute(Runnable command, Object sequenceKey);

    /**
     * @param task
     *         the Callable task to run sequentially with others under the same sequence key
     * @param sequenceKey
     *         the key under which all tasks are executed sequentially
     * @param <T>
     *         the type of the task's result
     * @return a Future representing pending completion of the submitted task
     */
    <T> Future<T> submit(Callable<T> task, Object sequenceKey);

    /**
     *
     */
    void shutdown();

    /**
     * @return true if the executor has been shutdown
     */
    boolean isTerminated();
}
