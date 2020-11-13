package com.proj.ckitchens.svc;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * clean up orders from shelves when food value reaches 0 or becomes negative
 */
public class CleanupService {
    private final ScheduledExecutorService executor;
    private boolean shutdownSignal;
    public CleanupService() {
        this.executor = Executors.newScheduledThreadPool(2);
        this.shutdownSignal = false;
    }

    public void run() {
        executor.scheduleWithFixedDelay(()->ShelfMgmtSystem.cleanupOrdersEndOfLife(), 30, 10, TimeUnit.SECONDS);
        while(!shutdownSignal) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {

            }
        }
        executor.shutdown();
    }

    public void signalShutdown() {
        this.shutdownSignal = true;
    }
}
