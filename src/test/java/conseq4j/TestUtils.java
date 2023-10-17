/*
 * MIT License
 *
 * Copyright (c) 2021 Qingtian Wang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package conseq4j;

import org.awaitility.Awaitility;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestUtils {

    private TestUtils() {
    }

    public static int actualExecutionThreadCount(List<SpyingTask> tasks) {
        return (int) tasks.stream().map(SpyingTask::getRunThreadName).distinct().count();
    }

    public static long actualExecutionThreadCountIfAllCompleteNormal(List<Future<SpyingTask>> futures) {
        return getIfAllCompleteNormal(futures).stream().map(SpyingTask::getRunThreadName).distinct().count();
    }

    public static void assertConsecutiveRuntimes(List<SpyingTask> tasks) {
        for (int i = 0; i < tasks.size() - 1; i++) {
            SpyingTask current = tasks.get(i);
            SpyingTask next = tasks.get(i + 1);
            assertTrue(current.getRunTimeEndMillis() <= next.getRunTimeStartMillis());
        }
    }

    public static void awaitAllComplete(List<SpyingTask> tasks) {
        Awaitility.await().until(() -> tasks.parallelStream().allMatch(SpyingTask::isDone));
    }

    public static <T> void awaitFutures(List<Future<T>> futures) {
        Awaitility.await().until(() -> futures.parallelStream().allMatch(Future::isDone));
    }

    public static <T> int cancellationCount(List<Future<T>> futures) {
        awaitFutures(futures);
        return futures.parallelStream().mapToInt(f -> f.isCancelled() ? 1 : 0).sum();
    }

    public static List<SpyingTask> createSpyingTasks(int taskCount) {
        return IntStream.range(0, taskCount).mapToObj(SpyingTask::new).collect(toList());
    }

    public static <T> List<T> getIfAllCompleteNormal(List<Future<T>> futures) {
        return futures.stream().map(f -> {
            try {
                return f.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new IllegalStateException(ex);
            }
        }).collect(toList());
    }

    public static <T> int normalCompletionCount(List<Future<T>> resultFutures) {
        awaitFutures(resultFutures);
        int normalCompletionCount = 0;
        for (Future<T> future : resultFutures) {
            if (future.isCancelled()) {
                continue;
            }
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                continue;
            }
            normalCompletionCount++;
        }
        return normalCompletionCount;
    }
}
