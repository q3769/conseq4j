[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769.qlib/conseq.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769.qlib%22%20AND%20a:%22conseq%22)
# Conseq

Conseq (**con**current **seq**uencer) is a Java concurrent API to sequence related tasks while concurring unrelated ones.

## User story
As a client of this Java concurrent API, I want to summon a thread/executor by a sequence key, so that all related tasks with the same sequence key are executed sequentially by the same executor while unrelated tasks with different sequence keys can be executed concurrently by different executors.

Consider using the Conseq API when, inside a globally sequenced processor, you want to achieve execution concurrency while preserving meaningful local order (see the full disclosure at the end).

## Prerequisite
Java 8 or better

## Get it...

In Maven

```
<dependency>
    <groupId>io.github.q3769.qlib</groupId>
    <artifactId>conseq</artifactId>
    <version>20211104.0.1</version>
</dependency>
```

In Gradle

```
implementation 'io.github.q3769.qlib:conseq:20211104.0.1'
```

## Use it...

For those who are in a hurry, skip directly to Setup 3.

The typical use case is with an asynchronous message consumer. First off, you can do Setup 1 in the consumer. The messaging provider (an EMS queue, a Kafka topic partition, etc.) will usually make sure that messages are delivered to the provider-managed `onMessage` method in the same order as they are received and won't deliver the next message until the previous call to the method returns. Thus logically, all messages are consumed in a single-threaded fashion in the same/correct order as they are delivered by the messaging provider. 

### Setup 1

```
public class MessageConsumer {

    /**
     * Suppose run-time invocation of this method is managed by the messaging provider
     */
    public void onMessage(Message shoppingEvent) {
        process(shoppingEvent); // delegate to business method
    }

    /**
     * Business processing method
     */
    private void process(Message shoppingEvent) {
        ...
    }
    ...
```

That is all well and good, but processing all messages in sequential order globally is a bit slow, isn't it?

To speed up the process, you really want to do Setup 2 if you can, just "shot-gun" a bunch of concurrent threads, except sometimes you can't - not when the order of message consumption matters:

Imagine while online shopping for a T-Shirt, the shopper changed the size of the shirt between Medium and Large, back and forth for like 10 times, and eventually settled on... Ok, Medium! The 10 size changing events got delivered to the messaging provider in the same order as the shopper placed them. At the time of delivery, though, your consumer application had been brought down for maintenance, so the 10 events were held and piled up in the messaging provider. Now your consumer application came back online, and all the 10 events were delivered to you in the correct order, albeit within a very short period of time. 

### Setup 2

```
public class MessageConsumer {
    private ExecutorService shotgunConcurrencer = Executors.newFixedThreadPool(10);
    
    /**
     * Suppose run-time invocation of this method is managed by the messaging provider
     */
    public void onMessage(Message shoppingEvent) {
        shotgunConcurrencer.execute(() -> process(shoppingEvent)); // Look ma, I got 10 concurrent threads working on this. That's gotta be faster, right?
    }    
    ...
```

As it turned out, with Setup 2, the shopper actually received a T-Shirt of size Large, instead of the Medium that s/he so painstakingly settled on (got real mad, called you a bunch of names and knocked over your beer). And you wonder why that happened... Oh, got it: 

*The shot-gun threads processed the events out of order!*

Ok then what, go back to Setup 1? Well sure, you can do that, at the expense of limiting performance. Or you may be able to achieve decent concurrency (and save your beer) by using a "conseq" as in Setup 3:

### Setup 3

```
public class MessageConsumer {
    private ConcurrentSequencer conseq = Conseq.newBuilder().maxConcurrentExecutors(10).build();
    
    /**
     * Suppose run-time invocation of this method is managed by the messaging provider
     */
    public void onMessage(Message shoppingEvent) {
        conseq.getSequentialExecutor(shoppingEvent.getShoppingCartId()).execute(() -> process(shoppingEvent)); // You still got up to 10 threads working for you, but all shopping events of the same shopping cart will be done by a single thread
    }
    ...
```

#### More details

On the API level, you get a good old JDK `ExecutorService` instance from one of the overloaded `getSequentialExecutor` methods:

```
public interface ConcurrentSequencer {
    ExecutorService getSequentialExecutor(CharSequence sequenceKey);
    ExecutorService getSequentialExecutor(Integer sequenceKey);
    ExecutorService getSequentialExecutor(Long sequenceKey);
    ExecutorService getSequentialExecutor(UUID sequenceKey);
    ExecutorService getSequentialExecutor(byte[] sequenceKey);
    ExecutorService getSequentialExecutor(ByteBuffer sequenceKey);
}
```

