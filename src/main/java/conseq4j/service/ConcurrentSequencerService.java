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
 * This may come as counter-intuitive for a concurrent API, but it is not mandatory for the implementation of this API
 * to be thread-safe. In fact, for simplicity and separation of concerns, the default API implementation is not
 * thread-safe in that it provides no garantee of access order.
 * <p>
 * It is the API client's responsibility and concern to ensure that tasks are submitted to the API in the correct
 * sequence in the first place. Fortunately often times, that is naturally the case e.g. when the API is invoked by a
 * single thread caller managed by a messaging provider. Otherwise, if the calling client is multi-threaded, then the
 * client needs to ensure the correct access order among the concurrent caller threads. E.g. This can be as simple as
 * setting up a <a
 * href="https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantLock.html#ReentrantLock-boolean-">fair
 * lock</a> to safeguard the API invocation.
 * <p>
 * Once the proper calling sequence is ensured by the client, it is then the concern and responsibility of the conseq4j
 * API that further processing of the tasks are executed in the meaningful order and concurrency as promised.
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
