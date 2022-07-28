package conseq4j.service;

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

@Log class StagingConcurrentSequencerServiceTest {

    @Test void noStageLingersOnSameSequenceKey() {
        StagingConcurrentSequencerService sut = new StagingConcurrentSequencerService();
        UUID sameSequenceKey = UUID.randomUUID();
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(100);

        tasks.parallelStream().forEach(t -> sut.execute(t, sameSequenceKey));
        TestUtils.awaitDone(tasks);

        await().until(() -> sut.getActiveExecutorCount() == 0);
    }

    @Test void noStageLingersOnRandomSequenceKeys() {
        StagingConcurrentSequencerService sut = new StagingConcurrentSequencerService();
        List<SpyingTask> tasks = TestUtils.createSpyingTasks(100);

        tasks.parallelStream().forEach(t -> sut.execute(t, UUID.randomUUID()));
        TestUtils.awaitDone(tasks);

        await().until(() -> sut.getActiveExecutorCount() == 0);
    }

    @Test void canCustomizeBackingThreadPool() {
        ExecutorService customBackingThreadPool = Executors.newFixedThreadPool(42);
        StagingConcurrentSequencerService conseqService =
                new StagingConcurrentSequencerService(customBackingThreadPool);

        String customPoolName = customBackingThreadPool.getClass().getName();
        assertEquals(customPoolName, conseqService.getExecutionThreadPoolTypeName());
    }

    @Test void defaultBackingThreadPool() {
        ExecutorService expected = ForkJoinPool.commonPool();
        StagingConcurrentSequencerService defaultConseqService = new StagingConcurrentSequencerService();

        String expectedPoolName = expected.getClass().getName();
        assertEquals(expectedPoolName, defaultConseqService.getExecutionThreadPoolTypeName());
    }

}
