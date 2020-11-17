package com.proj.ckitchens;

import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.svc.*;
import com.proj.ckitchens.utils.OrderParser;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@SpringBootApplication
public class CkitchensApplication {

	public static void main(String[] args) {
		System.out.println("- Application started -");
//		ApplicationContext applicationContext = SpringApplication.run(CkitchensApplication.class, args);

		List<Order> orders = OrderParser.readFromFile("orders.json");

		LinkedBlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
		LinkedBlockingQueue<Order> deliveryQueue = new LinkedBlockingQueue<>();
		OrderDispatchService dispatchService = new OrderDispatchService(orderQueue, deliveryQueue);
		OrderMgmtService orderMgmtService = new OrderMgmtService(orderQueue);
		ChefMgmtService chefMgmtService = new ChefMgmtService(3, dispatchService);
		DeliveryService deliveryService = new DeliveryService(3, dispatchService);
        CleanupService cleanupService = new CleanupService();

		Thread r = new Thread(() -> chefMgmtService.run());
		r.start();
		Thread t = new Thread(() -> deliveryService.run());
		t.start();
		Thread c = new Thread(() -> cleanupService.run());
		c.start();

		double ordersPerSecond = 20;
		int gap = (int) Math.round(1000/ordersPerSecond);

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
			Thread.sleep(60000);
		} catch(Exception e) {}

		cleanupService.signalShutdown();
		chefMgmtService.signalShutdown();
		deliveryService.signalShutdown();
		orderMgmtService.shutdown();
		dispatchService.signalShutDown();

//		Thread c = new Thread(() -> applicationContext.getBean(ChefMgmtService.class).run());
//		Thread d = new Thread(() -> applicationContext.getBean(DeliveryService.class).run());
//		Thread cu = new Thread(() -> applicationContext.getBean(CleanupService.class).run());
//		c.start();
//		d.start();
//		cu.start();
//
//		orders.stream().forEach(o ->
//				{
//					try {
//						Thread.sleep(gap);
//						applicationContext.getBean(OrderMgmtService.class).addOrder(o);
//					} catch (Exception e) {
//
//					}
//				}
//		);
//
//		try {
//			Thread.sleep(60000);
//		} catch(Exception e) {}
//
//		applicationContext.getBean(CleanupService.class).signalShutdown();
//		applicationContext.getBean(ChefMgmtService.class).signalShutdown();
//		applicationContext.getBean(DeliveryService.class).signalShutdown();
//		applicationContext.getBean(OrderMgmtService.class).shutdown();
//		applicationContext.getBean(OrderDispatchService.class).signalShutDown();
	}

}
