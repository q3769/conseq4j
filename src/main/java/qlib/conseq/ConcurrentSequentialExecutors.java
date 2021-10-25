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
package qlib.conseq;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author q3769
 */
public class ConcurrentSequentialExecutors implements ConcurrentSequencer {

    private static final Logger LOG = Logger.getLogger(ConcurrentSequentialExecutors.class.getName());

    public static Builder newBuilder() {
        return new Builder();
    }

    private final LoadingCache<Integer, ExecutorService> executorCache;
    private final SequentialExecutorServiceLoader sequentialExecutorServiceLoader;
    private final ConsistentBucketHasher bucketHasher;

    private ConcurrentSequentialExecutors() {
        this(DefaultBucketHasher.withTotalBuckets(null), null);
    }

    private ConcurrentSequentialExecutors(Integer maxCountOfConcurrentExecutors, Integer totalTaskQueueSize) {
        this(DefaultBucketHasher.withTotalBuckets(maxCountOfConcurrentExecutors), totalTaskQueueSize);
    }

    private ConcurrentSequentialExecutors(ConsistentBucketHasher bucketHasher, Integer totalTaskQueueSize) {
        LOG.log(Level.INFO, "Constructing conseq with consistent hasher : {0}, totalTaskQueueSize : {1}", new Object[] {
                bucketHasher, totalTaskQueueSize });
        this.bucketHasher = Objects.requireNonNull(bucketHasher, "Bucket hasher cannot be null");
        final int totalBuckets = bucketHasher.getTotalBuckets();
        if (totalBuckets <= 0) {
            throw new IllegalArgumentException("Total hash buckets must be positive : " + totalBuckets);
        }
        final Integer executorQueueSize = totalTaskQueueSize == null ? null : totalTaskQueueSize / totalBuckets;
        sequentialExecutorServiceLoader = new SequentialExecutorServiceLoader(executorQueueSize);
        this.executorCache = Caffeine.newBuilder()
                .maximumSize(totalBuckets)
                .build(sequentialExecutorServiceLoader);
    }

    /**
     * @return Max count of concurrent executors
     */
    public int size() {
        return this.bucketHasher.getTotalBuckets();
    }

    @Override
    public ExecutorService getSequentialExecutor(Object sequenceKey) {
        return this.executorCache.get(this.bucketHasher.hashToBucket(sequenceKey));
    }

    int getIndividualExecutorTaskQueueSize() {
        return this.sequentialExecutorServiceLoader.getExecutorQueueSize();
    }

    private static class SequentialExecutorServiceLoader implements CacheLoader<Integer, ExecutorService> {

        private static final int UNBOUNDED = Integer.MAX_VALUE;
        private static final int SINGLE_THREAD_COUNT = 1;
        private static final long KEEP_ALIVE_SAME_THREAD = 0L;

        private final int executorQueueSize;

        public SequentialExecutorServiceLoader(Integer executorQueueSize) {
            if (executorQueueSize == null || executorQueueSize <= 0) {
                LOG.log(Level.WARNING, "Defaulting executor queue size : {0} into unbounded", executorQueueSize);
                this.executorQueueSize = UNBOUNDED;
            } else {
                this.executorQueueSize = executorQueueSize;
            }
        }

        public int getExecutorQueueSize() {
            return executorQueueSize;
        }

        @Override
        public ExecutorService load(Integer sequentialExecutorIndex) throws Exception {
            LOG.log(Level.INFO, "Loading new sequential executor with key : {0}", sequentialExecutorIndex);
            if (this.executorQueueSize == UNBOUNDED) {
                return Executors.newSingleThreadExecutor();
            }
            LOG.log(Level.INFO, "Building new single thread executor with task queue size : {0}", new Object[] {
                    this.executorQueueSize });
            return new ThreadPoolExecutor(SINGLE_THREAD_COUNT, SINGLE_THREAD_COUNT, KEEP_ALIVE_SAME_THREAD,
                    TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(this.executorQueueSize));
        }
    }

    public static class Builder {

        private Integer maxCountOfConcurrentExecutors;
        private ConsistentBucketHasher consistentBucketHasher;
        private Integer totalTaskQueueSize;

        private Builder() {
        }

        @Override
        public String toString() {
            return "Builder{" + "maxCountOfConcurrentExecutors=" + maxCountOfConcurrentExecutors
                    + ", consistentBucketHasher=" + consistentBucketHasher + ", totalTaskQueueSize="
                    + totalTaskQueueSize + '}';
        }

        public ConcurrentSequentialExecutors build() {
            if (this.maxCountOfConcurrentExecutors == null && this.consistentBucketHasher == null) {
                LOG.log(Level.WARNING, "Building default conseq, disregarding any customization in builder : {0}",
                        this);
                return new ConcurrentSequentialExecutors();
            }
            if (this.maxCountOfConcurrentExecutors != null) {
                return new ConcurrentSequentialExecutors(this.maxCountOfConcurrentExecutors, this.totalTaskQueueSize);
            }
            LOG.log(Level.WARNING,
                    "Building conseq using customized consistent bucket hasher : {0}, total task queue size : {1}",
                    new Object[] { this.consistentBucketHasher, this.totalTaskQueueSize });
            return new ConcurrentSequentialExecutors(this.consistentBucketHasher, totalTaskQueueSize);
        }

        public Builder ofSize(int maxCountOfConcurrentExecutors) {
            if (this.consistentBucketHasher != null) {
                throw new IllegalStateException(
                        "Cannot set max concurrency after already set consistent bucket hasher : "
                                + this.consistentBucketHasher);
            }
            this.maxCountOfConcurrentExecutors = maxCountOfConcurrentExecutors;
            return this;
        }

        public Builder withConsistentBucketHasher(ConsistentBucketHasher bucketHasher) {
            if (this.maxCountOfConcurrentExecutors != null) {
                throw new IllegalStateException(
                        "Cannot set consistent bucket hasher after already set max concurrency : "
                                + this.maxCountOfConcurrentExecutors);
            }
            this.consistentBucketHasher = bucketHasher;
            return this;
        }

        public Builder withTotalTaskQueueSize(int totalTaskQueueSize) {
            this.totalTaskQueueSize = totalTaskQueueSize;
            return this;
        }
    }

}
