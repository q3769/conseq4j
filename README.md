[![Maven Central](https://img.shields.io/maven-central/v/io.github.q3769/conseq4j.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.q3769%22%20AND%20a:%22conseq4j%22)

# conseq4j

A Java concurrent API to sequence the executions of related tasks while concurring unrelated ones, where "conseq" is
short for **con**current **seq**uencer.

## User stories

1. As a client of the conseq4j API, I want to summon a thread/executor by a sequence key, so that I can sequentially
   execute all related tasks with the same sequence key using the same executor while unrelated tasks with different
   sequence keys can be executed concurrently by different executors.
2. As a client of the conseq4j API, I want to asynchronously submit a task for execution together with a sequence key,
   so that, across all such submissions, related tasks under the same/equal sequence key are executed sequentially and
   unrelated tasks of different sequence keys are executed concurrently.

Consider using conseq4j when you want to achieve concurrent processing globally while preserving meaningful local
execution order at the same time.

## Prerequisite

Java 8 or better

## Get it...

With Maven:

```
<dependency>
    <groupId>io.github.q3769</groupId>
    <artifactId>conseq4j</artifactId>
    <version>20220817.0.0</version>
</dependency>
```

With Gradle:

```
implementation 'io.github.q3769:conseq4j:20220817.0.0'
```

## Use it...

### TL;DR:

It is recommended that the API client use some form of synchronization, such as a managed single caller thread or
a [fair lock](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantLock.html#ReentrantLock-boolean-)
, to establish a certain sequence of task submissions; leave it to the conseq4j API to provide concurrency, as well as
proper sequencing, while it executes the submitted tasks.

Skip to see the API and usage samples below.

### The long version on usage:

The conseq4j implementation is thread-safe per its given sequence of submitted tasks. However, it is not recommended
that the API client use concurrent threads for task submission. In the context of sequencing and concurrency,
thread-safety goes beyond the concern of data corruption on individual tasks, into that of the execution order across
multiple related tasks. It is worthwhile to clarify the effect of using client-side multithreading, instead of the
conseq4j API, to achieve concurrency.

First, it is the API client's responsibility and concern that how tasks are submitted. If execution order is imperative,
the client has to ensure that tasks are submitted in proper sequence to begin with. Fortunately, often times that is
naturally the case e.g. when the client is under the management of a messaging provider running a single caller thread.
Otherwise, however, if the client is multithreaded, then organically there is no such thing as sequence among the tasks
submitted by different threads. If the client does not take specific measures to ensure the scheduling order among the
submission threads, then by definition of concurrency, such submitted tasks are considered "safe" to execute in any
order. With multithreading, though, it may not be trivial for the client to control the submission order among the
concurrent threads, as Java does not provide a strong guarantee on thread scheduling. Without definitive task submission
order in the first place, the sequencing capability of conseq4j will be rendered moot.

Second, when a certain submission sequence is established by the API client, it is then conseq4j's concern and
responsibility that further execution of the submitted tasks is in the meaningful order and concurrency as promised.
Although having no control over how task submissions are scheduled, conseq4j does guarantee that tasks are received in
the same order as their submissions. Also, a "fair" execution order is then guaranteed: Related tasks of the same
sequence key are sequentially executed in the same submitted/received order - the earliest-submitted/received task gets
executed first; meanwhile, unrelated tasks can be executed in parallel.

In other words, see the TL;DR above.

### *Style 1:* Summon a sequential executor by its sequence key, and use the executor as with a JDK ExecutorService.

This API style provides a sequential executor of JDK
type [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) for all the
tasks under the given sequence key. Consider using this style when your executor requires
the [syntax and semantic richness](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html#method.summary)
of the JDK API.

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

    /**
     * Default conseq's global concurrency is (java.lang.Runtime.availableProcessors + 1).
     * 
     * Or to set the global concurrency to 10, for example:
     * <code>
     * private ConcurrentSequencer conseq = new Conseq(10);
     * </code>
     */
    private ConcurrentSequencer conseq = new Conseq(); 
    
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
  returned by the executor, though, is still cancellable. In general, the hash collision may not be an issue for those
  workloads that are asynchronous and focused on overall through-put, but is something to be aware of.
- The default global concurrency is 1 plus the
  JVM
  run-time's [availableProcessors](https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#availableProcessors--)
  ,
  via the default constructor:
  ```
  ConcurrentSequencer conseq = new Conseq();
  ```

  If needed, the global concurrency can be customized using a constructor argument. E.g. This may become useful when the
  application is deployed using containers, where the `availableProcessors` reported to the Java Runtime may not reflect
  the actual CPU resource of the container.
  ```
  ConcurrentSequencer conseq = new Conseq(10);
  ```

### *Style 2:* Submit a task together with its sequence key, and directly use the conseq4j API as an executor service.

This API style bypasses the JDK ExecutorService API and, instead, services the submitted task directly. Prefer using
this style when you do not require the full-blown syntax and semantic support of a
JDK [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html).

#### API:

```
public interface ConcurrentSequencingExecutor {

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

    /**
     * Default service uses JDK's ForkJoinPooll#commonPool to facilitate async execution.
     * 
     * Or to provide a custom thread pool of size 10, for example:
     * <code>
     * private ConcurrentSequencingExecutor conseqExecutor = new ConseqExectuor(Executors.newFixedThreadPool(10));
     * </code>
     */
    private ConcurrentSequencingExecutor conseqExecutor = new ConseqExectuor();
    
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
        conseqExecutor.execute(
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
  pool size. Compared to the other conseq4j API style, this has the advantage of avoiding hash collision related issues,
  and may be preferable for simple cases that do not require the syntax and semantic richness
  an [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) has to
  offer.
- Since there is no bucket hashing, this API style decouples the submitted tasks from their execution threads. I.e. even
  related tasks of the same sequence key could be executed by different threads from the thread pool, albeit in
  sequential order. This may bring extra performance gain compared to the other API style.

  The default thread pool is
  JDK's [ForkJoinPool#commonPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html#commonPool--)
  , via the default constructor:
  ```
  ConcurrentSequencingExecutor conseqExecutor = new ConseqExecutor();
  ```

  Alternatively, the thread pool can be customized through a constructor argument. E.g. this is to use a customized
  thread pool of 10 threads:
  ```
  ConcurrentSequencingExecutor conseqExecutor = new ConseqExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
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
