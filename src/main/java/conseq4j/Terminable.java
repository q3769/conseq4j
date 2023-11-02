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

package conseq4j;

import java.util.List;

public interface Terminable {
    /**
     * Initiates an orderly terminate of all managed thread resources. Previously submitted tasks are executed, but no
     * new tasks will be accepted. Invocation has no additional effect if already terminated.
     * <p>
     * This method does not wait for the previously submitted tasks to complete execution. Use an external awaiting
     * mechanism to do that, with the help of {@link #isTerminated()}.
     */
    void terminate();

    /**
     * Non-blocking
     *
     * @return true if all tasks of all managed executors have completed following shut down. Note that isTerminated is
     * never true unless terminate was called first.
     */
    boolean isTerminated();

    /**
     * Attempts to terminate all actively executing tasks, halts the processing of waiting tasks, and returns a list of
     * the tasks that were awaiting execution.
     * <p>
     * This method does not wait for the previously submitted tasks to complete execution. Use an external awaiting
     * mechanism to do that, with the help of {@link #isTerminated()}.
     *
     * @return Tasks submitted but never started executing
     */
    List<Runnable> terminateNow();
}
