package com.proj.ckitchens.svc;

import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.common.LockedQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * get orders from {@link LockedQueue}, cook orders and place cooked orders on shelf
 * launch threads equal to the number of chefs to perform the actions
 */
public class ChefMgmtService {
//    private final OrderMgmtService orderMgmtService;
    private final OrderDispatchService orderDispatchService ;
    private final ExecutorService executor;
    private boolean shutdownSignal;
    public ChefMgmtService(int numOfChefs, OrderDispatchService dispatchService) {
        this.orderDispatchService = dispatchService;
        executor = Executors.newFixedThreadPool(numOfChefs);
        this.shutdownSignal = false;
    }

    public void run() {
        while (!shutdownSignal) {
            Order o = orderDispatchService.getIncomingOrder();

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
