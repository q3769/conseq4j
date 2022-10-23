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

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Main API of concurrent sequencer executor, bypassing the intermediate executor
 * ({@link java.util.concurrent.ExecutorService}) interface.
 * <p>
 * A public implementation of conseq4j should be thread-safe per its given order of task submissions. In the context of
 * concurrency and sequencing, thread-safety goes beyond the concerns of data corruption of individual tasks, into that
 * of execution order across multiple tasks. Once a certain submission sequence is established by the API client, it is
 * conseq4j's concern and responsibility that further execution of the submitted tasks is in the meaningful order and
 * concurrency as promised. The implementation is required to provide a "fair" execution order on already-submitted
 * tasks: Related tasks of the same sequence key should be executed sequentially in the same order as submitted - the
 * earliest-submitted task gets executed first; unrelated tasks should be executed in parallel.
 *
 * @author Qingtian Wang
 */
public interface ConcurrentSequencingExecutor {

    /**
     * @param command     the Runnable task to run sequentially with others under the same sequence key
     * @param sequenceKey the key under which all tasks are executed sequentially
     */
    void execute(Runnable command, Object sequenceKey);

    /**
     * @param task        the Callable task to run sequentially with others under the same sequence key
     * @param sequenceKey the key under which all tasks are executed sequentially
     * @param <T>         the type of the task's result
     * @return a Future representing pending completion of the task
     */
    <T> Future<T> submit(Callable<T> task, Object sequenceKey);
}
