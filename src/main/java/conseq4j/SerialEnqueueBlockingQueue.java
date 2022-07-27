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

    private final BlockingQueue<E> delegate;
    private final Lock enqueueLock;

    SerialEnqueueBlockingQueue(@NonNull BlockingQueue<E> delegate, boolean fair) {
        this.delegate = delegate;
        this.enqueueLock = new ReentrantLock(fair);
        log.fine(() -> "constructed " + this);
    }

    @Override public boolean add(E e) {
        enqueueLock.lock();
        try {
            return delegate.add(e);
        } finally {
            enqueueLock.unlock();
        }
    }

    @Override public boolean offer(E e) {
        enqueueLock.lock();
        try {
            return delegate.offer(e);
        } finally {
            enqueueLock.unlock();
        }
    }

    @Override public void put(E e) throws InterruptedException {
        enqueueLock.lock();
        try {
            delegate.put(e);
        } finally {
            enqueueLock.unlock();
        }
    }

    @Override public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        enqueueLock.lock();
        try {
            return delegate.offer(e, timeout, unit);
        } finally {
            enqueueLock.unlock();
        }
    }

    @Override public E take() throws InterruptedException {
        return delegate.take();
    }

    @Override public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.poll(timeout, unit);
    }

    @Override public int remainingCapacity() {
        return delegate.remainingCapacity();
    }

    @Override public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override public int drainTo(Collection<? super E> c) {
        return delegate.drainTo(c);
    }

    @Override public int drainTo(Collection<? super E> c, int maxElements) {
        return delegate.drainTo(c, maxElements);
    }

    @Override public E remove() {
        return delegate.remove();
    }

    @Override public E poll() {
        return delegate.poll();
    }

    @Override public E element() {
        return delegate.element();
    }

    @Override public E peek() {
        return delegate.peek();
    }

    @Override public int size() {
        return delegate.size();
    }

    @Override public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override public Iterator<E> iterator() {
        return delegate.iterator();
    }

    @Override public Object[] toArray() {
        return delegate.toArray();
    }

    @Override public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }

    @Override public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override public boolean addAll(Collection<? extends E> c) {
        return delegate.addAll(c);
    }

    @Override public boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override public boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override public void clear() {
        delegate.clear();
    }

}
