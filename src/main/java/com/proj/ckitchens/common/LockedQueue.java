package com.proj.ckitchens.common;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockedQueue<T> {
    private final Queue<T> q;
    private final Lock lock;
    private final Condition more;
    public LockedQueue() {
        this.lock = new ReentrantLock();
        q = new LinkedList<>();
        more = lock.newCondition();
    }

    public T get() {
        lock.lock();
        try {
            while(q.peek() == null) {
                more.await();
            }
            T o = q.poll();
            return o;
        } catch (InterruptedException e) {
            return null;
        } finally {
            lock.unlock();
        }
    }

    public boolean add(T o) {
        lock.lock();
        try {
            boolean addedOrder = q.offer(o);
            more.signalAll();
            return addedOrder;
        } finally {
            lock.unlock();
        }
    }
}
