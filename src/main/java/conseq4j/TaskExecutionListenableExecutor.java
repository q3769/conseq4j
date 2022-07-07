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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Qingtian Wang
 */
class TaskExecutionListenableExecutor extends ThreadPoolExecutor implements ExecutionListenable {

    protected final List<TaskExecutionListener> taskExecutionListeners =
            Collections.synchronizedList(new ArrayList<>());

    /**
     * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
     */
    protected TaskExecutionListenableExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    /**
     * Called before each execution, prior to notifying listener(s). Default to no-op.
     *
     * @see ThreadPoolExecutor#beforeExecute(Thread, Runnable)
     */
    protected void doBeforeExecute(Thread taskExecutionThread, Runnable task) {
        // no-op
    }

    /**
     * Called after each execution, prior to notifying listener(s). Default to no-op.
     *
     * @see ThreadPoolExecutor#afterExecute(Runnable, Throwable)
     */
    protected void doAfterExecute(Runnable task, Throwable taskExecutionError) {
        // no-op
    }

    /**
     * @see ThreadPoolExecutor#beforeExecute(Thread, Runnable)
     */
    protected void notifyListenersBeforeExecute(Thread taskExecutionThread, Runnable task) {
        synchronized (taskExecutionListeners) {
            if (taskExecutionListeners.isEmpty()) {
                return;
            }
            taskExecutionListeners.forEach(
                    taskExecutionListener -> taskExecutionListener.beforeExecute(taskExecutionThread, task));
        }
    }

    /**
     * @see ThreadPoolExecutor#afterExecute(Runnable, Throwable)
     */
    protected void notifyListenersAfterExecute(Runnable task, Throwable taskExecutionError) {
        synchronized (taskExecutionListeners) {
            if (taskExecutionListeners.isEmpty()) {
                return;
            }
            taskExecutionListeners.forEach(
                    taskExecutionListener -> taskExecutionListener.afterExecute(task, taskExecutionError));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override protected final void beforeExecute(Thread t, Runnable r) {
        doBeforeExecute(t, r);
        super.beforeExecute(t, r);
        notifyListenersBeforeExecute(t, r);
    }

    /**
     * {@inheritDoc}
     */
    @Override protected final void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        doAfterExecute(r, t);
        notifyListenersAfterExecute(r, t);
    }

    @Override public void addListener(TaskExecutionListener taskExecutionListener) {
        taskExecutionListeners.add(taskExecutionListener);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void clearListeners() {
        this.taskExecutionListeners.clear();
    }

}
