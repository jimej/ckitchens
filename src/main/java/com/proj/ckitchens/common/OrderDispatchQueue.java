package com.proj.ckitchens.common;

import com.proj.ckitchens.model.Order;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OrderDispatchQueue {
    private final Queue<Order> orders;
    private final Lock lock;
    private final Condition moreOrders;
    public OrderDispatchQueue() {
        this.lock = new ReentrantLock();
        orders = new LinkedList<>();
        moreOrders = lock.newCondition();
    }

    public boolean dispatchOrder(Order o) {
        lock.lock();
        try {
            boolean addedOrder = orders.offer(o);
            System.out.println(OrderDispatchQueue.class.getSimpleName() + " order " + o.getId() + " added to dispatch queue. queue size " + orders.size());
            moreOrders.signalAll();
            return addedOrder;
        } finally {
            lock.unlock();
        }
    }

    public Order getOrderForDelivery() {
        lock.lock();
        try {
            while(orders.peek() == null) {
                moreOrders.await();
            }
            System.out.println(OrderDispatchQueue.class.getSimpleName() + " order about to be removed from dispatch queue. queue size " + orders.size());
            Order o = orders.poll();
            System.out.println(OrderDispatchQueue.class.getSimpleName() + " order removed " + (o != null? o.getId() : null )+ " queue size " + orders.size());

            return o;
        } catch (InterruptedException e) {
            return null;
        } finally {
            lock.unlock();
        }

    }
}
