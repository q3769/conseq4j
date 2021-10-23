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

import java.util.concurrent.Executor;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * @author q3769
 */
public class ConcurrentSequentialExecutorsTest {

    private static final Logger LOG = Logger.getLogger(ConcurrentSequentialExecutorsTest.class.getName());

    private static ConsistentBucketHasher stubHasher() {
        return new ConsistentBucketHasher() {

            @Override
            public int hashToBucket(Object sequenceKey) {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                               // choose Tools | Templates.
            }

            @Override
            public int getTotalBuckets() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                               // choose Tools | Templates.
            }
        };
    }

    @Test
    public void defaultConcurrencyAndQueueSizeShouldBeUnbound() {
        ConcurrentSequentialExecutors target = ConcurrentSequentialExecutors.newBuilder()
                .build();
        assertEquals(Integer.MAX_VALUE, target.size());
        assertEquals(Integer.MAX_VALUE, target.getIndividualExecutorTaskQueueSize());
    }

    @Test
    public void shouldHonorSpecifiedConcurrency() {
        int stubConcurrency = 5;
        ConcurrentSequentialExecutors target = ConcurrentSequentialExecutors.newBuilder()
                .ofSize(stubConcurrency)
                .build();
        assertEquals(stubConcurrency, target.size());
    }

    @Test
    public void shouldReturnSameExcecutorOnSameName() {
        Object sequenceKey = new Object();
        ConcurrentSequentialExecutors target = ConcurrentSequentialExecutors.newBuilder()
                .build();

        Executor e1 = target.getSequentialExecutor(sequenceKey);
        Executor e2 = target.getSequentialExecutor(sequenceKey);

        assertSame(e1, e2);
    }

    @Test
    public void cannotSetMaxConcurrencyIfAlreadySetCustomizedHasher() {
        final int stubConcurrency = 999;
        try {
            ConcurrentSequentialExecutors.newBuilder()
                    .withConsistentBucketHasher(stubHasher())
                    .ofSize(stubConcurrency)
                    .build();
        } catch (IllegalStateException ex) {
            LOG.info("expected");
            return;
        }
        fail();
    }

    @Test
    public void cannotSetCustomizedHasherIfAlreadySetConcurrency() {
        final int stubConcurrency = 999;
        try {
            ConcurrentSequentialExecutors.newBuilder()
                    .ofSize(stubConcurrency)
                    .withConsistentBucketHasher(stubHasher())
                    .build();
        } catch (IllegalStateException ex) {
            LOG.info("expected");
            return;
        }
        fail();
    }

    @Test
    public void shouldDiscardTotalTaskQueueSizeIfMaxConcurrencyIsUnbounded() {
        int stubTotalTaskQueueSize = 999;
        ConcurrentSequentialExecutors target = ConcurrentSequentialExecutors.newBuilder()
                .withTotalTaskQueueSize(stubTotalTaskQueueSize)
                .build();
        assertEquals(Integer.MAX_VALUE, target.getIndividualExecutorTaskQueueSize());
    }

    @Test
    public void individualExecutorQueueSizeShouldBeProportioanlToConseqTotalTaskQueueSize() {
        final int maxConcurrency = 5;
        final int conseqTaskQueueSize = 100;
        ConcurrentSequentialExecutors target = ConcurrentSequentialExecutors.newBuilder()
                .ofSize(maxConcurrency)
                .withTotalTaskQueueSize(conseqTaskQueueSize)
                .build();
        assertEquals(conseqTaskQueueSize / maxConcurrency, target.getIndividualExecutorTaskQueueSize());
    }

}
