[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

# conseq4j

A Java concurrent API to asynchronously execute related tasks sequentially, and unrelated tasks concurrently.

- *conseq* is short for *con*current *seq*uencer.

## User stories

1. As an API client, I want to summon a sequential task executor by a sequence key, so that all the tasks sequentially
   submitted under the same sequence key will be executed by the same executor in the same order as submitted;
   meanwhile, the tasks with different sequence keys can be executed concurrently by different executors even when
   submitted sequentially.
2. As an API client, I want to asynchronously submit a task for execution together with a sequence key, so that, across
   all such submissions, tasks submitted sequentially under the same sequence key are executed in the same order as
   submitted; meanwhile, tasks of different sequence keys are executed concurrently even when submitted sequentially.

Consider using conseq4j to achieve asynchronous concurrent processing globally while preserving meaningful local
execution order at the same time.

## Prerequisite

- Java 21 or better (version 20230923.0.0 or newer)
- Java 8 or better (versions older than 20230923.0.0)

## Get it...

[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

Install as a compile-scope dependency in Maven or other build tools alike.

## Use it...

### General notes

#### Sequence keys

A sequence key cannot be `null`. Any two keys, `sequenceKey1` and `sequenceKey2`, are considered "the same sequence key"
if and only if `Objects.equals(sequenceKey1, sequenceKey2)` returns `true`.

#### Thread safety

A conseq4j instance is thread-safe in and of itself. The usual thread-safety rules and concerns, however, still apply
when programming the executable tasks. Moreover, in the context of concurrency and sequencing, the thread-safety concern
goes beyond concurrent modification of individual-task data, into that of meaningful execution order among multiple
related tasks.

#### Concurrency and sequencing

First of all, by definition, there is no such thing as order or sequence among tasks submitted concurrently by different
threads. No particular execution order is guaranteed on those concurrent tasks, regardless of their sequence keys. The
conseq4j API only manages sequentially-submitted tasks - those that are submitted by a single thread, or by each single
thread in case of multi-threading. To execute those sequential tasks, the conseq4j API provides both concurrency and
sequencing: The tasks will be executed sequentially if they have the same sequence key, and concurrently if they have
different sequence keys.

Technically, to form a sequence, the client task-submitting thread only needs to be "logically" single. It does not
always have to be the same physical thread e.g. sometimes one thread may need to be replaced by another for various
reasons. The conseq4j API should function correctly as long as the related tasks are submitted by at most one thread at
any time, and with the right order of submission sequence over time. Fortunately, that is often naturally the case for
the API client, e.g. when the task submission is managed by a messaging provider such as Kafka, JMS, x-MQ, TIBCO EMS,
etc...

### Style 1: summon a sequential executor by its sequence key, then use the executor as with a JDK `ExecutorService`

#### API

```java
public interface SequentialExecutorServiceFactory extends Terminable {
    /**
     * @param sequenceKey
     *         an {@link Object} instance whose hash code is used to summon the corresponding executor.
     * @return the sequential executor of type {@link java.util.concurrent.ExecutorService} that executes all tasks of
     *         this sequence key in the same order as they are submitted.
     */
    ExecutorService getExecutorService(Object sequenceKey);
}
```

where ```Terminable``` is defined as

```java
public interface Terminable {
    /**
     * Initiates an orderly shutdown of all managed thread resources. Previously submitted tasks are executed, but no
     * new tasks will be accepted. Invocation has no additional effect if already shut down.
     * <p>
     * This method does not wait for the previously submitted tasks to complete execution. Use an external awaiting
     * mechanism to do that, with the help of {@link #isTerminated()}.
     */
    void shutdown();

    /**
     * Non-blocking
     *
     * @return true if all tasks of all managed executors have completed following shut down. Note that isTerminated is
     *         never true unless shutdown was called first.
     */
    boolean isTerminated();

    /**
     * Attempts to stop all actively executing tasks, halts the processing of waiting tasks, and returns a list of the
     * tasks that were awaiting execution.
     * <p>
     * This method does not wait for the previously submitted tasks to complete execution. Use an external awaiting
     * mechanism to do that, with the help of {@link #isTerminated()}.
     *
     * @return Tasks submitted but never started executing
     */
    List<Runnable> shutdownNow();
}
```

This API style loosely takes the form of "thread affinity". Sequence keys are used to summon executors of JDK
type [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html). The same
sequence key always gets back the same sequential executor. All tasks of that sequence key can then be "affined" to and
executed sequentially by the summoned executor in the same submission order.

The total number of executors concurrently available at runtime is configurable. As each executor is sequential, the
number of available executors equals the number of tasks that can be executed in parallel.

Consider using this style when the summoned executor needs to provide
the [syntax and semantic richness](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html#method.summary)
of the JDK `ExecutorService` API.

#### Sample usage

```java
public class MessageConsumer {
    /**
     * Default conseq's concurrency is java.lang.Runtime.availableProcessors.
     * <p>
     * Or to set the global concurrency to 10, for example:
     * <code>
     * private SequentialExecutorServiceFactory conseqServiceFactory = ConseqServiceFactory.instance(10);
     * </code>
     */
    private final SequentialExecutorServiceFactory conseqServiceFactory = ConseqServiceFactory.instance();

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

- The implementation of this thread-affinity style relies on hashing of the sequence keys into a fixed number of
  "buckets". These buckets are each associated with a sequential executor. The same sequence key is always hashed to and
  summons back the same executor. Single-threaded, each executor ensures the execution order of all its tasks is the
  same as they are submitted; excessive tasks pending execution are buffered in a FIFO task queue. Thus, the total
  number of buckets (i.e. the max number of available executors and the general concurrency) is the maximum number of
  tasks that can be executed in parallel at any given time.
- As with hashing, collision may occur among different sequence keys. When hash collision happens, tasks of different
  sequence keys are assigned to the same executor. Due to the single-thread setup, the executor still ensures the local
  sequential execution order for each individual sequence key's tasks. However, unrelated tasks of different sequence
  keys now assigned to the same bucket/executor may delay each other's execution inadvertently while waiting in the
  executor's task queue. Consider this a trade-off of the executor's having the same syntax and semantic richness as a
  JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html).
- To account for hash collision, conseq4j does not support any shutdown action on the API-provided
  executor ([ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html))
  instance. That is to prevent unintended task cancellation across different sequence keys.
  The [Future](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html) instance(s) subsequently
  returned by the executor, though, is still cancellable. The hash collision may not be an issue for workloads that are
  asynchronous and focused on overall through-put, but is something to be aware of.
- The default general concurrency is the JVM
  run-time's [availableProcessors](https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#availableProcessors--):
  ```jshelllanguage
  ConseqServiceFactory.instance();
  ```

  The concurrency can be customized:
  ```jshelllanguage
  ConseqServiceFactory.instance(10)
  ```

### Style 2: submit each task directly for execution, together with its sequence key

#### API

```java
public interface SequentialExecutor extends Terminable {
    /**
     * @param command
     *         the Runnable task to run sequentially with others under the same sequence key
     * @param sequenceKey
     *         the key under which all tasks are executed sequentially
     * @return future holding run status of the submitted command
     */
    Future<Void> execute(Runnable command, Object sequenceKey);

    /**
     * @param task
     *         the Callable task to run sequentially with others under the same sequence key
     * @param sequenceKey
     *         the key under which all tasks are executed sequentially
     * @param <T>
     *         the type of the task's result
     * @return a Future representing pending completion of the submitted task
     */
    <T> Future<T> submit(Callable<T> task, Object sequenceKey);
}
```

This API style is more concise. Bypassing the JDK ExecutorService API, it services the submitted task directly. The same
execution semantics holds: Tasks of the same sequence key are executed in the same submission order; tasks of different
sequence keys are managed to execute in parallel.

Prefer this style when the full-blown syntax and semantic support of
JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) is not
required.

#### Sample usage

```java
public class MessageConsumer {

    /**
     * Default executor concurrency is java.lang.Runtime.availableProcessors.
     * <p>
     * Or to provide a custom concurrency of 10, for example:
     * <code>
     * private SequentialExecutor conseqExecutor = ConseqExecutor.instance(10));
     * </code>
     */
    private final SequentialExecutor conseqExecutor = ConseqExectuor.instance();

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
        conseqExecutor.execute(() -> shoppingEventProcessor.process(shoppingEvent), shoppingEvent.getShoppingCartId());
    }
}
```

- The interface of this direct-execute style uses `Future` as the return type, mainly to reduce conceptual weight of the
  API. The implementation actually returns `CompletableFuture`, and can be used/cast as such if need be.
- The implementation relies on
  JDK's [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) to
  achieve sequential execution of related tasks. One single pool of threads is used to facilitate the overall
  asynchronous execution. The concurrency to execute unrelated tasks is generally limited only by the backing work
  thread pool's capacity.
- Instead of thread-affinity or bucket hashing, tasks are decoupled from their execution threads. All pooled threads are
  anonymous and interchangeable to execute any tasks. Even sequential tasks of the same sequence key may be executed by
  different threads, albeit in sequential order. When the work thread pool has idle threads available, a task awaiting
  execution must have been blocked only by its own related task(s) of the same sequence key - as is necessary, and not
  by unrelated tasks of different sequence keys in the same "bucket" - as is unnecessary. This can be a desired
  advantage over the thread-affinity API style, at the trade-off of lesser syntax and semantic richness than the
  JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html).
- The default ConseqExecutor instance uses `Executors.newVirtualThreadPerTaskExecutor()` to facilitate the async
  operations since Java 21. With earlier JDK and conseq4j versions, the default instance uses a `ForkJoinPool` with
  parallelism equal to JVM
  run-time's [availableProcessors](https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#availableProcessors--):
  ```jshelllanguage
  ConseqExecutor.instance()
  ```
  If the concurrency is customized, ConseqExecutor instance uses a `ForkJoinPool` of such concurrency/parallelism:
  ```jshelllanguage
  ConseqExecutor.instance(10)
  ```
- The `ConseqExecutor` instance can also use a fully-customized `ExecutorService` to power its async operations:

  `ConseqExecutor.instance(ExecutorService workerExecutorService)`

## Full disclosure - Asynchronous Conundrum

The Asynchronous Conundrum refers to the fact that asynchronous concurrent processing and deterministic order of
execution do not come together naturally; in asynchronous systems, certain limits and impedance mismatch exist between
maintaining meaningful local execution order and maximizing global concurrency.

In asynchronous concurrent messaging, there are generally two approaches to achieve necessary order:

### 1. Preventive

This is more on the technical level. Sometimes it is possible to prevent related messages from ever being executed
out of order in a globally concurrent process. This implies:

(1) The message producer ensures that messages are posted to the messaging provider in correct order.

(2) The messaging provider ensures that messages are delivered to the message consumer in the same order they are
received.

(3) The message consumer ensures that related messages are processed in the same order, e.g. by using a
sequence/correlation key as with this API.

### 2. Curative

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
