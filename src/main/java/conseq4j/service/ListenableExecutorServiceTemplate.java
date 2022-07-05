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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Qingtian Wang
 */
abstract class ListenableExecutorServiceTemplate extends ThreadPoolExecutor implements ExecutionListenable {

    protected final List<ExecutionListener> executionListeners = Collections.synchronizedList(new ArrayList<>());

    /**
     * <p>Constructor for ListenableExecutorServiceTemplate.</p>
     *
     * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
     * @param corePoolSize a int.
     * @param maximumPoolSize a int.
     * @param keepAliveTime a long.
     * @param unit a {@link java.util.concurrent.TimeUnit} object.
     * @param workQueue a {@link java.util.concurrent.BlockingQueue} object.
     */
    protected ListenableExecutorServiceTemplate(int corePoolSize, int maximumPoolSize, long keepAliveTime,
            TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    abstract void doBeforeExecute(Thread taskExecutionThread, Runnable task);

    abstract void doAfterExecute(Runnable task, Throwable taskExecutionError);

    /**
     * <p>Notify all listeners before each execute.</p>
     *
     * @see ThreadPoolExecutor#beforeExecute(Thread, Runnable)
     * @param taskExecutionThread a {@link java.lang.Thread} object.
     * @param task a {@link java.lang.Runnable} object.
     */
    protected void notifyListenersBeforeExecute(Thread taskExecutionThread, Runnable task) {
        synchronized (executionListeners) {
            if (executionListeners.isEmpty()) {
                return;
            }
            executionListeners.forEach(
                    executionListener -> executionListener.beforeEachExecute(taskExecutionThread, task));
        }
    }

    /**
     * <p>Notify all listeners after each execute.</p>
     *
     * @see ThreadPoolExecutor#afterExecute(Runnable, Throwable)
     * @param task a {@link java.lang.Runnable} object.
     * @param taskExecutionError a {@link java.lang.Throwable} object.
     */
    protected void notifyListenersAfterExecute(Runnable task, Throwable taskExecutionError) {
        synchronized (executionListeners) {
            if (executionListeners.isEmpty()) {
                return;
            }
            executionListeners.forEach(
                    executionListener -> executionListener.afterEachExecute(task, taskExecutionError));
        }
    }

    /** {@inheritDoc} */
    @Override protected void beforeExecute(Thread t, Runnable r) {
        doBeforeExecute(t, r);
        super.beforeExecute(t, r);
        notifyListenersBeforeExecute(t, r);
    }

    /** {@inheritDoc} */
    @Override protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        doAfterExecute(r, t);
        notifyListenersAfterExecute(r, t);
    }

    /** {@inheritDoc} */
    @Override public void addListener(ExecutionListener executionListener) {
        executionListeners.add(executionListener);
    }

    /** {@inheritDoc} */
    @Override public void clearListeners() {
        this.executionListeners.clear();
    }

}
