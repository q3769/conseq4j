/*
 * MIT License
 *
 * Copyright (c) 2021 Qingtian Wang
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static conseq4j.TestUtils.createSpyingTasks;
import static conseq4j.TestUtils.getIfAllCompleteNormal;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Qingtian Wang
 */
class ConseqServiceFactoryTest {
    private static final int TASK_COUNT = 100;

    private static List<Callable<SpyingTask>> toCallables(List<SpyingTask> tasks) {
        return tasks.stream().map(SpyingTask::toCallable).collect(Collectors.toList());
    }

    void assertSingleThread(List<SpyingTask> tasks) {
        List<String> distinctThreads = tasks.stream().map(SpyingTask::getRunThreadName).distinct().toList();
        assertEquals(1, distinctThreads.size());
    }

    @Test
    void concurrencyBoundedByTotalTaskCount() {
        ConseqServiceFactory withHigherConcurrencyThanTaskCount = ConseqServiceFactory.instance(TASK_COUNT * 2);

        List<Future<SpyingTask>> futures = createSpyingTasks(TASK_COUNT).stream()
                .map(task -> withHigherConcurrencyThanTaskCount.getExecutorService(UUID.randomUUID())
                        .submit(task.toCallable()))
                .collect(toList());

        final long totalRunThreads =
                getIfAllCompleteNormal(futures).stream().map(SpyingTask::getRunThreadName).distinct().count();
        assertTrue(totalRunThreads <= TASK_COUNT);
    }

    @Test
    void higherConcurrencyRendersBetterThroughput() {
        List<SpyingTask> sameTasks = createSpyingTasks(TASK_COUNT);
        int lowConcurrency = 2;
        int highConcurrency = lowConcurrency * 10;

        ConseqServiceFactory withLowConcurrency = ConseqServiceFactory.instance(lowConcurrency);
        long lowConcurrencyStart = System.nanoTime();
        List<Future<SpyingTask>> lowConcurrencyFutures = sameTasks.stream()
                .map(t -> withLowConcurrency.getExecutorService(UUID.randomUUID()).submit(t.toCallable()))
                .collect(toList());
        TestUtils.awaitFutures(lowConcurrencyFutures);
        long lowConcurrencyTime = System.nanoTime() - lowConcurrencyStart;

        ConseqServiceFactory withHighConcurrency = ConseqServiceFactory.instance(highConcurrency);
        long highConcurrencyStart = System.nanoTime();
        List<Future<SpyingTask>> highConcurrencyFutures = sameTasks.stream()
                .map(task -> withHighConcurrency.getExecutorService(UUID.randomUUID()).submit(task.toCallable()))
                .collect(toList());
        TestUtils.awaitFutures(highConcurrencyFutures);
        long highConcurrencyTime = System.nanoTime() - highConcurrencyStart;

        assertTrue(lowConcurrencyTime > highConcurrencyTime);
    }

    @Test
    void invokeAllRunsTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        ConseqServiceFactory defaultConseqServiceFactory = ConseqServiceFactory.instance();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        final List<Future<SpyingTask>> completedFutures =
                defaultConseqServiceFactory.getExecutorService(sameSequenceKey).invokeAll(toCallables(tasks));

        final List<SpyingTask> doneTasks = getIfAllCompleteNormal(completedFutures);
        assertSingleThread(doneTasks);
    }

    @Test
    void invokeAnyChoosesTaskInSequenceRange() throws InterruptedException, ExecutionException {
        ConseqServiceFactory defaultConseqServiceFactory = ConseqServiceFactory.instance();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        SpyingTask doneTask =
                defaultConseqServiceFactory.getExecutorService(sameSequenceKey).invokeAny(toCallables(tasks));

        final Integer scheduledSequenceIndex = doneTask.getScheduledSequenceIndex();
        assertTrue(Range.closedOpen(0, TASK_COUNT).contains(scheduledSequenceIndex));
    }

    @Test
    void submitsRunAllTasksOfSameSequenceKeyInSequence() {
        ConseqServiceFactory defaultConseqServiceFactory = ConseqServiceFactory.instance();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();

        tasks.forEach(task -> defaultConseqServiceFactory.getExecutorService(sameSequenceKey).execute(task));

        TestUtils.awaitAllComplete(tasks);
        assertSingleThread(tasks);
    }
}
