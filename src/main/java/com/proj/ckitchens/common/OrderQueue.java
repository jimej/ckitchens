package com.proj.ckitchens.common;

import com.proj.ckitchens.model.Order;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OrderQueue {
    private final Queue<Order> orders;
    private final Lock lock;
    private final Condition moreOrders;
    public OrderQueue() {
        this.lock = new ReentrantLock();
        orders = new LinkedList<>();
        moreOrders = lock.newCondition();
    }

    public Order getOrder() {
        lock.lock();
        try {
            while(orders.peek() == null) {
                moreOrders.await();
            }
            System.out.println(OrderQueue.class.getSimpleName() + " an order is about to be removed from order queue. size: " + orders.size());
            Order o = orders.poll();
            System.out.println(OrderQueue.class.getSimpleName() + " order " + (o!=null? o.getId(): null) + " removed from order queue. size: " + orders.size());
            return o;
        } catch (InterruptedException e) {
            return null;
        } finally {
            lock.unlock();
        }
    }

    public boolean addOrder(Order o) {
        lock.lock();
        try {
            boolean addedOrder = orders.offer(o);
            System.out.println(OrderQueue.class.getSimpleName() + " order " + o.getId() + " added to order queue. size: " + orders.size());
            moreOrders.signalAll();
            return addedOrder;
        } finally {
            lock.unlock();
        }
    }
}
