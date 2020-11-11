package com.proj.ckitchens.svc;

import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.common.OrderQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * cook order and place cooked order on shelf
 */
public class ChefMgmtService {
    private final int numOfChefs;
//    private final OrderQueue orders;
    private final OrderMgmtService orderMgmtService;
    private final ExecutorService executor;
    private boolean shutdownSignal;
    public ChefMgmtService(int numOfChefs, OrderQueue oq, OrderMgmtService orderMgmtService) {
        this.numOfChefs = numOfChefs;
//        this.orders = oq;
        this.orderMgmtService = orderMgmtService;
        executor = Executors.newFixedThreadPool(numOfChefs);
        this.shutdownSignal = false;
    }

    public void run() {
//        if(shutdownSignal) {
//            executor.shutdown();
//            return;
//        }
//        while(!shutdownSignal) {
//            executor.execute(() -> {
////            Order o = orders.getOrder();
//                Order o = orderMgmtService.getOrder();
//                if (o != null) {
//                    cookOrder(o);
//                    System.out.println("order cooked and placed: in chef management");
//                    ShelfMgmtSystem.placePackaging(o);
//                }
//            });
//        }
//        this.executor.shutdown();
        while (!shutdownSignal) {
            Order o = orderMgmtService.getOrder();
            if (o != null) {
                executor.execute(() -> {
                    cookOrder(o);
                    System.out.println("order cooked and placed: in chef management");
                    ShelfMgmtSystem.placePackaging(o);

                });

            }
        }
        this.executor.shutdown();
    }

    public void cookOrder(Order order) {

    }

    public void signalShutdown() {
        this.shutdownSignal = true;
    }
    public void shutdown() {
        this.executor.shutdown();
    }

}
