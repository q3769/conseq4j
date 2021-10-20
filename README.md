# conseq (i.e. Concurrent Sequencer)

As a client of this Java API, I want to summon a thread/executor by a sequence key, so that all related tasks with the same sequence key are executed sequentially by the same executor while unrelated tasks with different sequence keys can be executed concurrently by different executors.

## Prerequisite
Java 8 or better

## Get it...
In Maven
```
<dependency>
    <groupId>io.github.q3769.qlib</groupId>
    <artifactId>conseq</artifactId>
    <version>20211019.0.0</version>
</dependency>
```
In Gradle
```
implementation 'io.github.q3769.qlib:conseq:20211019.0.0'
```

## Use it...
See test code but here's a gist
```
    @Test
    public void defaultConseqRunsWithUnboundMaxConcurrencyButBoundByTotalTaskCount() throws InterruptedException {
        ConcurrentSequencer defaultConseq = ConcurrentSequentialExecutors.newBuilder().build();
        assert defaultConseq.getMaxConcurrency() == Integer.MAX_VALUE; // Default max concurrency is "unbound".
        List<SpyingTaskPayload> taskPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT); // SpyingTaskPayload is an example, your input data can be of any type

        taskPayloads.forEach(payload -> {
            final Object sequenceKey = payload.getCorrelationKey(); // Sequence key can come from anywhere but most likely from the input data payload. Note that the same sequence key means sqeuential execution of the tasks behind the same (physically or logically) single thread.
            final ExecutorService sequentialExecutor = defaultConseq.getSequentialExecutor(sequenceKey); // Here you get an instance of good old JDK ExecutorService by way of Executors.newSingleThreadExecutor(); of course, the same instance is reused when summoned by the same seqence key. 
            sequentialExecutor.execute(new SpyingRunnableTask(payload, TASK_DURATION)); // Your task can be a Runnable, a Callable, or whatever ExecutorService supports. Up to you how to convert an input data item into a runnable command.
        });
        Thread.sleep(DURATION_UNTIL_ALL_TASKS_DONE.getSeconds() * 1000);

        Set<String> runThreadNames = taskPayloads.stream().map(item -> item.getRunThreadName()).collect(Collectors.toSet());
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[]{TASK_COUNT, totalRunThreads});
        assertTrue(totalRunThreads <= TASK_COUNT); // Even though "unbound" by default, concurrency won't be greater than total tasks.
    }
```

```
    @Test
    public void conseqShouldBeBoundByMaxMaxConcurrency() throws InterruptedException, ExecutionException {
        final int maxConcurrency = TASK_COUNT / 2;
        ConcurrentSequencer maxConcurrencyBoundConseq = ConcurrentSequentialExecutors.newBuilder().withMaxConcurrency(maxConcurrency).build();
        List<SpyingTaskPayload> dataPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT);
        List<Future<SpyingTaskPayload>> taskFutures = new ArrayList<>();

        dataPayloads.forEach(payload -> taskFutures.add(maxConcurrencyBoundConseq.getSequentialExecutor(payload.getCorrelationKey()).submit(new SpyingCallableTask(payload, TASK_DURATION))));

        Set<String> runThreadNames = new HashSet<>();
        for (Future<SpyingTaskPayload> f : taskFutures) {
            runThreadNames.add(f.get().getRunThreadName());
        }
        final int totalRunThreads = runThreadNames.size();
        LOG.log(Level.INFO, "{0} tasks were run by {1} theads", new Object[]{TASK_COUNT, totalRunThreads});
        assertTrue(totalRunThreads <= maxConcurrency); // If, as in most cases, the max concurrency (think "max thread pool size") is set to be smaller than your potential tasks, then the total number of concurrent threads to have run your tasks will be bound by the max concurrency you set.
    }
```

```
    @Test
    public void conseqShouldRunRelatedTasksInOrder() throws InterruptedException, ExecutionException {
        ConcurrentSequencer defaultConseq = ConcurrentSequentialExecutors.newBuilder().build();
        List<SpyingTaskPayload> regularPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT);
        List<SpyingTaskPayload> smallPayloads = getStubInputItemWithRandomCorrelationKeys(TASK_COUNT);
        List<Future<SpyingTaskPayload>> regularFutures = new ArrayList<>();
        List<Future<SpyingTaskPayload>> quickFutures = new ArrayList<>();
        Object sequenceKey = UUID.randomUUID();
        final ExecutorService regularTaskExecutor = defaultConseq.getSequentialExecutor(sequenceKey);
        final ExecutorService quickTaskExecutor = defaultConseq.getSequentialExecutor(sequenceKey); // Same sequence key for regular and quick tasks

        regularPayloads.stream().forEach(regularPayload -> {
            regularFutures.add(regularTaskExecutor.submit(new SpyingCallableTask(regularPayload, TASK_DURATION)));
        }); // Slower tasks first
        smallPayloads.stream().forEach(smallPayload -> {
            quickFutures.add(quickTaskExecutor.submit(new SpyingCallableTask(smallPayload, SMALL_TASK_DURATION)));
        }); // Faster tasks later so none of the faster ones should be executed until all slower ones are done

        assertSame(regularTaskExecutor, quickTaskExecutor); // Same sequence key, therefore, same executor thread.
        List<Long> regularCompleteTimes = new ArrayList<>();
        for (Future<SpyingTaskPayload> rf : regularFutures) {
            regularCompleteTimes.add(rf.get().getRunEndTimeNanos());
        }
        List<Long> quickStartTimes = new ArrayList<>();
        for (Future<SpyingTaskPayload> qf : quickFutures) {
            quickStartTimes.add(qf.get().getRunStartTimeNanos());
        }
        long latestCompleteTimeOfRegularTasks = regularCompleteTimes.stream().mapToLong(ct -> ct).max().getAsLong();
        long earliestStartTimeOfQuickTasks = quickStartTimes.stream().mapToLong(st -> st).min().getAsLong();
        assertTrue(latestCompleteTimeOfRegularTasks < earliestStartTimeOfQuickTasks); // OK ma, this is not enough to logically prove the global order but you get the idea...
    }
```
