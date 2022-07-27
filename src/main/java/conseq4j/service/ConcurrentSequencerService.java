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

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Main API of concurrent sequencer service, bypassing the intermediate
 * executor/{@link java.util.concurrent.ExecutorService} interface.
 * <p>
 * The conseq4j implementation is thread-safe. In the context of concurrency and sequencing, though, thread-safety goes
 * beyond simple data corruption concerns into that of data access order among multiple threads. For example, if
 * multiple tasks are submitted concurrently by different threads at exactly the same moment, then by definition, there
 * is no sequence among those tasks to be preserved no matter they are related tasks of the same sequence key or not; it
 * is considered "safe" to execute those tasks in any order. Meanwhile, "fair" execution order is required on related
 * tasks as they are submitted in proper sequence.
 * <p>
 * It is the API client's responsibility and concern how tasks are submitted to conseq4j. As execution order is
 * imperative, the client - either single or multi-threaded - has to ensure that tasks are submitted in proper sequence
 * to begin with. Fortunately often times, that is naturally the case, e.g. when the client is under the management of a
 * messaging provider running a single caller thread. Otherwise, if the caller is multi-threaded, then the client needs
 * to ensure the concurrent caller threads have proper access order to conseq4j. This can be as trivial as setting up a
 * <a
 * href="https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantLock.html#ReentrantLock-boolean-">fair
 * lock</a> to sequence related task submission; it is a client-side activity nonetheless.
 * <p>
 * Once proper submission sequence is ensured by the API client, it is then conseq4j's concern and responsibility that
 * further processing of the submitted tasks is executed in the meaningful order and concurrency as promised.
 *
 * @author Qingtian Wang
 */
public interface ConcurrentSequencerService {

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
