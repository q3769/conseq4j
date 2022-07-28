package conseq4j.service;

import lombok.extern.java.Log;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Log class StagingConcurrentSequencerServiceTest {

    @Test void noStageLingers() {
        StagingConcurrentSequencerService sut = new StagingConcurrentSequencerService();
        int taskCount = 5;
        long averageRunDurationMillis = 1000L;
        for (int i = 0; i < taskCount; i++) {
            sut.execute(() -> {
                long start = System.currentTimeMillis();
                await().until(() -> System.currentTimeMillis() - start >= averageRunDurationMillis);
            }, UUID.randomUUID());
        }

        Duration estimatedMaxActualRunAndCleanupDuration = Duration.ofSeconds(2);
        long sequentialRunDurationMillis = taskCount * averageRunDurationMillis;
        assertTrue(estimatedMaxActualRunAndCleanupDuration.toMillis() < sequentialRunDurationMillis);
        await().atMost(estimatedMaxActualRunAndCleanupDuration).until(() -> sut.getActiveExecutorCount() == 0);
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
