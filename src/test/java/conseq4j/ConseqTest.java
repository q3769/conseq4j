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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import lombok.extern.java.Log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * @author Qingtian Wang
 */
@Log
public class ConseqTest {

    private static final int TASK_COUNT = 100;

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
        log.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[] { TASK_COUNT, totalRunThreads });
        assertEquals(Integer.MAX_VALUE, builder.getGlobalConcurrency());
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

    private void assertHighConcurrencyIsFaster(long lowConcurrencyTime, long highConcurrencyTime) {
        assertTrue(lowConcurrencyTime > highConcurrencyTime);
    }

    @Test
    public void defaultConcurrencyRunsAllTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        ConcurrentSequencer defaultConseq = Conseq.newBuilder()
                .build();
        List<SpyingTask> tasks = getSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> {
            defaultConseq.execute(sameSequenceKey, task);
        });
        TimeUnit.MILLISECONDS.sleep(TASK_COUNT * SpyingTask.MAX_RUN_TIME_MILLIS);

        Set<String> uniqueThreadNames = new HashSet<>();
        tasks.forEach(task -> uniqueThreadNames.add(task.getRunThreadName()));
        assertEquals(1, uniqueThreadNames.size());

        for (int i = 0; i < tasks.size() - 1; i++) {
            final Instant currentEnd = tasks.get(i)
                    .getRunEnd();
            final Instant nextStart = tasks.get(i + 1)
                    .getRunStart();
            assertFalse(currentEnd.isAfter(nextStart));
        }
    }

    private static List<SpyingTask> getSpyingTasks(int total) {
        List<SpyingTask> result = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            result.add(new SpyingTask(i));
        }
        return result;
    }
}
