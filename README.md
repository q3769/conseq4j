[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

# conseq4j

A Java concurrent API to sequence the executions of related tasks while concurring unrelated ones, where "conseq" is
short for **con**current **seq**uencer.

## User stories

1. As a client of the API, I want to summon a thread/executor by a sequence key, so that I can sequentially execute all
   related tasks with the same sequence key using the same executor while unrelated tasks with different sequence keys
   can be executed concurrently by different executors.
2. As a client of the API, I want to asynchronously submit a task for execution together with a sequence key, so that,
   across multiple task submissions, all related tasks under the same/equal sequence key are executed sequentially and
   unrelated tasks of different sequence keys are executed concurrently.

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
    <version>20220715.0.1</version>
</dependency>
```

In Gradle:

```
implementation 'io.github.q3769:conseq4j:20220715.0.1'
```

## Use it...

It may seem counter-intuitive for a concurrent API, but the implementation of conseq4j does not have to be thread-safe.
In fact, for simplicity and separation of concerns, the default implementation is not thread-safe in that it provides no
garantee of access order in case of multi-thread racing conditions in client-side task submission.

It is the API client's responsibility and concern how tasks are submitted to conseq4j. If execution order is imperative,
the client - either single or multi-threaded, has to ensure that tasks are submitted in proper sequence
to begin with. Fortunately often times, that is naturally the case, e.g., when the client is under the management of a
messaging provider running a single caller thread. Otherwise, if the caller is multi-threaded, then the client needs to
ensure the concurrent caller threads have proper access order to conseq4j. This can be as trivial as setting up
a [fair lock](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantLock.html#ReentrantLock-boolean-)
to safeguard the conseq4j API invocation; it is a client-side activity nonetheless.

Once the proper task submission sequence is ensured by the API client, it is then conseq4j's concern and responsibility
that further processing of the tasks is executed in the meaningful order and concurrency as promised.

### Style 1: Summon a sequential executor by its sequence key, and use the executor as with a JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html).

This API style provides the client with a sequential executor of type `ExecutorService`. Consider using this style when
you need the syntax and semantic richness of
an [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html).

#### API:

```
public interface ConcurrentSequencer {

    /**
     * @param sequenceKey an {@link java.lang.Object} whose hash code is used to locate and summon the corresponding
     *                    sequential executor.
     * @return the executor of type {@link java.util.concurrent.ExecutorService} that executes all tasks of this
     *         sequence key in the same order as they are submitted
     */
    ExecutorService getSequentialExecutor(Object sequenceKey);
}
```

#### Sample usage:

```
public class MessageConsumer {

    private ConcurrentSequencer conseq = Conseq.newBuilder().globalConcurrency(10).build();
    
    @Autowired
    private ShoppingEventProcessor shoppingEventProcessor;
    
    
    /**
     * Suppose run-time invocation of this method is managed by the messaging provider.
     * This is usually via a single caller thread.
     * 
     * Concurrency is achieved when shopping events of different shopping cart IDs are 
     * processed in parallel, by different executors. Sequence is maintained on all 
     * shopping events of the same shopping cart ID, by the same executor.
     */
    public void onMessage(Message shoppingEvent) {       
        conseq.getSequentialExecutor(shoppingEvent.getShoppingCartId())
                .execute(() -> shoppingEventProcessor.process(shoppingEvent)); 
    }
    ...
```

Notes:

- The implementation of this style relies on hashing of the sequence keys into a fixed number of "buckets". These
  buckets are each associated with a sequential executor. The same/equal sequence key is always hashed to and summons
  back the same executor. Single-threaded, each executor ensures the execution order of all its tasks is the same as
  they are submitted; excessive tasks pending execution are buffered by the executor in a FIFO task queue. Thus, the
  total number of buckets (a.k.a. the max number of executors and the global concurrency) is the maximum number of tasks
  that can be executed in parallel at any given time.
- As with hashing, collision may occur among different sequence keys. When hash collision happens, tasks of different
  sequence keys are assigned to the same executor. Due to the single-thread setup, the executor still ensures the local
  execution order for each individual sequence key's tasks. Nevertheless, unrelated tasks of different sequence keys
  may delay each other's execution inadvertently while waiting in the executor's task queue. To account for hash
  collision, conseq4j does not support any shutdown action on the
  executor ([ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html))
  instance created by the API; that is to prevent unintended task cancellation across different sequence keys.
  The [Future](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html) instance(s) subsequently
  returned by the executor, however, is still cancellable. In general, hash collision may not be an issue for those
  workloads that are asynchronous and focused on overall through-put, but is something to be aware of.

### Style 2: Submit a task together with its sequence key, and directly use the conseq4j API as a service.

This API style bypasses
the [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html)
executor interface, and directly services the submitted task. Prefer using this style when you do not require the
full-blown syntax and semantic support of a JDK `ExecutorService`.

#### API:

```
public interface ConcurrentSequencerService {

    /**
     * @param command     the Runnable task to run sequentially with others under the same sequence key
     * @param sequenceKey the key under which all tasks are executed sequentially
     */
    void execute(Runnable command, Object sequenceKey);

    /**
     * @param task        the Callable task to run sequentially with others under the same sequence key
     * @param sequenceKey the key under which all tasks are executed sequentially
     * @param <T>         the type of the task's result
     * @return a Future representing pending completion of the task
     */
    <T> Future<T> submit(Callable<T> task, Object sequenceKey);
}
```

#### Sample usage:

```
public class MessageConsumer {

    private ConcurrentSequencerService conseqService = new ConseqService();
        
    /* 
     * Or, to use a custom thread pool of size 10, for example, you could do: 
     *
     * private ConcurrentSequencerService conseqService = new ConseqService(Executors.newFixedThreadPool(10));
     *
     */
    
    @Autowired
    private ShoppingEventProcessor shoppingEventProcessor;
    
    
    /**
     * Suppose run-time invocation of this method is managed by the messaging provider.
     * This is usually via a single caller thread.
     * 
     * Concurrency is achieved when shopping events of different shopping cart IDs are 
     * processed in parallel by different backing threads. Sequence is maintained on all 
     * shopping events of the same shopping cart ID, via linear progressing of the
     * {@link CompletableFuture}'s completion stages.
     */
    public void onMessage(Message shoppingEvent) {       
        conseqService.execute(
                () -> shoppingEventProcessor.process(shoppingEvent), 
                shoppingEvent.getShoppingCartId());
    }
    ...
```

Notes:

- The implementation of this style replies on the
  JDK [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) to
  achieve sequential execution of related tasks. Unrelated tasks are executed at a concurrency upper-bounded by the
  execution thread pool size. Compared to the other conseq4j API style, this has the advantage of avoiding hash
  collision related issues, and may be preferable for simple cases that do not require the syntax/semantic richness that
  an [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) executor has
  to offer.
- Since there is no bucket hashing, this API style decouples the submitted tasks from their execution threads. I.e. even
  related tasks of the same sequence key could be executed by different threads from the thread pool, albeit in
  sequential order. This may bring extra performance gain compared to the other API style. For simplicity, the default
  thread pool that facilitates this style's asynchronous execution is the
  JDK [ForkJoinPool#commonPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html#commonPool--)
  ; this is by using the default constructor:
  ```
  ConcurrentSequencerService conseqService = new ConseqService();
  ```

  Alternatively, the thread pool can be customized through a constructor argument. E.g. this is to use a thread pool
  with a fixed size of 10 threads:

  ```
  ConcurrentSequencerService conseqService = new ConseqService(Executors.newFixedThreadPool(10));
  ```

## Full disclosure - Asynchronous Conundrum

The Asynchronous Conundrum refers to the fact that asynchronous concurrent processing and deterministic order of
execution do not come together naturally; in asynchronous systems, certain limits and impedance mismatch exist between
maintaining meaningful local execution order and maximizing global concurrency.

In asynchronous messaging, there are generally two approaches to achieve ordering with concurrency:

### 1. Preventive

This is more on the technical level. Sometimes it is possible to prevent related messages from ever being executed out
of order in a globally concurrent process. This implies:

(1) The message producer ensures that messages are posted to the messaging provider in correct order.

(2) The messaging provider ensures that messages are delivered to the message consumer in the same order they are
received.

(3) The message consumer ensures that related messages are processed in the same order, e.g., by using a
sequence/correlation key as with this API.

### 2. Curative

This is more on the business rule level. Sometimes preventative measures of messaging order preservation, through the
likes of this API, are either not possible or not worthwhile to pursue. By the time the consumer receives the messages,
things can be out of order already. E.g., when the messages are coming in from independent producers and sources, there
may be no guarantee of correct ordering in the first place. Now the message consumer's job is to detect and make amends
when things do go out of order, by using business rules.

Compared to preventative measures, corrective ones can be much more complicated in terms of design, implementation
and runtime performance. E.g. it may help to do a stateful/historical look-up of all the data and other events received
so far that are related to the incoming event; this forms a correlated and collective session of information for
the incoming event. A comprehensive review of such session can detect and determine if the incoming event is out of
order per business rules; corrective (among other reactive) actions can then be taken as needed. This may fall into the
scope of [Complex Event Processing (CEP)](https://en.wikipedia.org/wiki/Complex_event_processing). State Machines can
also be a useful design in such scenario.
