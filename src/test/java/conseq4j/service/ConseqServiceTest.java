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

    private static List<SpyingTask> createSpyingTasks(int total) {
        List<SpyingTask> result = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            result.add(new SpyingTask(i));
        }
        return result;
    }

    @BeforeEach void setUp(TestInfo testInfo) {
        log.info(String.format("================================== start test: %s", testInfo.getDisplayName()));
    }

    @AfterEach void tearDown(TestInfo testInfo) {
        log.info(String.format("================================== done test: %s", testInfo.getDisplayName()));
    }

    @Test void submitConcurrencyBoundedByTotalTaskCount() {
        ConseqService conseqService = ConseqService.withExecutionThreadPool(Executors.newFixedThreadPool(20));

        List<Future<SpyingTask>> futures = createSpyingTasks(TASK_COUNT).stream()
                .map(task -> conseqService.submit(task, UUID.randomUUID()))
                .collect(toList());

        final long totalRunThreads = toDoneTasks(futures).stream().map(SpyingTask::getRunThreadName).distinct().count();
        log.log(Level.INFO, "{0} tasks were run by {1} threads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(totalRunThreads > 1);
        assertTrue(totalRunThreads <= TASK_COUNT);
        assertExecutorsSweptCleanWhenFinished(conseqService);
    }

    @Test void executeRunsAllTasksOfSameSequenceKeyInSequence() {
        ConseqService conseqService = new ConseqService();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> conseqService.execute(task, sameSequenceKey));

        assertConsecutiveRuntimes(tasks);
        assertExecutorsSweptCleanWhenFinished(conseqService);
    }

    @Test void exceptionallyCompletedSubmitShouldNotStopOtherTaskExecution()
            throws InterruptedException, ExecutionException {
        ConseqService conseqService = new ConseqService();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        List<Future<SpyingTask>> resultFutures = new ArrayList<>();
        int cancelTaskIdx = 1;
        for (int i = 0; i < TASK_COUNT; i++) {
            Future<SpyingTask> taskFuture = conseqService.submit(tasks.get(i), sameSequenceKey);
            if (i == cancelTaskIdx) {
                System.out.println("cancelling task " + taskFuture);
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

    @Test void returnMinimalisticFuture() {
        Future<SpyingTask> result = new ConseqService().submit(new SpyingTask(1), UUID.randomUUID());

        assertFalse(result instanceof CompletableFuture);
    }

    @Test void canCustomizeBackingThreadPool() {
        ExecutorService customBackingThreadPool = Executors.newFixedThreadPool(42);
        ConseqService conseqService = ConseqService.withExecutionThreadPool(customBackingThreadPool);

        String customPoolName = customBackingThreadPool.getClass().getName();
        assertEquals(customPoolName, conseqService.getExecutionThreadPoolTypeName());
    }

    @Test void defaultBackingThreadPool() {
        ExecutorService expected = ForkJoinPool.commonPool();
        ConseqService defaultConseqService = new ConseqService();

        String expectedPoolName = expected.getClass().getName();
        assertEquals(expectedPoolName, defaultConseqService.getExecutionThreadPoolTypeName());
    }

    private int normalCompleteCount(List<Future<SpyingTask>> resultFutures)
            throws ExecutionException, InterruptedException {
        List<SpyingTask> results = new ArrayList<>();
        for (Future<SpyingTask> future : resultFutures) {
            if (future.isCancelled())
                continue;
            try {
                results.add(future.get());
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "error obtaining result from " + future, e);
            }
        }
        log.log(Level.FINE, results.size() + " normal results in " + results);
        return results.size();
    }

    private int cancelledCount(List<Future<SpyingTask>> futures) {
        int result = 0;
        for (Future<SpyingTask> f : futures) {
            if (f.isCancelled()) {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    log.log(Level.WARNING, f + " was interrupted", e);
                } catch (ExecutionException e) {
                    log.log(Level.WARNING, f + " had execution error", e);
                } catch (RuntimeException e) {
                    log.log(Level.WARNING, "run-time error obtaining result from " + f, e);
                }
                result++;
            }
        }
        return result;
    }

    private void assertConsecutiveRuntimes(List<SpyingTask> tasks) {
        for (int i = 0; i < tasks.size() - 1; i++) {
            SpyingTask current = tasks.get(i);
            SpyingTask next = tasks.get(i + 1);
            if (current.getRunEnd() > next.getRunStart())
                log.log(Level.WARNING,
                        "execution out of order between current task " + current + " and next task " + next);
            assertFalse(current.getRunEnd() > next.getRunStart());
        }
    }

    List<SpyingTask> toDoneTasks(List<Future<SpyingTask>> futures) {
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
}
