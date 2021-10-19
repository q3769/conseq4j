# conseq (i.e. Concurrent Sequencer)

As a client of this Java API, I want to summon a thread/executor by a sequence key, so that all related tasks with the same sequence key are executed sequentially by the same executor while unrelated tasks with different sequence keys can be executed concurrently by different executors.

## Prerequisite
Java 8 or better

## Get it...
### In Maven
```
<dependency>
    <groupId>io.github.q3769.qlib</groupId>
    <artifactId>conseq</artifactId>
    <version>20211019.0.0</version>
</dependency>
```
### In Gradle
```
implementation 'io.github.q3769.qlib:conseq:20211017.2.0'
```

## Use it...
See test code but here's a gist
```
    @Test
    public void defaultConseqRunsWithUnboundMaxConcurrencyButBoundByTotalTaskCount() throws InterruptedException {
        ConcurrentSequencer defaultConseq = ConcurrentSequentialExecutors.newBuilder().build();
        assert defaultConseq.getMaxConcurrency() == Integer.MAX_VALUE; // Default max concurrency is "unbound".
        Collection<Runnable> runnableTasks = spyingRunnables(TASK_COUNT, TASK_DURATION);

        runnableTasks.stream().forEach((Runnable task) -> {
            SpyingRunnableTask action = (SpyingRunnableTask) task;
            final Object sequenceKey = action.getSequenceKey(); // Sequence key can come from anywhere but recall that the same sequence key means sqeuential execution of the tasks behind the same (physically or logically) single thread.
            final ExecutorService sequentialExecutor = defaultConseq.getSequentialExecutor(sequenceKey); // Here you get an instance of good old JDK ExecutorService by way of Executors.newSingleThreadExecutor(); of course, the same instance is reused when summoned by the same seqence key. 
            sequentialExecutor.execute(task); // Your task can be a Runnable, a Callable, or whatever ExecutorService supports.
        });
        Thread.sleep(DURATION_UNTIL_ALL_TASKS_DONE_MILLIS);

        Set<String> runThreadNames = runnableTasks.stream().map(action -> ((SpyingRunnableTask) action).getRunThreadName()).collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[]{TASK_COUNT, totalRunThreads});
        assertTrue(totalRunThreads <= TASK_COUNT); // Even though "unbound" by default, concurrency won't be greater than total tasks.
    }
```

```
    @Test
    public void conseqShouldBeBoundByMaxMaxConcurrency() throws InterruptedException {
        final int maxConcurrency = TASK_COUNT / 2;
        ConcurrentSequencer maxConcurrencyBoundConseq = ConcurrentSequentialExecutors.newBuilder().withMaxConcurrency(maxConcurrency).build();
        Collection<Callable> callableTasks = spyingCallables(TASK_COUNT, TASK_DURATION);

        callableTasks.stream().forEach((Callable task) -> {
            SpyingCallableTask action = (SpyingCallableTask) task;
            maxConcurrencyBoundConseq.getSequentialExecutor(action.getSequenceKey()).submit(task);
        });
        Thread.sleep(DURATION_UNTIL_ALL_TASKS_DONE_MILLIS);

        Set<String> runThreadNames = callableTasks.stream().map(action -> ((SpyingCallableTask) action).getRunThreadName()).collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[]{TASK_COUNT, totalRunThreads});
        assertTrue(totalRunThreads <= maxConcurrency); // If, as in most cases, the max concurrency (think "max thread pool size") is set to be smaller than your potential tasks, then the total number of concurrent threads to have run your tasks will be bound by the max concurrency you set.
    }
```

```
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
        long latestCompleteTimeOfRegularTasks = regularTasks.stream().mapToLong(task -> ((SpyingCallableTask) task).getRunEndTimeNanos()).max().orElseThrow();
        long earliestStartTimeOfQuickTasks = quickTasks.stream().mapToLong(task -> ((SpyingCallableTask) task).getRunStartTimeNanos()).min().orElseThrow();
        assertTrue(latestCompleteTimeOfRegularTasks < earliestStartTimeOfQuickTasks); // OK ma, this is not enough to logically prove the global order but you get the idea...
    }
```
