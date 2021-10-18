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
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    private static final long DURATION_UNTIL_ALL_TASKS_DONE_MILLIS = 3 * TASK_DURATION.getSeconds() * 1000;

    @Test
    public void defaultConseqRunsWithUnboundMaxConcurrencyButBoundByTotalTaskCount() throws InterruptedException {
        ConcurrentSequencer defaultConseq = ConcurrentSequentialExecutors.newBuilder().build();
        assert defaultConseq.getMaxConcurrency() == Integer.MAX_VALUE; // Default max concurrency is "unbound".
        Collection<Runnable> runnableTasks = spyingRunnables(TASK_COUNT, TASK_DURATION);

        runnableTasks.stream().forEach((Runnable task) -> {
            SpyingRunnableTask action = (SpyingRunnableTask) task;
            final Object sequenceKey = action.getSequenceKey(); // Sequence key can come from anywhere but recall that same sequence key means sqeuential execution of the tasks behind a (physically or logically) single thread.
            final ExecutorService sequentialExecutor = defaultConseq.getSequentialExecutor(sequenceKey); // Here you get an instance of good old JDK ExecutorService by way of Executors.newSingleThreadExecutor(); of course, the same instance is reused when summoned by the same seqence key. So yes, your task can be a Runnable, a Callable, or whatever ExecutorService supports.
            sequentialExecutor.execute(action);
        });
        Thread.sleep(DURATION_UNTIL_ALL_TASKS_DONE_MILLIS);

        Set<String> runThreadNames = runnableTasks.stream().map(action -> ((SpyingRunnableTask) action).getRunThreadName()).collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[]{TASK_COUNT, totalRunThreads});
        assertTrue(totalRunThreads <= TASK_COUNT); // Even though "unbound" by default, concurrency won't be greater than total tasks.
    }

    @Test
    public void conseqShouldBeBoundByMaxMaxConcurrency() throws InterruptedException {
        final int maxConcurrency = TASK_COUNT / 2;
        ConcurrentSequencer maxConcurrencyBoundConseq = ConcurrentSequentialExecutors.newBuilder().withMaxConcurrency(maxConcurrency).build();
        Collection<Callable> callableTasks = spyingCallables(TASK_COUNT, TASK_DURATION);

        callableTasks.stream().forEach((Callable task) -> {
            SpyingCallableTask action = (SpyingCallableTask) task;
            maxConcurrencyBoundConseq.getSequentialExecutor(action.getSequenceKey()).submit(action);
        });
        Thread.sleep(DURATION_UNTIL_ALL_TASKS_DONE_MILLIS);

        Set<String> runThreadNames = callableTasks.stream().map(action -> ((SpyingCallableTask) action).getRunThreadName()).collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[]{TASK_COUNT, totalRunThreads});
        assertTrue(totalRunThreads <= maxConcurrency); // If, as in most cases, the max concurrency (think "max thread pool size") is set to be smaller than your potential tasks, then the total number of concurrent threads to have run your tasks will be bound by the max concurrency you set.
    }

    @Test
    public void conseqShouldRunRelatedTasksInOrder() throws InterruptedException {
        ConcurrentSequencer defaultConseq = ConcurrentSequentialExecutors.newBuilder().build();
        int regularTaskCount = TASK_COUNT;
        int quickTaskCount = TASK_COUNT;
        Collection<Callable> regularTasks = spyingCallables(regularTaskCount, TASK_DURATION);
        Collection<Callable> quickTasks = spyingCallables(quickTaskCount, TASK_DURATION_QUICK);
        Object sequenceKey = UUID.randomUUID();
        final ExecutorService regularTaskExecutor = defaultConseq.getSequentialExecutor(sequenceKey);
        final ExecutorService quickTaskExecutor = defaultConseq.getSequentialExecutor(sequenceKey); // Same sequence key

        regularTasks.stream().forEach((Callable task) -> {
            regularTaskExecutor.submit(task);
        }); // Slower tasks first
        quickTasks.stream().forEach((Callable task) -> {
            quickTaskExecutor.submit(task);
        }); // Faster tasks later so none of the faster ones should be executed until all slower ones are done
        Thread.sleep(DURATION_UNTIL_ALL_TASKS_DONE_MILLIS);

        assertSame(regularTaskExecutor, quickTaskExecutor); // Same sequence key, therefore, same executor thread.
        long latestCompleteTimeOfRegularTasks = regularTasks.stream().mapToLong(task -> ((SpyingCallableTask) task).getRunEndNanos()).max().orElseThrow();
        long earliestStartTimeOfQuickTasks = quickTasks.stream().mapToLong(task -> ((SpyingCallableTask) task).getRunStartNanos()).min().orElseThrow();
        assertTrue(latestCompleteTimeOfRegularTasks < earliestStartTimeOfQuickTasks); // OK ma, scientifically this is not enough to prove the global order but you get the idea...
    }

    private Collection<Runnable> spyingRunnables(int taskCount, Duration taskDuration) {
        Collection<Runnable> runnableTasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            runnableTasks.add(new SpyingRunnableTask(UUID.randomUUID(), taskDuration));
        }
        return runnableTasks;
    }

    private Collection<Callable> spyingCallables(int taskCount, Duration taskDuration) {
        Collection<Callable> callableTasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            callableTasks.add(new SpyingCallableTask(UUID.randomUUID(), taskDuration));
        }
        return callableTasks;
    }

}
