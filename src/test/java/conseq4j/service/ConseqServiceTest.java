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

package conseq4j.service;

import com.google.common.collect.Range;
import conseq4j.SpyingTask;
import conseq4j.TestUtils;
import lombok.extern.java.Log;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

@Log class ConseqServiceTest {

    private static final int TASK_COUNT = 100;

    private static final Level TEST_RUN_LOG_LEVEL = Level.INFO;

    @BeforeAll public static void setLoggingLevel() {
        Logger root = Logger.getLogger("");
        // .level= ALL
        root.setLevel(TEST_RUN_LOG_LEVEL);
        for (Handler handler : root.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                // java.util.logging.ConsoleHandler.level = ALL
                handler.setLevel(TEST_RUN_LOG_LEVEL);
            }
        }
    }

    @BeforeEach void setUp(TestInfo testInfo) {
        log.info(String.format("===== start test: %s", testInfo.getDisplayName()));
    }

    @AfterEach void tearDown(TestInfo testInfo) {
        log.info(String.format("##### done test: %s", testInfo.getDisplayName()));
    }

    @Test void submitConcurrencyBoundedByThreadPoolSize() {
        int threadPoolSize = TASK_COUNT / 10;
        ConseqService conseqService = new ConseqService(Executors.newFixedThreadPool(threadPoolSize));

        List<Future<SpyingTask>> futures = TestUtils.createSpyingTasks(TASK_COUNT)
                .stream()
                .map(task -> conseqService.submit(task, UUID.randomUUID()))
                .collect(toList());

        final long actualThreadCount = TestUtils.actualCompletionThreadCount(futures);
        log.log(Level.INFO, "{0} tasks were run by {1} threads, with thread pool size {2}",
                new Object[] { TASK_COUNT, actualThreadCount, threadPoolSize });
        assertEquals(threadPoolSize, actualThreadCount);
    }

    @Test void submitConcurrencyBoundedByTotalTaskCount() {
        int threadPoolSize = TASK_COUNT * 10;
        ConseqService conseqService = new ConseqService(Executors.newFixedThreadPool(threadPoolSize));

        List<Future<SpyingTask>> futures = TestUtils.createSpyingTasks(TASK_COUNT)
                .stream()
                .map(task -> conseqService.submit(task, UUID.randomUUID()))
                .collect(toList());

        final long actualThreadCount = TestUtils.actualCompletionThreadCount(futures);
        log.log(Level.INFO, "{0} tasks were run by {1} threads, with thread pool size {2}",
                new Object[] { TASK_COUNT, actualThreadCount, threadPoolSize });
        assertEquals(TASK_COUNT, actualThreadCount);
    }

    @Test void executeRunsAllTasksOfSameSequenceKeyInSequence() {
        ConseqService conseqService = new ConseqService(Executors.newFixedThreadPool(100));
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> conseqService.execute(task, sameSequenceKey));

        TestUtils.assertConsecutiveRuntimes(tasks);
        int actualThreadCount = TestUtils.actualExecutionThreadCount(tasks);
        log.info(TASK_COUNT + " tasks were run by " + actualThreadCount + " threads");
        assertTrue(Range.closed(1, TASK_COUNT).contains(actualThreadCount));
    }

    @Test void exceptionallyCompletedSubmitShouldNotStopOtherTaskExecution() {
        ConseqService conseqService = new ConseqService();
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        List<Future<SpyingTask>> resultFutures = new ArrayList<>();
        int cancelTaskIdx = 1;
        for (int i = 0; i < TASK_COUNT; i++) {
            Future<SpyingTask> taskFuture = conseqService.submit(tasks.get(i), sameSequenceKey);
            if (i == cancelTaskIdx) {
                log.info("cancelling task " + taskFuture);
                try {
                    taskFuture.cancel(true);
                } catch (Exception e) {
                    log.log(Level.WARNING, "error cancelling " + taskFuture, e);
                }
            }
            resultFutures.add(taskFuture);
        }

        int cancelledCount = TestUtils.cancellationCount(resultFutures);
        int normalCompleteCount = TestUtils.normalCompletionCount(resultFutures);
        assertEquals(1, cancelledCount);
        assertEquals(resultFutures.size() - cancelledCount, normalCompleteCount);
    }

    @Test void returnMinimalFuture() {
        Future<SpyingTask> result = new ConseqService().submit(new SpyingTask(1), UUID.randomUUID());

        assertFalse(result instanceof CompletableFuture);
    }

}
