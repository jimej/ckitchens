package com.proj.ckitchens.svc;

//import com.proj.ckitchens.common.OrderDispatchQueue;
import com.proj.ckitchens.common.LockedQueue;
import com.proj.ckitchens.model.Order;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;

/**
 * perform these actions:
 * <ul>
 *     <li>get order from orders queue</li>
 *     <li>publish order to delivery queue</li>
 *     <li>get order for delivery from delivery queue</li>
 * </ul>
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

    /**
     * called by {@link ChefMgmtService} to cook and place the order on shelf
     * publish the order to {@link OrderDispatchService#deliveryQueue}
     * @return order from {@link OrderDispatchService#orders} queue
     */
    public Order getIncomingOrder() {
        Order order = getOrderFromQueue(orders);
        if(order != null) moveOrderToDeliveryQueue(order);
        return order;
    }

    /**
     * add an order to the delivery queue
     * @param order
     */
    public void moveOrderToDeliveryQueue(Order order) {
        executor.execute(() ->
                {
                    deliveryQueue.add(order);
                    logger.log(Level.DEBUG, OrderDispatchService.class.getSimpleName() + " order {} dispatched to delivery queue", order.getId());
                }
        );

    }

    /**
     * called by {@link DeliveryService}
     * @return
     */
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
        this.shutdownSignal = true;
        this.executor.shutdownNow();
    }
}
