package com.proj.ckitchens.svc;

import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.common.OrderQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * cook order and place cooked order on shelf
 */
public class ChefMgmtService {
    private final OrderMgmtService orderMgmtService;
    private final ExecutorService executor;
    private boolean shutdownSignal;
    public ChefMgmtService(int numOfChefs, OrderQueue oq, OrderMgmtService orderMgmtService) {
        this.orderMgmtService = orderMgmtService;
        executor = Executors.newFixedThreadPool(numOfChefs);
        this.shutdownSignal = false;
    }

    public void run() {
        while (!shutdownSignal) {
            Order o = orderMgmtService.getOrder();

            if (o != null) {
                executor.execute(() -> {
                    cookOrder(o);
                    System.out.println(ChefMgmtService.class.getSimpleName() + " order cooked and to be placed on shelf: " + o.getId());
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

}
