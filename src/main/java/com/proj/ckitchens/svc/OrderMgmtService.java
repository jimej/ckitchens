package com.proj.ckitchens.svc;

import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.common.OrderQueue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * order intake and publish to queue
 */
public class OrderMgmtService {
    private final ExecutorService executor;
    private final OrderQueue orders;
    private final OrderDispatchService dispatchService;
    private boolean shutdownSignal;
    public OrderMgmtService(OrderQueue oq, OrderDispatchService dispatchService) {
        this.executor = Executors.newFixedThreadPool(2);
        this.orders = oq;
        this.dispatchService = dispatchService;
        this.shutdownSignal = false;
    }

    public void addOrder(Order o) {
//        if(shutdownSignal) {
//            executor.shutdown();
//            return;
//        }
            executor.execute(() -> {
                orders.addOrder(o);
                System.out.println(OrderMgmtService.class.getSimpleName() + " order " + o.getId() + " temp: " + o.getTemp() + " placed on queue by order management");
                dispatchService.dispatch(o);
                System.out.println(OrderMgmtService.class.getSimpleName() +  " order " + o.getId() + " temp: " + o.getTemp() + " dispatched from order management");
            });

        }

    public Order getOrder() {
      //if orders.peek != null
        Future<Order> orderFuture = executor.submit(
                () -> orders.getOrder(shutdownSignal)
        );
        Order order;
        try {
            order = orderFuture.get();
            System.out.println(OrderMgmtService.class.getSimpleName() + " get order");
        } catch (ExecutionException | InterruptedException e) {
            return null;
        }
        return order;
    }

    public void signalShutdown() {
        this.shutdownSignal = true;
    }

    public void shutdown() {
        this.executor.shutdown();
    }
}
