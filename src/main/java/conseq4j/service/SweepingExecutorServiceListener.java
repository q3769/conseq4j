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
import org.apache.commons.pool2.ObjectPool;

import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * @author Qingitan Wang
 */
@Log class SweepingExecutorServiceListener implements ExecutionListener {

    private final Object sequenceKey;
    private final ConcurrentMap<Object, GlobalConcurrencyBoundedRunningTasksCountingExecutorService>
            servicingSequentialExecutors;
    private final ObjectPool<GlobalConcurrencyBoundedRunningTasksCountingExecutorService> executorPool;

    public SweepingExecutorServiceListener(Object sequenceKey,
            ConcurrentMap<Object, GlobalConcurrencyBoundedRunningTasksCountingExecutorService> servicingSequentialExecutors,
            ObjectPool<GlobalConcurrencyBoundedRunningTasksCountingExecutorService> executorPool) {
        this.sequenceKey = sequenceKey;
        this.servicingSequentialExecutors = servicingSequentialExecutors;
        this.executorPool = executorPool;
    }

    @Override public void beforeEachExecute(Thread t, Runnable r) {
        // no-op
    }

    @Override public void afterEachExecute(Runnable r, Throwable t) {
        maySweepExecutor(r, t);
    }

    private void maySweepExecutor(Runnable r, Throwable t) {
        log.log(Level.FINE,
                () -> "Start sweeping-check executor" + forSequenceKey() + ", after servicing Runnable: " + r
                        + " with Throwable: " + t + ", in " + servicingSequentialExecutors.size()
                        + " active sequential executors");
        servicingSequentialExecutors.compute(sequenceKey,
                (presentSequenceKey, presentExecutor) -> keepingExecutorInService(presentExecutor, t) ?
                        presentExecutor : null);
        log.log(Level.FINE,
                () -> "Done sweeping-check executor" + forSequenceKey() + ", " + servicingSequentialExecutors.size()
                        + " active sequential executors left");
    }

    private boolean keepingExecutorInService(
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService presentExecutor, Throwable t) {
        if (presentExecutor == null) {
            log.log(Level.FINE,
                    () -> "executor" + forSequenceKey() + " already swept off of servicing map by another check");
            return false;
        }
        final int runningTaskCount = presentExecutor.getRunningTaskCount();
        final boolean isShutDown = presentExecutor.isShutdown();
        log.log(Level.FINE,
                () -> "checking executor " + presentExecutor + forSequenceKey() + " with running task count: "
                        + runningTaskCount + ", shutdown initiated: " + isShutDown + ", execution error: " + t);
        if (t == null && runningTaskCount != 0 && !isShutDown) {
            log.log(Level.FINE, () -> "keeping executor " + presentExecutor + forSequenceKey() + " in servicing map");
            return true;
        }
        log.log(Level.FINE, () -> "sweeping executor " + presentExecutor + forSequenceKey() + " off of servicing map");
        try {
            executorPool.returnObject(presentExecutor);
        } catch (Exception ex) {
            log.log(Level.WARNING,
                    "error returning executor " + presentExecutor + forSequenceKey() + " back to pool " + executorPool,
                    ex);
        }
        return false;
    }

    private String forSequenceKey() {
        return " for sequence key " + sequenceKey;
    }

}
