/*
 * The MIT License Copyright 2022 QingtianWang. Permission is hereby granted, free of charge, to any
 * person obtaining a copy of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions: The
 * above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package conseq4j.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.java.Log;

/**
 * @author Qingtian Wang
 */
@Log
@ToString
@Getter
public class SpyingTask implements Runnable, Callable<SpyingTask> {

    public static final Random RANDOM = new Random();
    public static final int MAX_RUN_TIME_MILLIS = 40;

    private static int randomIntInclusive(int min, int max) {
        return min + RANDOM.nextInt(max - min + 1);
    }

    final Integer scheduledSequence;
    final int targetRunTimeMillis;

    Instant runStart;
    Instant runEnd;
    String runThreadName;

    public SpyingTask(Integer scheduledSequence) {
        this.scheduledSequence = scheduledSequence;
        this.targetRunTimeMillis = randomIntInclusive(1, MAX_RUN_TIME_MILLIS);
    }

    @Override
    public void run() {
        this.runStart = Instant.now();
        this.runThreadName = Thread.currentThread()
                .getName();
        try {
            TimeUnit.MILLISECONDS.sleep(targetRunTimeMillis);
        } catch (InterruptedException ex) {
            log.log(Level.WARNING, "Interrupted while " + this + " was trying to sleep for " + Duration.ofMillis(
                    targetRunTimeMillis), ex);
            Thread.currentThread()
                    .interrupt();
        }
        this.runEnd = Instant.now();
        log.log(Level.FINEST, () -> "End running: " + this + ", took " + Duration.between(runStart, runEnd)
                .toMillis() + " millis");
    }

    @Override
    public SpyingTask call() throws Exception {
        this.run();
        return this;
    }
}
