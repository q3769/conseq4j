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
package conseq4j.summon;

import lombok.ToString;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Math.floorMod;

/**
 * A factory to produce sequential executors of type {@link ExecutorService} with an upper-bound global execution
 * concurrency. Providing task execution concurrency, as well as sequencing, via the {@link ExecutorService} API.
 *
 * @author Qingtian Wang
 */

@ThreadSafe
@ToString
public final class Conseq implements ConcurrentSequencer {

    private static final int DEFAULT_GLOBAL_CONCURRENCY = Runtime.getRuntime().availableProcessors() + 1;
    private final ConcurrentMap<Object, ExecutorService> sequentialExecutors = new ConcurrentHashMap<>();
    private final int globalConcurrency;

    /**
     * @param globalConcurrency max count of "buckets"/executors, i.e. the max number of unrelated tasks that can be
     *                          concurrently executed at any given time by this conseq instance.
     */
    private Conseq(int globalConcurrency) {
        if (globalConcurrency <= 0) {
            throw new IllegalArgumentException(
                    "expecting positive global concurrency, but given: " + globalConcurrency);
        }
        this.globalConcurrency = globalConcurrency;
    }

    /**
     * @param globalConcurrency max number of tasks possible to be executed in parallel
     * @return ExecutorService factory with given global concurrency
     */
    public static Conseq ofConcurrency(int globalConcurrency) {
        return new Conseq(globalConcurrency);
    }

    /**
     * @return ExecutorService factory with default global concurrency
     */
    public static Conseq ofDefaultConcurrency() {
        return new Conseq(DEFAULT_GLOBAL_CONCURRENCY);
    }

    /**
     * @return a single-thread executor that does not support any shutdown action.
     */
    @Override
    public ExecutorService getSequentialExecutorService(Object sequenceKey) {
        return this.sequentialExecutors.computeIfAbsent(bucketOf(sequenceKey),
                bucket -> new FairSynchronizingExecutorService(new ShutdownDisabledExecutorService(Executors.newSingleThreadExecutor())));
    }

    private int bucketOf(Object sequenceKey) {
        return floorMod(Objects.hash(sequenceKey), this.globalConcurrency);
    }
}
