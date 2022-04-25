/*
 * The MIT License
 * Copyright 2022 QingtianWang.
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
 * @author Qingtian Wang
 */
@ToString final class IrrevocableExecutorService implements ExecutorService {

    private static final String SHUTDOWN_UNSUPPORTED_MESSAGE =
            "Shutdown not supported. Tasks being executed by this service may be from unrelated owners; shutdown features are disabled to protect against undesired task cancalation on other owners.";

    private final ExecutorService executorService;

    public IrrevocableExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override public void shutdown() {
        throw new UnsupportedOperationException(SHUTDOWN_UNSUPPORTED_MESSAGE);
    }

    @Override public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException(SHUTDOWN_UNSUPPORTED_MESSAGE);
    }

    @Override public boolean isShutdown() {
        return this.executorService.isShutdown();
    }

    @Override public boolean isTerminated() {
        return this.executorService.isTerminated();
    }

    @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return this.executorService.awaitTermination(timeout, unit);
    }

    @Override public <T> Future<T> submit(Callable<T> task) {
        return this.executorService.submit(task);
    }

    @Override public <T> Future<T> submit(Runnable task, T result) {
        return this.executorService.submit(task, result);
    }

    @Override public Future<?> submit(Runnable task) {
        return this.executorService.submit(task);
    }

    @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return this.executorService.invokeAll(tasks);
    }

    @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return this.executorService.invokeAll(tasks, timeout, unit);
    }

    @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return this.executorService.invokeAny(tasks);
    }

    @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return this.executorService.invokeAny(tasks, timeout, unit);
    }

    @Override public void execute(Runnable command) {
        this.executorService.execute(command);
    }

}
