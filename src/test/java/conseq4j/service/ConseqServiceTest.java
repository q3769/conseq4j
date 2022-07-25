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
import lombok.extern.java.Log;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Log class ConseqServiceTest {

    private static final int TASK_COUNT = 100;

    private static final Level TEST_RUN_LOG_LEVEL = Level.FINE;

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

    private static List<SpyingTask> createSpyingTasks() {
        List<SpyingTask> result = new ArrayList<>();
        for (int i = 0; i < ConseqServiceTest.TASK_COUNT; i++) {
            result.add(new SpyingTask(i));
        }
        return result;
    }

    private static int totalRunThreadCount(List<SpyingTask> tasks) {
        return (int) tasks.stream().map(SpyingTask::getRunThreadName).distinct().count();
    }

    private static long totalDoneThreadCount(List<Future<SpyingTask>> futures) {
        return toDoneTasks(futures).stream().map(SpyingTask::getRunThreadName).distinct().count();
    }

    static List<SpyingTask> toDoneTasks(List<Future<SpyingTask>> futures) {
        log.log(Level.FINER, () -> "Wait and get all results on futures " + futures);
        final List<SpyingTask> doneTasks = futures.stream().map(f -> {
            try {
                return f.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new IllegalStateException(ex);
            }
        }).collect(toList());
        log.log(Level.FINER, () -> "All futures done, results: " + doneTasks);
        return doneTasks;
    }

    static private <T> void awaitAllDone(List<Future<T>> futures) {
        await().until(() -> futures.parallelStream().allMatch(Future::isDone));
    }

    @BeforeEach void setUp(TestInfo testInfo) {
        log.info(String.format("================================== start test: %s", testInfo.getDisplayName()));
    }

    @AfterEach void tearDown(TestInfo testInfo) {
        log.info(String.format("================================== done test: %s", testInfo.getDisplayName()));
    }

    @Test void submitConcurrencyBoundedByThreadPoolSize() {
        int threadPoolSize = TASK_COUNT / 10;
        ConseqService conseqService = new ConseqService(Executors.newFixedThreadPool(threadPoolSize));

        List<Future<SpyingTask>> futures = createSpyingTasks().stream()
                .map(task -> conseqService.submit(task, UUID.randomUUID()))
                .collect(toList());

        final long ranThreadsTotal = totalDoneThreadCount(futures);
        log.log(Level.INFO, "{0} tasks were run by {1} threads, with thread pool size {2}",
                new Object[] { TASK_COUNT, ranThreadsTotal, threadPoolSize });
        assertEquals(threadPoolSize, ranThreadsTotal);
        assertExecutorsSweptCleanWhenFinished(conseqService);
    }

    @Test void submitConcurrencyBoundedByTotalTaskCount() {
        int threadPoolSize = TASK_COUNT * 10;
        ConseqService conseqService = new ConseqService(Executors.newFixedThreadPool(threadPoolSize));

        List<Future<SpyingTask>> futures = createSpyingTasks().stream()
                .map(task -> conseqService.submit(task, UUID.randomUUID()))
                .collect(toList());

        final long ranThreadsTotal = totalDoneThreadCount(futures);
        log.log(Level.INFO, "{0} tasks were run by {1} threads, with thread pool size {2}",
                new Object[] { TASK_COUNT, ranThreadsTotal, threadPoolSize });
        assertEquals(TASK_COUNT, ranThreadsTotal);
        assertExecutorsSweptCleanWhenFinished(conseqService);
    }

    @Test void executeRunsAllTasksOfSameSequenceKeyInSequence() {
        ConseqService conseqService = new ConseqService();
        List<SpyingTask> tasks = createSpyingTasks();
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> conseqService.execute(task, sameSequenceKey));

        assertExecutorsSweptCleanWhenFinished(conseqService);
        assertConsecutiveRuntimes(tasks);
        int ranThreadsTotal = totalRunThreadCount(tasks);
        log.info(TASK_COUNT + " tasks were run by " + ranThreadsTotal + " threads");
        assertTrue(Range.closed(1, TASK_COUNT).contains(ranThreadsTotal));
    }

    @Test void exceptionallyCompletedSubmitShouldNotStopOtherTaskExecution() {
        ConseqService conseqService = new ConseqService();
        List<SpyingTask> tasks = createSpyingTasks();
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

        int cancelledCount = cancelledCount(resultFutures);
        int normalCompleteCount = normalCompleteCount(resultFutures);
        assertEquals(1, cancelledCount);
        assertEquals(resultFutures.size() - cancelledCount, normalCompleteCount);
        assertExecutorsSweptCleanWhenFinished(conseqService);
    }

    private void assertExecutorsSweptCleanWhenFinished(ConseqService conseqService) {
        long timeStartNanos = System.nanoTime();
        await().with().pollInterval(20, TimeUnit.MILLISECONDS).until(() -> conseqService.getActiveExecutorCount() == 0);
        log.log(Level.INFO, "all executors swept clean in " + Duration.ofNanos(System.nanoTime() - timeStartNanos));
    }

    @Test void returnMinimalFuture() {
        Future<SpyingTask> result = new ConseqService().submit(new SpyingTask(1), UUID.randomUUID());

        assertFalse(result instanceof CompletableFuture);
    }

    @Test void canCustomizeBackingThreadPool() {
        ExecutorService customBackingThreadPool = Executors.newFixedThreadPool(42);
        ConseqService conseqService = new ConseqService(customBackingThreadPool);

        String customPoolName = customBackingThreadPool.getClass().getName();
        assertEquals(customPoolName, conseqService.getExecutionThreadPoolTypeName());
    }

    @Test void defaultBackingThreadPool() {
        ExecutorService expected = ForkJoinPool.commonPool();
        ConseqService defaultConseqService = new ConseqService();

        String expectedPoolName = expected.getClass().getName();
        assertEquals(expectedPoolName, defaultConseqService.getExecutionThreadPoolTypeName());
    }

    private int normalCompleteCount(List<Future<SpyingTask>> resultFutures) {
        int normalCompletionCount = 0;
        for (Future<SpyingTask> future : resultFutures) {
            if (future.isCancelled())
                continue;
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                continue;
            }
            normalCompletionCount++;
        }
        return normalCompletionCount;
    }

    private int cancelledCount(List<Future<SpyingTask>> futures) {
        awaitAllDone(futures);
        return futures.parallelStream().mapToInt(f -> f.isCancelled() ? 1 : 0).sum();
    }

    private void assertConsecutiveRuntimes(List<SpyingTask> tasks) {
        for (int i = 0; i < tasks.size() - 1; i++) {
            SpyingTask current = tasks.get(i);
            SpyingTask next = tasks.get(i + 1);
            if (current.getRunTimeEndMillis() > next.getRunTimeStartMillis())
                log.log(Level.WARNING,
                        "execution out of order between current task " + current + " and next task " + next);
            assertFalse(current.getRunTimeEndMillis() > next.getRunTimeStartMillis());
        }
    }
}
