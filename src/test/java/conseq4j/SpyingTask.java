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

import elf4j.Logger;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.Callable;

import static org.awaitility.Awaitility.await;

/**
 * @author Qingtian Wang
 */
@ToString
@Getter
public class SpyingTask implements Runnable, Callable<SpyingTask> {
    public static final int MAX_RUN_TIME_MILLIS = 20;
    public static final Random RANDOM = new Random();
    public static final int UNSET_TIME_STAMP = 0;
    private static final Logger LOGGER = Logger.instance(SpyingTask.class);
    final Integer scheduledSequence;
    final long targetRunDurationMillis;
    volatile long runTimeStartMillis = UNSET_TIME_STAMP;
    volatile long runTimeEndMillis = UNSET_TIME_STAMP;
    String runThreadName;

    public SpyingTask(Integer scheduledSequence) {
        this.scheduledSequence = scheduledSequence;
        this.targetRunDurationMillis = randomIntInclusive();
    }

    private static int randomIntInclusive() {
        return 1 + RANDOM.nextInt(SpyingTask.MAX_RUN_TIME_MILLIS);
    }

    @Override
    public void run() {
        this.runTimeStartMillis = System.currentTimeMillis();
        this.runThreadName = Thread.currentThread().getName();
        await().with()
                .pollInterval(Duration.ofMillis(1))
                .until(() -> (System.currentTimeMillis() - this.runTimeStartMillis) >= this.targetRunDurationMillis);
        this.runTimeEndMillis = System.currentTimeMillis();
        LOGGER.atTrace()
                .log("run duration: [{}] on [{}]", Duration.ofMillis(runTimeEndMillis - runTimeStartMillis), this);
    }

    @Override
    public SpyingTask call() {
        this.run();
        return this;
    }
}
