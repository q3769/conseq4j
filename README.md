# conseq (i.e. Concurrent Sequencer)

As a client of this JAVA API, I want to summon a thread/executor by a sequence key, so that all related tasks with the same sequence key are executed sequentially by the same executor while unrelated tasks with different sequence keys can be executed concurrently by different executors.

## Prerequisite
Java 8 or better

## Get it...
### In Maven
```
<dependency>
    <groupId>io.github.q3769.qlib</groupId>
    <artifactId>conseq</artifactId>
    <version>20211017.2.0</version>
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
    public void defaultConseqRunsWithUnboundMaxConcurrencyButBoundByTotalTaskCount() {
        ConcurrentSequencer defaultConseq = ConcurrentSequentialExecutors.newBuilder().build();
        assert defaultConseq.getMaxConcurrency() == Integer.MAX_VALUE; // Default max concurrency is "unbound"
        Collection<Runnable> runnableTasks = stubRunnables(TASK_COUNT, TASK_DURATION);
        
        runnableTasks.stream().forEach((Runnable task) -> {
            TestRunnable action = (TestRunnable) task;
            defaultConseq.getSequentialExecutor(action.getSequenceKey()).execute(action); // Sequence key can come from anywhere but recall that same sequence key means sqeuential executions behind a logical single thread
        });
        
        Set<String> runThreadNames = runnableTasks.stream().map(action -> ((TestRunnable) action).getRunThreadName()).collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[]{TASK_COUNT, totalRunThreads});
        assertTrue(totalRunThreads <= TASK_COUNT); // Even "unbound" by default, concurrency won't be greater than total tasks
    }
```

```
    @Test
    public void conseqShouldBeBoundByMaxMaxConcurrency() {
        final int maxConcurrency = TASK_COUNT / 2;
        ConcurrentSequencer maxConcurrencyBoundConseq = ConcurrentSequentialExecutors.newBuilder().withMaxConcurrency(maxConcurrency).build(); // Set your own max concurrency
        Collection<Callable> callableTasks = stubCallables(TASK_COUNT, TASK_DURATION);
        
        callableTasks.stream().forEach((Callable task) -> {
            TestCallable action = (TestCallable) task;
            maxConcurrencyBoundConseq.getSequentialExecutor(action.getSequenceKey()).submit(action); // Here you get an instance of good old JDK ExecutorService by way of Executors.newSingleThreadExecutor(), of course, reused under the same seqence key. So yes, your task can be a Runnable, a Callable, or whatever ExecutorService supports.
        });
        
        Set<String> runThreadNames = callableTasks.stream().map(action -> ((TestCallable) action).getRunThreadName()).collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[]{TASK_COUNT, totalRunThreads});
        assertTrue(totalRunThreads <= maxConcurrency); // If as most likely your max concurrency is set to be smaller than your tasks, the total number of concurrent threads to have run your tasks is bound by the max concurrency you set.
    }
```

```
    @Test
    public void conseqShouldRunRelatedTasksInOrder() {
        ConcurrentSequencer defaultConseq = ConcurrentSequentialExecutors.newBuilder().build();
        int quickTaskCount = TASK_COUNT;
        int regularTaskCount = TASK_COUNT;
        Collection<Callable> regularTasks = stubCallables(regularTaskCount, TASK_DURATION);
        Collection<Callable> quickTasks = stubCallables(quickTaskCount, TASK_DURATION_QUICK);
        Object sequenceKey = UUID.randomUUID(); // Same sequence key
        
        regularTasks.stream().forEach((Callable task) -> {
            defaultConseq.getSequentialExecutor(sequenceKey).submit(task);
        }); // Slower tasks first
        quickTasks.stream().forEach((Callable task) -> {
            defaultConseq.getSequentialExecutor(sequenceKey).submit(task);
        }); // Faster tasks later but none of the faster ones should be executed until all slower ones are done

        Collection<Callable> allTasks = Stream.of(regularTasks, quickTasks).flatMap(Collection::stream).collect(Collectors.toList());
        Set<String> runThreadNames = allTasks.stream().map(task -> ((TestConseqable) task).getRunThreadName()).collect(Collectors.toSet());
        assertEquals(1L, runThreadNames.size()); // Same sequence key, therefore, same executor thread.
        long latestCompleteTimeOfRegularTasks = regularTasks.stream().mapToLong(task -> ((TestCallable) task).getRunEndNanos()).max().orElseThrow();
        long earliestStartTimeOfQuickTasks = quickTasks.stream().mapToLong(task -> ((TestCallable) task).getRunStartNanos()).min().orElseThrow();
        assertTrue(latestCompleteTimeOfRegularTasks < earliestStartTimeOfQuickTasks); // OK ma, this doesn't test out the execution order scientifically but you get the idea...
    }
```
