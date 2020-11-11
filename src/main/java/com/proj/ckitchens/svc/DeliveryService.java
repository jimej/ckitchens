package com.proj.ckitchens.svc;

import com.proj.ckitchens.common.OrderDispatchQueue;
import com.proj.ckitchens.model.Order;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeliveryService {
    private final ScheduledExecutorService executor;
    //    private final ExecutorService executor;
//    private final OrderDispatchQueue dispatchedOrders;
    private final OrderDispatchService dispatchService;
    private boolean shutdownSignal;

    public DeliveryService(int delivererCount, OrderDispatchQueue dispatchedOrders, OrderDispatchService dispatchService) {
        executor = Executors.newScheduledThreadPool(delivererCount);
//        executor = Executors.newFixedThreadPool(delivererCount);
//        this.dispatchedOrders = dispatchedOrders;
        this.dispatchService = dispatchService;
        this.shutdownSignal = false;
    }

    public void run() {
        //while (true)
        //executor.shutdown()
//        executor.execute(r);
//        while(!shutdownSignal) {
//            int delay = new Random().nextInt(4) + 2;
//
//            executor.schedule(
//                    ()-> {
////                        Order o = dispatchedOrders.getOrderForDelivery();
//                        Order o = dispatchService.getOrderForDelivery();
//                        System.out.println("to get order from shelf: in delivery service");
//                        if(o != null) {
//                            ShelfMgmtSystem.deliverOrder(o);
//                        }
//                    }, delay, TimeUnit.SECONDS
//            );
//
//        }
//        executor.shutdown();


        while (!shutdownSignal) {
            Order o = dispatchService.getOrderForDelivery();
            if (o != null) {
                int delay = new Random().nextInt(4) + 2;
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

    public void shutdown() {
        this.executor.shutdown();
    }
}
