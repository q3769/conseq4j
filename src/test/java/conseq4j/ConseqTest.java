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
package conseq4j;

import lombok.extern.java.Log;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Qingtian Wang
 */
@Log class ConseqTest {

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

    private static void awaitAllComplete(List<Future<SpyingTask>> futures) {
        futures.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new IllegalStateException(ex);
            }
        });
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

    @Test void concurrencyBoundedByTotalTaskCount() {
        Conseq defaultConseq = Conseq.newBuilder().build();

        List<Future<SpyingTask>> futures = new ArrayList<>();
        createSpyingTasks(TASK_COUNT).forEach(task -> futures.add(
                defaultConseq.getSequentialExecutor(UUID.randomUUID()).submit((Callable<SpyingTask>) task)));

        final long totalRunThreads = toDoneTasks(futures).stream().map(SpyingTask::getRunThreadName).distinct().count();
        log.log(Level.INFO, "{0} tasks were run by {1} threads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(totalRunThreads <= TASK_COUNT);
    }

    @Test void higherConcurrencyRendersBetterThroughput() {
        List<SpyingTask> sameTasks = createSpyingTasks(TASK_COUNT);
        int lowConcurrency = 2;
        int highConcurrency = lowConcurrency * 10;

        Conseq lowConcurrencyService = Conseq.newBuilder().globalConcurrency(lowConcurrency).build();
        List<Future<SpyingTask>> lowConcurrencyFutures = new ArrayList<>();
        long lowConcurrencyStart = System.nanoTime();
        sameTasks.forEach(task -> lowConcurrencyFutures.add(
                lowConcurrencyService.getSequentialExecutor(UUID.randomUUID()).submit((Callable<SpyingTask>) task)));
        awaitAllComplete(lowConcurrencyFutures);
        long lowConcurrencyTime = System.nanoTime() - lowConcurrencyStart;

        Conseq highConcurrencyService = Conseq.newBuilder().globalConcurrency(highConcurrency).build();
        List<Future<SpyingTask>> highConcurrencyFutures = new ArrayList<>();
        long highConcurrencyStart = System.nanoTime();
        sameTasks.forEach(task -> highConcurrencyFutures.add(
                highConcurrencyService.getSequentialExecutor(UUID.randomUUID()).submit((Callable<SpyingTask>) task)));
        awaitAllComplete(highConcurrencyFutures);
        long highConcurrencyTime = System.nanoTime() - highConcurrencyStart;

        log.log(Level.INFO, "Low concurrency run time: {0}, high concurrency run time: {1}",
                new Object[] { Duration.ofNanos(lowConcurrencyTime), Duration.ofNanos(highConcurrencyTime) });
        assertTrue(lowConcurrencyTime > highConcurrencyTime);
    }

    @Test void invokeAllRunsTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        Conseq defaultConseq = Conseq.newBuilder().build();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        log.log(Level.INFO, () -> "Start single sync invoke all " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);
        final List<Future<SpyingTask>> futures = defaultConseq.getSequentialExecutor(sameSequenceKey).invokeAll(tasks);
        log.log(Level.INFO, () -> "Done single sync invoke all " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);

        final List<SpyingTask> doneTasks = toDoneTasks(futures);
        assertSingleThread(doneTasks);
    }

    @Test void invokeAnyChoosesTaskInSequenceRange() throws InterruptedException, ExecutionException {
        Conseq defaultConseq = Conseq.newBuilder().build();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        log.log(Level.INFO, () -> "Start single sync invoke any in " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);
        SpyingTask doneTask = defaultConseq.getSequentialExecutor(sameSequenceKey).invokeAny(tasks);
        log.log(Level.INFO, () -> "Done single sync invoke any in " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);

        final Integer scheduledSequence = doneTask.getScheduledSequence();
        log.log(Level.INFO, "Chosen task sequence : {0}", scheduledSequence);
        assertTrue(scheduledSequence >= 0 && scheduledSequence < TASK_COUNT);
    }

    @Test void submitsRunAllTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        Conseq defaultConseq = Conseq.newBuilder().build();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();
        final int extraFactorEnsuringAllDone = TASK_COUNT / 10;
        final int timeToAllowAllComplete = (TASK_COUNT + extraFactorEnsuringAllDone) * SpyingTask.MAX_RUN_TIME_MILLIS;

        log.log(Level.INFO, () -> "Start async submitting each of " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);
        tasks.forEach(task -> defaultConseq.getSequentialExecutor(sameSequenceKey).execute(task));
        log.log(Level.INFO, () -> "Done async submitting each of " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);
        TimeUnit.MILLISECONDS.sleep(timeToAllowAllComplete);

        assertSingleThread(tasks);
    }

    @Test void excessiveTasksOverTaskQueueCapacityWillBeRejected() {
        int executorTaskQueueCapacity = 42;
        assertTrue(true);
        Conseq taskQueueCapacityLimited = Conseq.newBuilder().executorTaskQueueSize(executorTaskQueueCapacity).build();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        try {
            tasks.forEach(task -> taskQueueCapacityLimited.getSequentialExecutor(sameSequenceKey).execute(task));
        } catch (RejectedExecutionException e) {
            log.log(Level.WARNING,
                    "almost never a good idea to limit task queue capacity; unless you intend to reject excessive tasks that the task queue cannot hold, consider default/unbounded capacity instead",
                    e);
            return;
        }
        fail();
    }

    void assertSingleThread(List<SpyingTask> tasks) {
        Set<String> uniqueThreadNames =
                tasks.stream().map(SpyingTask::getRunThreadName).filter(Objects::nonNull).collect(toSet());
        assertEquals(1, uniqueThreadNames.size());
        log.log(Level.INFO, "{0} tasks executed by single thread {1}",
                new Object[] { tasks.size(), uniqueThreadNames.stream().findFirst().get() });
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
