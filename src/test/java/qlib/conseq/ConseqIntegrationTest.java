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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * @author q3769
 */
public class ConseqIntegrationTest {

    private static final Logger LOG = Logger.getLogger(ConseqIntegrationTest.class.getName());
    private static final Duration TASK_DURATION = Duration.ofMillis(42);
    private static final Duration SMALL_TASK_DURATION = Duration.ofSeconds(TASK_DURATION.getSeconds() / 10);
    private static final Duration DURATION_UNTIL_ALL_TASKS_DONE = Duration.ofSeconds(TASK_DURATION.getSeconds() * 100);
    private static final int TASK_COUNT = 10;
    private static final Random RANDOM = new Random();

    @Test
    public void defaultConseqRunsWithUnboundMaxConcurrencyButBoundByTotalTaskCount() throws InterruptedException {
        ConcurrentSequencer defaultConseq = Conseq.newBuilder()
                .build();
        List<SpyingTaskPayload> taskPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT); // SpyingTaskPayload
                                                                                                      // is an example,
                                                                                                      // your input data
                                                                                                      // can be of any
                                                                                                      // type

        taskPayloads.forEach(payload -> {
            final Long sequenceKey = payload.getCorrelationKey(); // Sequence key can come from anywhere but most
                                                                  // likely from the input data payload. Note that the
                                                                  // same sequence key means sqeuential execution of
                                                                  // the tasks behind the same (physically or
                                                                  // logically) single thread.
            final ExecutorService sequentialExecutor = defaultConseq.getSequentialExecutor(sequenceKey); // Here you get
                                                                                                         // an instance
                                                                                                         // of good old
                                                                                                         // JDK
                                                                                                         // ExecutorService
                                                                                                         // by way of
                                                                                                         // Executors.newSingleThreadExecutor();
                                                                                                         // of course,
                                                                                                         // the same
                                                                                                         // instance is
                                                                                                         // reused when
                                                                                                         // summoned by
                                                                                                         // the same
                                                                                                         // seqence key.
            sequentialExecutor.execute(new SpyingRunnableTask(payload, TASK_DURATION)); // Your task can be a Runnable,
                                                                                        // a Callable, or whatever
                                                                                        // ExecutorService supports. Up
                                                                                        // to you how to convert an
                                                                                        // input data item into a
                                                                                        // runnable command.
        });
        Thread.sleep(DURATION_UNTIL_ALL_TASKS_DONE.getSeconds() * 1000);

        Set<String> runThreadNames = taskPayloads.stream()
                .map(item -> item.getRunThreadName())
                .collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(totalRunThreads <= TASK_COUNT); // Even though "unbound" by default, concurrency won't be greater
                                                   // than total tasks.
    }

    @Test
    public void conseqShouldBeBoundByMaxMaxConcurrency() throws InterruptedException, ExecutionException {
        final int maxConcurrency = TASK_COUNT / 2;
        ConcurrentSequencer maxConcurrencyBoundConseq = Conseq.newBuilder()
                .maxConcurrentExecutors(maxConcurrency)
                .singleExecutorTaskQueueSize(TASK_COUNT * 10)
                .build();
        List<SpyingTaskPayload> dataPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT);
        List<Future<SpyingTaskPayload>> taskFutures = new ArrayList<>();

        dataPayloads.forEach(payload -> taskFutures.add(maxConcurrencyBoundConseq.getSequentialExecutor(payload
                .getCorrelationKey())
                .submit(new SpyingCallableTask(payload, TASK_DURATION))));

        Set<String> runThreadNames = new HashSet<>();
        for (Future<SpyingTaskPayload> f : taskFutures) {
            runThreadNames.add(f.get()
                    .getRunThreadName());
        }
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(totalRunThreads <= maxConcurrency); // If, as in most cases, the max concurrency (think "max thread
                                                       // pool getMaxConcurrentExecutors") is set to be smaller than
                                                       // your potential tasks,
                                                       // then the total number of concurrent threads to have run your
                                                       // tasks will be bound by the max concurrency you set.
    }

    @Test
    public void conseqShouldRunRelatedTasksInOrder() throws InterruptedException, ExecutionException {
        ConcurrentSequencer defaultConseq = Conseq.newBuilder()
                .build();
        List<SpyingTaskPayload> regularPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT);
        List<SpyingTaskPayload> smallPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT);
        List<Future<SpyingTaskPayload>> regularFutures = new ArrayList<>();
        List<Future<SpyingTaskPayload>> quickFutures = new ArrayList<>();
        UUID sequenceKey = UUID.randomUUID();
        final ExecutorService regularTaskExecutor = defaultConseq.getSequentialExecutor(sequenceKey);
        final ExecutorService quickTaskExecutor = defaultConseq.getSequentialExecutor(sequenceKey); // Same sequence key
                                                                                                    // for regular and
                                                                                                    // quick tasks

        regularPayloads.stream()
                .forEach(regularPayload -> {
                    regularFutures.add(regularTaskExecutor.submit(new SpyingCallableTask(regularPayload,
                            TASK_DURATION)));
                }); // Slower tasks first
        smallPayloads.stream()
                .forEach(smallPayload -> {
                    quickFutures.add(quickTaskExecutor.submit(new SpyingCallableTask(smallPayload,
                            SMALL_TASK_DURATION)));
                }); // Faster tasks later so none of the faster ones should be executed until all slower ones are done

        assertSame(regularTaskExecutor, quickTaskExecutor); // Same sequence key, therefore, same executor thread.
        List<Long> regularCompleteTimes = new ArrayList<>();
        for (Future<SpyingTaskPayload> rf : regularFutures) {
            regularCompleteTimes.add(rf.get()
                    .getRunEndTimeNanos());
        }
        List<Long> quickStartTimes = new ArrayList<>();
        for (Future<SpyingTaskPayload> qf : quickFutures) {
            quickStartTimes.add(qf.get()
                    .getRunStartTimeNanos());
        }
        long latestCompleteTimeOfRegularTasks = regularCompleteTimes.stream()
                .mapToLong(ct -> ct)
                .max()
                .getAsLong();
        long earliestStartTimeOfQuickTasks = quickStartTimes.stream()
                .mapToLong(st -> st)
                .min()
                .getAsLong();
        assertTrue(latestCompleteTimeOfRegularTasks < earliestStartTimeOfQuickTasks); // OK ma, this is not enough to
                                                                                      // logically prove the global
                                                                                      // order but you get the idea...
    }

    private List<SpyingTaskPayload> getStubInputItemWithRandomCorrelationKeys(int collectionSize) {
        List<SpyingTaskPayload> result = new ArrayList<>();
        for (int i = 0; i < collectionSize; i++) {
            result.add(new SpyingTaskPayload(RANDOM.nextLong()));
        }
        return result;
    }

}
