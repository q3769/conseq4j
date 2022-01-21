/*
 * The MIT License
 * Copyright 2021 Qingtian Wang.
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package conseq4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * @author q3769
 */
public class ConseqTest {

    private static final Logger LOG = Logger.getLogger(ConseqTest.class.getName());
    private static final Duration TASK_DURATION = Duration.ofMillis(42);
    private static final int TASK_COUNT = 10;

    @Test
    public void defaultConcurrencyBoundedByTotalTaskCount() throws InterruptedException {
        ConcurrentSequencer defaultConseq = Conseq.ofDefault();
        List<SpyingRunnable> tasks = getSpyingRunnables(TASK_COUNT, TASK_DURATION);
        tasks.stream()
                .forEach(task -> defaultConseq.runAsync(UUID.randomUUID(), task));
        final Duration durationTillAllTasksDone = Duration.ofSeconds(TASK_DURATION.getSeconds() * (TASK_COUNT + 1));
        // await().atLeast(durationTillAllTasksDone);
        Thread.sleep(durationTillAllTasksDone.toMillis());

        Set<String> runThreadNames = tasks.stream()
                .map(task -> task.getRunThreadName())
                .collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(runThreadNames.size() > 1, "Expecting concurrency, not sequencing");
        assertTrue(totalRunThreads <= TASK_COUNT);
    }

    @Test
    public void concurrencyBoundedByConfiguration() throws InterruptedException {
        final int customizedConcurrency = TASK_COUNT / 2;
        ConcurrentSequencer conseqWithConcurrencyCustomized = Conseq.ofConcurrency(customizedConcurrency);
        List<SpyingRunnable> tasks = getSpyingRunnables(TASK_COUNT, TASK_DURATION);
        tasks.stream()
                .forEach(task -> conseqWithConcurrencyCustomized.runAsync(UUID.randomUUID(), task));
        final Duration durationTillAllTasksDone = Duration.ofSeconds(TASK_DURATION.getSeconds() * (TASK_COUNT + 1));
        // await().atLeast(durationTillAllTasksDone);
        Thread.sleep(durationTillAllTasksDone.toMillis());

        Set<String> runThreadNames = tasks.stream()
                .map(task -> task.getRunThreadName())
                .collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(runThreadNames.size() > 1, "Expecting concurrency, not sequencing");
        assertTrue(totalRunThreads < TASK_COUNT);
        assertTrue(totalRunThreads >= customizedConcurrency);
    }

    @Test
    public void consecutiveOrderOnSameSequenceKeyRegardlessConcurrency() throws InterruptedException {
        final int bigConcurrency = 1000;
        final ConcurrentSequencer conseqOfhighConcurrency = Conseq.ofConcurrency(bigConcurrency);
        List<SpyingRunnable> tasks = getSpyingRunnables(TASK_COUNT, TASK_DURATION);
        final Duration longerDuration = TASK_DURATION.multipliedBy(10);
        List<SpyingRunnable> bigTasks = getSpyingRunnables(TASK_COUNT, longerDuration);
        List<SpyingRunnable> allTasks = new ArrayList(bigTasks);
        allTasks.addAll(tasks);

        allTasks.stream()
                .forEach(task -> conseqOfhighConcurrency.runAsync("sameSequenceKey", task));
        final long untilAllDone = longerDuration.toMillis() * allTasks.size();
        // await().atLeast(Duration.ofMillis(untilAllDone));
        Thread.sleep(untilAllDone);

        List<Instant> doneTimes = allTasks.stream()
                .map(task -> task.getRunEnd())
                .collect(Collectors.toList());
        List<Instant> sorted = new ArrayList(doneTimes);
        Collections.sort(sorted);
        assertEquals(doneTimes, sorted);
    }

    private static List<SpyingRunnable> getSpyingRunnables(int total, Duration runDuration) {
        List<SpyingRunnable> result = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            result.add(new SpyingRunnable(i, runDuration));
        }
        return result;
    }

}
