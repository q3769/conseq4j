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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Qingtian Wang
 */
@Log class ConseqTest {

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

    private static List<SpyingTask> createSpyingTasks() {
        List<SpyingTask> result = new ArrayList<>();
        for (int i = 0; i < ConseqTest.TASK_COUNT; i++) {
            result.add(new SpyingTask(i));
        }
        return result;
    }

    static <T> List<T> getAll(List<Future<T>> futures) {
        return futures.stream().map(f -> {
            try {
                return f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).collect(toList());
    }

    @BeforeEach void setUp(TestInfo testInfo) {
        log.info(String.format("===== start test: %s", testInfo.getDisplayName()));
    }

    @AfterEach void tearDown(TestInfo testInfo) {
        log.info(String.format("##### done test: %s", testInfo.getDisplayName()));
    }

    @Test void concurrencyBoundedByTotalTaskCount() {
        Conseq withHigherConcurrencyThanTaskCount = new Conseq(TASK_COUNT * 2);

        List<Future<SpyingTask>> futures = createSpyingTasks().stream()
                .map(task -> withHigherConcurrencyThanTaskCount.getSequentialExecutor(UUID.randomUUID())
                        .submit((Callable<SpyingTask>) task))
                .collect(toList());

        final long totalRunThreads = getAll(futures).stream().map(SpyingTask::getRunThreadName).distinct().count();
        log.log(Level.INFO, "{0} tasks were run by {1} threads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(totalRunThreads <= TASK_COUNT);
    }

    @Test void higherConcurrencyRendersBetterThroughput() {
        List<SpyingTask> sameTasks = createSpyingTasks();
        int lowConcurrency = 2;
        int highConcurrency = lowConcurrency * 10;

        Conseq lowConcurrencyService = new Conseq(lowConcurrency);
        long lowConcurrencyStart = System.nanoTime();
        List<Future<SpyingTask>> lowConcurrencyFutures = sameTasks.stream()
                .map(t -> lowConcurrencyService.getSequentialExecutor(UUID.randomUUID())
                        .submit((Callable<SpyingTask>) t))
                .collect(toList());
        TestUtils.awaitAll(lowConcurrencyFutures);
        long lowConcurrencyTime = System.nanoTime() - lowConcurrencyStart;

        Conseq highConcurrencyService = new Conseq(highConcurrency);
        long highConcurrencyStart = System.nanoTime();
        List<Future<SpyingTask>> highConcurrencyFutures = sameTasks.stream()
                .map(task -> highConcurrencyService.getSequentialExecutor(UUID.randomUUID())
                        .submit((Callable<SpyingTask>) task))
                .collect(toList());
        TestUtils.awaitAll(highConcurrencyFutures);
        long highConcurrencyTime = System.nanoTime() - highConcurrencyStart;

        log.log(Level.INFO, "low concurrency: {0}, run time: {1}",
                new Object[] { lowConcurrency, Duration.ofNanos(lowConcurrencyTime) });
        log.log(Level.INFO, "high concurrency: {0}, run time: {1}",
                new Object[] { highConcurrency, Duration.ofNanos(highConcurrencyTime) });
        assertTrue(lowConcurrencyTime > highConcurrencyTime);
    }

    @Test void invokeAllRunsTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        Conseq defaultConseq = new Conseq();
        List<SpyingTask> tasks = createSpyingTasks();
        UUID sameSequenceKey = UUID.randomUUID();

        final List<Future<SpyingTask>> completedFutures =
                defaultConseq.getSequentialExecutor(sameSequenceKey).invokeAll(tasks);

        final List<SpyingTask> doneTasks = getAll(completedFutures);
        assertSingleThread(doneTasks);
    }

    @Test void invokeAnyChoosesTaskInSequenceRange() throws InterruptedException, ExecutionException {
        Conseq defaultConseq = new Conseq();
        List<SpyingTask> tasks = createSpyingTasks();
        UUID sameSequenceKey = UUID.randomUUID();

        SpyingTask doneTask = defaultConseq.getSequentialExecutor(sameSequenceKey).invokeAny(tasks);

        final Integer scheduledSequence = doneTask.getScheduledSequence();
        log.log(Level.INFO, "Chosen task sequence : {0}", scheduledSequence);
        assertTrue(scheduledSequence >= 0 && scheduledSequence < TASK_COUNT);
    }

    @Test void submitsRunAllTasksOfSameSequenceKeyInSequence() {
        Conseq defaultConseq = new Conseq();
        List<SpyingTask> tasks = createSpyingTasks();
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> defaultConseq.getSequentialExecutor(sameSequenceKey).execute(task));

        TestUtils.awaitDone(tasks);
        assertSingleThread(tasks);
    }

    void assertSingleThread(List<SpyingTask> tasks) {
        assertEquals(1, tasks.stream().map(SpyingTask::getRunThreadName).distinct().count());
        log.log(Level.INFO, "{0} tasks executed by single thread: {1}", new Object[] { tasks.size(),
                tasks.stream().findFirst().orElseThrow(NoSuchElementException::new).getRunThreadName() });
    }
}
