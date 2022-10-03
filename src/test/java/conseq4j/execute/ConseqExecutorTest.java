/*
 * MIT License
 *
 * Copyright (c) 2022 Qingtian Wang
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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static conseq4j.TestUtils.awaitDone;
import static conseq4j.TestUtils.createSpyingTasks;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class ConseqExecutorTest {

    private static final int TASK_COUNT = 100;

    @Test
    void submitConcurrencyBoundedByThreadPoolSize() {
        int threadPoolSize = TASK_COUNT / 10;
        ConseqExecutor conseqExecutor = new ConseqExecutor(Executors.newFixedThreadPool(threadPoolSize));

        List<Future<SpyingTask>> futures = TestUtils.createSpyingTasks(TASK_COUNT)
                .stream()
                .map(task -> conseqExecutor.submit(task, UUID.randomUUID()))
                .collect(toList());

        final long actualThreadCount = TestUtils.actualCompletionThreadCount(futures);
        log.atInfo()
                .log("[{}] tasks were run by [{}] threads, with thread pool size [{}]",
                        TASK_COUNT,
                        actualThreadCount,
                        threadPoolSize);
        assertEquals(threadPoolSize, actualThreadCount);
    }

    @Test
    void submitConcurrencyBoundedByTotalTaskCount() {
        int threadPoolSize = TASK_COUNT * 10;
        ConseqExecutor conseqExecutor = new ConseqExecutor(Executors.newFixedThreadPool(threadPoolSize));

        List<Future<SpyingTask>> futures = TestUtils.createSpyingTasks(TASK_COUNT)
                .stream()
                .map(task -> conseqExecutor.submit(task, UUID.randomUUID()))
                .collect(toList());

        final long actualThreadCount = TestUtils.actualCompletionThreadCount(futures);
        log.atInfo()
                .log("[{}] tasks were run by [{}] threads, with thread pool size [{}]",
                        TASK_COUNT,
                        actualThreadCount,
                        threadPoolSize);
        assertEquals(TASK_COUNT, actualThreadCount);
    }

    @Test
    void executeRunsAllTasksOfSameSequenceKeyInSequence() {
        ConseqExecutor conseqExecutor = new ConseqExecutor(Executors.newFixedThreadPool(100));
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> conseqExecutor.execute(task, sameSequenceKey));

        TestUtils.assertConsecutiveRuntimes(tasks);
        int actualThreadCount = TestUtils.actualExecutionThreadCount(tasks);
        log.atInfo().log("[{}] tasks were run by [{}] threads", TASK_COUNT, actualThreadCount);
        assertTrue(Range.closed(1, TASK_COUNT).contains(actualThreadCount));
    }

    @Test
    void exceptionallyCompletedSubmitShouldNotStopOtherTaskExecution() {
        ConseqExecutor conseqExecutor = new ConseqExecutor();
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        List<Future<SpyingTask>> resultFutures = new ArrayList<>();
        int cancelTaskIdx = 1;
        for (int i = 0; i < TASK_COUNT; i++) {
            Future<SpyingTask> taskFuture = conseqExecutor.submit(tasks.get(i), sameSequenceKey);
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
    void returnMinimalFuture() {
        Future<SpyingTask> result = new ConseqExecutor().submit(new SpyingTask(1), UUID.randomUUID());

        assertFalse(result instanceof CompletableFuture);
    }

    @Test
    void provideConcurrencyAmongDifferentSequenceKeys() {
        List<SpyingTask> sameTasks = createSpyingTasks(TASK_COUNT / 2);
        UUID sameSequenceKey = UUID.randomUUID();
        ConseqExecutor sut = new ConseqExecutor();

        long sameKeyStartTimeMillis = System.currentTimeMillis();
        sameTasks.forEach(t -> sut.execute(t, sameSequenceKey));
        awaitDone(sameTasks);
        long sameKeyEndTimeMillis = System.currentTimeMillis();
        long differentKeysStartTimeMillis = System.currentTimeMillis();
        sameTasks.forEach(t -> sut.execute(t, UUID.randomUUID()));
        awaitDone(sameTasks);
        long differentKeysEndTimeMillis = System.currentTimeMillis();

        long runtimeConcurrent = differentKeysEndTimeMillis - differentKeysStartTimeMillis;
        long runtimeSequential = sameKeyEndTimeMillis - sameKeyStartTimeMillis;
        assertTrue(runtimeConcurrent < runtimeSequential);
    }
}