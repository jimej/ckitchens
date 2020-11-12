package com.proj.ckitchens.svc;

import com.proj.ckitchens.common.OrderDispatchQueue;
import com.proj.ckitchens.model.Order;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeliveryService {
    private final ScheduledExecutorService executor;
    private final OrderDispatchService dispatchService;
    private boolean shutdownSignal;

    public DeliveryService(int delivererCount, OrderDispatchQueue dispatchedOrders, OrderDispatchService dispatchService) {
        executor = Executors.newScheduledThreadPool(delivererCount);
        this.dispatchService = dispatchService;
        this.shutdownSignal = false;
    }

    public void run() {
        while (!shutdownSignal) {
            Order o = dispatchService.getOrderForDelivery();
            if (o != null) {
                int delay = new Random().nextInt(20) + 60; //20, 60
                executor.schedule(
                        () -> {
                            System.out.println(DeliveryService.class.getSimpleName() + " to remove order from shelf " + o.getId());
                            ShelfMgmtSystem.deliverOrder(o);
                        }, delay, TimeUnit.SECONDS
                );
            }
        }
        executor.shutdown();
    }

    public void signalShutdown() {
        this.shutdownSignal = true;
    }
}
