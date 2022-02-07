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
class ListenableSingleThreadExecutorServiceFactory extends BasePooledObjectFactory<
        ListenableRunningTasksCountingExecutorService> {

    private final Semaphore globalConcurrencySemaphore;
    private final int taskQueueCapacity;

    public ListenableSingleThreadExecutorServiceFactory(Semaphore concurrencySemaphore, int taskQueueCapacity) {
        this.globalConcurrencySemaphore = concurrencySemaphore;
        this.taskQueueCapacity = taskQueueCapacity;
    }

    @Override
    public ListenableRunningTasksCountingExecutorService create() throws Exception {
        return ListenableRunningTasksCountingExecutorService.newListenableSingleThreadExecutorService(taskQueueCapacity,
                globalConcurrencySemaphore);
    }

    @Override
    public PooledObject<ListenableRunningTasksCountingExecutorService> wrap(
            ListenableRunningTasksCountingExecutorService t) {
        return new DefaultPooledObject<>(t);
    }

}
