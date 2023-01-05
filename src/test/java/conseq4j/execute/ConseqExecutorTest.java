/*
 * MIT License
 *
 * Copyright (c) 2023 Qingtian Wang
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
import elf4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static conseq4j.TestUtils.awaitTasks;
import static conseq4j.TestUtils.createSpyingTasks;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class ConseqExecutorTest {
    private static final int TASK_COUNT = 100;
    private static final Logger info = Logger.instance().atInfo();

    @Test
    void canCustomizeBackingThreadPool() {
        ExecutorService customBackingThreadPool = Executors.newFixedThreadPool(42);
        ConseqExecutor sut = ConseqExecutor.withThreadPool(customBackingThreadPool);

        String customPoolName = customBackingThreadPool.getClass().getName();
        assertEquals(customPoolName, sut.getWorkerThreadPoolType());
    }

    @Test
    void defaultBackingThreadPool() {
        ExecutorService expected = ForkJoinPool.commonPool();
        ConseqExecutor sut = ConseqExecutor.withDefaultThreadPool();

        String expectedPoolName = expected.getClass().getName();
        assertEquals(expectedPoolName, sut.getWorkerThreadPoolType());
    }

    @Test
    void exceptionallyCompletedSubmitShouldNotStopOtherTaskExecution() {
        ConseqExecutor conseqExecutor = ConseqExecutor.withDefaultThreadPool();
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        List<Future<SpyingTask>> resultFutures = new ArrayList<>();
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

    @Test
    void executeRunsAllTasksOfSameSequenceKeyInSequence() {
        ConseqExecutor conseqExecutor = ConseqExecutor.withThreadPool(newFixedThreadPool(100));
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> conseqExecutor.execute(task, sameSequenceKey));

        TestUtils.assertConsecutiveRuntimes(tasks);
        int actualThreadCount = TestUtils.actualExecutionThreadCount(tasks);
        info.log("[{}] tasks were run by [{}] threads", TASK_COUNT, actualThreadCount);
        assertTrue(Range.closed(1, TASK_COUNT).contains(actualThreadCount));
    }

    @Test
    void noExecutorLingersOnRandomSequenceKeys() {
        ConseqExecutor sut = ConseqExecutor.withDefaultThreadPool();
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(100);

        tasks.parallelStream().forEach(t -> sut.execute(t, UUID.randomUUID()));
        TestUtils.awaitTasks(tasks);

        await().until(() -> sut.estimateActiveExecutorCount() == 0);
    }

    @Test
    void noExecutorLingersOnSameSequenceKey() {
        ConseqExecutor sut = ConseqExecutor.withDefaultThreadPool();
        UUID sameSequenceKey = UUID.randomUUID();
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(100);

        tasks.parallelStream().forEach(t -> sut.execute(t, sameSequenceKey));
        TestUtils.awaitTasks(tasks);

        await().until(() -> sut.estimateActiveExecutorCount() == 0);
    }

    @Test
    void provideConcurrencyAmongDifferentSequenceKeys() {
        List<SpyingTask> sameTasks = createSpyingTasks(TASK_COUNT / 2);
        UUID sameSequenceKey = UUID.randomUUID();
        ConseqExecutor sut = ConseqExecutor.withDefaultThreadPool();

        long sameKeyStartTimeMillis = System.currentTimeMillis();
        sameTasks.forEach(t -> sut.execute(t, sameSequenceKey));
        awaitTasks(sameTasks);
        long sameKeyEndTimeMillis = System.currentTimeMillis();
        long differentKeysStartTimeMillis = System.currentTimeMillis();
        sameTasks.forEach(t -> sut.execute(t, UUID.randomUUID()));
        awaitTasks(sameTasks);
        long differentKeysEndTimeMillis = System.currentTimeMillis();

        long runtimeConcurrent = differentKeysEndTimeMillis - differentKeysStartTimeMillis;
        long runtimeSequential = sameKeyEndTimeMillis - sameKeyStartTimeMillis;
        assertTrue(runtimeConcurrent < runtimeSequential);
    }

    @Test
    void returnMinimalFuture() {
        Future<SpyingTask> result =
                ConseqExecutor.withDefaultThreadPool().submit(new SpyingTask(1).toCallable(), UUID.randomUUID());

        assertFalse(result instanceof CompletableFuture);
    }

    @Test
    void submitConcurrencyBoundedByThreadPoolSize() {
        int threadPoolSize = TASK_COUNT / 10;
        ConseqExecutor conseqExecutor = ConseqExecutor.withThreadPool(newFixedThreadPool(threadPoolSize));

        List<Future<SpyingTask>> futures = TestUtils.createSpyingTasks(TASK_COUNT)
                .stream()
                .map(task -> conseqExecutor.submit(task.toCallable(), UUID.randomUUID()))
                .collect(toList());

        final long actualThreadCount = TestUtils.actualCompletionThreadCountIfAllNormal(futures);
        info.log("[{}] tasks were run by [{}] threads, with thread pool size [{}]",
                TASK_COUNT,
                actualThreadCount,
                threadPoolSize);
        assertEquals(threadPoolSize, actualThreadCount);
    }

    @Test
    void submitConcurrencyBoundedByTotalTaskCount() {
        int threadPoolSize = TASK_COUNT * 10;
        ConseqExecutor conseqExecutor = ConseqExecutor.withThreadPool(newFixedThreadPool(threadPoolSize));

        List<Future<SpyingTask>> futures = TestUtils.createSpyingTasks(TASK_COUNT)
                .stream()
                .map(task -> conseqExecutor.submit(task.toCallable(), UUID.randomUUID()))
                .collect(toList());

        final long actualThreadCount = TestUtils.actualCompletionThreadCountIfAllNormal(futures);
        info.log("[{}] tasks were run by [{}] threads, with thread pool size [{}]",
                TASK_COUNT,
                actualThreadCount,
                threadPoolSize);
        assertEquals(TASK_COUNT, actualThreadCount);
    }
}
