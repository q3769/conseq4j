/*
 * The MIT License
 *
 * Copyright 2021 Qingtian Wang.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package qlib.conseq;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 *
 * @author q3769
 */
public class ConcurrentSequentialExecutorsIntegrationTest {

    private static final Logger LOG = Logger.getLogger(ConcurrentSequentialExecutorsIntegrationTest.class.getName());
    private static final Duration TASK_DURATION = Duration.ofMillis(42);
    private static final Duration TASK_DURATION_QUICK = Duration.ofMillis(42 / 10);
    private static final int TASK_COUNT = 10;

    @Test
    public void defaultConseqRunsWithUnboundMaxConcurrencyButBoundByTotalTaskCount() {
        ConcurrentSequencer defaultConseq = ConcurrentSequentialExecutors.newBuilder().build();
        assert defaultConseq.getMaxConcurrency() == Integer.MAX_VALUE;
        Collection<Runnable> runnableTasks = stubRunnables(TASK_COUNT, TASK_DURATION);
        
        runnableTasks.stream().forEach((Runnable task) -> {
            TestRunnable action = (TestRunnable) task;
            defaultConseq.getSequentialExecutor(action.getSequenceKey()).execute(action);
        });
        
        Set<String> runThreadNames = runnableTasks.stream().map(action -> ((TestRunnable) action).getRunThreadName()).collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[]{TASK_COUNT, totalRunThreads});
        assertTrue(totalRunThreads <= TASK_COUNT);
    }

    @Test
    public void conseqShouldBeBoundByMaxMaxConcurrency() {
        final int maxConcurrency = TASK_COUNT / 2;
        ConcurrentSequencer maxConcurrencyBoundConseq = ConcurrentSequentialExecutors.newBuilder().withMaxConcurrency(maxConcurrency).build();
        Collection<Callable> callableTasks = stubCallables(TASK_COUNT, TASK_DURATION);
        
        callableTasks.stream().forEach((Callable task) -> {
            TestCallable action = (TestCallable) task;
            maxConcurrencyBoundConseq.getSequentialExecutor(action.getSequenceKey()).submit(action);
        });
        
        Set<String> runThreadNames = callableTasks.stream().map(action -> ((TestCallable) action).getRunThreadName()).collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[]{TASK_COUNT, totalRunThreads});
        assertTrue(runThreadNames.size() <= maxConcurrency);
    }

    @Test
    public void conseqShouldRunRelatedTasksInOrder() {
        ConcurrentSequencer defaultConseq = ConcurrentSequentialExecutors.newBuilder().build();
        int quickTaskCount = TASK_COUNT;
        int regularTaskCount = TASK_COUNT;
        Collection<Callable> regularTasks = stubCallables(regularTaskCount, TASK_DURATION);
        Collection<Callable> quickTasks = stubCallables(quickTaskCount, TASK_DURATION_QUICK);
        Object sequenceKey = UUID.randomUUID();
        
        regularTasks.stream().forEach((Callable task) -> {
            defaultConseq.getSequentialExecutor(sequenceKey).submit(task);
        });
        quickTasks.stream().forEach((Callable task) -> {
            defaultConseq.getSequentialExecutor(sequenceKey).submit(task);
        });

        Collection<Callable> allTasks = Stream.of(regularTasks, quickTasks).flatMap(Collection::stream).collect(Collectors.toList());
        Set<String> runThreadNames = allTasks.stream().map(task -> ((TestConseqable) task).getRunThreadName()).collect(Collectors.toSet());
        assertEquals(1L, runThreadNames.size());
        long latestCompleteTimeOfRegularTasks = regularTasks.stream().mapToLong(task -> ((TestCallable) task).getRunEndNanos()).max().orElseThrow();
        long earliestStartTimeOfQuickTasks = quickTasks.stream().mapToLong(task -> ((TestCallable) task).getRunStartNanos()).max().orElseThrow();
        assertTrue(latestCompleteTimeOfRegularTasks < earliestStartTimeOfQuickTasks);
    }

    private Collection<Runnable> stubRunnables(int taskCount, Duration taskDuration) {
        Collection<Runnable> runnableTasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            runnableTasks.add(new TestRunnable(UUID.randomUUID(), taskDuration));
        }
        return runnableTasks;
    }

    private Collection<Callable> stubCallables(int taskCount, Duration taskDuration) {
        Collection<Callable> callableTasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            callableTasks.add(new TestCallable(UUID.randomUUID(), taskDuration));
        }
        return callableTasks;
    }

}
