package conseq4j;

/*
 * The MIT License
 * Copyright 2021 Qingtian Wang.
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


import com.google.common.util.concurrent.ListeningExecutorService;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * @author q3769
 */
public class ConseqTest {

    private static final Logger LOG = Logger.getLogger(ConseqTest.class.getName());

    private static ConsistentHasher stubHasher() {
        return new ConsistentHasher() {

            @Override
            public int getTotalBuckets() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                               // choose Tools | Templates.
            }

            @Override
            public int hashToBucket(CharSequence sequenceKey) {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                               // choose Tools | Templates.
            }

            @Override
            public int hashToBucket(Integer sequenceKey) {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                               // choose Tools | Templates.
            }

            @Override
            public int hashToBucket(Long sequenceKey) {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                               // choose Tools | Templates.
            }

            @Override
            public int hashToBucket(UUID sequenceKey) {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                               // choose Tools | Templates.
            }

            @Override
            public int hashToBucket(byte[] sequenceKey) {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                               // choose Tools | Templates.
            }

            @Override
            public int hashToBucket(ByteBuffer sequenceKey) {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                               // choose Tools | Templates.
            }
        };
    }

    @Test
    public void shouldHonorMaxExecutors() {
        int stubConcurrency = 5;
        Conseq target = Conseq.newBuilder()
                .maxConcurrentExecutors(stubConcurrency)
                .build();
        assertEquals(stubConcurrency, target.getMaxConcurrentExecutors());
    }

    @Test
    public void shouldReturnSameExcecutorOnSameName() {
        UUID sequenceKey = UUID.randomUUID();
        Conseq target = Conseq.newBuilder()
                .build();

        Executor e1 = target.getSequentialExecutor(sequenceKey);
        Executor e2 = target.getSequentialExecutor(sequenceKey);

        assertSame(e1, e2);
    }

    @Test
    public void cannotSetBothCustomizedHasherAndMaxExecutors() {
        final int stubConcurrency = 999;
        try {
            Conseq.newBuilder()
                    .maxConcurrentExecutors(stubConcurrency)
                    .consistentHasher(stubHasher())
                    .build();
        } catch (IllegalArgumentException ex) {
            LOG.info("expected");
            return;
        }
        fail();
    }

    @Test
    public void irrevocable() {
        Conseq target = Conseq.newBuilder()
                .build();
        final ListeningExecutorService sequentialExecutor = target.getSequentialExecutor("foo");
        sequentialExecutor.execute(() -> {
            try {
                TimeUnit.SECONDS.sleep(1L);
            } catch (InterruptedException ex) {
                Logger.getLogger(ConseqTest.class.getName())
                        .log(Level.SEVERE, null, ex);
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
