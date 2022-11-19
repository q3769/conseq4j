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
package conseq4j.summon;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author q3769
 */
class ConfigTest {

    @Test
    void shouldReturnSameExecutorOnSameName() {
        Conseq sut = Conseq.ofDefaultConcurrency();
        UUID sameSequenceKey = UUID.randomUUID();

        Executor e = sut.getSequentialExecutorService(sameSequenceKey);
        int additionalSummonTimes = 1 + new Random().nextInt(100);
        for (int i = 0; i < additionalSummonTimes; i++) {
            assertSame(e, sut.getSequentialExecutorService(sameSequenceKey));
        }
    }

    @Test
    void errorOnNonPositiveConcurrency() {
        int errors = 0;
        try {
            Conseq.ofConcurrency(0);
        } catch (IllegalArgumentException e) {
            errors++;
        }
        try {
            Conseq.ofConcurrency(-999);
        } catch (IllegalArgumentException e) {
            errors++;
        }
        assertEquals(2, errors);
    }

    @Test
    void shutdownUnsupported() {
        Conseq target = Conseq.ofDefaultConcurrency();
        final ExecutorService sequentialExecutor = target.getSequentialExecutorService("foo");
        sequentialExecutor.execute(() -> {
            long runDurationMillis = 100L;
            long startTimeMillis = System.currentTimeMillis();
            await().until(() -> System.currentTimeMillis() - startTimeMillis >= runDurationMillis);
        });

        int errors = 0;
        try {
            sequentialExecutor.shutdown();
        } catch (UnsupportedOperationException ex) {
            errors++;
            try {
                sequentialExecutor.shutdownNow();
            } catch (UnsupportedOperationException ex2) {
                errors++;
            }
        }
        assertEquals(2, errors);
    }
}
