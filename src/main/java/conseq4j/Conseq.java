/*
 * The MIT License
 * Copyright 2021 Qingtian Wang.
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package conseq4j;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * @author q3769
 */
public final class Conseq implements ConcurrentSequencer {

    private final Cache<Object, CompletableFuture<Void>> executionQueues;

    private final Executor executor;

    public static Conseq ofDefault() {
        return new Conseq(null);
    }

    public static Conseq ofConcurrency(int concurrency) {
        return new Conseq(concurrency);
    }

    private Conseq(Integer parallelism) {
        this.executor = parallelism == null ? ForkJoinPool.commonPool() : new ForkJoinPool(parallelism);
        this.executionQueues = Caffeine.newBuilder()
                .weakValues()
                .build();
    }

    @Override
    public void runAsync(Object sequenceKey, Runnable task) {
        this.executionQueues.asMap()
                .compute(sequenceKey, (key, presentExecutionQueue) -> (presentExecutionQueue == null)
                        ? CompletableFuture.runAsync(task, this.executor) : presentExecutionQueue.thenRunAsync(task,
                                this.executor));
    }

}
