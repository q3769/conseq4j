/*
 * MIT License
 *
 * Copyright (c) 2021 Qingtian Wang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package conseq4j.summon;

import java.util.concurrent.ExecutorService;

/**
 * Main API of conseq service factory, producing sequential executor instances summoned by sequence keys.
 * <p>
 * For sequencing, the executor of the same sequence key should execute all tasks sequentially in the same order of
 * submissions. For concurrency, executor instances of different sequence keys should run in parallel by different
 * threads.
 *
 * @author Qingtian Wang
 */
public interface SequentialExecutorServiceFactory {
    /**
     * Produces sequential executor for the specified sequence key. All calls passing in the same sequence key return
     * the same executor.
     *
     * @param sequenceKey an {@link Object} instance whose hash code is used to summon the corresponding executor.
     * @return the sequential executor of type {@link java.util.concurrent.ExecutorService} that executes all tasks of
     * this sequence key in the same order as they are submitted.
     */
    ExecutorService getExecutorService(Object sequenceKey);
}
