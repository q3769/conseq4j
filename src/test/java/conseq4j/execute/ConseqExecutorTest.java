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

package conseq4j.execute;

import com.google.common.collect.Range;
import conseq4j.SpyingTask;
import conseq4j.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import static conseq4j.TestUtils.awaitAllComplete;
import static conseq4j.TestUtils.createSpyingTasks;
import static java.util.stream.Collectors.toList;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConseqExecutorTest {
    private static final int TASK_COUNT = 100;

    @Test
    void exceptionallyCompletedSubmitShouldNotStopOtherTaskExecution() {
        List<Future<SpyingTask>> resultFutures;
        try (ConseqExecutor conseqExecutor = ConseqExecutor.instance()) {
            List<SpyingTask> tasks = TestUtils.createSpyingTasks(TASK_COUNT);
            UUID sameSequenceKey = UUID.randomUUID();

            resultFutures = new ArrayList<>();
            int cancelTaskIdx = 1;
            for (int i = 0; i < TASK_COUNT; i++) {
                Future<SpyingTask> taskFuture = conseqExecutor.submit(tasks.get(i).toCallable(), sameSequenceKey);
                if (i == cancelTaskIdx) {
                    taskFuture.cancel(true);
                }
                resultFutures.add(taskFuture);
            }
            int cancelledCount = TestUtils.cancellationCount(resultFutures);
            int normalCompleteCount = TestUtils.normalCompletionCount(resultFutures);
            assertEquals(1, cancelledCount);
            assertEquals(resultFutures.size(), cancelledCount + normalCompleteCount);
        }
    }

    @Test
    void executeRunsAllTasksOfSameSequenceKeyInSequence() {
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(TASK_COUNT);
        int concurrency = TASK_COUNT * 2;
        ConseqExecutor conseqExecutor = ConseqExecutor.instance(concurrency);
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> conseqExecutor.execute(task, sameSequenceKey));

        TestUtils.assertConsecutiveRuntimes(tasks);
        int actualThreadCount = TestUtils.actualExecutionThreadCount(tasks);
        assertTrue(Range.closed(1, TASK_COUNT).contains(actualThreadCount));
    }

    @Test
    void noExecutorLingersOnRandomSequenceKeys() {
        try (ConseqExecutor sut = ConseqExecutor.instance()) {
            List<SpyingTask> tasks = TestUtils.createSpyingTasks(100);

            tasks.parallelStream().forEach(t -> sut.execute(t, UUID.randomUUID()));
            TestUtils.awaitAllComplete(tasks);

            await().until(() -> sut.estimateActiveExecutorCount() == 0);
        }
    }

    @Test
    void noExecutorLingersOnSameSequenceKey() {
        try (ConseqExecutor sut = ConseqExecutor.instance()) {
            UUID sameSequenceKey = UUID.randomUUID();
            List<SpyingTask> tasks = TestUtils.createSpyingTasks(100);

            tasks.parallelStream().forEach(t -> sut.execute(t, sameSequenceKey));
            TestUtils.awaitAllComplete(tasks);

            await().until(() -> sut.estimateActiveExecutorCount() == 0);
        }
    }

    @Test
    void provideConcurrencyAmongDifferentSequenceKeys() {
        List<SpyingTask> sameTasks = createSpyingTasks(TASK_COUNT / 2);
        UUID sameSequenceKey = UUID.randomUUID();
        long sameKeyStartTimeMillis;
        long sameKeyEndTimeMillis;
        long differentKeysStartTimeMillis;
        try (ConseqExecutor sut = ConseqExecutor.instance()) {

            sameKeyStartTimeMillis = System.currentTimeMillis();
            sameTasks.forEach(t -> sut.execute(t, sameSequenceKey));
            awaitAllComplete(sameTasks);
            sameKeyEndTimeMillis = System.currentTimeMillis();
            differentKeysStartTimeMillis = System.currentTimeMillis();
            sameTasks.forEach(t -> sut.execute(t, UUID.randomUUID()));
        }
        awaitAllComplete(sameTasks);
        long differentKeysEndTimeMillis = System.currentTimeMillis();

        long runtimeConcurrent = differentKeysEndTimeMillis - differentKeysStartTimeMillis;
        long runtimeSequential = sameKeyEndTimeMillis - sameKeyStartTimeMillis;
        assertTrue(runtimeConcurrent < runtimeSequential);
    }

    @Test
    void submitConcurrencyBoundedByThreadPoolSize() {
        int threadPoolSize = TASK_COUNT / 10;
        List<Future<SpyingTask>> futures;
        try (ConseqExecutor conseqExecutor = ConseqExecutor.instance(threadPoolSize)) {

            futures = TestUtils.createSpyingTasks(TASK_COUNT)
                    .stream()
                    .map(task -> conseqExecutor.submit(task.toCallable(), UUID.randomUUID()))
                    .collect(toList());
        }

        final long actualThreadCount = TestUtils.actualExecutionThreadCountIfAllCompleteNormal(futures);
        assertEquals(threadPoolSize, actualThreadCount);
    }

    @Test
    void submitConcurrencyBoundedByTotalTaskCount() {
        List<Future<SpyingTask>> futures;
        try (ConseqExecutor conseqExecutor = ConseqExecutor.instance(TASK_COUNT * 10)) {

            futures = TestUtils.createSpyingTasks(TASK_COUNT)
                    .stream()
                    .map(task -> conseqExecutor.submit(task.toCallable(), UUID.randomUUID()))
                    .collect(toList());
        }

        final long actualThreadCount = TestUtils.actualExecutionThreadCountIfAllCompleteNormal(futures);
        assertTrue(actualThreadCount <= TASK_COUNT);
    }
}
