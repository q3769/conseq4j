[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Conseq4J)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

# conseq4j

A Java concurrent API to sequence the asynchronous executions of related tasks while concurring unrelated ones.

- *conseq* is short for *con*current *seq*uencer.

## User Stories

1. As an API client, I want to summon a sequential task executor by a sequence key, so that all the tasks sequentially
   submitted under the same sequence key will be executed by the same executor in the same order as submitted; tasks
   with different sequence keys can be executed by different executors in parallel even when the tasks are submitted
   sequentially.
2. As an API client, I want to asynchronously submit a task for execution together with a sequence key, so that, across
   all such submissions, tasks submitted sequentially under the same sequence key are executed in the same order as
   submitted; tasks of different sequence keys are executed concurrently even when submitted sequentially.

Consider using conseq4j to achieve asynchronous concurrent processing globally while preserving meaningful local
execution order at the same time.

## Prerequisite

Java 8 or better

## Get It...

Available
at: [![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

## Use It...

**Sequence Keys**

A sequence key cannot be `null`. Any two keys, `sequenceKey1` and `sequenceKey2`, are considered "the same sequence key"
if and only if `Objects.equals(sequenceKey1, sequenceKey2)` returns `true`.

**Thread Safety**

A conseq4j instance is thread-safe in and of itself. The usual thread-safety rules and concerns, however, still apply
when programming the executable tasks. Moreover, in the context of concurrency and sequencing, the thread-safety concern
goes beyond concurrent modification of individual-task data, into that of meaningful execution order among multiple
related tasks.

**Concurrency And Sequencing**

First of all, by definition, there is no such thing as order or sequence among tasks submitted concurrently by different
threads. No particular execution order is guaranteed on those concurrent tasks, regardless of their sequence keys. The
conseq4j API only manages sequentially-submitted tasks - those that are submitted by a single thread, or by each single
thread in case of multi-threading. To execute those tasks, the conseq4j API provides both concurrency and sequencing:
The tasks will be executed sequentially if they have the same sequence key, and concurrently if they have different
sequence keys.

- Technically, to form a sequence, the client task-submitting thread only needs to be "logically" single. It
  does not always have to be the same physical thread e.g. sometimes one thread may need to be replaced by another for
  various reasons. The conseq4j API should function correctly as long as the related tasks are submitted by at most one
  thread at any given time, and with the right order of submission sequence over the time. Fortunately, that is often
  the case naturally for the API client, e.g. in the message-driven method invoked by a caller-thread managed by the
  messaging provider.

### Style 1: Summon An Executor By Its Sequence Key, Then Use That Sequential Executor As With A JDK `ExecutorService`

In this API style, sequence keys are used to summon executors of JDK
type [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html). The same
sequence key always gets back the same executor from the API, no matter when or how many times the executor is summoned.
All tasks sequentially submitted to that executor are considered part of the same sequence, therefore, executed
sequentially in exactly the same order as submitted.

There is no limit on the number of different sequence keys from the API client to summon executors. The total available
number of executors are configurable. As each executor is sequential, the total number of executors equals the maximum
number of tasks that can be executed in parallel.

Consider using this style when the summoned executor needs to provide
the [syntax and semantic richness](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html#method.summary)
of the JDK `ExecutorService` API.

#### API

```java
public interface SequentialExecutorServiceFactory {

    /**
     * @param sequenceKey an {@link Object} whose hash code is used to summon the corresponding executor.
     * @return the executor of type {@link java.util.concurrent.ExecutorService} that executes all tasks of this 
     *         sequence key in the same order as they are submitted.
     */
    ExecutorService getExecutorService(Object sequenceKey);
}
```

#### Sample Usage

```java
public class MessageConsumer {

    /**
     * Default conseq's concurrency is either 16 or java.lang.Runtime.availableProcessors, which ever is larger.
     * <p>
     * Or to set the global concurrency to 10, for example:
     * <code>
     * private SequentialExecutorServiceFactory conseqServiceFactory = ConseqServiceFactory.newInstance(10);
     * </code>
     */
    private final SequentialExecutorServiceFactory conseqServiceFactory = ConseqServiceFactory.newInstance();

    @Autowired private ShoppingEventProcessor shoppingEventProcessor;

    /**
     * Suppose run-time invocation of this method is managed by the messaging provider. This is usually via a single 
     * caller thread.
     * <p>
     * Concurrency is achieved when shopping events of different shopping cart IDs are processed in parallel, by 
     * different executors. Sequence is maintained on all shopping events of the same shopping cart ID, by the same 
     * executor.
     */
    public void onMessage(Message shoppingEvent) {

        conseqServiceFactory.getExecutorService(shoppingEvent.getShoppingCartId())
                .execute(() -> shoppingEventProcessor.process(shoppingEvent));
    }
}
```

Notes:

- The implementation of this style loosely takes the form of "thread affinity". It relies on hashing of the sequence
  keys into a fixed number of "buckets". These buckets are each associated with a sequential executor. The same/equal
  sequence key is always hashed to and summons back the same executor. Single-threaded, each executor ensures the
  execution order of all its tasks is the same as they are submitted; excessive tasks pending execution are buffered a
  FIFO task queue. Thus, the total number of buckets (i.e. the max number of available executors and the general
  concurrency) is the maximum number of tasks that can be executed in parallel at any given time.
- As with hashing, collision may occur among different sequence keys. When hash collision happens, tasks of different
  sequence keys are assigned to the same executor. Due to the single-thread setup, the executor still ensures the local
  execution order for each individual sequence key's tasks. However, unrelated tasks of different sequence keys yet
  assigned to the same bucket/executor may delay each other's execution inadvertently while waiting in the executor's
  task queue. Consider this a trade-off of the executor's having the same syntax and semantic richness as a
  JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html).
- To account for hash collision, conseq4j does not support any shutdown action on the API-provided
  executor ([ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html))
  instance. That is to prevent unintended task cancellation across different sequence keys.
  The [Future](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html) instance(s) subsequently
  returned by the executor, though, is still cancellable. The hash collision may not be an issue for workloads that are
  asynchronous and focused on overall through-put, but is something to be aware of.
- The default general concurrency is either 16 or the JVM
  run-time's [availableProcessors](https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#availableProcessors--),
  which ever is larger:
  ```jshelllanguage
  ConseqServiceFactory.newInstance();
  ```

  The concurrency can be customized:
  ```jshelllanguage
  ConseqServiceFactory.newInstance(10)
  ```

### Style 2: Submit Each Task Directly For Execution, Together With Its Sequence Key

This API style is more concise. Bypassing the JDK ExecutorService API, it services the submitted task directly. The same
execution semantics holds: Tasks of the same sequence key are executed in the same submission order; tasks of different
sequence keys are managed to execute in parallel.

Prefer this style when the full-blown syntax and semantic support of
JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) is not
required.

#### API

```java
public interface SequentialExecutor {

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
     * <p>
     * Or to provide a custom concurrency of 10, for example:
     * <code>
     * private SequentialExecutor conseqExecutor = ConseqExecutor.newInstance(10));
     * </code>
     */
    private final SequentialExecutor conseqExecutor = ConseqExectuor.newInstance();

    @Autowired private ShoppingEventProcessor shoppingEventProcessor;

    /**
     * Suppose run-time invocation of this method is managed by the messaging provider. This is usually via a single 
     * caller thread.
     * <p>
     * Concurrency is achieved when shopping events of different shopping cart IDs are processed in parallel by 
     * different backing threads. Sequence is maintained for all shopping events of the same shopping cart ID, via 
     * linear progression of execution stages with {@link java.util.concurrent.CompletableFuture}.
     */
    public void onMessage(Message shoppingEvent) {
        conseqExecutor.submit(() -> shoppingEventProcessor.process(shoppingEvent), shoppingEvent.getShoppingCartId());
    }
}
```

Notes:

- The implementation of this style relies on
  JDK's [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) to
  achieve sequential execution of related tasks. One single backing thread pool is used to facilitate the overall
  asynchronous execution. The concurrency to execute unrelated tasks is only limited by the backing thread pool size.
- Without "thread affinity" or bucket hashing, this API style decouples tasks from their execution threads. All pooled
  threads are anonymous and interchangeable to execute any tasks. Even sequential tasks of the same sequence key can be
  executed by different threads, albeit in sequential order. A task awaiting execution must have been blocked only by
  its own related task(s) of the same sequence key - as it is supposed to be, and not by unrelated tasks of different
  sequence keys in the same "bucket" - as is unnecessary. This can be a desired advantage over the other conseq4j API
  style, at the trade-off of lesser syntax and semantic richness than the
  JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html).
