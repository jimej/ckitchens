package com.proj.ckitchens.svc;

//import com.proj.ckitchens.common.OrderDispatchQueue;
import com.proj.ckitchens.common.LockedQueue;
import com.proj.ckitchens.model.Order;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;

/**
 * publish order to delivery system; get order for delivery
 */
public class OrderDispatchService {
    private final LinkedBlockingQueue<Order> deliveryQueue;
    private final LinkedBlockingQueue<Order> orders;
    private final ExecutorService executor;
    private volatile boolean shutdownSignal;

    private static final Logger logger = LogManager.getLogger(OrderDispatchService.class);
    public OrderDispatchService(LinkedBlockingQueue<Order> orders, LinkedBlockingQueue<Order> deliveryQueue) {
        this.orders = orders;
        this.deliveryQueue = deliveryQueue;
        executor = Executors.newFixedThreadPool(2);
    }

    public Order getIncomingOrder() {
        Order order = getOrderFromQueue(orders);
        if(order != null) moveOrderToDeliveryQueue(order);
        return order;
    }

    public void moveOrderToDeliveryQueue(Order order) {
        executor.execute(() ->
                {
                    deliveryQueue.add(order);
                    logger.log(Level.DEBUG, OrderDispatchService.class.getSimpleName() + " order {} dispatched to delivery queue", order.getId());
                }
        );

    }

    public Order getOrderForDelivery() {
        return getOrderFromQueue(deliveryQueue);
    }

    private Order getOrderFromQueue(LinkedBlockingQueue<Order> queue) {
        Future<Order> orderFuture = executor.submit(() -> queue.poll());

        Order order;
        try {
            if(shutdownSignal) {
                orderFuture.cancel(true);
                return null;
            }
            order = orderFuture.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
        return order;
    }

    public void signalShutDown() {
        this.shutdownSignal = true; this.executor.shutdownNow();
    }
}
