[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

# conseq4j

A Java concurrent API to sequence related tasks while concurring unrelated ones, where "conseq" is short for
**con**current **seq**uencer.

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
    <version>20220607.0.4</version>
</dependency>
```

In Gradle:

```
implementation 'io.github.q3769:conseq4j:20220607.0.4'
```

## Use it...

### Style 1 - Summon a sequential executor by its sequence key, and use the executor as with a JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html)

The implementation of this style relies on further hashing of the sequence key's hash code into a fixed number of
"buckets". These buckets are each associated with a sequential/single-thread executor. The same/equal sequence key
summons and always gets back the same sequential executor which ensures execution order of its tasks.

As with hashing, collision may occur among different sequence keys. When hash collision heppens, different sequence
keys' tasks are assigned to the same executor. In that case, while the local execution order for each indivdiual
sequence key is still preserved (due to the single-thread setup), unrelated tasks may unfairly block/delay each other
from executing. With the benefit of fewer synchronization checks, though, this style may better suit workloads that
are more sensitive on overall system throughput.

#### The API:

```
public interface ConcurrentSequencer {
    ExecutorService getSequentialExecutor(Object sequenceKey);
}
```

#### The usage example:

```
public class MessageConsumer {

    private ConcurrentSequencer conseq = Conseq.newBuilder().globalConcurrency(10).build();
    
    /**
     * Suppose run-time invocation of this method is managed by the messaging provider, via a single calling thread
     */
    public void onMessage(Message shoppingEvent) {
    
        // conseq4j API to summon sequential executor: Events with the same shopping cart Id are processed sequentially 
        // by the same executor. Events with different sequence keys will be attempted to process concurrently by 
        // different executors.
        
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

### Style 2 - Submit [Runnable](https://docs.oracle.com/javase/8/docs/api/java/lang/Runnable.html)/[Callable](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Callable.html) task(s) together with a sequence key, directly using the conseq4j API as a service similar to the JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html)

This style further decouples the runnable tasks from their executors, by avoiding the secondary bucket hashing. The
sequence key's hash code is directly used to locate the corresponding (pooled) sequential executor.

This prevents unrelated tasks from unfairly blocking each other from executing due to hash collisions. The only
factor that may still limit/delay task execution would be the maximum global concurrency i.e. the maximum number
of concurrent executors configured via the API. The trade-off of this setup, though, is that more synchronization
checks exist. This style may better suit workloads that are more sensitive on individual task's immediate
execution when submitted.

#### The API:

```
public interface ConcurrentSequencerService {

    void execute(Object sequenceKey, Runnable runnable);

    <T> Future<T> submit(Object sequenceKey, Callable<T> task);

    <T> Future<T> submit(Object sequenceKey, Runnable task, T result);

    Future<?> submit(Object sequenceKey, Runnable task);

    <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks) throws InterruptedException;

    <T> List<Future<T>> invokeAll(Object sequenceKey, Collection<? extends Callable<T>> tasks, Duration timeout) throws InterruptedException;

    <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException;

    <T> T invokeAny(Object sequenceKey, Collection<? extends Callable<T>> tasks, Duration timeout) throws InterruptedException, ExecutionException, TimeoutException;

}
```

#### The usage example:

```
public class MessageConsumer {

    private ConcurrentSequencerService conseqService = ConseqService.newBuilder().globalConcurrency(10).build();
    
    /**
     * Suppose run-time invocation of this method is managed by the messaging provider, via a single calling thread
     */
    public void onMessage(Message shoppingEvent) {
        try {
                
            // conseq4j API as a service - concurrently process inventory and payment tasks, preserving local 
            // order/sequence per each sequence key
            
            List<Future<InventoryResult>> sequencedInventoryResults = 
                    conseqService.invokeAll(shoppingEvent.getInventoryId(), toSequencedInventoryCallables(shoppingEvent));
            List<Future<PaymentResult>> sequencedPaymentResults = 
                    conseqService.invokeAll(shoppingEvent.getPaymentId(), toSequencedPaymentCallables(shoppingEvent));

            ...          
        } catch(InterruptedException e) {
            ...
        }
    }
    ...
```

## Full disclosure - Asynchronous Conundrum

The Asynchronous Conundrum refers to the fact that asynchronous concurrent processing and deterministic order of
execution do not come together naturally; in asynchronous systems, certain limits and impedance mismatch exist between
maintaining meaningful local order and maximizing global concurrency.

In asynchronous messaging, there are generally two approaches to achieve ordering with concurrency:

### 1. Preventive

This is more on the technical level. Sometimes it is possible to prevent related messages from ever being processed out
of order in globally concurrent executions. This implies:

(1) The message producer ensures that messages are posted to the messaging provider in correct order.

(2) The messaging provider ensures that messages are delivered to the message consumer in the same order they are
received.

(3) The message consumer ensures that related messages are processed in the same order, e.g., by using a
sequence/correlation key as with this API.

### 2. Curative

This is more on the business rule level. Sometimes preventative measures of message order preservation, through the
likes of this API, are either not possible or not worthwhile to pursue. By the time the consumer receives the messages,
things can be out of order already. E.g., when the messages are coming in from independent message producers and
sources, there may be no guarantee of correct ordering in the first place. Now the message consumer's job is to detect
and make amends when things do go out of order, by using business rules.

Compared to preventative ones, corrective measures can be much more complicated in terms of design, implementation
and runtime performance. E.g. it may help to do a stateful/historical look-up of all data and other events received
so far that are related to the incoming event; this forms a correlated and collective session of information for
the incoming event. A comprehensive review of such session can detect and determine if the incoming event is out of
order per business rules; corrective actions (among others) can then be taken as needed. This may fall into the scope
of [Complex Event Processing (CEP)](https://en.wikipedia.org/wiki/Complex_event_processing). State Machines can also
be a useful design in such scenario.
