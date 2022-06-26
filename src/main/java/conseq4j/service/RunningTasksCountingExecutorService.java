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

import lombok.ToString;
import lombok.extern.java.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Qingitan Wang
 */
@Log @ToString(callSuper = true) class RunningTasksCountingExecutorService extends AsyncListenableExecutorService {

    private final AtomicInteger runningTaskCount = new AtomicInteger();

    RunningTasksCountingExecutorService(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    static RunningTasksCountingExecutorService newSingleThreadInstance(int taskQueueSize) {
        return new RunningTasksCountingExecutorService(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(taskQueueSize));
    }

    public int getRunningTaskCount() {
        return runningTaskCount.get();
    }

    @Override void doBeforeExecute(Thread taskExecutionThread, Runnable task) {
        runningTaskCount.incrementAndGet();
    }

    @Override void doAfterExecute(Runnable task, Throwable taskExecutionError) {
        runningTaskCount.decrementAndGet();
    }
}