- The default general concurrency or max execution thread pool size is either 16 or the JVM
  run-time's [availableProcessors](https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#availableProcessors--),
  which ever is larger:
  ```jshelllanguage
  ConseqExecutor.newInstance()
  ```

  The concurrency can be customized:
  ```jshelllanguage
  ConseqExecutor.newInstance(10)
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

(3) The message consumer ensures that related messages are processed in the same order, e.g. by using a
sequence/correlation key as with this API.

**2. Curative**

This is more on the business rule level. Sometimes preventative measures are either not possible or not worthwhile to
pursue. By the time messages arrive at the consumer, they may be intrinsically out of order. E.g. when the messages are
coming in from independent producers and sources, there may be no guarantee of correct ordering in the first place. In
such cases, the message consumer may be able to take a curative approach, by applying business rules to restore
necessary order and properly handle the out-of-order messages.

Compared to preventative measures, corrective ones can be much more complicated in terms of design, implementation and
runtime performance. E.g. for each incoming event, it may help to do a stateful/historical look-up of all the data and
other events that are related; this forms a correlated and collective information session of the incoming event. A
comprehensive review of such session can detect and determine if the incoming event is out of order per business rules;
corrective measures can then be taken to restore the right order, among other reactive actions. This may fall into the
scope of [Complex Event Processing (CEP)](https://en.wikipedia.org/wiki/Complex_event_processing). State Machines can
also be a useful design in such scenario.
