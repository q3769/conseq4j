/*
 * MIT License
 *
 * Copyright (c) 2022 Qingtian Wang
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

import lombok.NonNull;
import lombok.ToString;
import lombok.extern.java.Log;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Log @ToString final class SerialEnqueueBlockingQueue<E> implements BlockingQueue<E> {

    private final BlockingQueue<E> workQueue;
    private final Lock enqueueLock;

    SerialEnqueueBlockingQueue(@NonNull BlockingQueue<E> workQueue, boolean fair) {
        this.workQueue = workQueue;
        this.enqueueLock = new ReentrantLock(fair);
        log.fine(() -> "constructed " + this);
    }

    @Override public boolean add(E e) {
        enqueueLock.lock();
        try {
            return workQueue.add(e);
        } finally {
            enqueueLock.unlock();
        }
    }

    @Override public boolean offer(E e) {
        enqueueLock.lock();
        try {
            return workQueue.offer(e);
        } finally {
            enqueueLock.unlock();
        }
    }

    @Override public void put(E e) throws InterruptedException {
        enqueueLock.lock();
        try {
            workQueue.put(e);
        } finally {
            enqueueLock.unlock();
        }
    }

    @Override public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        enqueueLock.lock();
        try {
            return workQueue.offer(e, timeout, unit);
        } finally {
            enqueueLock.unlock();
        }
    }

    @Override public E take() throws InterruptedException {
        return workQueue.take();
    }

    @Override public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return workQueue.poll(timeout, unit);
    }

    @Override public int remainingCapacity() {
        return workQueue.remainingCapacity();
    }

    @Override public boolean remove(Object o) {
        return workQueue.remove(o);
    }

    @Override public boolean contains(Object o) {
        return workQueue.contains(o);
    }

    @Override public int drainTo(Collection<? super E> c) {
        return workQueue.drainTo(c);
    }

    @Override public int drainTo(Collection<? super E> c, int maxElements) {
        return workQueue.drainTo(c, maxElements);
    }

    @Override public E remove() {
        return workQueue.remove();
    }

    @Override public E poll() {
        return workQueue.poll();
    }

    @Override public E element() {
        return workQueue.element();
    }

    @Override public E peek() {
        return workQueue.peek();
    }

    @Override public int size() {
        return workQueue.size();
    }

    @Override public boolean isEmpty() {
        return workQueue.isEmpty();
    }

    @Override public Iterator<E> iterator() {
        return workQueue.iterator();
    }

    @Override public Object[] toArray() {
        return workQueue.toArray();
    }

    @Override public <T> T[] toArray(T[] a) {
        return workQueue.toArray(a);
    }

    @Override public boolean containsAll(Collection<?> c) {
        return workQueue.containsAll(c);
    }

    @Override public boolean addAll(Collection<? extends E> c) {
        return workQueue.addAll(c);
    }

    @Override public boolean removeAll(Collection<?> c) {
        return workQueue.removeAll(c);
    }

    @Override public boolean retainAll(Collection<?> c) {
        return workQueue.retainAll(c);
    }

    @Override public void clear() {
        workQueue.clear();
    }

}
