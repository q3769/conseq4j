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
package conseq4j;

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
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import lombok.extern.java.Log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author Qingtian Wang
 */
@Log
public class ConseqTest {

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

    @Test
    public void concurrencyBoundedByTotalTaskCount() throws InterruptedException {
        final Conseq.Builder builder = Conseq.newBuilder();
        Conseq defaultConseq = builder.build();
        List<SpyingTask> tasks = getSpyingTasks(TASK_COUNT);

        List<Future<SpyingTask>> results = new ArrayList<>();
        tasks.forEach(task -> results.add(defaultConseq.submit(UUID.randomUUID(), (Callable) task)));

        Set<String> runThreadNames = results.stream()
                .map(r -> {
                    String threadName = null;
                    try {
                        threadName = r.get()
                                .getRunThreadName();
                    } catch (InterruptedException | ExecutionException ex) {
                        throw new IllegalStateException(ex);
                    }
                    return threadName == null ? null : threadName;
                })
                .collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        log.log(Level.INFO, "{0} tasks were run by {1} threads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(totalRunThreads <= TASK_COUNT);
    }

    @Test
    public void concurrencyBoundedByMaxConccurrency() throws InterruptedException {
        List<SpyingTask> sameTasks = getSpyingTasks(TASK_COUNT);
        final int lowConcurrency = TASK_COUNT / 10;
        Conseq lcConseq = Conseq.newBuilder()
                .globalConcurrency(lowConcurrency)
                .build();
        List<Future<SpyingTask>> lcFutures = new ArrayList<>();
        long lowConcurrencyStart = System.nanoTime();
        sameTasks.forEach(task -> lcFutures.add(lcConseq.submit(UUID.randomUUID(), (Callable) task)));
        lcFutures.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new IllegalStateException(ex);
            }
        });
        long lowConcurrencyTime = System.nanoTime() - lowConcurrencyStart;

        final int highConcurrency = TASK_COUNT;
        Conseq hcConseq = Conseq.newBuilder()
                .globalConcurrency(highConcurrency)
                .build();
        List<Future<SpyingTask>> hcFutures = new ArrayList<>();
        long highConcurrencyStart = System.nanoTime();
        sameTasks.forEach(task -> hcFutures.add(hcConseq.submit(UUID.randomUUID(), (Callable) task)));
        hcFutures.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new IllegalStateException(ex);
            }
        });
        long highConcurrencyTime = System.nanoTime() - highConcurrencyStart;

        log.log(Level.INFO, "Low concurrency run time {0}, high concurrency run time {1}", new Object[] { Duration
                .ofNanos(lowConcurrencyTime), Duration.ofNanos(highConcurrencyTime) });
        assertHighConcurrencyIsFaster(lowConcurrencyTime, highConcurrencyTime);
    }

    void assertHighConcurrencyIsFaster(long lowConcurrencyTime, long highConcurrencyTime) {
        assertTrue(lowConcurrencyTime > highConcurrencyTime);
    }

    @Test
    public void bulkSubmitRunsAllTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        ConcurrentSequencer defaultConseq = Conseq.newBuilder()
                .build();
        List<SpyingTask> tasks = getSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        final List<Future<SpyingTask>> futures = defaultConseq.invokeAll(sameSequenceKey, tasks);

        final List<SpyingTask> futureResults = toAllResults(futures);
        assertSingleThread(futureResults);
        assertSequence(futureResults);
    }

    @Test
    public void bulkAnySubmitChoosesTaskInSequenceRange() throws InterruptedException, ExecutionException {
        ConcurrentSequencer defaultConseq = Conseq.newBuilder()
                .build();
        List<SpyingTask> tasks = getSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        SpyingTask doneTask = defaultConseq.invokeAny(sameSequenceKey, tasks);

        final Integer scheduledSequence = doneTask.getScheduledSequence();
        log.log(Level.INFO, "Chosen task sequence : {0}", scheduledSequence);
        assertTrue(scheduledSequence >= 0 && scheduledSequence < TASK_COUNT);
    }

    @Test
    public void singleSubmitRunsAllTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        ConcurrentSequencer defaultConseq = Conseq.newBuilder()
                .build();
        List<SpyingTask> tasks = getSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> {
            defaultConseq.execute(sameSequenceKey, task);
        });
        final int extraFactorEnsuringAllDone = TASK_COUNT / 10;
        TimeUnit.MILLISECONDS.sleep((TASK_COUNT + extraFactorEnsuringAllDone) * SpyingTask.MAX_RUN_TIME_MILLIS);

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

    List<SpyingTask> toAllResults(List<Future<SpyingTask>> futures) {
        return futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (InterruptedException | ExecutionException ex) {
                        throw new IllegalStateException(ex);
                    }
                })
                .collect(toList());
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

    private static List<SpyingTask> getSpyingTasks(int total) {
        List<SpyingTask> result = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            result.add(new SpyingTask(i));
        }
        return result;
    }
}
