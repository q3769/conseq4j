[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

# conseq4j

A Java concurrent API to sequence the executions of related tasks while concurring unrelated ones.

- **conseq** is short for **con**current **seq**uencer.

## User Stories

1. As a client of the conseq4j API, I want to summon a thread/executor by a sequence key, so that I can sequentially
   execute all related tasks sequentially submitted with the same sequence key using the same executor; unrelated
   tasks with different sequence keys can be executed concurrently by different executors even when they are submitted
   sequentially.
2. As a client of the conseq4j API, I want to asynchronously submit a task for execution together with a sequence key,
   so that, across all such submissions, related tasks submitted sequentially under the same sequence key are executed
   sequentially; unrelated tasks of different sequence keys are executed concurrently even when they are submitted
   sequentially.

Consider using conseq4j when you want to achieve concurrent processing globally while preserving meaningful local
execution order at the same time.

## Prerequisite

Java 8 or better

## Get It...

[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

## Use It...

**Sequence Keys**

A sequence key cannot be `null`. Any two keys are considered "the same sequence key" if and only
if `Objects.equals(sequenceKey1, sequenceKey2)` returns `true`.

**Thread Safety**

The conseq4j implementation is thread-safe in that its own internal state has no data corruption due to concurrent
modification. The thread-safety programming rules and concerns still apply to the executed tasks, as usual. In the
context of concurrency and sequencing, though, the thread-safety concern goes beyond concurrent modification of
individual-task data, into that of meaningful execution order among multiple related tasks.

**Concurrency And Sequencing**

By definition, there is no such thing as order or sequence among tasks submitted concurrently by different threads.
Those tasks will execute in whatever order scheduled by the JVM, regardless of sequence keys. However, tasks submitted
by a single thread, or by each single thread in case of multi-threading, will be managed by conseq4j: These
thread-sequenced tasks will be executed sequentially if they have the same sequence key, and concurrently if they have
different sequence keys. As such, client-side multi-threading is not recommended when sequencing is imperative; instead,
use conseq4j to provide both concurrency and sequencing.

- Technically, the task-submitting thread only needs to be "logically" single; it doesn't have to be the same physical
  thread, as long as at most one single thread is submitting the related tasks at any given time.

### Style 1: Summon A Sequential Executor By Its Sequence Key, Then Use The Executor As With A JDK ExecutorService

In this API style, sequence keys are used to summon executors of JDK
type [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html). The same
sequence key always gets back the same executor from the API, no matter when or how many
times the executor is summoned. All tasks submitted to that executor, no matter when or how many, are considered part of
the same sequence; therefore, executed sequentially in exactly the same order as submitted.

There is no limit on the total number of sequence keys the API client can use to summon executors. Behind the scenes,
tasks of different sequence keys will be managed to execute in parallel, with a configurable global maximum concurrency.

Consider using this style when the summoned executor needs to provide
the [syntax and semantic richness](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html#method.summary)
of the JDK `ExecutorService` API.

#### API

```java
public interface ConcurrentSequencer {

    /**
     * @param sequenceKey an {@link Object} whose hash code is used to summon the corresponding executor.
     * @return the executor of type {@link ExecutorService} that executes all tasks of this sequence key in the same
     *         order as they are submitted.
     */
    ExecutorService getSequentialExecutorService(Object sequenceKey);
}
```

#### Sample Usage

```java
public class MessageConsumer {

    /**
     * Default conseq's concurrency is either 16 or java.lang.Runtime.availableProcessors, which ever is larger.
     * <p></p>
     * Or to set the global concurrency to 10, for example:
     * <code>
     * private ConcurrentSequencer conseq = Conseq.newInstance(10);
     * </code>
     */
    private final ConcurrentSequencer conseq = Conseq.newInstance();

    @Autowired private ShoppingEventProcessor shoppingEventProcessor;

    /**
     * Suppose run-time invocation of this method is managed by the messaging provider.
     * This is usually via a single caller thread.
     * <p></p>
     * Concurrency is achieved when shopping events of different shopping cart IDs are 
     * processed in parallel, by different executors. Sequence is maintained on all 
     * shopping events of the same shopping cart ID, by the same executor.
     */
    public void onMessage(Message shoppingEvent) {
        conseq.getSequentialExecutorService(shoppingEvent.getShoppingCartId())
                .execute(() -> shoppingEventProcessor.process(shoppingEvent));
    }
}
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
  returned by the executor, though, is still cancellable. In general, the hash collision may not be an issue for those
  workloads that are asynchronous and focused on overall through-put, but is something to be aware of.
- The default concurrency is either 16 or the JVM
  run-time's [availableProcessors](https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#availableProcessors--),
  which ever is larger:
  ```jshelllanguage
  ConcurrentSequencer conseq = Conseq.newInstance();
  ```

  The concurrency can be customized:
  ```jshelllanguage
  ConcurrentSequencer conseq = Conseq.newInstance(10);
  ```

### Style2: Submit Each Task Together With A SequenceKey, Directly To Conseq4J API For Execution

This API style is more concise. It bypasses the JDK ExecutorService API and, instead, services the submitted task
directly. The same execution semantics holds: Tasks submitted with the same sequence key are executed in the same
submission order; tasks of different sequence keys are managed to execute in parallel, by a thread pool of configurable
size.

Prefer this style when the full-blown syntax and semantic support of
JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) is not
required.

#### API

```java
public interface ConcurrentSequencingExecutor {

    /**
     * @param command     the Runnable task to run sequentially with others under the same sequence key
     * @param sequenceKey the key under which all tasks are executed sequentially
     * @return future holding run status of the executing command
     */
    Future<Void> execute(Runnable command, Object sequenceKey);

    /**
     * @param task        the Callable task to run sequentially with others under the same sequence key
     * @param sequenceKey the key under which all tasks are executed sequentially
     * @param <T>         the type of the task's result
     * @return a Future representing pending completion of the submitted task
     */
    <T> Future<T> submit(Callable<T> task, Object sequenceKey);
}
```

#### Sample Usage

```java
public class MessageConsumer {

    /**
     * Default executor concurrency is either 16 or java.lang.Runtime.availableProcessors, which ever is larger.
     * <p></p>
     * Or to provide a custom concurrency of 10, for example:
     * <code>
     * private ConcurrentSequencingExecutor conseqExecutor = ConseqExecutor.newInstance(10));
     * </code>
     */
    private final ConcurrentSequencingExecutor conseqExecutor = ConseqExectuor.newInstance();

    @Autowired private ShoppingEventProcessor shoppingEventProcessor;

    /**
     * Suppose run-time invocation of this method is managed by the messaging provider.
     * This is usually via a single caller thread.
     * <p></p>
     * Concurrency is achieved when shopping events of different shopping cart IDs are 
     * processed in parallel by different backing threads. Sequence is maintained on all 
     * shopping events of the same shopping cart ID, via linear progressing of the
     * {@link CompletableFuture}'s completion stages.
     */
    public void onMessage(Message shoppingEvent) {
        conseqExecutor.submit(() -> shoppingEventProcessor.process(shoppingEvent), shoppingEvent.getShoppingCartId());
    }
}
```

Notes:

- The implementation of this style relies on
  JDK's [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)
  behind the scenes to achieve sequential execution of related tasks. A backing thread pool is used to facilitate the
  overall asynchronous execution. The global concurrency of unrelated tasks are upper-bounded by the execution thread
  pool size. Compared to the other conseq4j API style, this has the advantage of avoiding the issues associated with
  hash collision, and may be preferable for simple cases that do not require the syntax and semantic richness
  an [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) has to
  offer.
- Since there is no bucket hashing, this API style decouples the submitted tasks from their execution threads. All
  pooled threads are anonymous and interchangeable to execute any tasks. I.e. even related tasks of the same sequence
  key could be executed by different pooled threads, albeit in sequential order. This may bring extra performance gain
  compared to the other API style.

  The default concurrency or max pool size is either 16 or the JVM
  run-time's [availableProcessors](https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#availableProcessors--),
  which ever is larger:
  ```jshelllanguage
  ConcurrentSequencingExecutor conseqExecutor = ConseqExecutor.newInstance();
  ```

  The concurrency can be customized:
  ```jshelllanguage
  ConcurrentSequencingExecutor conseqExecutor = ConseqExecutor.newInstance(10);
  ```

## Full Disclosure - Asynchronous Conundrum

The Asynchronous Conundrum refers to the fact that asynchronous concurrent processing and deterministic order of
execution do not come together naturally; in asynchronous systems, certain limits and impedance mismatch exist between
maintaining meaningful local execution order and maximizing global concurrency.

In asynchronous concurrent messaging, there are generally two approaches to achieve necessary order:

**1. Preventive**

This is more on the technical level. Sometimes it is possible to prevent related messages from ever being executed
out of order in a globally concurrent process. This implies:

(1) The message producer ensures that messages are posted to the messaging provider in correct order.

(2) The messaging provider ensures that messages are delivered to the message consumer in the same order they are
received.

(3)The message consumer ensures that related messages are processed in the same order, e.g. by using a
sequence/correlation key as with this API.

**2. Curative**

This is more on the business rule level. Sometimes preventative measures for messaging order preservation are either not
possible or not worthwhile to pursue. By the time the consumer receives the messages, things can be out of order
already. E.g. when the messages are coming in from independent producers and sources, there may be no guarantee of
correct ordering in the first place. In such cases, the message consumer's job would be to detect and make amends when
things do go out of order, by applying business rules to restore the proper sequence.

Compared to preventative measures, corrective ones can be much more complicated in terms of design, implementation and
runtime performance. E.g. it may help to do a stateful/historical look-up of all the data and other events received so
far that are related to the incoming event; this forms a correlated and collective session of information for the
incoming event. A comprehensive review of such session can detect and determine if the incoming event is out of order
per business rules; corrective (among other reactive) actions can then be taken for the order restoration. This may fall
into the scope of [Complex Event Processing (CEP)](https://en.wikipedia.org/wiki/Complex_event_processing). State
Machines can also be a useful design in such scenario.
