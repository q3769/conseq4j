[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

# conseq4j

Conseq4J is a Java concurrent API to sequence related tasks while concurring unrelated ones, where "conseq" is short
for **con**current **seq**uencer.

## User stories

1. As a client of this Java concurrent API, I want to summon a thread/executor by a sequence key, so that all related
   tasks with the same sequence key are executed sequentially by the same executor while unrelated tasks with different
   sequence keys can be executed concurrently by different executors.

2. As a client of this Java concurrent API, I want to submit my runnable/callable tasks together with a sequence key,
   so that all related tasks with the same sequence key are executed sequentially while unrelated tasks with different
   sequence keys are executed concurrently.

Consider using conseq4j when you want to achieve concurrent processing globally while preserving meaningful local
execution order at the same time.

## Prerequisite

Java 8 or better

## Get it...

In Maven:

```
<dependency>
    <groupId>io.github.q3769</groupId>
    <artifactId>conseq4j</artifactId>
    <version>20220306.1.4</version>
</dependency>
```

In Gradle:

```
implementation 'io.github.q3769:conseq4j:20220306.1.4'
```

## Use it...

**STYLE 1:** Summon a sequential executor by a sequence key, and use the executor as a service as with a
JDK/Guava [`ExecutorService`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html)
/[`ListeningExecutorService`](https://guava.dev/releases/snapshot/api/docs/com/google/common/util/concurrent/ListeningExecutorService.html)

The API:

```
public interface ConcurrentSequencer {
    ListeningExecutorService getSequentialExecutor(CharSequence sequenceKey);
    ListeningExecutorService getSequentialExecutor(Integer sequenceKey);
    ListeningExecutorService getSequentialExecutor(Long sequenceKey);
    ListeningExecutorService getSequentialExecutor(UUID sequenceKey);
    ListeningExecutorService getSequentialExecutor(byte[] sequenceKey);
    ListeningExecutorService getSequentialExecutor(ByteBuffer sequenceKey);
}
```

The usage example:

```
public class MessageConsumer {

    private ConcurrentSequencer conseq = Conseq.newBuilder().maxConcurrentExecutors(10).build();
    
    /**
     * Suppose run-time invocation of this method is managed by the messaging provider
     */
    public void onMessage(Message shoppingEvent) {
    
        // conseq4j API to summon sequential executor: Events with the same shopping cart Id are processed sequentially by the same executor. Events with different sequence keys will be attempted to process concurrently by different executors.
        conseq.getSequentialExecutor(shoppingEvent.getShoppingCartId()).execute(() -> process(shoppingEvent)); 
    }
    
    /**
     * Business method, most likely translating message to domain objects for further processing by using other collaborators 
     */
    private void process(Message shoppingEvent) {
        ...
    }
    ...
```

**STYLE 2:** Submit `Runnable`/`Callable` task(s) together with a sequence key, directly using the conseq4j API as a
service similar to
JDK [`ExecutorService`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html)

The API:

```
public interface ConcurrentSequencerService {

    void execute(Object sequenceKey, Runnable runnable);

    <T> Future<T> submit(Object sequenceKey, Callable<T> task);

    <T> Future<T> submit(Object sequenceKey, Runnable task, T result);

    Future<?> submit(Object sequenceKey, Runnable task);

    <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks) throws InterruptedException;

    <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException;

    <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException;

    <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;

}
```

The usage example:

```
public class MessageConsumer {

    private ConcurrentSequencerService conseqService = ConseqService.newBuilder().globalConcurrency(10).build();
    
    /**
     * Suppose run-time invocation of this method is managed by the messaging provider
     */
    public void onMessage(Message shoppingEvent) {
        try {
        
            // conseq4j API as a service: Concurrent process, preserving the order/sequence of the tasks
            List<Future<MySelectionResult>> sequencedResults = conseqService.invokeAll(shoppingEvent.getShoppingCartId(), toSequencedSelectionCallables(shoppingEvent));
             
            // Single-threaded send, same order/sequence as processed
            publishAll(sequencedResults);
        } catch(InterruptedException e) {
            ...
        }
    }
    
    /**
     * Convert to Callable tasks, in the proper order/sequence, which are submitted to the conseq4j API.
     */
    private List<Callable<MySelectionResult>> toSequencedSelectionCallables(Message shoppingEvent) {
        ...
    }
    
    /**
     * Use a message sender to publish future results. Yes, we are single-threading here to preserve the processing order.
     */
    private void publishAll(List<Future<MySelectionResult>> sequencedResults) {
        sequencedResults.forEach(futureResult -> messageSender.send(futureResult.get());         
    }
    ...
```

## Full disclosure - Asynchronous Conundrum

The Asynchronous Conundrum refers to the fact that asynchronous concurrent processing and deterministic order of
execution do not come together naturally: In asynchronous systems, certain limits and impedance mismatch exist between
maintaining meaningful local order and maximizing global concurrency.

In asynchronous messaging, there are generally two approaches to achieve ordering with concurrency:

### 1. Proactive/Preventive

This is more on the technical level. Sometimes it is possible to ensure related messages are never processed out of
order in globally concurrent execution. This implies:

(1) The message producer ensures that messages are posted to the messaging provider in correct order.

(2) The messaging provider ensures that messages are delivered to the message consumer in the same order they are
received.

(3) The message consumer ensures that related messages are processed in the same order, e.g., by using a
sequence/correlation key as with this API.

### 2. Reactive/Curative

This is more on the business rule level. Sometimes preventative measures of message order preservation are either not
possible or not worthwhile to pursue. By the time of processing on the message consumer side, things can be out of order
already. E.g., when the messages are coming in from different message producers and sources, there may be no guarantee
of correct ordering in the first place, despite the messaging provider's ordering mechanism. Now the message consumer's
job is to detect and make amends when things do go out of order, by using business rules. This corrective measure can be
much more complicated both in terms of coding and runtime performance. E.g., in Setup 2, a business rule to conduct a
history (persistent store) look-up on the user activity time stamps of all the events for the same shopping session
could help put things back in order. Another example of the responsive measures is using State Machines.
