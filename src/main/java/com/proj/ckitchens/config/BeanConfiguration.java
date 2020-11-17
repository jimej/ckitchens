package com.proj.ckitchens.config;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.model.Shelf;
import com.proj.ckitchens.svc.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
public class BeanConfiguration {
    private final LinkedBlockingQueue<Order> deliveryQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Order> orders = new LinkedBlockingQueue<>();

    @Bean
    public Shelf hotShelf() {
        Lock lock = new ReentrantLock(true);
        return new Shelf(lock, 10, Temperature.HOT.name());
    }

    @Bean
    public Shelf coldShelf() {
        Lock lock = new ReentrantLock(true);
        return new Shelf(lock, 10, Temperature.COLD.name());
    }

    @Bean
    public Shelf frozenShelf() {
        Lock lock = new ReentrantLock(true);
        return new Shelf(lock, 10, Temperature.FROZEN.name());
    }

    @Bean
    public Shelf overflowShelf() {
        Lock lock = new ReentrantLock(true);
        return new Shelf(lock, 15, "Overflow");
    }

    @Bean
    public OrderDispatchService dispatchService() {
        return new OrderDispatchService(orders, deliveryQueue);
    }

    @Bean
    public ChefMgmtService chefMgmtService() {
        return new ChefMgmtService(3, dispatchService());
    }

    @Bean
    public DeliveryService deliveryService() {
        return new DeliveryService(3, dispatchService());
    }

    @Bean
    public OrderMgmtService orderMgmtService() {
        return new OrderMgmtService(orders);
    }

    @Bean
    public CleanupService cleanupService() {
        return new CleanupService();
    }

}
