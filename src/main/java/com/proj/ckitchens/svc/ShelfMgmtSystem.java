package com.proj.ckitchens.svc;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.model.Shelf;
import com.proj.ckitchens.svc.operations.ShelfService;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * manage movement of orders on shelves
 * This class is the entry point for placing orders on shelves, delivering/discarding orders,
 * moving orders between shelves, cleaning up orders
 */
@Service
public class ShelfMgmtSystem {
    private static final Lock hot = new ReentrantLock(true);
    private static final Lock cold = new ReentrantLock(true);
    private static final Lock frozen = new ReentrantLock(true);
    private static final Lock overflow = new ReentrantLock(true);
    private static final ShelfService shelfServiceInstance = new ShelfService(
            new Shelf(hot, 10, Temperature.HOT.name()),
            new Shelf(cold, 10, Temperature.COLD.name()),
            new Shelf(frozen, 10, Temperature.FROZEN.name()),
            new Shelf(overflow, 15, "Overflow")
    );
    private final Shelf hotShelf; // = new Shelf(hot, 10, Temperature.HOT.name());
    private final Shelf coldShelf; // = new Shelf(cold, 10, Temperature.COLD.name());
    private final Shelf frozenShelf; // = new Shelf(frozen, 10, Temperature.FROZEN.name());
    private final Shelf overflowShelf; // = new Shelf(overflow, 20, "Overflow");
    private final ShelfService shelfService; // =  new ShelfService(hotShelf, coldShelf, frozenShelf, overflowShelf);
    public static  Lock masterLock = new ReentrantLock(true);
    private static final Logger logger = LogManager.getLogger(ShelfMgmtSystem.class);


    //    private static final ShelfService SHELF_H = new ShelfService();
//    private static final ShelfService SHELF_C = new ShelfService();
//    private static final ShelfService SHELF_F = new ShelfService();
//    private static final ShelfService SHELF_O = new ShelfService();
    public static ShelfMgmtSystem shelfMgmtSystem = new ShelfMgmtSystem(shelfServiceInstance);
    public ShelfMgmtSystem(ShelfService shelfService) {
        this.shelfService = shelfService;
        hotShelf = shelfService.getHotShelf();
        coldShelf = shelfService.getColdShelf();
        frozenShelf = shelfService.getFrozenShelf();
        overflowShelf = shelfService.getOverflowShelf();

    }

    /**
     * entry point for placing order on shelves (which can start the order movement between shelves)
     * @param order
     */
    public void placeOrderOnShelf(Order order) {
        switch (order.getTemp()) {
            case HOT:
                if (!shelfService.placeOnShelf(order, hotShelf)) {
                    placeOrderOnOverflow(order);
                }
                break;
            case COLD:
                if (!shelfService.placeOnShelf(order, coldShelf)) {
                    placeOrderOnOverflow(order);
                }
                break;
            case FROZEN:
                if (!shelfService.placeOnShelf(order, frozenShelf)) {
                    placeOrderOnOverflow(order);
                }
        }
    }

    /**
     * entry point for delivering an order
     * called by {@link DeliveryService} to deliver an order
     * first check if the order is on overflow, then check on a regular shelf
     * @param order
     */
    public void deliverOrder(Order order) {
        if (!shelfService.removeForDelivery(order, overflowShelf)) {
            switch (order.getTemp()) {
                case HOT:
                    shelfService.removeForDelivery(order, hotShelf); //should lock both? if the order is just to be placed; can't be placed on HOT shelf anyway
                    break;
                case COLD:
                    shelfService.removeForDelivery(order, coldShelf);
                    break;
                case FROZEN:
                    shelfService.removeForDelivery(order, frozenShelf);
            }
        }
    }

    /**
     * clean up orders that reached end of life
     * called periodically by {@link CleanupService} to discard orders
     */
    public void cleanupOrdersEndOfLife() {
        shelfService.cleanup(overflowShelf);
        shelfService.cleanup(hotShelf);
        shelfService.cleanup(coldShelf);
        shelfService.cleanup(frozenShelf);
    }

    /**
     * readContents on all shelves
     * masterLock is used to lock all shelves before reading.
     * @param className
     * @param triggerEvent
     */
    public void readContents(String triggerEvent, String className) {
        masterLock.lock();
        System.out.println(LocalTime.now()  + " | " + triggerEvent + " [" + className +"] ");
        System.out.println("=============================");
        shelfService.readContentOnShelf(overflowShelf);
        shelfService.readContentOnShelf(hotShelf);
        shelfService.readContentOnShelf(coldShelf);
        shelfService.readContentOnShelf(frozenShelf);
        masterLock.unlock();
    }

    /**
     * place an order on overflow when a HOT/COLD/FROZEN shelf is full
     * @param order
     */
    private void placeOrderOnOverflow(Order order) {
        overflow.lock();
        if (!shelfService.placeOnShelf(order, overflowShelf)) {
            logger.log(Level.DEBUG, ShelfMgmtSystem.class.getSimpleName() + " not able to directly place {} on overflow", order.getId());
            moveOrderFromOverflow(order);
        }
        overflow.unlock();
    }

    /**
     * move an order from overflow to a regular shelf, place the new order on overflow
     * called by {@link ShelfMgmtSystem#placeOrderOnOverflow(Order)} when overflow is full
     *
     * @param order
     */
    public void moveOrderFromOverflow(Order order) {
        try {
            overflow.lock();
            hot.lock();
            if (shelfService.hasOnShelf(Temperature.HOT, overflowShelf) && shelfService.isCellAvailable(hotShelf)) {
                vacateFromOverflow(order, Temperature.HOT, hotShelf);
                return;
            }
        } finally {
            hot.unlock();
            overflow.unlock();
        }

        try {
            overflow.lock();
            cold.lock();

            if (shelfService.hasOnShelf(Temperature.COLD, overflowShelf) && shelfService.isCellAvailable(coldShelf)) {
                vacateFromOverflow(order, Temperature.COLD, coldShelf);
                return;
            }

        } finally {
            cold.unlock();
            overflow.unlock();
        }

        try {
            overflow.lock();
            frozen.lock();
            if (shelfService.hasOnShelf(Temperature.FROZEN, overflowShelf) && shelfService.isCellAvailable(frozenShelf)) {
                vacateFromOverflow(order, Temperature.FROZEN, frozenShelf);
                return;
            }
        } finally {
            frozen.unlock();
            overflow.unlock();
        }

        //when an order on overflow can't be moved to another shelf, a random order is discarded
        //at this point the overflow shelf is full
        overflow.lock();
        Order discarded = shelfService.discardRandom(overflowShelf);
        if(discarded != null) {
            shelfService.placeOnShelf(order, overflowShelf);
            logger.log(Level.DEBUG,ShelfMgmtSystem.class.getSimpleName() + " order {} is placed on overflow shelf after discarding an order on overflow", order.getId());
        } else { //only happens if overflow has 0 capacity
            logger.log(Level.WARN, ShelfMgmtSystem.class.getSimpleName() + " order {} is thrown away. REMOVAL - thrown away", order.getId());
        }
        overflow.unlock();
    }

    /**
     * this is called when a move from overflow to HOT/COLD/FROZEN shelf is possible
     * @param order
     * @param temp
     * @param shelf
     */
    private void vacateFromOverflow(Order order, Temperature temp, Shelf shelf) {
            Order o = shelfService.removeBasedOnTemperature(temp, overflowShelf);
            o.setLifeAfterMove();
            shelfService.placeOnShelf(o, shelf);
            shelfService.placeOnShelf(order, overflowShelf);
    }
}
