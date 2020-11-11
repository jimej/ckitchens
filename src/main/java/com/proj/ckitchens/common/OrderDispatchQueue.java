package com.proj.ckitchens.common;

import com.proj.ckitchens.model.Order;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OrderDispatchQueue {
    private final Queue<Order> orders;
    private final Lock lock;
    private final Condition moreOrders;
    private boolean cancelled;
    public OrderDispatchQueue() {
        this.lock = new ReentrantLock();
        orders = new LinkedList<>();
        moreOrders = lock.newCondition();
        cancelled = false;
    }

    public boolean dispatchOrder(Order o) {
        lock.lock();
        try {
            boolean addedOrder = orders.offer(o);
            System.out.println("order dispatch queue count when added " + orders.size());
            moreOrders.signalAll();
            return addedOrder;
        } finally {
            lock.unlock();
        }
    }

    public Order getOrderForDelivery(boolean shutdownSignal) {
        lock.lock();
        try {
            while(!cancelled && orders.peek() == null) {
                moreOrders.await(10, TimeUnit.MILLISECONDS);
            }
            System.out.println("order dispatch queue count before removal " + orders.size());
            Order o = orders.poll();
            System.out.println("order dispatch queue count after removal " + orders.size());

            return o;
        } catch (InterruptedException e) {
            return null;
        } finally {
            lock.unlock();
        }

    }

    public void setCancelled() {
        lock.lock();
        this.cancelled = true;
        lock.unlock();
    }

}
