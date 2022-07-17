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
package conseq4j;

import java.util.concurrent.ExecutorService;

/**
 * Main API of concurrent sequencer, exposing a sequential executor of type {@link ExecutorService}
 * <p>
 * It may seem counter-intuitive for a concurrent API, but the implementation of conseq4j does not have to be
 * thread-safe. In fact, for simplicity and separation of concerns, the default implementation is not thread-safe in
 * that it provides no garantee of access order in case of multi-thread racing conditions in client-side task
 * submission.
 * <p>
 * It is the API client's responsibility and concern how tasks are submitted to conseq4j. If execution order is
 * imperative, the client - no matter running in single or multiple threads - has to ensure that tasks are submitted in
 * proper sequence to begin with. Fortunately often times, that is naturally the case, e.g., when the client is under
 * the management of a messaging provider running a single caller thread. Otherwise, if the caller is multi-threaded,
 * then the client needs to ensure proper access order to conseq4j among the concurrent caller threads. This can be as
 * trivial as setting up a <a
 * href="https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantLock.html#ReentrantLock-boolean-">fair
 * lock</a> to safeguard the conseq4j API invocation; it is a client-side activity nonetheless.
 * <p>
 * Once the proper task submission sequence is ensured by the API client, it is then conseq4j's concern and
 * responsibility that further processing of the tasks is executed in the meaningful order and concurrency as promised.
 *
 * @author Qingtian Wang
 */
public interface ConcurrentSequencer {

    /**
     * @param sequenceKey an {@link java.lang.Object} whose hash code is used to locate and summon the corresponding
     *                    sequential executor.
     * @return the executor of type {@link java.util.concurrent.ExecutorService} that executes all tasks of this
     *         sequence key in the same order as they are submitted
     */
    ExecutorService getSequentialExecutor(Object sequenceKey);
}
