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
import org.apache.commons.pool2.ObjectPool;

import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * @author Qingitan Wang
 */
@Log @ToString class SweepingExecutorServiceListener implements ExecutionListener {

    private final Object sequenceKey;
    private final ConcurrentMap<Object, RunningTaskCountingExecutorService> servicingSequentialExecutors;
    private final ObjectPool<RunningTaskCountingExecutorService> executorPool;

    public SweepingExecutorServiceListener(Object sequenceKey,
            ConcurrentMap<Object, RunningTaskCountingExecutorService> servicingSequentialExecutors,
            ObjectPool<RunningTaskCountingExecutorService> executorPool) {
        this.sequenceKey = sequenceKey;
        this.servicingSequentialExecutors = servicingSequentialExecutors;
        this.executorPool = executorPool;
    }

    @Override public void beforeEachExecute(Thread taskExecutionThread, Runnable task) {
        // no-op
    }

    @Override public void afterEachExecute(Runnable task, Throwable taskExecutionError) {
        sweepOrKeepExecutorInService(task, taskExecutionError);
    }

    private void sweepOrKeepExecutorInService(Runnable task, Throwable taskExecutionError) {
        log.log(Level.FINER,
                () -> "start sweeping-check executor after servicing task " + task + " with execution error "
                        + taskExecutionError + ", using " + this);
        servicingSequentialExecutors.compute(sequenceKey,
                (presentSequenceKey, presentExecutor) -> sweepingExecutor(presentExecutor, taskExecutionError) ? null :
                        presentExecutor);
        log.log(Level.FINER, () -> "done sweeping-check executor using " + this);
    }

    private boolean sweepingExecutor(RunningTaskCountingExecutorService presentExecutor, Throwable executionError) {
        if (presentExecutor == null) {
            log.log(Level.FINE, () -> "executor already swept off of servicing map by another listener than " + this);
            return true;
        }
        final int runningTaskCount = presentExecutor.getRunningTaskCount();
        final boolean shutdown = presentExecutor.isShutdown();
        if (runningTaskCount == 0 || shutdown || executionError != null) {
            log.log(Level.FINE, () -> "sweeping executor " + presentExecutor + " off of servicing map");
            try {
                executorPool.returnObject(presentExecutor);
            } catch (Exception ex) {
                log.log(Level.WARNING, "error returning executor " + presentExecutor + " back to pool " + executorPool,
                        ex);
            }
            return true;
        }
        log.log(Level.FINE, () -> "keeping executor " + presentExecutor + " in servicing map");
        return false;
    }
}
