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
package conseq4j.summon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;

/**
 * @author q3769
 */
class ConfigTest {

    @Test
    void errorOnNonPositiveConcurrency() {
        int errors = 0;

        try {
            ConseqServiceFactory.instance(0);
        } catch (IllegalArgumentException e) {
            errors++;
        }
        try {
            ConseqServiceFactory.instance(-999);
        } catch (IllegalArgumentException e) {
            errors++;
        }

        assertEquals(2, errors);
    }

    @Test
    void shouldReturnSameExecutorOnSameName() {
        ConseqServiceFactory sut = ConseqServiceFactory.instance();
        UUID sameSequenceKey = UUID.randomUUID();

        Executor e = sut.getExecutorService(sameSequenceKey);
        int additionalSummonTimes = 1 + new Random().nextInt(100);
        for (int i = 0; i < additionalSummonTimes; i++) {
            assertSame(e, sut.getExecutorService(sameSequenceKey));
        }
    }

    @Test
    void shutdownUnsupported() {
        ConseqServiceFactory target = ConseqServiceFactory.instance();
        final ExecutorService sequentialExecutor = target.getExecutorService("testSequenceKey");
        int errors = 0;

        try {
            sequentialExecutor.shutdown();
        } catch (UnsupportedOperationException e) {
            errors++;
        }
        try {
            sequentialExecutor.shutdownNow();
        } catch (UnsupportedOperationException e) {
            errors++;
        }

        assertEquals(2, errors);
    }
}
