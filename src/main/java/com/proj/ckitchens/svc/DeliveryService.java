package com.proj.ckitchens.svc;

import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.utils.RandomInt;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static com.proj.ckitchens.svc.ShelfMgmtSystem.shelfMgmtSystem;


/**
 * deliver orders through this service
 * launch threads equal to the number of courier
 */
public class DeliveryService {
    private final ScheduledExecutorService executor;
    private final OrderDispatchService dispatchService;
    private volatile boolean shutdownSignal;

    private static final Logger logger = LogManager.getLogger(DeliveryService.class);
    public DeliveryService(int courierCount, OrderDispatchService dispatchService) {
        executor = Executors.newScheduledThreadPool(courierCount);
        this.dispatchService = dispatchService;
        this.shutdownSignal = false;
    }

    public void run() {
        while (!shutdownSignal) {
            Order o = dispatchService.getOrderForDelivery();
            if (o != null) {
                //10, 30; 10, 40;
                int delay = RandomInt.randomDelay(2, 6); //new Random().nextInt(10) + 10; //20, 60 // 4, 2
                executor.schedule(
                        () -> {
                            logger.log(Level.DEBUG, DeliveryService.class.getSimpleName() + "to remove order {} from shelf", o.getId());
                            shelfMgmtSystem.deliverOrder(o);
                        }, delay, TimeUnit.SECONDS
                );
            }
        }
        executor.shutdown();
    }

    public void signalShutdown() {
        this.shutdownSignal = true;
    }
}
