package conseq4j.service;

import conseq4j.SpyingTask;
import lombok.extern.java.Log;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

@Log class ConseqServiceTest {

    private static final int TASK_COUNT = 100;

    private static final Level TEST_RUN_LOG_LEVEL = Level.FINE;

    @BeforeAll public static void setLoggingLevel() {
        Logger root = Logger.getLogger("");
        // .level= ALL
        root.setLevel(TEST_RUN_LOG_LEVEL);
        for (Handler handler : root.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                // java.util.logging.ConsoleHandler.level = ALL
                handler.setLevel(TEST_RUN_LOG_LEVEL);
            }
        }
    }

    private static List<SpyingTask> createSpyingTasks(int total) {
        List<SpyingTask> result = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            result.add(new SpyingTask(i));
        }
        return result;
    }

    @BeforeEach void setUp(TestInfo testInfo) {
        log.info(String.format("================================== start test: %s", testInfo.getDisplayName()));
    }

    @AfterEach void tearDown(TestInfo testInfo) {
        log.info(String.format("================================== done test: %s", testInfo.getDisplayName()));
    }

    @Test void submitConcurrencyBoundedByTotalTaskCount() {
        ConseqService conseqService = new ConseqService();

        List<Future<SpyingTask>> futures = createSpyingTasks(TASK_COUNT).stream()
                .map(task -> conseqService.submit(task, UUID.randomUUID()))
                .collect(toList());

        final long totalRunThreads = toDoneTasks(futures).stream().map(SpyingTask::getRunThreadName).distinct().count();
        log.log(Level.INFO, "{0} tasks were run by {1} threads", new Object[] { TASK_COUNT, totalRunThreads });
        assertTrue(totalRunThreads > 1);
        assertTrue(totalRunThreads <= TASK_COUNT);
        assertEquals(0, conseqService.getActiveExecutorCount());
    }

    @Test void executeRunsAllTasksOfSameSequenceKeyInSequence() throws InterruptedException {
        ConseqService conseqService = new ConseqService();
        List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
        UUID sameSequenceKey = UUID.randomUUID();
        final int extraFactorEnsuringAllDone = TASK_COUNT / 10;
        final int timeToAllowAllComplete = (TASK_COUNT + extraFactorEnsuringAllDone) * SpyingTask.MAX_RUN_TIME_MILLIS;

        log.log(Level.INFO, () -> "Start async submitting each of " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);
        tasks.forEach(task -> conseqService.execute(task, sameSequenceKey));
        log.log(Level.INFO, () -> "Done async submitting each of " + tasks.size() + " tasks under same sequence key "
                + sameSequenceKey);
        TimeUnit.MILLISECONDS.sleep(timeToAllowAllComplete);

        assertConsecutiveRuntimes(tasks);
        assertEquals(0, conseqService.getActiveExecutorCount());
    }

    private void assertConsecutiveRuntimes(List<SpyingTask> tasks) {
        for (int i = 0; i < tasks.size() - 1; i++) {
            SpyingTask current = tasks.get(i);
            SpyingTask next = tasks.get(i + 1);
            assertFalse(current.getRunEnd().isAfter(next.getRunStart()));
        }
    }

    List<SpyingTask> toDoneTasks(List<Future<SpyingTask>> futures) {
        log.log(Level.FINER, () -> "Wait and get all results on futures " + futures);
        final List<SpyingTask> doneTasks = futures.stream().map(f -> {
            try {
                return f.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new IllegalStateException(ex);
            }
        }).collect(toList());
        log.log(Level.FINER, () -> "All futures done, results: " + doneTasks);
        return doneTasks;
    }
}
