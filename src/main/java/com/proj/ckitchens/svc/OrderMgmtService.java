package com.proj.ckitchens.svc;

import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.common.LockedQueue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * order intake and publish to queue
 */
public class OrderMgmtService {
    private final ExecutorService executor;
    private final LockedQueue<Order> orders;
//    private final OrderDispatchService dispatchService;
    public OrderMgmtService(LockedQueue<Order> oq) {
        this.executor = Executors.newFixedThreadPool(2);
        this.orders = oq;
//        this.dispatchService = dispatchService;
    }

    public void addOrder(Order o) {
            executor.execute(() -> {
                orders.add(o);
                System.out.println(OrderMgmtService.class.getSimpleName() + " order " + o.getId() + " temp: " + o.getTemp() + " placed on queue by order management");
//                dispatchService.dispatch(o);
                System.out.println(OrderMgmtService.class.getSimpleName() +  " order " + o.getId() + " temp: " + o.getTemp() + " dispatched from order management");
            });

        }

//    public Order getOrder() {
//        Future<Order> orderFuture = executor.submit(
//                () -> orders.get()
//        );
//        Order order;
//        try {
//            order = orderFuture.get();
//        } catch (ExecutionException | InterruptedException e) {
//            return null;
//        }
//        return order;
//    }

    public void shutdown() {
        this.executor.shutdownNow();
    }
}
