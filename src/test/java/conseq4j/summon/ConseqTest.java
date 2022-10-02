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
package conseq4j.summon;

import com.google.common.collect.Range;
import conseq4j.SpyingTask;
import conseq4j.TestUtils;
import lombok.extern.slf4j.Slf4j;
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
import static conseq4j.TestUtils.getAll;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Qingtian Wang
 */
@Slf4j
class ConseqTest {

    private static final int TASK_COUNT = 100;

    @Test
    void concurrencyBoundedByTotalTaskCount() {
        Conseq withHigherConcurrencyThanTaskCount = new Conseq(TASK_COUNT * 2);

        List<Future<SpyingTask>> futures = createSpyingTasks(TASK_COUNT).stream()
                .map(task -> withHigherConcurrencyThanTaskCount.getSequentialExecutor(UUID.randomUUID())
                        .submit((Callable<SpyingTask>) task))
                .collect(toList());

        final long totalRunThreads = getAll(futures).stream().map(SpyingTask::getRunThreadName).distinct().count();
        log.atInfo().log("[{}] tasks were run by [{}] threads", TASK_COUNT, totalRunThreads);
        assertTrue(totalRunThreads <= TASK_COUNT);
    }

    @Test
    void higherConcurrencyRendersBetterThroughput() {
        List<SpyingTask> sameTasks = createSpyingTasks(TASK_COUNT);
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

        log.atInfo().log("low concurrency: [{}], run time: [{}]", lowConcurrency, Duration.ofNanos(lowConcurrencyTime));
        log.atInfo()
                .log("high concurrency: [{}], run time: [{}]", highConcurrency, Duration.ofNanos(highConcurrencyTime));
        assertTrue(lowConcurrencyTime > highConcurrencyTime);
    }

    @Test
    void invokeAllRunsTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        Conseq defaultConseq = new Conseq();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        final List<Future<SpyingTask>> completedFutures =
                defaultConseq.getSequentialExecutor(sameSequenceKey).invokeAll(tasks);

        final List<SpyingTask> doneTasks = getAll(completedFutures);
        assertSingleThread(doneTasks);
    }

    @Test
    void invokeAnyChoosesTaskInSequenceRange() throws InterruptedException, ExecutionException {
        Conseq defaultConseq = new Conseq();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        SpyingTask doneTask = defaultConseq.getSequentialExecutor(sameSequenceKey).invokeAny(tasks);

        final Integer scheduledSequence = doneTask.getScheduledSequence();
        log.atInfo().log("Chosen task sequence : [{}]", scheduledSequence);
        assertTrue(Range.closedOpen(0, TASK_COUNT).contains(scheduledSequence));
    }

    @Test
    void submitsRunAllTasksOfSameSequenceKeyInSequence() {
        Conseq defaultConseq = new Conseq();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> defaultConseq.getSequentialExecutor(sameSequenceKey).execute(task));

        TestUtils.awaitDone(tasks);
        assertSingleThread(tasks);
    }

    void assertSingleThread(List<SpyingTask> tasks) {
        Set<String> distinctThreads = tasks.stream().map(SpyingTask::getRunThreadName).collect(Collectors.toSet());
        assertEquals(1, distinctThreads.size());
        log.atInfo()
                .log("[{}] tasks executed by single thread: [{}]",
                        tasks.size(),
                        distinctThreads.stream().findFirst().orElseThrow(NoSuchElementException::new));
    }
}
