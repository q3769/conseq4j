/*
 * MIT License
 *
 * Copyright (c) 2022 Qingtian Wang
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

package conseq4j.execute;

import conseq4j.SpyingTask;
import conseq4j.TestUtils;
import lombok.extern.java.Log;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Log class StagingExecutorTest {

    @Test void noExecutorLingersOnSameSequenceKey() {
        StagingExecutor sut = new StagingExecutor();
        UUID sameSequenceKey = UUID.randomUUID();
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(100);

        tasks.parallelStream().forEach(t -> sut.execute(t, sameSequenceKey));
        TestUtils.awaitDone(tasks);

        await().until(() -> sut.getActiveExecutorCount() == 0);
    }

    @Test void noExecutorLingersOnRandomSequenceKeys() {
        StagingExecutor sut = new StagingExecutor();
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(100);

        tasks.parallelStream().forEach(t -> sut.execute(t, UUID.randomUUID()));
        TestUtils.awaitDone(tasks);

        await().until(() -> sut.getActiveExecutorCount() == 0);
    }

    @Test void canCustomizeBackingThreadPool() {
        ExecutorService customBackingThreadPool = Executors.newFixedThreadPool(42);
        StagingExecutor sut = new StagingExecutor(customBackingThreadPool);

        String customPoolName = customBackingThreadPool.getClass().getName();
        assertEquals(customPoolName, sut.getExecutionThreadPoolTypeName());
    }

    @Test void defaultBackingThreadPool() {
        ExecutorService expected = ForkJoinPool.commonPool();
        StagingExecutor sut = new StagingExecutor();

        String expectedPoolName = expected.getClass().getName();
        assertEquals(expectedPoolName, sut.getExecutionThreadPoolTypeName());
    }

}
