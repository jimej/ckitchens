package com.proj.ckitchens;

//import com.proj.ckitchens.common.OrderDispatchQueue;
import com.proj.ckitchens.common.LockedQueue;
import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.svc.*;
import com.proj.ckitchens.utils.OrderParser;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class CkitchensApplication {

	public static void main(String[] args) {
		System.out.println("hey");
//		SpringApplication.run(CkitchensApplication.class, args);
		System.out.println("lo");

		List<Order> orders = OrderParser.readFromFile("orders.json");

		LockedQueue<Order> orderQueue = new LockedQueue<>();
		LockedQueue<Order> deliveryQueue = new LockedQueue<>();
		OrderDispatchService dispatchService = new OrderDispatchService(orderQueue, deliveryQueue);
		OrderMgmtService orderMgmtService = new OrderMgmtService(orderQueue);
		ChefMgmtService chefMgmtService = new ChefMgmtService(3, dispatchService);
		DeliveryService deliveryService = new DeliveryService(3, dispatchService);
        CleanupService cleanupService = new CleanupService();

		Order ord_1 = new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", 300, 0.23);
		Order ord_2 = new Order(UUID.randomUUID(), Temperature.COLD, "Italian", 300, 0.23);
		Order ord_3 = new Order(UUID.randomUUID(), Temperature.FROZEN, "Pizza", 300, 0.23);
		Order ord_4 = new Order(UUID.randomUUID(), Temperature.COLD, "Ice Cream", 300, 0.23);
		Order ord_5 = new Order(UUID.randomUUID(), Temperature.FROZEN, "Pizza", 300, 0.23);

		Thread r = new Thread(() -> chefMgmtService.run());
		r.start();
		Thread t = new Thread(() -> deliveryService.run());
		t.start();
		new Thread(() -> cleanupService.run()).start();

//		orderMgmtService.addOrder(ord_1);
//		orderMgmtService.addOrder(ord_2);
//		orderMgmtService.addOrder(ord_3);
//		orderMgmtService.addOrder(ord_4);
//		orderMgmtService.addOrder(ord_5);

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

		cleanupService.signalShutdown();
		chefMgmtService.signalShutdown();
		deliveryService.signalShutdown();
		orderMgmtService.shutdown();
		dispatchService.shutdown();
	}

}
