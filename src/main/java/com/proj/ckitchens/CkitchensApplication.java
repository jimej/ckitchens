package com.proj.ckitchens;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proj.ckitchens.common.OrderDispatchQueue;
import com.proj.ckitchens.common.OrderQueue;
import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Fake;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.svc.ChefMgmtService;
import com.proj.ckitchens.svc.DeliveryService;
import com.proj.ckitchens.svc.OrderDispatchService;
import com.proj.ckitchens.svc.OrderMgmtService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class CkitchensApplication {

	public static void main(String[] args) {
		System.out.println("hey");
//		SpringApplication.run(CkitchensApplication.class, args);
		System.out.println("lo");

		ObjectMapper mapper = new ObjectMapper();
		List<Order> ords = null;
		try {
//			new File(getClass().getClassLoader().getResource("orders").getFile())
			//Paths.get("orders.json").toFile()
//			List<Fake> ords = Arrays.asList(mapper.readValue(new ClassPathResource("orders.json").getFile(), Fake[].class));
			ords = Arrays.asList(mapper.readValue(new ClassPathResource("orders.json").getFile(), Order[].class));
		} catch (Exception e) {
			System.out.println("something is wrong");
		}

		OrderQueue orderQueue = new OrderQueue();
		OrderDispatchQueue dispatchQueue = new OrderDispatchQueue();
		OrderDispatchService dispatchService = new OrderDispatchService(dispatchQueue);
		OrderMgmtService orderMgmtService = new OrderMgmtService(orderQueue, dispatchService);
		ChefMgmtService chefMgmtService = new ChefMgmtService(3,orderQueue, orderMgmtService);
		DeliveryService deliveryService = new DeliveryService(3,dispatchQueue, dispatchService);


		Order order_1 = new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", 300, 0.23);
		Order order_2 = new Order(UUID.randomUUID(), Temperature.COLD, "Italian", 300, 0.23);
		Order order_3 = new Order(UUID.randomUUID(), Temperature.FROZEN, "Pizza", 300, 0.23);
		Order order_4 = new Order(UUID.randomUUID(), Temperature.COLD, "Ice Cream", 300, 0.23);
		Order order_5 = new Order(UUID.randomUUID(), Temperature.FROZEN, "Pizza", 300, 0.23);
//		orderMgmtService.addOrder(order_1);
//		orderMgmtService.addOrder(order_2);
//		orderMgmtService.addOrder(order_3);
//		orderMgmtService.addOrder(order_4);
//		orderMgmtService.addOrder(order_5);
		Thread r = new Thread(() -> chefMgmtService.run());
		r.start();
		Thread t = new Thread(() -> deliveryService.run());
		t.start();

		ords.stream().forEach(o ->
				{
					try {
						Thread.sleep(50);
						orderMgmtService.addOrder(o);
					} catch (Exception e) {

					}
				}
		);

		try {
			Thread.sleep(30000);
		} catch(Exception e) {}

//		orderMgmtService.shutdown();
////		chefMgmtService.shutdown();
//		chefMgmtService.signalShutdown();
//
//
//		dispatchService.shutdown();
//		deliveryService.signalShutdown();

		chefMgmtService.signalShutdown();
		deliveryService.signalShutdown();
		orderMgmtService.shutdown();
		dispatchService.shutdown();
		dispatchQueue.setCancelled();

//		deliveryService.shutdown();
//		System.exit(0);
//		try {
//			r.join();
//			t.join();
//		} catch (Exception e) {
//
//		}
	}

}
