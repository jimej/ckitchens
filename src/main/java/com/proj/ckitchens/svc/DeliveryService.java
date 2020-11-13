package com.proj.ckitchens.svc;

import com.proj.ckitchens.common.OrderDispatchQueue;
import com.proj.ckitchens.model.Order;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * deliver orders through this service
 * launch threads equal to the number of courier
 */
public class DeliveryService {
    private final ScheduledExecutorService executor;
    private final OrderDispatchService dispatchService;
    private boolean shutdownSignal;

    public DeliveryService(int courierCount, OrderDispatchService dispatchService) {
        executor = Executors.newScheduledThreadPool(courierCount);
        this.dispatchService = dispatchService;
        this.shutdownSignal = false;
    }

    public void run() {
        while (!shutdownSignal) {
            Order o = dispatchService.getOrderForDelivery();
            if (o != null) {
                int delay = new Random().nextInt(10) + 10; //20, 60 // 4, 2
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
