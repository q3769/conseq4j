package conseq4j;

/*
 * The MIT License
 * Copyright 2021 QingtianWang.
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


import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author QingtianWang
 */
public abstract class AbstractSpyingConseqable {

    private static final Logger LOG = Logger.getLogger(AbstractSpyingConseqable.class.getName());

    protected final UUID id = UUID.randomUUID();
    protected final Duration taskRunDuration;
    protected final SpyingTaskPayload taskData;

    public AbstractSpyingConseqable(SpyingTaskPayload taskData, Duration taskRunDuration) {
        this.taskRunDuration = taskRunDuration;
        this.taskData = taskData;
    }

    public Duration getTaskRunDuration() {
        return taskRunDuration;
    }

    public SpyingTaskPayload getTaskData() {
        return taskData;
    }

    public UUID getId() {
        return id;
    }

    protected long threadRunDurationMillis() {
        return this.taskRunDuration.getSeconds() * 1000;
    }

    protected void doRun() throws InterruptedException {
        this.taskData.setRunStartTimeNanos(System.nanoTime());
        this.taskData.setRunThreadName(Thread.currentThread()
                .getName());
        Thread.sleep(threadRunDurationMillis());
        this.taskData.setRunEndTimeNanos(System.nanoTime());
        LOG.log(Level.INFO, "Task : {0} completed with data : {1}", new Object[] { this.getId(), this.getTaskData() });
    }
}
