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
package conseq4j;

import lombok.ToString;
import lombok.extern.java.Log;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * @author Qingtian Wang
 */
@ToString @Log public abstract class SpyingTaskTemplate {

    protected final UUID id = UUID.randomUUID();
    protected final Duration taskRunDuration;
    protected final SpyingTaskPayload taskData;

    public SpyingTaskTemplate(SpyingTaskPayload taskData, Duration taskRunDuration) {
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
        this.taskData.setRunThreadName(Thread.currentThread().getName());
        TimeUnit.MILLISECONDS.sleep(threadRunDurationMillis());
        this.taskData.setRunEndTimeNanos(System.nanoTime());
        log.log(Level.FINE, () -> "task " + this.id + " completed with data " + this.getTaskData());
    }
}
