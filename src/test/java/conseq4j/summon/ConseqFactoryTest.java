/*
 * MIT License
 *
 * Copyright (c) 2023 Qingtian Wang
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
package conseq4j.summon;

import com.google.common.collect.Range;
import conseq4j.SpyingTask;
import conseq4j.TestUtils;
import elf4j.Logger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static conseq4j.TestUtils.createSpyingTasks;
import static conseq4j.TestUtils.getResultsIfAllNormal;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Qingtian Wang
 */
class ConseqFactoryTest {
    private static final int TASK_COUNT = 100;
    private static final Logger info = Logger.instance(ConseqFactoryTest.class);

    private static List<Callable<SpyingTask>> toCallables(List<SpyingTask> tasks) {
        return tasks.stream().map(SpyingTask::toCallable).collect(Collectors.toList());
    }

    void assertSingleThread(List<SpyingTask> tasks) {
        Set<String> distinctThreads = tasks.stream().map(SpyingTask::getRunThreadName).collect(Collectors.toSet());
        assertEquals(1, distinctThreads.size());
        info.log("[{}] tasks executed by single thread: [{}]",
                tasks.size(),
                distinctThreads.stream().findFirst().orElseThrow(NoSuchElementException::new));
    }

    @Test
    void concurrencyBoundedByTotalTaskCount() {
        Conseq withHigherConcurrencyThanTaskCount = Conseq.ofConcurrency(TASK_COUNT * 2);

        List<Future<SpyingTask>> futures = createSpyingTasks(TASK_COUNT).stream()
                .map(task -> withHigherConcurrencyThanTaskCount.getSequentialExecutorService(UUID.randomUUID())
                        .submit(task.toCallable()))
                .collect(toList());

        final long totalRunThreads =
                getResultsIfAllNormal(futures).stream().map(SpyingTask::getRunThreadName).distinct().count();
        info.log("[{}] tasks were run by [{}] threads", TASK_COUNT, totalRunThreads);
        assertTrue(totalRunThreads <= TASK_COUNT);
    }

    @Test
    void higherConcurrencyRendersBetterThroughput() {
        List<SpyingTask> sameTasks = createSpyingTasks(TASK_COUNT);
        int lowConcurrency = 2;
        int highConcurrency = lowConcurrency * 10;

        Conseq withLowConcurrency = Conseq.ofConcurrency(lowConcurrency);
        long lowConcurrencyStart = System.nanoTime();
        List<Future<SpyingTask>> lowConcurrencyFutures = sameTasks.stream()
                .map(t -> withLowConcurrency.getSequentialExecutorService(UUID.randomUUID()).submit(t.toCallable()))
                .collect(toList());
        TestUtils.awaitFutures(lowConcurrencyFutures);
        long lowConcurrencyTime = System.nanoTime() - lowConcurrencyStart;

        Conseq withHighConcurrency = Conseq.ofConcurrency(highConcurrency);
        long highConcurrencyStart = System.nanoTime();
        List<Future<SpyingTask>> highConcurrencyFutures = sameTasks.stream()
                .map(task -> withHighConcurrency.getSequentialExecutorService(UUID.randomUUID())
                        .submit(task.toCallable()))
                .collect(toList());
        TestUtils.awaitFutures(highConcurrencyFutures);
        long highConcurrencyTime = System.nanoTime() - highConcurrencyStart;

        info.log("low concurrency: {}, ran duration: {}; high concurrency: {}, ran duration: {}",
                lowConcurrency,
                Duration.ofNanos(lowConcurrencyTime),
                highConcurrency,
                Duration.ofNanos(highConcurrencyTime));
        assertTrue(lowConcurrencyTime > highConcurrencyTime);
    }

    @Test
    void invokeAllRunsTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        Conseq defaultConseq = Conseq.ofDefaultConcurrency();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        final List<Future<SpyingTask>> completedFutures =
                defaultConseq.getSequentialExecutorService(sameSequenceKey).invokeAll(toCallables(tasks));

        final List<SpyingTask> doneTasks = getResultsIfAllNormal(completedFutures);
        assertSingleThread(doneTasks);
    }

    @Test
    void invokeAnyChoosesTaskInSequenceRange() throws InterruptedException, ExecutionException {
        Conseq defaultConseq = Conseq.ofDefaultConcurrency();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        SpyingTask doneTask = defaultConseq.getSequentialExecutorService(sameSequenceKey).invokeAny(toCallables(tasks));

        final Integer scheduledSequence = doneTask.getScheduledSequence();
        info.log("chosen task sequence: [{}]", scheduledSequence);
        assertTrue(Range.closedOpen(0, TASK_COUNT).contains(scheduledSequence));
    }

    @Test
    void submitsRunAllTasksOfSameSequenceKeyInSequence() {
        Conseq defaultConseq = Conseq.ofDefaultConcurrency();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> defaultConseq.getSequentialExecutorService(sameSequenceKey).execute(task));

        TestUtils.awaitTasks(tasks);
        assertSingleThread(tasks);
    }
}
