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

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.ToString;

/**
 * @author q3769
 */
@ToString
public final class Conseq implements ConcurrentSequencer {

    private static final Logger LOG = Logger.getLogger(Conseq.class.getName());

    public static Builder newBuilder() {
        return new Builder();
    }

    private final LoadingCache<Integer, ListeningExecutorService> executorCache;
    private final ConsistentHasher consistentHasher;

    private Conseq(Builder builder) {
        LOG.log(Level.INFO, "Constructing conseq with builder: {0}", builder);
        if (builder.maxConcurrentExecutors > 0 && builder.consistentHasher != null) {
            throw new IllegalArgumentException(
                    "Cannot set hasher and max executors at the same time because hasher's total bucket count already implies max executors, and vice versa");
        }
        if (builder.consistentHasher == null) {
            this.consistentHasher = DefaultHasher.ofTotalBuckets(builder.maxConcurrentExecutors);
        } else {
            this.consistentHasher = builder.consistentHasher;
        }
        this.executorCache = Caffeine.newBuilder()
                .maximumSize(consistentHasher.getTotalBuckets())
                .build(SequentialExecutorServiceCacheLoader.withExecutorQueueSize(builder.singleExecutorTaskQueueSize));

    }

    /**
     * @return Max count of concurrent executors
     */
    public int getMaxConcurrentExecutors() {
        return this.consistentHasher.getTotalBuckets();
    }

    @Override
    public ListeningExecutorService getSequentialExecutor(CharSequence sequenceKey) {
        return this.executorCache.get(this.consistentHasher.hashToBucket(sequenceKey));
    }

    @Override
    public ListeningExecutorService getSequentialExecutor(Integer sequenceKey) {
        return this.executorCache.get(this.consistentHasher.hashToBucket(sequenceKey));
    }

    @Override
    public ListeningExecutorService getSequentialExecutor(Long sequenceKey) {
        return this.executorCache.get(this.consistentHasher.hashToBucket(sequenceKey));
    }

    @Override
    public ListeningExecutorService getSequentialExecutor(UUID sequenceKey) {
        return this.executorCache.get(this.consistentHasher.hashToBucket(sequenceKey));
    }

    @Override
    public ListeningExecutorService getSequentialExecutor(byte[] sequenceKey) {
        return this.executorCache.get(this.consistentHasher.hashToBucket(sequenceKey));
    }

    @Override
    public ListeningExecutorService getSequentialExecutor(ByteBuffer sequenceKey) {
        return this.executorCache.get(this.consistentHasher.hashToBucket(sequenceKey));
    }

    @ToString
    private static class SequentialExecutorServiceCacheLoader implements CacheLoader<Integer,
            ListeningExecutorService> {

        private static final int SINGLE_THREAD_COUNT = 1;
        private static final long KEEP_ALIVE_SAME_THREAD = 0L;

        private final int executorQueueSize;

        public static SequentialExecutorServiceCacheLoader withExecutorQueueSize(int executorQueueSize) {
            final SequentialExecutorServiceCacheLoader sequentialExecutorServiceCacheLoader =
                    new SequentialExecutorServiceCacheLoader(executorQueueSize);
            LOG.log(Level.INFO, "Created {0}", sequentialExecutorServiceCacheLoader);
            return sequentialExecutorServiceCacheLoader;
        }

        private SequentialExecutorServiceCacheLoader(int executorQueueSize) {
            this.executorQueueSize = executorQueueSize;
        }

        public int getExecutorQueueSize() {
            return executorQueueSize;
        }

        @Override
        public ListeningExecutorService load(Integer sequentialExecutorCacheKey) throws Exception {
            LOG.log(Level.INFO, "Loading new sequential executor with cache key : {0}", sequentialExecutorCacheKey);
            final ExecutorService executorService;
            if (this.executorQueueSize < 0) {
                LOG.log(Level.WARNING, "Defaulting executor queue size : {0} into unbounded", this.executorQueueSize);
                executorService = Executors.newSingleThreadExecutor();
            } else {
                LOG.log(Level.INFO, "Building new single thread executor with task queue size : {0}",
                        this.executorQueueSize);
                executorService = this.executorQueueSize == 0 ? new ThreadPoolExecutor(SINGLE_THREAD_COUNT,
                        SINGLE_THREAD_COUNT, KEEP_ALIVE_SAME_THREAD, TimeUnit.MILLISECONDS, new SynchronousQueue<>(
                                true)) : new ThreadPoolExecutor(SINGLE_THREAD_COUNT, SINGLE_THREAD_COUNT,
                                        KEEP_ALIVE_SAME_THREAD, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(
                                                this.executorQueueSize));
            }
            return MoreExecutors.listeningDecorator(new IrrevocableExecutorService(executorService));
        }
    }

    @ToString
    public static class Builder {

        private static final int UNBOUNDED = Integer.MAX_VALUE;

        private int maxConcurrentExecutors = UNBOUNDED;
        private ConsistentHasher consistentHasher;
        private int singleExecutorTaskQueueSize = UNBOUNDED;

        private Builder() {
        }

        public Conseq build() {
            return new Conseq(this);
        }

        public Builder maxConcurrentExecutors(int maxCountOfConcurrentExecutors) {
            this.maxConcurrentExecutors = maxCountOfConcurrentExecutors;
            return this;
        }

        public Builder consistentHasher(ConsistentHasher bucketHasher) {
            this.consistentHasher = bucketHasher;
            return this;
        }

        public Builder singleExecutorTaskQueueSize(int singleExecutorTaskQueueSize) {
            this.singleExecutorTaskQueueSize = singleExecutorTaskQueueSize;
            return this;
        }
    }

}
