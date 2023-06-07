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

package conseq4j.util;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 *
 */
public class MoreRejectedExecutionHandlers {
    private MoreRejectedExecutionHandlers() {
    }

    /**
     * @return a {@link RejectedExecutionHandler} that uses/blocks the caller thread to retry the rejected task until it
     *         is accepted, or drop the task if the executor has been shut down.
     */
    public static RejectedExecutionHandler blockingRetryPolicy() {
        return blockingRetryPolicy(null);
    }

    /**
     * @param retryPeriod
     *         Duration to pause/block the calling thread before each retry
     * @return a {@link RejectedExecutionHandler} that uses/blocks the caller thread to retry the rejected task until it
     *         is accepted, or drop the task if the executor has been shut down.
     */
    public static RejectedExecutionHandler blockingRetryPolicy(Duration retryPeriod) {
        return new BlockingRetryPolicy(retryPeriod);
    }

    /**
     *
     */
    private static class BlockingRetryPolicy implements RejectedExecutionHandler {
        private static final Duration DEFAULT_RETRY_PERIOD = Duration.ofMillis(10);
        private final ConditionFactory await = Awaitility.await().forever();
        private final Duration retryPeriod;

        private BlockingRetryPolicy(Duration retryPeriod) {
            this.retryPeriod = retryPeriod == null ? DEFAULT_RETRY_PERIOD : retryPeriod;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                return;
            }
            await.with().pollDelay(retryPeriod).until(() -> true);
            executor.execute(r);
        }
    }
}
