/*
 * The MIT License Copyright 2021 Qingtian Wang. Permission is hereby granted, free of charge, to
 * any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
 * KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package conseq4j.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import lombok.extern.java.Log;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * @author Qingtian Wang
 */
@Log
public class ConseqServiceTest {

    private static final int TASK_COUNT = 100;

    private static final Level TEST_RUN_LOG_LEVEL = Level.FINE;

    @BeforeAll
    public static void setLoggingLevel() {
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

    @BeforeEach
    void setUp(TestInfo testInfo) {
        log.info(String.format("================================== start test: %s", testInfo.getDisplayName()));
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        log.info(String.format("================================== done test: %s", testInfo.getDisplayName()));
    }

    @Test
    public void concurrencyBoundedByTotalTaskCount() throws InterruptedException {
        ConseqService defaultConseq = ConseqService.newBuilder()
                .build();

        List<Future<SpyingTask>> futures = new ArrayList<>();
        createSpyingTasks(TASK_COUNT).forEach(task -> futures.add(defaultConseq.submit(UUID.randomUUID(),
                (Callable) task)));

        final long totalRunThreads = toDoneTasks(futures).stream()
                .map(SpyingTask::getRunThreadName)
                .distinct()
                .count();
        log.log(Level.INFO, "{0} tasks were run by {1} threads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(totalRunThreads <= TASK_COUNT);
    }

    @Test
    public void concurrencyBoundedByMaxConccurrency() throws InterruptedException {
        List<SpyingTask> sameTasks = createSpyingTasks(TASK_COUNT);
        final int lowConcurrency = TASK_COUNT / 10;
        ConseqService lcConseq = ConseqService.newBuilder()
                .globalConcurrency(lowConcurrency)
                .build();
        List<Future<SpyingTask>> lcFutures = new ArrayList<>();
        long lowConcurrencyStart = System.nanoTime();
        sameTasks.forEach(task -> lcFutures.add(lcConseq.submit(UUID.randomUUID(), (Callable) task)));
        waitForAllComplete(lcFutures);
        long lowConcurrencyTime = System.nanoTime() - lowConcurrencyStart;

        final int highConcurrency = TASK_COUNT;
        ConseqService hcConseq = ConseqService.newBuilder()
                .globalConcurrency(highConcurrency)
                .build();
        List<Future<SpyingTask>> hcFutures = new ArrayList<>();
        long highConcurrencyStart = System.nanoTime();
        sameTasks.forEach(task -> hcFutures.add(hcConseq.submit(UUID.randomUUID(), (Callable) task)));
        waitForAllComplete(hcFutures);
        long highConcurrencyTime = System.nanoTime() - highConcurrencyStart;

        log.log(Level.INFO, "Low concurrency run time {0}, high concurrency run time {1}", new Object[] { Duration
                .ofNanos(lowConcurrencyTime), Duration.ofNanos(highConcurrencyTime) });
        assertHighConcurrencyIsFaster(lowConcurrencyTime, highConcurrencyTime);
    }

    private static void waitForAllComplete(List<Future<SpyingTask>> futures) {
        futures.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    void assertHighConcurrencyIsFaster(long lowConcurrencyTime, long highConcurrencyTime) {
        assertTrue(lowConcurrencyTime > highConcurrencyTime);
    }

    @Test
    public void syncInvokeAllRunsTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        ConcurrentSequencerService defaultConseq = ConseqService.newBuilder()
                .build();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        log.log(Level.INFO, () -> "Start single sync invoke all " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);
        final List<Future<SpyingTask>> futures = defaultConseq.invokeAll(sameSequenceKey, tasks);
        log.log(Level.INFO, () -> "Done single sync invoke all " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);

        final List<SpyingTask> doneTasks = toDoneTasks(futures);
        assertSingleThread(doneTasks);
        assertSequence(doneTasks);
    }

    @Test
    public void syncInvokeAnyChoosesTaskInSequenceRange() throws InterruptedException, ExecutionException {
        ConcurrentSequencerService defaultConseq = ConseqService.newBuilder()
                .build();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        log.log(Level.INFO, () -> "Start single sync invoke any in " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);
        SpyingTask doneTask = defaultConseq.invokeAny(sameSequenceKey, tasks);
        log.log(Level.INFO, () -> "Done single sync invoke any in " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);

        final Integer scheduledSequence = doneTask.getScheduledSequence();
        log.log(Level.INFO, "Chosen task sequence : {0}", scheduledSequence);
        assertTrue(scheduledSequence >= 0 && scheduledSequence < TASK_COUNT);
    }

    @Test
    public void asyncSubmitsRunAllTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        ConcurrentSequencerService defaultConseq = ConseqService.newBuilder()
                .build();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        log.log(Level.INFO, () -> "Start async submitting each of " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);
        tasks.forEach(task -> {
            defaultConseq.execute(sameSequenceKey, task);
        });
        log.log(Level.INFO, () -> "Done async submitting each of " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);
        final int extraFactorEnsuringAllDone = TASK_COUNT / 10;
        final int timeToAllowAllComplete = (TASK_COUNT + extraFactorEnsuringAllDone) * SpyingTask.MAX_RUN_TIME_MILLIS;
        log.log(Level.INFO, () -> "Sleeping for " + Duration.ofMillis(timeToAllowAllComplete)
                + " to allow all to complete...");
        TimeUnit.MILLISECONDS.sleep(timeToAllowAllComplete);

        assertSingleThread(tasks);
        assertSequence(tasks);
    }

    void assertSingleThread(List<SpyingTask> tasks) {
        Set<String> uniqueThreadNames = tasks.stream()
                .map(SpyingTask::getRunThreadName)
                .filter(Objects::nonNull)
                .collect(toSet());
        assertEquals(1, uniqueThreadNames.size());
        log.log(Level.INFO, "{0} tasks executed by single thread {1}", new Object[] { tasks.size(), uniqueThreadNames
                .stream()
                .findFirst()
                .get() });
    }

    List<SpyingTask> toDoneTasks(List<Future<SpyingTask>> futures) {
        log.log(Level.FINE, () -> "Wait and get all results on futures " + futures);
        final List<SpyingTask> doneTasks = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (InterruptedException | ExecutionException ex) {
                        throw new IllegalStateException(ex);
                    }
                })
                .collect(toList());
        log.log(Level.FINE, () -> "All futures done, results: " + doneTasks);
        return doneTasks;
    }

    void assertSequence(List<SpyingTask> tasks) {
        for (int i = 0; i < tasks.size() - 1; i++) {
            final Instant currentEnd = tasks.get(i)
                    .getRunEnd();
            final Instant nextStart = tasks.get(i + 1)
                    .getRunStart();
            assertFalse(currentEnd.isAfter(nextStart));
        }
        log.log(Level.INFO, "{0} tasks executed sequentially in chronical order", tasks.size());
    }

    private static List<SpyingTask> createSpyingTasks(int total) {
        List<SpyingTask> result = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            result.add(new SpyingTask(i));
        }
        return result;
    }
}
