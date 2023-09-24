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
package conseq4j.summon;

import conseq4j.Terminable;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.Delegate;
import org.awaitility.Awaitility;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static java.lang.Math.floorMod;

/**
 * A factory to produce sequential executors of type {@link ExecutorService} with an upper-bound global execution
 * concurrency. Providing task execution concurrency, as well as sequencing, via the {@link ExecutorService} API.
 *
 * @author Qingtian Wang
 */

@ThreadSafe
@ToString
public final class ConseqServiceFactory implements SequentialExecutorServiceFactory, Terminable, AutoCloseable {
    private final int concurrency;
    private final ConcurrentMap<Object, ShutdownDisabledExecutorService> sequentialExecutors;

    /**
     * @param concurrency
     *         max count of "buckets"/executors, i.e. the max number of unrelated tasks that can be concurrently
     *         executed at any given time by this conseq instance.
     */
    private ConseqServiceFactory(int concurrency) {
        if (concurrency <= 0) {
            throw new IllegalArgumentException("expecting positive concurrency, but given: " + concurrency);
        }
        this.concurrency = concurrency;
        this.sequentialExecutors = new ConcurrentHashMap<>(concurrency);
    }

    /**
     * @return ExecutorService factory with default concurrency
     */
    public static ConseqServiceFactory instance() {
        return new ConseqServiceFactory(Runtime.getRuntime().availableProcessors());
    }

    /**
     * @param concurrency
     *         max number of tasks possible to be executed in parallel
     * @return ExecutorService factory with given concurrency
     */
    public static ConseqServiceFactory instance(int concurrency) {
        return new ConseqServiceFactory(concurrency);
    }

    /**
     * @return a single-thread executor that does not support any shutdown action.
     */
    @Override
    public ExecutorService getExecutorService(@NonNull Object sequenceKey) {
        return this.sequentialExecutors.computeIfAbsent(bucketOf(sequenceKey),
                bucket -> new ShutdownDisabledExecutorService(Executors.newFixedThreadPool(1)));
    }

    @Override
    public void shutdown() {
        this.sequentialExecutors.values().parallelStream().forEach(ShutdownDisabledExecutorService::shutdownDelegate);
    }

    @Override
    public boolean isTerminated() {
        return this.sequentialExecutors.values().stream().allMatch(ExecutorService::isTerminated);
    }

    @Override
    public List<Runnable> shutdownNow() {
        return sequentialExecutors.values()
                .parallelStream()
                .map(ShutdownDisabledExecutorService::shutdownDelegateNow)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    @Override
    public void close() {
        this.shutdown();
        Awaitility.await().forever().until(this::isTerminated);
    }

    private int bucketOf(Object sequenceKey) {
        return floorMod(Objects.hash(sequenceKey), this.concurrency);
    }

    /**
     * An {@link ExecutorService} that doesn't support shut down.
     *
     * @author Qingtian Wang
     */
    @ToString
    static final class ShutdownDisabledExecutorService implements ExecutorService {

        private static final String SHUTDOWN_UNSUPPORTED_MESSAGE =
                "Shutdown not supported: Tasks being executed by this service may be from unrelated owners; shutdown features are disabled to prevent undesired task cancellation on other owners";

        @Delegate(excludes = ShutdownOperations.class) private final ExecutorService delegate;

        /**
         * @param delegate
         *         the delegate {@link ExecutorService} to run the submitted task(s).
         */
        public ShutdownDisabledExecutorService(ExecutorService delegate) {
            this.delegate = delegate;
        }

        /**
         * Does not allow shutdown because the same executor could be running task(s) for different sequence keys,
         * albeit in sequential/FIFO order. Shutdown of the executor would stop executions for all tasks, which is
         * disallowed to prevent undesired task-execute coordination.
         */
        @Override
        public void shutdown() {
            throw new UnsupportedOperationException(SHUTDOWN_UNSUPPORTED_MESSAGE);
        }

        /**
         * @see #shutdown()
         */
        @Override
        public @Nonnull List<Runnable> shutdownNow() {
            throw new UnsupportedOperationException(SHUTDOWN_UNSUPPORTED_MESSAGE);
        }

        void shutdownDelegate() {
            this.delegate.shutdown();
        }

        @Nonnull
        List<Runnable> shutdownDelegateNow() {
            return this.delegate.shutdownNow();
        }

        /**
         * Methods that require complete overriding instead of delegation/decoration
         */
        private interface ShutdownOperations {
            void shutdown();

            List<Runnable> shutdownNow();
        }
    }
}
