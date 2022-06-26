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
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import java.util.Objects;

/**
 * @author Qingitan Wang
 */
@Log @ToString(callSuper = true) class PooledSingleThreadExecutorFactory
        extends BasePooledObjectFactory<RunningTasksCountingExecutorService> {

    private final int executorTaskQueueCapacity;

    public PooledSingleThreadExecutorFactory(int executorTaskQueueCapacity) {
        this.executorTaskQueueCapacity = executorTaskQueueCapacity;
    }

    @Override public RunningTasksCountingExecutorService create() {
        return RunningTasksCountingExecutorService.newSingleThreadInstance(executorTaskQueueCapacity);
    }

    @Override public PooledObject<RunningTasksCountingExecutorService> wrap(RunningTasksCountingExecutorService t) {
        return new DefaultPooledObject<>(t);
    }

    @Override public void activateObject(PooledObject<RunningTasksCountingExecutorService> p) throws Exception {
        super.activateObject(p);
        Objects.requireNonNull(p.getObject()).clearListeners();
    }

    @Override public boolean validateObject(PooledObject<RunningTasksCountingExecutorService> p) {
        RunningTasksCountingExecutorService executorService =
                Objects.requireNonNull(p.getObject(), "unexpected NULL executor being returned to pool");
        if (executorService.isShutdown()) {
            log.warning("executor " + executorService + " already shut down, thus being dropped from pool");
            return false;
        }
        return true;
    }
}
