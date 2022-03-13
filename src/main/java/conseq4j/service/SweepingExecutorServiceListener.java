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
package conseq4j.service;

import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import lombok.extern.java.Log;
import org.apache.commons.pool2.ObjectPool;

/**
 * @author Qingitan Wang
 */
@Log
class SweepingExecutorServiceListener implements ExecutorServiceListener {

    private static final String FOR_SEQUENCE_KEY = " for sequence key ";

    private final Object sequenceKey;
    private final ConcurrentMap<Object,
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService> sequentialExecutors;
    private final ObjectPool<GlobalConcurrencyBoundedRunningTasksCountingExecutorService> executorPool;

    public SweepingExecutorServiceListener(Object sequenceKey, ConcurrentMap<Object,
            GlobalConcurrencyBoundedRunningTasksCountingExecutorService> sequentialExecutors, ObjectPool<
                    GlobalConcurrencyBoundedRunningTasksCountingExecutorService> executorPool) {
        this.sequenceKey = sequenceKey;
        this.sequentialExecutors = sequentialExecutors;
        this.executorPool = executorPool;
    }

    @Override
    public void beforeEachExecute(Thread t, Runnable r) {
        // no-op
    }

    @Override
    public void afterEachExecute(Runnable r, Throwable t) {
        maySweepExecutor(r, t);
    }

    private void maySweepExecutor(Runnable r, Throwable t) {
        log.log(Level.FINE, () -> "Start check-sweeping executor" + sequenceKeyLog() + ", after servicing Runnable: "
                + r + " with Throwable: " + t + ", in " + sequentialExecutors.size() + " active sequential executors");
        sequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            if (presentExecutor == null) {
                log.log(Level.FINE, () -> "Executor" + sequenceKeyLog() + " already swept off by another check");
                return null;
            }
            final int runningTaskCount = presentExecutor.getRunningTaskCount();
            final boolean isShutDown = presentExecutor.isShutdown();
            log.log(Level.FINE, () -> "Checking executor " + presentExecutor + sequenceKeyLog()
                    + " with running task count: " + runningTaskCount + ", shutdown initiated: " + isShutDown
                    + ", execution Throwable: " + t);
            if (runningTaskCount != 0 && !isShutDown && t == null) {
                log.log(Level.FINE, () -> "Keeping executor " + presentExecutor + sequenceKeyLog());
                return presentExecutor;
            }
            log.log(Level.FINE, () -> "Sweeping executor " + presentExecutor + sequenceKeyLog());
            try {
                executorPool.returnObject(presentExecutor);
                return null;
            } catch (Exception ex) {
                log.log(Level.WARNING, "Error returning executor " + presentExecutor + sequenceKeyLog()
                        + " back to pool " + executorPool, ex);
                return null;
            }
        });
        log.log(Level.FINE, () -> "Done check-sweeping executor" + sequenceKeyLog() + ", " + sequentialExecutors.size()
                + " active sequential executors left");
    }

    private String sequenceKeyLog() {
        return FOR_SEQUENCE_KEY + sequenceKey;
    }

}
