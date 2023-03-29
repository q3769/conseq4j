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

import lombok.ToString;
import lombok.experimental.Delegate;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * An {@link ExecutorService} that doesn't support shut down.
 *
 * @author Qingtian Wang
 */
@ToString
final class ShutdownDisabledExecutorService implements ExecutorService {

    private static final String SHUTDOWN_UNSUPPORTED_MESSAGE =
            "Shutdown not supported: Tasks being executed by this service may be from unrelated owners; shutdown features are disabled to prevent undesired task cancellation on other owners";

    @Delegate(excludes = ShutdownOperations.class) private final ExecutorService delegate;

    /**
     * @param delegate
     *         the delegate {@link ExecutorService} to run the submitted task(s).
     */
    public ShutdownDisabledExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    /**
     * Does not allow shutdown because the same executor could be running task(s) for different sequence keys, albeit in
     * sequential/FIFO order. Shutdown of the executor would stop executions for all tasks, which is disallowed to
     * prevent undesired task-execute coordination.
     */
    @Override
    public void shutdown() {
        throw new UnsupportedOperationException(SHUTDOWN_UNSUPPORTED_MESSAGE);
    }

    /**
     * @see #shutdown()
     */
    @Override
    public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException(SHUTDOWN_UNSUPPORTED_MESSAGE);
    }

    /**
     * Methods that require complete overriding instead of delegation/decoration
     */
    private interface ShutdownOperations {
        void shutdown();

        List<Runnable> shutdownNow();
    }
}
