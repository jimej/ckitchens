package com.proj.ckitchens;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.svc.*;
import com.proj.ckitchens.utils.OrderParser;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

@SpringBootApplication
public class CkitchensApplication {

	public static void main(String[] args) {
		System.out.println("- Application started -");
		SpringApplication.run(CkitchensApplication.class, args);

		List<Order> orders = OrderParser.readFromFile("orders.json");

		LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
		LinkedBlockingQueue<Order> deliveryQueue = new LinkedBlockingQueue<>();
		OrderDispatchService dispatchService = new OrderDispatchService(orderQueue, deliveryQueue);
		OrderMgmtService orderMgmtService = new OrderMgmtService(orderQueue);
		ChefMgmtService chefMgmtService = new ChefMgmtService(3, dispatchService);
		DeliveryService deliveryService = new DeliveryService(3, dispatchService);
        CleanupService cleanupService = new CleanupService();
//		System.out.println(org.apache.logging.log4j.Logger.class.getResource("/org/apache/logging/log4j/Logger.class"));
//		System.out.println(org.apache.logging.log4j.Logger.class.getResource("/org/apache/logging/log4j/core/Appender.class"));
//		System.out.println(org.apache.logging.log4j.Logger.class.getResource("/log4j2.xml"));

		Order ord_1 = new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", 100, 0.23);
		Order ord_2 = new Order(UUID.randomUUID(), Temperature.COLD, "Italian", 50, 0.23);
		Order ord_3 = new Order(UUID.randomUUID(), Temperature.FROZEN, "Pizza", 100, 0.23);
		Order ord_4 = new Order(UUID.randomUUID(), Temperature.COLD, "Ice Cream", 70, 0.23);
		Order ord_5 = new Order(UUID.randomUUID(), Temperature.FROZEN, "Pizza", 10, 0.23);
		Order ord_6 = new Order(UUID.randomUUID(), Temperature.COLD, "Pizza", 30, 0.23);
		Order ord_7 = new Order(UUID.randomUUID(), Temperature.FROZEN, "Pizza", 100, 0.23);

		Thread r = new Thread(() -> chefMgmtService.run());
		r.start();
		Thread t = new Thread(() -> deliveryService.run());
		t.start();
		Thread c = new Thread(() -> cleanupService.run());
		c.start();

//		orderMgmtService.addOrder(ord_1);
//		orderMgmtService.addOrder(ord_2);
//		orderMgmtService.addOrder(ord_3);
//		orderMgmtService.addOrder(ord_4);
//		orderMgmtService.addOrder(ord_5);
//		orderMgmtService.addOrder(ord_6);
//		orderMgmtService.addOrder(ord_7);

		double ordersPerSecond = 20; //
		int gap = (int) Math.round(1000/ordersPerSecond); //poisson distribution

		orders.stream().forEach(o ->
				{
					try {
						Thread.sleep(gap);
						orderMgmtService.addOrder(o);
					} catch (Exception e) {

					}
				}
		);

		try {
			Thread.sleep(60000);//200000
		} catch(Exception e) {}

//		try {
//			r.join();
//			t.join();
//			c.join();
//		} catch (Exception e) {
//
//		}
		cleanupService.signalShutdown();
		chefMgmtService.signalShutdown();
		deliveryService.signalShutdown();
		orderMgmtService.shutdown();
		dispatchService.signalShutDown();

	}

}
