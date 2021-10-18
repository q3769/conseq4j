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
import java.util.UUID;

/**
 *
 * @author QingtianWang
 */
public abstract class AbstractSpyingConseqable implements SpyingConseqable {

    protected final UUID id = UUID.randomUUID();
    protected final Object correlationId;
    protected Long runStartNanos;
    protected Long runEndNanos;
    protected final Duration taskRunDuration;
    protected String runThreadName;

    public AbstractSpyingConseqable(Object correlationId, Duration taskRunDuration) {
        this.correlationId = correlationId;
        this.taskRunDuration = taskRunDuration;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public String getRunThreadName() {
        return runThreadName;
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

    public Duration getTaskRunDuration() {
        return this.taskRunDuration;
    }

    @Override
    public Object getSequenceKey() {
        return this.getCorrelationId();
    }

    protected long threadRunDurationMillis() {
        return this.taskRunDuration.getSeconds() * 1000;
    }
}
