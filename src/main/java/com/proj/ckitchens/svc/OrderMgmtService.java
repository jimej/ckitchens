package com.proj.ckitchens.svc;

import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.common.LockedQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * order intake and publish to queue
 */
public class OrderMgmtService {
    private final ExecutorService executor;
    private final LinkedBlockingQueue<Order> orders;
    public OrderMgmtService(LinkedBlockingQueue<Order> oq) {
        this.executor = Executors.newFixedThreadPool(2);
        this.orders = oq;
    }

    public void addOrder(Order o) {
            executor.execute(() -> {
                orders.add(o);
                System.out.println(OrderMgmtService.class.getSimpleName() + " order " + o.getId() + " temp: " + o.getTemp() + " placed on queue by order management");
            });

        }

    public void shutdown() {
        this.executor.shutdownNow();
    }
}
