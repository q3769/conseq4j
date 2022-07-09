/*
 * MIT License
 *
 * Copyright (c) 2022. Qingtian Wang
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

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author q3769
 */
class ConseqTest {

    @Test void shouldReturnSameExecutorOnSameName() {
        UUID sequenceKey = UUID.randomUUID();
        Conseq target = Conseq.newBuilder().build();

        Executor e1 = target.getSequentialExecutor(sequenceKey);
        Executor e2 = target.getSequentialExecutor(sequenceKey);

        assertSame(e1, e2);
    }

    @Test void errorOnNonPositiveConcurrency() {
        int errors = 0;
        try {
            Conseq.newBuilder().globalConcurrency(0);
        } catch (IllegalArgumentException e) {
            errors++;
        }
        try {
            Conseq.newBuilder().globalConcurrency(-999);
        } catch (IllegalArgumentException e) {
            errors++;
        }
        if (errors == 2)
            return;
        fail();
    }

    @Test void errorOnNonPositiveTaskQueueSize() {
        int errors = 0;
        try {
            Conseq.newBuilder().executorTaskQueueSize(0);
        } catch (IllegalArgumentException e) {
            errors++;
        }
        try {
            Conseq.newBuilder().executorTaskQueueSize(-999);
        } catch (IllegalArgumentException e) {
            errors++;
        }
        if (errors == 2)
            return;
        fail();
    }

    @Test void irrevocable() {
        Conseq target = Conseq.newBuilder().build();
        final ExecutorService sequentialExecutor = target.getSequentialExecutor("foo");
        sequentialExecutor.execute(() -> {
            try {
                TimeUnit.SECONDS.sleep(1L);
            } catch (InterruptedException ex) {
                Logger.getLogger(ConseqTest.class.getName()).log(Level.SEVERE, null, ex);
                Thread.currentThread().interrupt();
            }
        });

        try {
            sequentialExecutor.shutdown();
        } catch (UnsupportedOperationException ex) {
            try {
                sequentialExecutor.shutdownNow();
            } catch (UnsupportedOperationException ex2) {
                return;
            }
        }
        fail();
    }
}
