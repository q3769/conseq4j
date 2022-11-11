# conseq4j

A Java concurrent API to sequence the executions of related tasks while concurring unrelated ones, where "conseq" is
short for **con**current **seq**uencer.

## User stories

1. As a client of the conseq4j API, I want to summon a thread/executor by a sequence key, so that I can sequentially
   execute all related tasks with the same sequence key using the same executor while unrelated tasks with different
   sequence keys can be executed concurrently by different executors.
2. As a client of the conseq4j API, I want to asynchronously submit a task for execution together with a sequence key,
   so that, across all such submissions, related tasks under the same sequence key are executed sequentially and
   unrelated tasks of different sequence keys are executed concurrently.

Consider using conseq4j when you want to achieve concurrent processing globally while preserving meaningful local
execution order at the same time.

## Prerequisite

Java 8 or better

## Get it...

[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

## Use it...

A sequence key cannot be `null`. Any two keys are considered "the same sequence key" if and only if
`Objects.equals(key1, key2)` returns `true`.

### Style 1: Summon a sequential executor by its sequence key, and use the executor as with a JDK ExecutorService

In this API style, the `SequentialExecutorServiceFactory` produces executors of JDK
type [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html). The same
sequence key always gets back the same executor from the factory, no matter when or how many times the executor is
summoned. All tasks submitted to that executor, no matter when or how many, are considered part of the same sequence;
therefore, executed sequentially in exactly the same order as submitted.

There is no limit on the total number of sequence keys the API client can use to summon executors. Behind the scenes,
tasks of different sequence keys will be managed to execute in parallel, by a thread pool of configurable size.

Consider using this style when the summoned executor needs to provide
the [syntax and semantic richness](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html#method.summary)
of the JDK `ExecutorService` API.

#### API

```
public interface SequentialExecutorServiceFactory {

    /**
     * @param sequenceKey an {@link java.lang.Object} whose hash code is used to locate and summon the corresponding
     *                    sequential executor.
     * @return the executor of type {@link java.util.concurrent.ExecutorService} that executes all tasks of this
     *         sequence key in the same order as they are submitted
     */
    ExecutorService getExecutorService(Object sequenceKey);
}
```

#### Sample usage

```
public class MessageConsumer {

    /**
     * Default conseq's global concurrency is (java.lang.Runtime.availableProcessors + 1).
     * 
     * Or to set the global concurrency to 10, for example:
     * <code>
     * private SequentialExecutorServiceFactory conseqFactory = new ConseqFactory(10);
     * </code>
     */
    private SequentialExecutorServiceFactory conseqFactory = new ConseqFactory(); 
    
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
        conseqFactory.getExecutorService(shoppingEvent.getShoppingCartId())
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
  returned by the executor, though, is still cancellable. In general, the hash collision may not be an issue for those
  workloads that are asynchronous and focused on overall through-put, but is something to be aware of.
- The default global concurrency is 1 plus the JVM
  run-time's [availableProcessors](https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#availableProcessors--)
  , via the default constructor:
  ```
  SequentialExecutorServiceFactory conseqFactory = new ConseqFactory();
  ```

  The global concurrency can be customized by a constructor argument. This may become useful when the application is
  deployed using containers, where the `availableProcessors` reported to the Java Runtime may not reflect the actual CPU
  resource of the container.
  ```
  SequentialExecutorServiceFactory conseqFactory = new ConseqFactory(10);
  ```

### Style 2: Submit a task together with its sequence key, and directly use the conseq4j API as an executor service

This API style is more concise. It bypasses the JDK ExecutorService API and, instead, services the submitted task
directly. The same execution semantics holds: Tasks submitted with the same sequence key are executed in the same
submission order; tasks of different sequence keys are managed to execute in parallel, by a thread pool of configurable
size.

Prefer this style when the full-blown syntax and semantic support of a
JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) is not
required.

#### API

```
public interface SequentialExecutor {

    /**
     * @param command     the Runnable task to run sequentially with others under the same sequence key
     * @param sequenceKey the key under which all tasks are executed sequentially
     * @return future holding run status of the command
     */
    Future<Void> execute(Runnable command, Object sequenceKey);

    /**
     * @param task        the Callable task to run sequentially with others under the same sequence key
     * @param sequenceKey the key under which all tasks are executed sequentially
     * @param <T>         the type of the task's result
     * @return a Future representing pending completion of the task
     */
    <T> Future<T> submit(Callable<T> task, Object sequenceKey);
}
```

#### Sample usage

```
public class MessageConsumer {

    /**
     * Default service uses JDK's ForkJoinPooll#commonPool to facilitate async execution.
     * 
     * Or to provide a custom thread pool of size 10, for example:
     * <code>
     * private SequentialExecutor conseqExecutor = new ConseqExectuor(Executors.newFixedThreadPool(10));
     * </code>
     */
    private SequentialExecutor conseqExecutor = new ConseqExectuor();
    
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
        conseqExecutor.submit(
                () -> shoppingEventProcessor.process(shoppingEvent), 
                shoppingEvent.getShoppingCartId());
    }
    ...
```

Notes:

- The implementation of this style relies on
  JDK's [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)
  behind the scenes to achieve sequential execution of related tasks. A thread pool is employed to facilitate the
  overall asynchronous execution. The global concurrency of unrelated tasks are upper-bounded by the execution thread
  pool size. Compared to the other conseq4j API style, this has the advantage of avoiding the issues associated with
  hash collision, and may be preferable for simple cases that do not require the syntax and semantic richness
  an [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) has to
  offer.
- Since there is no bucket hashing, this API style decouples the submitted tasks from their execution threads. All
  pooled threads are anonymous and interchangeable to execute any tasks. I.e. even related tasks of the same sequence
  key could be executed by different pooled threads, albeit in sequential order. This may bring extra performance gain
  compared to the other API style.

  The default thread pool is
  JDK's [ForkJoinPool#commonPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html#commonPool--)
  , via the default constructor:
  ```
  SequentialExecutor conseqExecutor = new ConseqExecutor();
  ```

  Alternatively, the thread pool can be customized through a constructor argument. E.g. this is to use a customized
  thread pool of 10 threads:
  ```
  SequentialExecutor conseqExecutor = new ConseqExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
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
