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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class SerialExecutorService implements ExecutorService {

    private final ExecutorService workService;
    private final Lock lock;

    SerialExecutorService(ExecutorService workService, boolean fair) {
        this.workService = workService;
        this.lock = new ReentrantLock(fair);
    }

    @Override public void shutdown() {
        lock.lock();
        try {
            workService.shutdown();
        } finally {
            lock.unlock();
        }
    }

    @Override public List<Runnable> shutdownNow() {
        lock.lock();
        try {
            return workService.shutdownNow();
        } finally {
            lock.unlock();
        }
    }

    @Override public boolean isShutdown() {
        lock.lock();
        try {
            return workService.isShutdown();
        } finally {
            lock.unlock();
        }
    }

    @Override public boolean isTerminated() {
        lock.lock();
        try {
            return workService.isTerminated();
        } finally {
            lock.unlock();
        }
    }

    @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            return workService.awaitTermination(timeout, unit);
        } finally {
            lock.unlock();
        }
    }

    @Override public <T> Future<T> submit(Callable<T> task) {
        try {
            return workService.submit(task);
        } finally {
            lock.unlock();
        }
    }

    @Override public <T> Future<T> submit(Runnable task, T result) {
        try {
            return workService.submit(task, result);
        } finally {
            lock.unlock();
        }
    }

    @Override public Future<?> submit(Runnable task) {
        try {
            return workService.submit(task);
        } finally {
            lock.unlock();
        }
    }

    @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        try {
            return workService.invokeAll(tasks);
        } finally {
            lock.unlock();
        }
    }

    @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        try {
            return workService.invokeAll(tasks, timeout, unit);
        } finally {
            lock.unlock();
        }
    }

    @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        try {
            return workService.invokeAny(tasks);
        } finally {
            lock.unlock();
        }
    }

    @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return workService.invokeAny(tasks, timeout, unit);
        } finally {
            lock.unlock();
        }
    }

    @Override public void execute(Runnable command) {
        try {
            workService.execute(command);
        } finally {
            lock.unlock();
        }
    }
}