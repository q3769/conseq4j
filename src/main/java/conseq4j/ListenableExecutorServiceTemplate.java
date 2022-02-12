/*
 * The MIT License
 * Copyright 2022 Qingitan Wang.
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
 * @author Qingitan Wang
 */
abstract class ListenableExecutorServiceTemplate extends ThreadPoolExecutor implements ListenableExecutorService {

    protected final List<ExecutorServiceListener> executorServiceListeners = Collections.synchronizedList(
            new ArrayList<>());

    protected ListenableExecutorServiceTemplate(int corePoolSize, int maximumPoolSize, long keepAliveTime,
            TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    abstract void doBeforeExecute(Thread t, Runnable r);

    abstract void doAfterExecute(Runnable r, Throwable t);

    protected void notifyListenersBeforeExecute(Thread t, Runnable r) {
        synchronized (executorServiceListeners) {
            if (executorServiceListeners.isEmpty()) {
                return;
            }
            executorServiceListeners.forEach(executorServiceListener -> executorServiceListener.beforeEachExecute(t,
                    r));
        }
    }

    protected void notifyListenersAfterExecute(Runnable r, Throwable t) {
        synchronized (executorServiceListeners) {
            if (executorServiceListeners.isEmpty()) {
                return;
            }
            executorServiceListeners.forEach(executorServiceListener -> executorServiceListener.afterEachExecute(r, t));
        }
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        doBeforeExecute(t, r);
        super.beforeExecute(t, r);
        notifyListenersBeforeExecute(t, r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        doAfterExecute(r, t);
        notifyListenersAfterExecute(r, t);
    }

    @Override
    public void addListener(ExecutorServiceListener executorServiceListener) {
        executorServiceListeners.add(executorServiceListener);
    }

    @Override
    public void clearListeners() {
        this.executorServiceListeners.clear();
    }

}
