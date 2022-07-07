[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

# conseq4j

A Java concurrent API to sequence related tasks while concurring unrelated ones, where "conseq" is short for
**con**current **seq**uencer.

## User story

As a client of this Java concurrent API, I want to summon a thread/executor by a sequence key, so that all related
tasks with the same sequence key are executed sequentially by the same executor while unrelated tasks with different
sequence keys can be executed concurrently by different executors.

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
    <version>20220707.0.0</version>
</dependency>
```

In Gradle:

```
implementation 'io.github.q3769:conseq4j:20220707.0.0'
```

## Use it...

Summon a sequential executor by its sequence key, and use the executor as with a
JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html).

Notes:

- The current implementation relies on further hashing of the sequence keys' hash codes into a fixed number of "buckets"
  . These buckets are each associated with a sequential executor. The same/equal sequence key summons and always gets
  back the same executor. Single-threaded, the executor ensures the same execution order of all its tasks as they are
  submitted.

- As with hashing, collision may occur among different sequence keys. When hash collision happens, different sequence
  keys' tasks are assigned to the same executor. In that case, due to the single-thread setup, the local execution
  order for each individual sequence key is still preserved; however, unrelated tasks may unfairly block/delay each
  other from executing. For that reason, conseq4j does not support any shutdown action on an executor (of
  type [`ExecutorService`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html))
  obtained through its API, to prevent unintended task cancellation across different sequence keys. This may not be an
  issue for an asynchronous, overall-throughput driven, workload but something to be aware of.

### The API:

```
public interface ConcurrentSequencer {
    ExecutorService getSequentialExecutor(Object sequenceKey);
}
```

### Usage example:

```
public class MessageConsumer {

    private ConcurrentSequencer conseq = Conseq.newBuilder().globalConcurrency(10).build();
    
    @Autowired
    private ShoppingEventProcessor shoppingEventProcessor;
    
    
    /**
     * Suppose run-time invocation of this method is managed by the messaging provider.
     * This is usually via a single calling thread.
     */
    public void onMessage(Message shoppingEvent) {
    
        // conseq4j API to summon sequential executor: Events with the same shopping cart Id are processed sequentially 
        // by the same executor. Events with different sequence keys will be attempted to process concurrently by 
        // different executors.
        
        conseq.getSequentialExecutor(shoppingEvent.getShoppingCartId())
                .execute(() -> shoppingEventProcessor.process(shoppingEvent)); 
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
