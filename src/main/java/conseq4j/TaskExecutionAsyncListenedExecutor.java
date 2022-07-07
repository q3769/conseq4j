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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Qingtian Wang
 */
class TaskExecutionAsyncListenedExecutor extends TaskExecutionListenedExecutor {

    /**
     * Trying not to compete with executors for threading resource, using one single thread for maintenance/clean up.
     */
    private final Executor listenerThreadPool = Executors.newSingleThreadExecutor();

    TaskExecutionAsyncListenedExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    /**
     * {@inheritDoc}
     */
    @Override protected void notifyListenersBeforeExecute(Thread taskExecutionThread, Runnable task) {
        listenerThreadPool.execute(() -> super.notifyListenersBeforeExecute(taskExecutionThread, task));
    }

    /**
     * {@inheritDoc}
     */
    @Override protected void notifyListenersAfterExecute(Runnable task, Throwable taskExecutionError) {
        listenerThreadPool.execute(() -> super.notifyListenersAfterExecute(task, taskExecutionError));
    }
}
