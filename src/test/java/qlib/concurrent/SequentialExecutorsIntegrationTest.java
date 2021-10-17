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
package qlib.concurrent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author q3769
 */
public class SequentialExecutorsIntegrationTest {

    private static final Logger LOG = Logger.getLogger(SequentialExecutorsIntegrationTest.class.getName());

    private static final int MAX_CONCURRENCY = 100;

    private static final int RELATED_JOBS_PER_SEQUENCE = 10;

    private static final int SEQUENCE_COUNT = 3;

    @Test
    public void testRun() throws InterruptedException {
        SequentialExecutors target = SequentialExecutors.newBuilder().withMaxConcurrency(MAX_CONCURRENCY).build();
        List<Future<String>> result = new ArrayList<>();
        Object seq1 = UUID.randomUUID();
        Object seq2 = UUID.randomUUID();
        Object seq3 = UUID.randomUUID();

        for (int i = 0; i < RELATED_JOBS_PER_SEQUENCE; i++) {
            result.add(target.getSequentialExecutor(seq1).submit(new ThreadNameCallable()));
            result.add(target.getSequentialExecutor(seq2).submit(new ThreadNameCallable()));
            result.add(target.getSequentialExecutor(seq3).submit(new ThreadNameCallable()));
        }

        Map<String, Integer> threadJobCounts = new HashMap<>();
        result.forEach(f -> {
            try {
                final String threadId = f.get();
                final Integer jobCount = threadJobCounts.get(threadId);
                threadJobCounts.put(threadId, jobCount == null ? 1 : jobCount + 1);
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(SequentialExecutorsIntegrationTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        LOG.log(Level.INFO, "Thread job counts : {0}", threadJobCounts);
        Assertions.assertEquals(SEQUENCE_COUNT, threadJobCounts.size());
    }

    private static class ThreadNameCallable implements Callable<String> {

        @Override
        public String call() throws Exception {
            Thread.sleep(10);
            return Thread.currentThread().getName();
        }
    }

}
