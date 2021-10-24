# ConseQ

ConseQ (Concurrent Sequencer) is a Java concurrent API to sequence related tasks while concurring unrelated ones.

## User story
As a client of this Java concurrent API, I want to summon a thread/executor by a sequence key, so that all related tasks with the same sequence key are executed sequentially by the same executor while unrelated tasks with different sequence keys can be executed concurrently by different executors.

## Prerequisite
Java 8 or better

## Get it...
In Maven
```
<dependency>
    <groupId>io.github.q3769.qlib</groupId>
    <artifactId>conseq</artifactId>
    <version>20211023.0.1</version>
</dependency>
```
In Gradle
```
implementation 'io.github.q3769.qlib:conseq:20211023.0.1'
```

## Use it...
For those who are in a hurry, skip directly to Setup 3.

The typical use case is with an asynchronous message consumer. First off, you can do Setup 1. The messaging provider (an EMS queue, a Kafka topic/partition, etc.) will usually make sure that messages are delivered to the `onMessage` method in the same order as they are received, and won't deliver the next message until the previous call to `onMessage` returns. Thus logically, all messages are consumed in a single-threaded fashion in the same/correct order as they are delivered by the messaging provider. 

### Setup 1
```
public class MessageConsumer {
    public void onMessage(Message shoppingEvent) {
        process(shoppingEvent);
    }

    private void process(Message shoppingEvent) {
        ...
    }
    ...
```

That is all well and good, but processing all messages in sequential order globally is a bit slow, isn't it?

To speed up the process, you really want to do Setup 2 if you can - just "shot-gun" a bunch of concurrent threads - except sometimes you can't, not when the order of message consumption matters:

Imagine while shopping for a T-Shirt, the shopper changed the size of the shirt between Medium and Large, back and forth for like 10 times, and eventually settled on... Ok, Medium! The 10 size changing events got delivered to the messaging provider in the same order as the shopper placed them. At the time of delivery, though, your consumer application was brought down for maintenance, so the 10 events were held and piled up in the messaging provider. Now your consumer application came back online, and all the 10 events were delivered to you in the correct order albeit within a very short period of time. 

### Setup 2
```
public class MessageConsumer {
    private ExecutorService concurrencer = Executors.newFixedThreadPool(10);
    
    public void onMessage(Message shoppingEvent) {
        concurrencer.execute(() -> process(shoppingEvent)); // Look ma, I got 10 concurrent threads working on this. That's gotta be faster, right?
    }    
    ...
```
As it turned out, with Setup 2, the shopper actually received a T-Shirt of size Large, instead of the Medium that s/he so painstakingly settled on (got real mad; called you a bunch of names and knocked over your beer). And you wonder why that happened... Oh, got it: The shot-gun threads processed the events out of order!

Ok then what, going back to Setup 1? Well sure, you can do that, at the expense of limitting performance. Or, you could save your beer by using this ConseQ (Concurrent Sequencer) API as in Setup 3:

### Setup 3
```
public class MessageConsumer {
    private ConcurrentSequencer conseq = ConcurrentSequentialExecutors.newBuilder().ofSize(10).build();
    
    public void onMessage(Message shoppingEvent) {
        conseq.getSequentialExecutor(shoppingEvent.getShoppingCartId()).execute(() -> process(shoppingEvent)); // You still got up to 10 threads working for you, but all shopping events of the same shopping cart will be done by a single thread
    }
    ...
```

Consider using the ConseQ API whenever the incoming events carry some kind of correlatable information that can be used/converted as a sequence key (see the full disclosure below). On the API level, a sequence key can be any type of `Object` but good choices are identifiers that can, after hashing, group related events into the same hash code and unrelated events into different hash codes. An exemplary sequence key can be a user id, shipment id, ticket reservation id, session id, etc.... 

The default hashing algorithm of this API is from the Guava library, namely MurmurHash3-128. That should be good enough but for those who have PhDs in hashing, you can provide your own consistent hasher by using `ConcurrentSequentialExecutors.newBuilder().withBucketHasher(myConsistentHasher).build()` instead of `ConcurrentSequentialExecutors.newBuilder().ofSize(myMaxConcurrencyInt).build()`.

The default maximum count of concurrent executors is "unbounded" (`Integer.MAX_VALUE`) if you directly use `ConcurrentSequentialExecutors.newBuilder().build()`. In that case, related tasks with the same sequence key are still processed sequentially by the same executor, while unrelated tasks are processed concurrently by a potentially unbound number of executors.

### More details

This default conseq has all capacities unbounded, including max concurrency and totoal task queue size.
```
ConcurrentSequencer conseqDefault = ConcurrentSequentialExecutors.newBuilder().build(); // all default
```

This conseq has max concurrency of 10, total task queue size of 200. i.e. Each sequential executor has a task queue size of 20 (200/10).
```
ConcurrentSequencer conseq = ConcurrentSequentialExecutors.newBuilder().ofSize(10).withTotalTaskQueueSize(200).build();
```

This conseq has a max of 10 concurrent threads/executors. Each executor has an unbounded task queue size.
```
ConcurrentSequencer conseq = ConcurrentSequentialExecutors.newBuilder().ofSize(10).build();
```

This conseq falls back to all default because totol task queue size is the only set value
```
ConcurrentSequencer conseq = ConcurrentSequentialExecutors.newBuilder().withTotalTaskQueueSize(100).build();
```

## Full disclosure
For a multi-threaded/concurrent system, there are generally two approaches to ensure correct order of message consumption:
1. Proactive/Preventive: This is on the technical level. Sometimes we can make sure that related events are never processed out of order, e.g. by using a sequence/correlation key as with this API in Setup 3.
2. Reactive/Responsive: This is on the business rule level. Sometimes we have to accept the fact that preventative messures are not always possible, and assume at the time of processing, things can be out of order already. E.g. when the events are coming from different message producers and sources, there may be no garantee of correct ordering in the first place in spite of the messaging provider's ordering mechanism. Now the job is to detect and make amends when things are out of order based on business rules. This can be much more complicated both in terms of coding and runtime performance. E.g. In Setup 2, a history (persistent-store) look-up on the time stamps of all the events for the same shopping session in question could help put things back in order. Other responsive measures inlcude using State Machines.
