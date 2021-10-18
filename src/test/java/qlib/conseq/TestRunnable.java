/*
 * The MIT License
 *
 * Copyright 2021 QingtianWang.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package qlib.conseq;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author q3769
 */
class TestRunnable implements Runnable, TestConseqable {

    private static final Logger LOG = Logger.getLogger(TestRunnable.class.getName());
    private final UUID id = UUID.randomUUID();
    private final Object correlationId;
    private Long runStartNanos;
    private Long runEndNanos;
    private final Duration minRunDuration;
    private String runTreadName;

    public TestRunnable(Object correlationId, Duration minRunTime) {
        this.correlationId = correlationId;
        this.minRunDuration = minRunTime;
    }

    public Object getCorrelationId() {
        return correlationId;
    }

    public Long getRunStartNanos() {
        return runStartNanos;
    }

    public Long getRunEndNanos() {
        return runEndNanos;
    }

    public Duration getMinRunDuration() {
        return minRunDuration;
    }

    @Override
    public void run() {
        this.runStartNanos = System.nanoTime();
        this.runTreadName = Thread.currentThread().getName();
        try {
            Thread.sleep(this.minRunDuration.get(ChronoUnit.SECONDS) * 1000);
        } catch (InterruptedException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        this.runEndNanos = System.nanoTime();
        LOG.log(Level.INFO, "Task : {0} with correlation ID : {1} executed by thread : {2}", new Object[]{this.id, this.correlationId, this.getRunThreadName()});
    }

    @Override
    public Object getSequenceKey() {
        return this.getCorrelationId();
    }

    @Override
    public String getRunThreadName() {
        return this.runTreadName;
    }

}
