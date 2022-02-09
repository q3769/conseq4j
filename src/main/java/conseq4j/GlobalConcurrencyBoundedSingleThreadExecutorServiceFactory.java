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

import java.util.concurrent.Semaphore;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 * @author Qingitan Wang
 */
class GlobalConcurrencyBoundedSingleThreadExecutorServiceFactory extends BasePooledObjectFactory<
        GlobalConcurrencyBoundedRunningTasksCountingExecutorService> {

    private final Semaphore globalConcurrencySemaphore;
    private final int taskQueueCapacity;

    public GlobalConcurrencyBoundedSingleThreadExecutorServiceFactory(Semaphore globalConcurrencySemaphore,
            int taskQueueCapacity) {
        this.globalConcurrencySemaphore = globalConcurrencySemaphore;
        this.taskQueueCapacity = taskQueueCapacity;
    }

    @Override
    public GlobalConcurrencyBoundedRunningTasksCountingExecutorService create() throws Exception {
        return GlobalConcurrencyBoundedRunningTasksCountingExecutorService.newListenableSingleThreadExecutorService(
                globalConcurrencySemaphore, taskQueueCapacity);
    }

    @Override
    public PooledObject<GlobalConcurrencyBoundedRunningTasksCountingExecutorService> wrap(
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService t) {
        return new DefaultPooledObject<>(t);
    }

    @Override
    public void activateObject(PooledObject<GlobalConcurrencyBoundedRunningTasksCountingExecutorService> p)
            throws Exception {
        super.activateObject(p);
        p.getObject()
                .clearListeners();
    }

}
