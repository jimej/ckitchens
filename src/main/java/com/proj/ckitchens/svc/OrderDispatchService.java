package com.proj.ckitchens.svc;

//import com.proj.ckitchens.common.OrderDispatchQueue;
import com.proj.ckitchens.common.OrderQueue;
import com.proj.ckitchens.model.Order;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * publish order to delivery system; get order for delivery
 */
public class OrderDispatchService {
    private final OrderQueue orders;
    private final ExecutorService executor;
    public OrderDispatchService(OrderQueue orders) {
        this.orders = orders;
        executor = Executors.newFixedThreadPool(2);
    }
    public void dispatch(Order order) {
        executor.execute(() ->
                {
                    orders.addOrder(order);
                    System.out.println(OrderDispatchService.class.getSimpleName() + " order dispatched to dispatch queue. order " + order.getId());
                }
        );

    }

    public Order getOrderForDelivery() {
            Future<Order> orderFuture = executor.submit(() -> orders.getOrder());

            Order order;
            try {
                order = orderFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                return null;
            }
            return order;
    }

    public void shutdown() {
        this.executor.shutdownNow();
    }
}
