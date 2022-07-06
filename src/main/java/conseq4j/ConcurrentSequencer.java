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

import java.util.concurrent.ExecutorService;

/**
 * <p>Main API interface to summon a sequential executor by a sequence key. The same/equal sequence key gets back the
 * same single-thread executor; this ensures task(s) of the same key are executed in the same order as submitted.</p>
 *
 * @author Qingtian Wang
 */
public interface ConcurrentSequencer {

    /**
     * @param sequenceKey an {@link java.lang.Object} object whose hash code is used to locate and summon the
     *                    corresponding sequential executor.
     * @return a {@link java.util.concurrent.ExecutorService} where dedicated execute tasks of the same sequence key in
     *         the same order they are submitted.
     */
    ExecutorService getSequentialExecutor(Object sequenceKey);

}
