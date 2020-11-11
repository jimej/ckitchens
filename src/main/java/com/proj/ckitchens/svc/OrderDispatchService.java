package com.proj.ckitchens.svc;

import com.proj.ckitchens.common.OrderDispatchQueue;
import com.proj.ckitchens.model.Order;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * publish order to delivery system; get order for delivery
 */
public class OrderDispatchService {
    private final OrderDispatchQueue orders;
    private final ExecutorService executor;
    private boolean shutdownSignal;
    public OrderDispatchService(OrderDispatchQueue orders) {
        this.orders = orders;
        executor = Executors.newFixedThreadPool(2);
        shutdownSignal = false;
    }
    public void dispatch(Order order) {
        executor.execute(() ->
                {
                    orders.dispatchOrder(order);
                    System.out.println("order arrived for delivery: in dispatch service");
                }
        );

    }

    public Order getOrderForDelivery() {
//          if(shutdownSignal) {
//              orders.setCancelled();
//              return null;
//          }
////        if(!shutdownSignal) {
            Future<Order> orderFuture = executor.submit(() -> orders.getOrderForDelivery(shutdownSignal));

            Order order;
            try {
//                if(shutdownSignal) {orderFuture.cancel(true); return null;}
                order = orderFuture.get();
                System.out.println("order ready for delivery: in dispatch service");
            } catch (ExecutionException | InterruptedException e) {
                return null;
            }
            return order;
//        }
//        return null;
//        return orders.getOrderForDelivery();
    }

    public void shutdown() {
        this.shutdownSignal = true;
        this.executor.shutdown();

    }
}