The returned instance is a single-threaded executor; it bears all the same syntactic richness and semantic robustness that an `ExecutorService` has to offer in terms of sequentially running your tasks. Repeated calls on the same (equal) sequence key get back the same (created/[cached](https://github.com/ben-manes/caffeine)) executor instance. Thus, starting from the single-thread consumer, as long as you summon the conseq's executors by the right sequence keys, you can rest assured that related events with the same sequence key are never executed out of order, while unrelated events enjoy concurrent executions of up to the maximum number of executors.

For simplicity, the Conseq API only supports a limited set of JDK types for the sequence key. Internally, [consistent hashing](https://en.wikipedia.org/wiki/Consistent_hashing) is used to determine the target executor for a sequence key.  **Good sequence key choices are the likes of consistent business domain identifiers** that can, after hashing, group related events into the same hash code and unrelated events into different hash codes. An exemplary sequence key can be a user id, shipment id, travel reservation id, session id, etc...., or a combination of such. Most often, such sequence keys tend to be of the supported JDK types organically; otherwise, you may have to convert your desired sequence key into one of the supported types.

At run-time, a conseq's concurrency is not only decided by the preset maximum number of concurrent executors, but also by how evenly the tasks are distributed to run among those executors - the more evenly, the better. The task distribution is mainly driven by:

- How evenly spread-out the sequence keys' values are (e.g., if all tasks carry the same sequence key, then only one/same executor will be running the tasks no matter how many executors are potentially available.)
- How evenly the consistent hashing algorithm can spread different sequence keys into different hash buckets

The default hash algorithm of this API is from the [Guava](https://github.com/google/guava) library, namely [MurmurHash3](https://en.wikipedia.org/wiki/MurmurHash#MurmurHash3)-128. It should be good enough in most cases. But for those who have PhDs in hashing, you can provide your own `ConsistentHasher` by using 

- `Conseq.newBuilder().consistentHasher(myConsistentHasher).build()`

instead of **the usual setup** of

- `Conseq.newBuilder().maxConcurrentExecutors(myMaxCountOfConcurrentExecutors).singleExecutorTaskQueueSize(myExecutorTaskQueueSize).build()`

to build the conseq instance. 

A default conseq has all its capacities unbounded (`Integer.MAX_VALUE`). Capacities include the conseq's maximum count of concurrent executors and each executor's task queue size. As usual, even with unbounded capacities, related tasks with the same sequence key are still processed sequentially by the same executor, while unrelated tasks can be processed concurrently by a potentially unbounded number of executors:

```
ConcurrentSequencer conseq = Conseq.newBuilder().build(); // all default, unbounded capacities
```

This conseq has a max of 10 concurrent executors, each executor has an unbounded task queue size:

```
ConcurrentSequencer conseq = Conseq.newBuilder().maxConcurrentExecutors(10).build();
```

The following is a typical way of setting up a conseq. The exact capacity numbers are up to your discretion. This particular conseq has a max of 10 concurrent executors, each executor has a task queue size of 20. Note that, in this case, the total task queue size of the entire conseq is 200 (i.e., 20 x 10):

```
ConcurrentSequencer conseq = Conseq.newBuilder().maxConcurrentExecutors(10).singleExecutorTaskQueueSize(20).build();
```

## Full disclosure - Asynchronous Conundrum

The "asynchronous conundrum" refers to fact that asynchronous concurrent processing and deterministic order of execution do not come together naturally, often times it is not trivial to maintain meaningful order while processing asynchoronously in a concurrent system. 

In asynchronous messaging, there are generally two approaches to achieve ordering:

### 1. Proactive/Preventive

This is on the technical level. Sometimes it is possible to ensure related messages are never processed out of order. This implies that

(1) The message producer ensures that messages are posted to the messaging provider in correct order.
   
(2) The messaging provider ensures that messages are delivered to the message consumer in the same order they are received.
    
(3) The message consumer ensures that related messages are processed in the same order, e.g., by using a sequence/correlation key as with this API in Setup 3. 

### 2. Reactive/Responsive
    
This is on the business rule level. Sometimes preventative measures of message order preservation are either not possible or not worthwhile to pursue. By the time of processing at the message consumer side, things can be out of order already. E.g., when the messages are coming in from different message producers and sources, there may be no guarantee of correct ordering from the get-go, despite the messaging provider's ordering mechanism. Now the message consumer's job is to detect and make amends when things do go out of order, by using business rules. This can be much more complicated both in terms of coding and runtime performance. E.g., in Setup 2, a business rule to conduct a history (persistent store) look-up on the user activity time stamps of all the events for the same shopping session could help put things back in order. Other responsive measures include using State Machines.
