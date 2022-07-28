package conseq4j;

import lombok.extern.java.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;

import static java.util.stream.Collectors.toList;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Log public class TestUtils {

    private TestUtils() {
    }

    public static List<SpyingTask> createSpyingTasks(int taskCount) {
        List<SpyingTask> result = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            result.add(new SpyingTask(i));
        }
        return result;
    }

    public static int actualExecutionThreadCount(List<SpyingTask> tasks) {
        return (int) tasks.stream().map(SpyingTask::getRunThreadName).distinct().count();
    }

    public static long actualCompletionThreadCount(List<Future<SpyingTask>> futures) {
        return getAll(futures).stream().map(SpyingTask::getRunThreadName).distinct().count();
    }

    static <T> List<T> getAll(List<Future<T>> futures) {
        log.log(Level.FINER, () -> "Wait and get all results on futures " + futures);
        final List<T> doneTasks = futures.stream().map(f -> {
            try {
                return f.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new IllegalStateException(ex);
            }
        }).collect(toList());
        log.log(Level.FINER, () -> "All futures done, results: " + doneTasks);
        return doneTasks;
    }

    public static <T> void awaitAll(List<Future<T>> futures) {
        await().until(() -> futures.parallelStream().allMatch(Future::isDone));
    }

    public static void awaitDone(List<SpyingTask> tasks) {
        await().until(
                () -> tasks.parallelStream().allMatch(t -> t.getRunTimeEndMillis() != SpyingTask.UNSET_TIME_STAMP));
    }

    public static <T> int normalCompletionCount(List<Future<T>> resultFutures) {
        int normalCompletionCount = 0;
        for (Future<T> future : resultFutures) {
            if (future.isCancelled())
                continue;
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                continue;
            }
            normalCompletionCount++;
        }
        return normalCompletionCount;
    }

    public static <T> int cancellationCount(List<Future<T>> futures) {
        awaitAll(futures);
        return futures.parallelStream().mapToInt(f -> f.isCancelled() ? 1 : 0).sum();
    }

    public static void assertConsecutiveRuntimes(List<SpyingTask> tasks) {
        for (int i = 0; i < tasks.size() - 1; i++) {
            SpyingTask current = tasks.get(i);
            SpyingTask next = tasks.get(i + 1);
            if (current.getRunTimeEndMillis() > next.getRunTimeStartMillis())
                log.log(Level.WARNING,
                        "execution out of order between current task " + current + " and next task " + next);
            assertFalse(current.getRunTimeEndMillis() > next.getRunTimeStartMillis());
        }
    }
}
