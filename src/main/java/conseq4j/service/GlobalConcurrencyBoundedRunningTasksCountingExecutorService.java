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
package conseq4j.service;

import lombok.extern.java.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * @author Qingitan Wang
 */
@Log class GlobalConcurrencyBoundedRunningTasksCountingExecutorService extends AsyncListenableExecutorService {

    static final int DEFAULT_TASK_QUEUE_SIZE = Integer.MAX_VALUE;
    private final AtomicInteger runningTaskCount = new AtomicInteger();
    private final Semaphore globalConcurrencySemaphore;

    GlobalConcurrencyBoundedRunningTasksCountingExecutorService(int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, Semaphore concurrencySemaphore) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        this.globalConcurrencySemaphore = concurrencySemaphore;
    }

    static GlobalConcurrencyBoundedRunningTasksCountingExecutorService newSingleThreadInstance(
            Semaphore globalConcurrencySemaphore) {
        return newSingleThreadInstance(globalConcurrencySemaphore, DEFAULT_TASK_QUEUE_SIZE);
    }

    static GlobalConcurrencyBoundedRunningTasksCountingExecutorService newSingleThreadInstance(
            Semaphore concurrencySemaphore, int taskQueueSize) {
        return new GlobalConcurrencyBoundedRunningTasksCountingExecutorService(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(taskQueueSize), concurrencySemaphore);
    }

    @Override public String toString() {
        return "GlobalConcurrencyBoundedRunningTasksCountingExecutorService{" + "runningTaskCount=" + runningTaskCount
                + ", globalConcurrencySemaphore=" + globalConcurrencySemaphore + ", executionListeners="
                + executionListeners + ", taskQueueDepth=" + getQueue().size() + ", shutdown=" + isShutdown() + '}';
    }

    public int getRunningTaskCount() {
        return runningTaskCount.get();
    }

    @Override void doBeforeExecute(Thread t, Runnable r) {
        try {
            globalConcurrencySemaphore.acquire();
        } catch (InterruptedException ex) {
            log.log(Level.SEVERE,
                    "Interrupted while acquiring concurrency semaphore in Thread " + t + " for Runnable " + r, ex);
            Thread.currentThread().interrupt();
        }
        runningTaskCount.incrementAndGet();
    }

    @Override void doAfterExecute(Runnable r, Throwable t) {
        runningTaskCount.decrementAndGet();
        globalConcurrencySemaphore.release();
    }
}
