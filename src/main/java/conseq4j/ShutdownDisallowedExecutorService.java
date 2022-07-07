/*
 * The MIT License
 * Copyright 2022 Qingtian Wang.
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

package conseq4j;

import lombok.ToString;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * An {@code ExecutorService} that does not allow shutdown of task(s) once submitted.
 *
 * @author Qingtian Wang
 */
@ToString final class ShutdownDisallowedExecutorService implements ExecutorService {

    private static final String SHUTDOWN_UNSUPPORTED_MESSAGE =
            "Shutdown not supported: tasks being executed by this service may be from unrelated owners; shutdown features are disabled to prevent undesired task cancellation on other owners.";

    private final ExecutorService executorService;

    /**
     * <p>Constructor for ShutdownDisallowedExecutorService.</p>
     *
     * @param executorService the delegate {@link java.util.concurrent.ExecutorService} to run the submitted task(s).
     */
    public ShutdownDisallowedExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * Does not allow shutdown because the same executor could be running task(s) for different sequence keys, albeit in
     * sequential/FIFO order. Shutdown of the executor would stop executions for all tasks, which is disallowed to
     * prevent undesired task-execute coordination.
     */
    @Override public void shutdown() {
        throw new UnsupportedOperationException(SHUTDOWN_UNSUPPORTED_MESSAGE);
    }

    /**
     * @see this#shutdown()
     */
    @Override public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException(SHUTDOWN_UNSUPPORTED_MESSAGE);
    }

    /**
     * {@inheritDoc}
     */
    @Override public boolean isShutdown() {
        return this.executorService.isShutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override public boolean isTerminated() {
        return this.executorService.isTerminated();
    }

    /**
     * {@inheritDoc}
     */
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return this.executorService.awaitTermination(timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override public <T> Future<T> submit(Callable<T> task) {
        return this.executorService.submit(task);
    }

    /**
     * {@inheritDoc}
     */
    @Override public <T> Future<T> submit(Runnable task, T result) {
        return this.executorService.submit(task, result);
    }

    /**
     * {@inheritDoc}
     */
    @Override public Future<?> submit(Runnable task) {
        return this.executorService.submit(task);
    }

    /**
     * {@inheritDoc}
     */
    @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return this.executorService.invokeAll(tasks);
    }

    /**
     * {@inheritDoc}
     */
    @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return this.executorService.invokeAll(tasks, timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return this.executorService.invokeAny(tasks);
    }

    /**
     * {@inheritDoc}
     */
    @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return this.executorService.invokeAny(tasks, timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void execute(Runnable command) {
        this.executorService.execute(command);
    }

}
