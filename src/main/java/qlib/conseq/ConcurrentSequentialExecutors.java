/*
 * The MIT License
 *
 * Copyright 2021 Qingtian Wang.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author q3769
 */
public class ConcurrentSequentialExecutors implements ConcurrentSequencer {

    private static final Logger LOG = Logger.getLogger(ConcurrentSequentialExecutors.class.getName());

    public static Builder newBuilder() {
        return new Builder();
    }

    private final LoadingCache<Integer, ExecutorService> executorCache;

    private final ConsistentBucketHasher bucketHasher;

    private ConcurrentSequentialExecutors(Integer maxCountOfConcurrentExecutors) {
        this(DefaultBucketHasher.withTotalBuckets(maxCountOfConcurrentExecutors));
    }

    private ConcurrentSequentialExecutors(ConsistentBucketHasher bucketHasher) {
        this.bucketHasher = Objects.requireNonNull(bucketHasher, "Bucket hasher cannot be null");
        this.executorCache = Caffeine.newBuilder()
                .maximumSize(bucketHasher.getTotalBuckets())
                .build(new SequentialExecutorServiceLoader());
    }

    /**
     *
     * @return Max count of concurrent executors
     */
    public int size() {
        return this.bucketHasher.getTotalBuckets();
    }

    @Override
    public ExecutorService getSequentialExecutor(Object sequenceKey) {
        return this.executorCache.get(this.bucketHasher.hashToBucket(sequenceKey));
    }

    private static class SequentialExecutorServiceLoader implements CacheLoader<Integer, ExecutorService> {

        @Override
        public ExecutorService load(Integer sequentialExecutorIndex) throws Exception {
            LOG.log(Level.INFO, "Creating new single thread executor with index : {0}", sequentialExecutorIndex);
            return Executors.newSingleThreadExecutor();
        }
    }

    public static class Builder {

        private Integer maxCountOfConcurrentExecutors;

        private ConsistentBucketHasher bucketHasher;

        private Builder() {
        }

        public ConcurrentSequentialExecutors build() {
            if (this.maxCountOfConcurrentExecutors != null && this.bucketHasher != null) {
                throw new IllegalStateException("Concurrency and bucket hasher cannot be set at the same time. Max concurrency and total buckets are the same thing.s");
            }
            if (this.maxCountOfConcurrentExecutors == null && this.bucketHasher == null) {
                LOG.log(Level.INFO, "Using default bucket hasher with unbound bucket count");
                return new ConcurrentSequentialExecutors(Integer.MAX_VALUE);
            }
            if (this.maxCountOfConcurrentExecutors != null) {
                LOG.log(Level.INFO, "Using default bucket hasher with max bucket count : {0}", this.maxCountOfConcurrentExecutors);
                return new ConcurrentSequentialExecutors(this.maxCountOfConcurrentExecutors);
            }
            LOG.log(Level.WARNING, "Using customized bucket hasher : {0} with max bucket count : {1}", new Object[]{this.bucketHasher, this.bucketHasher.getTotalBuckets()});
            return new ConcurrentSequentialExecutors(this.bucketHasher);
        }

        public Builder ofSize(int maxConcurrentExecutorCount) {
            if (this.bucketHasher != null) {
                throw new IllegalStateException("Cannot set concurrency after already set bucket hasher : " + this.bucketHasher);
            }
            this.maxCountOfConcurrentExecutors = maxConcurrentExecutorCount;
            return this;
        }

        public Builder withBucketHasher(ConsistentBucketHasher bucketHasher) {
            if (this.maxCountOfConcurrentExecutors != null) {
                throw new IllegalStateException("Cannot set bucket hasher after already set concurrency : " + this.maxCountOfConcurrentExecutors);
            }
            this.bucketHasher = bucketHasher;
            return this;
        }
    }

}
