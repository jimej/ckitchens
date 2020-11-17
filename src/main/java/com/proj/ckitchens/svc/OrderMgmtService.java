package com.proj.ckitchens.svc;

import com.proj.ckitchens.model.Order;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * order intake and publish to orders queue
 */
public class OrderMgmtService {
    private final ExecutorService executor;
    private final LinkedBlockingQueue<Order> orders;
    private static final Logger logger = LogManager.getLogger(OrderMgmtService.class);
    public OrderMgmtService(LinkedBlockingQueue<Order> oq) {
        this.executor = Executors.newFixedThreadPool(2);
        this.orders = oq;
    }

    public void addOrder(Order o) {
            executor.execute(() -> {
                orders.add(o);
                logger.log(Level.DEBUG, OrderMgmtService.class.getSimpleName() + " order {}  temp: {} placed on queue by order management", o.getId() , o.getTemp());
            });

        }

    public void shutdown() {
        this.executor.shutdownNow();
    }
}
