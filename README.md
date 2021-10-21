# conseq (i.e. Concurrent Sequencer)

As a client of this Java concurrent API, I want to summon a thread/executor by a sequence key, so that all related tasks with the same sequence key are executed sequentially by the same executor while unrelated tasks with different sequence keys can be executed concurrently by different executors.

## Prerequisite
Java 8 or better

## Get it...
In Maven
```
<dependency>
    <groupId>io.github.q3769.qlib</groupId>
    <artifactId>conseq</artifactId>
    <version>20211021.0.1</version>
</dependency>
```
In Gradle
```
implementation 'io.github.q3769.qlib:conseq:20211021.0.1'
```

## Use it...
For those that are in a hurry, skip directly to Setup 3.

The typical use case is with an asynchronous message consumer. First off, you can do Setup 1. The messaging provider (e.g. an EMS queue, a Kafka topic, ...) will usually make sure that messages are delivered to the `onMessage` method in the same order as they are received by the provider, and won't deliver the next message until the previous call to `onMessage` returns. Thus logically, all messages are consumed in a single-threaded fashion in the same/correct order as they are delivered. This is fine but processing all messages in sequential order globally is a bit slow, isn't it?

### Setup 1
```
public class MessageConsumer {
    public void onMessage(Message shoppingOrderEvent) {
        process(shoppingOrderEvent);
    }

    private void process(Message shoppingOrderEvent) {
        ...
    }
    ...
```
To speed up the process, you really want to do Setup 2 if you can - just "shot-gun" a bunch of concurrent threads - except sometimes you can't, not when the order of message consumption matters:

Imagine a shopping order is for a T-Shirt, and the shopper changed the size of the shirt between Medium and Large, back and forth for like 10 times, and eventually settled on... Medium. The 10 size changing events got posted to the messaging provider in the same order as the shopper placed them. At the time of posting, though, your consumer application was brought down for maintenance, so the 10 events were held and piled up in the messaging provider. Now your consumer application came back online, and all the 10 events were delivered to you in the correct order albeit within a very short period of time. 

### Setup 2
```
public class MessageConsumer {
    private ExecutorService concurrencer = Executors.newFixedThreadPool(10);
    
    public void onMessage(Message shoppingOrderEvent) {
        concurrencer.execute(() -> process(shoppingOrderEvent)); // Look ma, I got 10 concurrent threads working on this. That's gotta be faster, right?
    }    
    ...
```
As it turned out, with Setup 2, the shopper actually received a T-Shirt of size Large instead of the Medium that s/he so painstakingly settled on (got real mad at you, and knocked over your beer). And you wonder why that happened... Oh, got it, the shot-gun threads processed the 10 events out of order!

What now then, going back to Setup 1? Well, you can do that, at the expense of limiting performance. Or, you could use a "conseq" instead (and save your beer), as in Setup 3:

### Setup 3
```
public class MessageConsumer {
    private ConcurrentSequencer conseq = ConcurrentSequentialExecutors.newBuilder().ofSize(10).build();
    
    public void onMessage(Message shoppingOrderEvent) {
        conseq.getSequentialExecutor(shoppingOrderEvent.getOrderId()).execute(() -> process(shoppingOrderEvent)); // You still got up to 10 threads working for you, but all events of the same shopping order (orderId) will be done by a single thread
    }
    ...
```

As long as all the incoming events carry some kind of correlatable information that can be used or converted as a sequence key (see the full disclosure below), you can consider making use of a conseq. On the API level the sequence key can be any type of Object but good choices are identifiers that, after hashing, can group related events into the same hash code, and unrelated events into different hash codes. Some examples of the seqence key are order id, shipment id, ticket reservation id, session id, etc.... The default hashing algorithm is from the Guava library; that should be good enough but for those who have PhDs on hashing, you can provide your own consistent hasher as in `ConcurrentSequentialExecutors.newBuilder().withBucketHasher(myConsistentHasher)` instead of `ConcurrentSequentialExecutors.newBuilder().ofSize(myMaxConcurrencyInt)`.

### Full disclosure
In a multi-threaded/concurrent system there are generally two approaches to ensure correct order of message consumption:
1. Proactive/Preventive: This is on the technical level, making sure that related events are never processed out of order, e.g. by using a sequence/correlation key with this API as in Setup 3.
2. Reactive/Curative: This is on business rule level. Sometimes we have to accept the fact that preventative messures are not always possible, and assume at the time of processing things can be out of order already, e.g. when the events are coming from different message producers and sources; there is no garantee of correct ordering in the first place. Now the job is to "cure" the order based on business rules, after the fact. This can be much more complex both in terms of coding and runtime performance. E.g. In Setup 2, a history/persistent-store check on the time stamps of all the events for the same order in question could help put things back in order.

### More details
For more details of this API, see test code but here's a gist
```
    @Test
    public void defaultConseqRunsWithUnboundMaxConcurrencyButBoundByTotalTaskCount() throws InterruptedException {
        ConcurrentSequencer defaultConseq = ConcurrentSequentialExecutors.newBuilder().build();
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

    @Test
    public void conseqShouldBeBoundByMaxMaxConcurrency() throws InterruptedException, ExecutionException {
        final int maxConcurrency = TASK_COUNT / 2;
        ConcurrentSequencer maxConcurrencyBoundConseq = ConcurrentSequentialExecutors.newBuilder().ofSize(maxConcurrency).build();
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
