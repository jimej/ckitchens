package com.proj.ckitchens.svc;

import com.proj.ckitchens.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static com.proj.ckitchens.svc.TestFixture.*;

public class OrderDispatchServiceTest {
    private static LinkedBlockingQueue<Order> ordersQueue;
    private static LinkedBlockingQueue<Order> deliveryQueue;
    private static OrderDispatchService dispatchService;

    @BeforeEach
    public void init() {
        ordersQueue = new LinkedBlockingQueue<>();
        deliveryQueue = new LinkedBlockingQueue<>();
        dispatchService = new OrderDispatchService(ordersQueue, deliveryQueue);
    }

    @Test
    public void testGetIncomingOrder() {
        Order o = dispatchService.getIncomingOrder();
        assertTrue(o == null);

        o = generateOneHotOrder();
        ordersQueue.offer(o);
        Order order = dispatchService.getIncomingOrder();
        assertTrue(order.getId() == o.getId());

        assertTrue(dispatchService.getOrderForDelivery().getId() == o.getId());
    }

    @Test
    public void testDispatchToDeliveryInteraction() {
        //not called
        dispatchService = spy(dispatchService);
        dispatchService.getIncomingOrder();
        verify(dispatchService, times(0)).moveOrderToDeliveryQueue(any());

        //called once
        Order o = generateOneHotOrder();
        ordersQueue.offer(o);
        dispatchService.getIncomingOrder();
        verify(dispatchService, times(1)).moveOrderToDeliveryQueue(o);
    }

    @Test
    public void testMovingOrderToDelivery() {
         Order o = generateOneHotOrder();
         dispatchService.moveOrderToDeliveryQueue(o);
         Order order = dispatchService.getOrderForDelivery();
         assertTrue(order.getId() == o.getId());
    }

    @Test
    public void testGetOrderForDelivery() {
        Order o = dispatchService.getOrderForDelivery();
        assertTrue(o == null);

        o = generateOneHotOrder();
        deliveryQueue.offer(o);
        Order order = dispatchService.getOrderForDelivery();
        assertTrue(order.getId() == o.getId());
    }
}
