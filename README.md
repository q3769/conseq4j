[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769.qlib/conseq.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769.qlib%22%20AND%20a:%22conseq%22)
# Conseq

Conseq (**con**current **seq**uencer) is a Java concurrent API to sequence related tasks while concurring unrelated ones.

## User story
As a client of this Java concurrent API, I want to summon a thread/executor by a sequence key, so that all related tasks with the same sequence key are executed sequentially by the same executor while unrelated tasks with different sequence keys can be executed concurrently by different executors.

Consider using the Conseq API when, from inside a globally sequenced processor, you want to achieve execution concurrency while preserving meaningful local order (see the full disclosure at the end).

## Prerequisite
Java 8 or better

## Get it...

In Maven

```
<dependency>
    <groupId>io.github.q3769.qlib</groupId>
    <artifactId>conseq</artifactId>
    <version>20211104.0.3</version>
</dependency>
```

In Gradle

```
implementation 'io.github.q3769.qlib:conseq:20211104.0.3'
```

## Use it...

While being a generic Java concurrent API, Conseq has a typical use case with an asynchronous message consumer (running on a multi-core node). For those who are in a hurry, skip directly to [Setup 3](https://github.com/q3769/qlib-conseq/blob/main/README.md#setup-3-globally-concurrently-locally-sequential).

First off, you can do Setup 1 in a message consumer. The messaging provider (an EMS queue, a Kafka topic partition, etc.) will usually make sure that messages are delivered to the provider-managed `onMessage` method in the same order as they are received and won't deliver the next message until the previous call to the method has returned. Thus logically, all messages are consumed in a single-threaded fashion in the same order as they are delivered by the messaging provider. 

### Setup 1: globally sequential

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

- That is all well and good, but processing all messages in sequential order globally is a bit slow, isn't it? It's overly conservative, to say the least, especially for multiprocessing systems.

To speed up the process, you really want to do Setup 2 if you can - just "shot-gun" a bunch of concurrent threads - except sometimes you can't, not when the order of message consumption matters:

Imagine while online shopping for a T-Shirt, the shopper changed the size of the shirt between Medium and Large, back and forth for like 10 times, and eventually settled on... Ok, Medium! The 10 size changing events got delivered to the messaging provider in the same order as the shopper placed them. At the time of delivery, though, your consumer application had been brought down for maintenance, so the 10 events were held and piled up inside see the messaging provider. Now your consumer application came back online, and all the 10 events were delivered to you in the correct order, albeit within a very short period of time. 

### Setup 2: globally concurrent

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

As it turned out, with Setup 2, the shopper actually received a T-Shirt of size Large, instead of the Medium that s/he so painstakingly settled upon (got real mad, called you a bunch of names and knocked over your beer). And you wonder how such thing could have ever happened... Oh, got it: 

- The shot-gun threads processed the events out of order!

Ok then what, go back to Setup 1? Well sure, you can do that, at the expense of limiting performance. Or you may be able to achieve decent concurrency (and save your beer) by using a "conseq" as in Setup 3:

### Setup 3: globally concurrent, locally sequential (a.k.a. conseq)

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

- That is, when it comes to processing: related events - sequential; unrelated events - concurrent (potentially).

#### More details

On the API level, you get a JDK [`ExecutorService`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) instance from one of the Conseq's `getSequentialExecutor` methods:

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

The returned `ExecutorService` instance is a logically single-threaded executor; it bears all the same syntactic richness and semantic robustness that [the JDK implementation of `ExecutorService`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html) has to offer, in terms of sequentially running your tasks. Repeated calls on the same (equal) sequence key get back the same (created/[cached](https://github.com/ben-manes/caffeine)) executor instance. Thus, starting from the single-thread consumer, as long as you summon the conseq's executors by the right sequence keys, you can rest assured that related events with the same sequence key are never executed out of order, while unrelated events enjoy concurrent executions of up to the maximum number of executors.

For simplicity, the Conseq API only supports limited JDK types of sequence keys. Internally, [consistent hashing](https://en.wikipedia.org/wiki/Consistent_hashing) is used to determine the target executor for a sequence key.  **Good sequence key choices are the likes of consistent business domain identifiers** that, after hashing, can group related events into the same hash code and unrelated events into different hash codes. An exemplary sequence key can be a user id, shipment id, travel reservation id, session id, etc...., or a combination of such. Most often, such sequence keys tend to be of the supported JDK types organically; otherwise, you may have to convert your desired sequence key into one of the supported types, e.g., a `CharSequence`/`String` or a `long`.

At run-time, the concurrency of a conseq is decided not only by the preset maximum number of concurrent executors, but also by how evenly the tasks are distributed to run among those executors - the more evenly, the better. The task distribution is mainly driven by:

1. How evenly spread-out the sequence keys' values are (e.g., if all tasks carry the same sequence key, then only one/same executor will be running the tasks no matter how many executors are potentially available.)
2. How evenly the consistent hashing algorithm can spread different sequence keys into different hash buckets

The default hash algorithm of this API is from the [Guava](https://github.com/google/guava) library, namely [MurmurHash3](https://en.wikipedia.org/wiki/MurmurHash#MurmurHash3)-128. That should be good enough in most cases. But for those who have PhDs in hashing, you can provide your own [`ConsistentHasher`](https://github.com/q3769/qlib-conseq/blob/5c3213c7b8c38d4a4c1d1a79f767fbfbc8e7bb18/src/main/java/qlib/conseq/ConsistentHasher.java), as in Build -1, when building a Conseq instance:

##### Build -1: custom hasher

```
ConcurrentSequencer conseq = Conseq.newBuilder().consistentHasher(myConsistentHasher).build();
```

A default conseq has unbounded (`Integer.MAX_VALUE`) capacities. The capacities refer to

1. the conseq's maximum count of concurrent executors
2. each executor's task queue size (See [Javadoc on capacity](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/LinkedBlockingQueue.html#LinkedBlockingQueue-int-) of a bounded `BlockingQueue`) 

As always, even with unbounded capacities as in Build 0, related tasks with the same sequence key are still processed sequentially by the same executor, while unrelated tasks can be processed concurrently by a potentially unbounded number of executors:

##### Build 0: all default, unbounded capacities

```
ConcurrentSequencer conseq = Conseq.newBuilder().build(); // all default, unbounded capacities
```

The conseq in Build 1 has a max of 10 concurrent executors, each executor has an unbounded task queue size:

##### Build 1: partially bounded on max concurrent executors

```
ConcurrentSequencer conseq = Conseq.newBuilder().maxConcurrentExecutors(10).build();
```

The conseq in Build 2 has an unbounded max number of concurrent executors, each executor has a task queue size of 20:

##### Build 2: partially bounded on task queue size

```
ConcurrentSequencer conseq = Conseq.newBuilder().singleExecutorTaskQueueSize(20).build();
```

The conseq in Build 3 has a max of 10 concurrent executors, each executor has a task queue size of 20. Note that, in this case, the total task queue size of the entire conseq is 200 (i.e., 20 x 10):

##### Build 3: fully bounded on both max concurrent executors and task queue size

```
ConcurrentSequencer conseq = Conseq.newBuilder().maxConcurrentExecutors(10).singleExecutorTaskQueueSize(20).build();
```

##### Considerations on capacities

When running in a Cloud environment, you might want to consider leaving at least one of the conseq's capacities as default/unbounded, especially the task queue size of the individual executor. When an executor's capacity is exceeded, the [default/JDK policy](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.AbortPolicy.html) is to reject further tasks by throwing exceptions. If you fully bound a conseq's capacities as in Build 3, you may be able to prevent the running node from crashing but tasks beyond the preset capacities will be rejected and error out, which is undesirable. By having some unbounded capacity as in Build 0 through Build 2, the idea is to leverage the Cloud's autoscaling mechanism to properly scale out the system before either undesired effects happens: node crash or task rejection. 

## Full disclosure - Asynchronous Conundrum

Asynchronous Conundrum refers to the fact that asynchronous concurrent processing and deterministic order of execution do not come together naturally, often times it is not trivial to maintain meaningful order while processing asynchoronously in a concurrent system. 

In asynchronous messaging, there are generally two approaches to achieve ordering:

### 1. Proactive/Preventive

This is on the technical level. Sometimes it is possible to ensure related messages are never processed out of order. This implies:

(1) The message producer ensures that messages are posted to the messaging provider in correct order.

(2) The messaging provider ensures that messages are delivered to the message consumer in the same order they are received.

(3) The message consumer ensures that related messages are processed in the same order, e.g., by using a sequence/correlation key as with this API in Setup 3. 

### 2. Reactive/Responsive
    
This is on the business rule level. Sometimes preventative measures of message order preservation are either not possible or not worthwhile to pursue. By the time of processing on the message consumer side, things can be out of order already. E.g., when the messages are coming in from different message producers and sources, there may be no guarantee of correct ordering in the first place, despite the messaging provider's ordering mechanism. Now the message consumer's job is to detect and make amends when things do go out of order, by using business rules. This can be much more complicated both in terms of coding and runtime performance. E.g., in Setup 2, a business rule to conduct a history (persistent store) look-up on the user activity time stamps of all the events for the same shopping session could help put things back in order. Other responsive measures include using State Machines.
