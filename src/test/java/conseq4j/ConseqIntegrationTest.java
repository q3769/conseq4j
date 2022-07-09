/*
 * MIT License
 *
 * Copyright (c) 2022. Qingtian Wang
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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author q3769
 */
class ConseqIntegrationTest {

    private static final Logger LOG = Logger.getLogger(ConseqIntegrationTest.class.getName());
    private static final Duration TASK_DURATION = Duration.ofMillis(42);
    private static final Duration SMALL_TASK_DURATION = Duration.ofSeconds(TASK_DURATION.getSeconds() / 10);
    private static final Duration DURATION_UNTIL_ALL_TASKS_DONE = Duration.ofSeconds(TASK_DURATION.getSeconds() * 100);
    private static final int TASK_COUNT = 10;
    private static final Random RANDOM = new Random();

    @Test void defaultConseqRunsWithUnboundedMaxConcurrencyButBoundedByTotalTaskCount() {
        ConcurrentSequencer defaultConseq = Conseq.newBuilder().build();
        List<SpyingTaskPayload> taskPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT);
        taskPayloads.forEach(payload -> {
            final Long sequenceKey = payload.getCorrelationKey();
            final ExecutorService sequentialExecutor = defaultConseq.getSequentialExecutor(sequenceKey);
            sequentialExecutor.execute(new SpyingRunnableTask(payload, TASK_DURATION));
        });
        await().atLeast(DURATION_UNTIL_ALL_TASKS_DONE);

        Set<String> runThreadNames =
                taskPayloads.stream().map(SpyingTaskPayload::getRunThreadName).collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} threads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(totalRunThreads <= TASK_COUNT);
    }

    @Test void conseqShouldBeBoundedByMaxConcurrency() throws InterruptedException, ExecutionException {
        final int maxConcurrency = TASK_COUNT / 2;
        ConcurrentSequencer maxConcurrencyBoundConseq =
                Conseq.newBuilder().globalConcurrency(maxConcurrency).executorTaskQueueSize(TASK_COUNT * 10).build();
        List<SpyingTaskPayload> dataPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT);
        List<Future<SpyingTaskPayload>> taskFutures = new ArrayList<>();

        dataPayloads.forEach(payload -> taskFutures.add(
                maxConcurrencyBoundConseq.getSequentialExecutor(payload.getCorrelationKey())
                        .submit(new SpyingCallableTask(payload, TASK_DURATION))));

        Set<String> runThreadNames = new HashSet<>();
        for (Future<SpyingTaskPayload> f : taskFutures) {
            runThreadNames.add(f.get().getRunThreadName());
        }
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} threads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(totalRunThreads <= maxConcurrency);
    }

    @Test void conseqShouldRunRelatedTasksInOrder() throws InterruptedException, ExecutionException {
        ConcurrentSequencer defaultConseq = Conseq.newBuilder().build();
        List<SpyingTaskPayload> regularPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT);
        List<SpyingTaskPayload> smallPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT);
        List<Future<SpyingTaskPayload>> regularFutures = new ArrayList<>();
        List<Future<SpyingTaskPayload>> quickFutures = new ArrayList<>();
        UUID sequenceKey = UUID.randomUUID();
        final ExecutorService regularTaskExecutor = defaultConseq.getSequentialExecutor(sequenceKey);
        final ExecutorService quickTaskExecutor = defaultConseq.getSequentialExecutor(sequenceKey);

        regularPayloads.forEach(regularPayload -> regularFutures.add(regularTaskExecutor.submit(
                new SpyingCallableTask(regularPayload, TASK_DURATION)))); // Slower tasks first
        smallPayloads.forEach(smallPayload -> quickFutures.add(
                quickTaskExecutor.submit(new SpyingCallableTask(smallPayload, SMALL_TASK_DURATION))));

        List<Long> regularCompleteTimes = new ArrayList<>();
        for (Future<SpyingTaskPayload> rf : regularFutures) {
            regularCompleteTimes.add(rf.get().getRunEndTimeNanos());
        }
        List<Long> quickStartTimes = new ArrayList<>();
        for (Future<SpyingTaskPayload> qf : quickFutures) {
            quickStartTimes.add(qf.get().getRunStartTimeNanos());
        }
        long latestCompleteTimeOfRegularTasks = regularCompleteTimes.stream().mapToLong(ct -> ct).max().getAsLong();
        long earliestStartTimeOfQuickTasks = quickStartTimes.stream().mapToLong(st -> st).min().getAsLong();
        assertSame(regularTaskExecutor, quickTaskExecutor);
        assertTrue(latestCompleteTimeOfRegularTasks < earliestStartTimeOfQuickTasks);
    }

    private List<SpyingTaskPayload> getStubInputItemWithRandomCorrelationKeys(int collectionSize) {
        List<SpyingTaskPayload> result = new ArrayList<>();
        for (int i = 0; i < collectionSize; i++) {
            result.add(new SpyingTaskPayload(RANDOM.nextLong()));
        }
        return result;
    }

}
