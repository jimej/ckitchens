package com.proj.ckitchens.common;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockedQueue<T> {
    private final Queue<T> orders;
    private final Lock lock;
    private final Condition more;
    public LockedQueue() {
        this.lock = new ReentrantLock();
        orders = new LinkedList<>();
        more = lock.newCondition();
    }

    public T get() {
        lock.lock();
        try {
            while(orders.peek() == null) {
                more.await();
            }
//            System.out.println(OrderQueue.class.getSimpleName() + " an order is about to be removed from order queue. size: " + orders.size());
            T o = orders.poll();
//            System.out.println(OrderQueue.class.getSimpleName() + " order " + (o!=null? o.getId(): null) + " removed from order queue. size: " + orders.size());
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
            boolean addedOrder = orders.offer(o);
//            System.out.println(OrderQueue.class.getSimpleName() + " order " + o.getId() + " added to order queue. size: " + orders.size());
            more.signalAll();
            return addedOrder;
        } finally {
            lock.unlock();
        }
    }
}
