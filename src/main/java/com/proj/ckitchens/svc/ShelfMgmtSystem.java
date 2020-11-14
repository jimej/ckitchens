package com.proj.ckitchens.svc;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.model.OverflowShelf;
import com.proj.ckitchens.model.Shelf;

import java.time.LocalTime;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * manage movement of orders on shelves
 * This class is the entry point for placing orders on shelves, delivering/discarding orders,
 * moving orders between shelves, cleaning up orders
 */
public class ShelfMgmtSystem {
    private static final Lock hot = new ReentrantLock(true);
    private static final Lock cold = new ReentrantLock(true);
    private static final Lock frozen = new ReentrantLock(true);
    private static final Lock overflow = new ReentrantLock(true);
    public static final Lock masterLock = new ReentrantLock(true);
    private static final Shelf SHELF_H = new Shelf(hot, 10, Temperature.HOT);
    private static final Shelf SHELF_C = new Shelf(cold, 10, Temperature.COLD);
    private static final Shelf SHELF_F = new Shelf(frozen, 10, Temperature.FROZEN);
    private static final OverflowShelf SHELF_O = new OverflowShelf(overflow, 20);

    /**
     * entry point for placing order on shelves
     * @param order
     */
    public static void placeOrderOnShelf(Order order) {
        switch (order.getTemp()) {
            case HOT:
                if (!SHELF_H.placeOnShelf(order)) {
                    placeOrderOnOverflow(order);
                }
                break;
            case COLD:
                if (!SHELF_C.placeOnShelf(order)) {
                    placeOrderOnOverflow(order);
                }
                break;
            case FROZEN:
                if (!SHELF_F.placeOnShelf(order)) {
                    placeOrderOnOverflow(order);
                }
        }
    }

    /**
     * readContents on all shelves
     * masterLock is used to lock all shelves before reading.
     * @param timestamp
     * @param className
     * @param triggerEvent
     */
    public static void readContents(String triggerEvent, String className) {
        masterLock.lock();
        System.out.println(LocalTime.now()  + " | " + triggerEvent + " [" + className +"] ");
        System.out.println("=============================");
        SHELF_O.readContentOnShelf();
        SHELF_H.readContentOnShelf();
        SHELF_C.readContentOnShelf();
        SHELF_F.readContentOnShelf();
        masterLock.unlock();
    }

    /**
     * clean up orders that reached end of life
     * called periodically by {@link CleanupService} to discard orders
     */
    public static void cleanupOrdersEndOfLife() {
        SHELF_O.cleanup();
        SHELF_H.cleanup();
        SHELF_C.cleanup();
        SHELF_F.cleanup();
    }

    /**
     * entry point for delivering an order
     * called by {@link DeliveryService} to deliver an order
     * first check if the order is on overflow, then check on a regular shelf
     * @param order
     */
    public static void deliverOrder(Order order) {
        if (!SHELF_O.removeForDelivery(order)) {
            switch (order.getTemp()) {
                case HOT:
                    SHELF_H.removeForDelivery(order.getId()); //should lock both? if the order is just to be placed; can't be placed on HOT shelf anyway
                    break;
                case COLD:
                    SHELF_C.removeForDelivery(order.getId());
                    break;
                case FROZEN:
                    SHELF_F.removeForDelivery(order.getId());
            }
        }
    }

    /**
     * move an order from overflow to a regular shelf, place the new order on overflow
     * called by {@link ShelfMgmtSystem#placeOrderOnOverflow(Order)} when overflow is full
     *
     * @param order
     */
    private static void moveOrderFromOverflow(Order order) {
        try {
            overflow.lock();
            hot.lock();
            if (SHELF_O.hasOnShelf(Temperature.HOT) && SHELF_H.isCellAvailable()) {
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " about to move an order from overflow to hot shelf to place " + order.getId() + " on overflow shelf");
                Order o = SHELF_O.removeBasedOnTemperature(Temperature.HOT);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " removed an order " + o.getId() + " temp " + o.getTemp() + " from overflow shelf to place " + order.getId());
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order before moving from overflow " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and default max lifeAfterMove " + o.getLifeAfterMove());
                o.setLifeAfterMove();
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and shorter life " + o.getLifeAfterMove());
                SHELF_H.placeOnShelf(o);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " is placed on hot shelf and yielded overflow shelf position to order " + order.getId());
                SHELF_O.placeOnShelf(order);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + order.getId() + " is placed on overflow shelf after moving to hot position on overflow by order " + o.getId());
                return;
            }

        } finally {
            hot.unlock();
            overflow.unlock();
        }

        try {
            overflow.lock();
            cold.lock();
            if (SHELF_O.hasOnShelf(Temperature.COLD) && SHELF_C.isCellAvailable()) {
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " about to move an order from overflow to cold shelf to place " + order.getId() + " on overflow shelf");
                Order o = SHELF_O.removeBasedOnTemperature(Temperature.COLD);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " removed an order " + o.getId() + " temp " + o.getTemp() + " from overflow shelf to place " + order.getId());
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order before moving from overflow " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and default max lifeAfterMove " + o.getLifeAfterMove());
                o.setLifeAfterMove();
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and shorter life " + o.getLifeAfterMove());
                SHELF_C.placeOnShelf(o);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " is placed on cold shelf and yielded overflow shelf position to order " + order.getId());
                SHELF_O.placeOnShelf(order);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + order.getId() + " is placed on overflow shelf after moving to cold position on overflow by order " + o.getId());
                return;
            }

        } finally {
            cold.unlock();
            overflow.unlock();
        }

        try {
            overflow.lock();
            frozen.lock();
            if (SHELF_O.hasOnShelf(Temperature.FROZEN) && SHELF_F.isCellAvailable()) {
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " about to move an order from overflow to frozen shelf to place " + order.getId() + " on overflow shelf");
                Order o = SHELF_O.removeBasedOnTemperature(Temperature.FROZEN);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " removed an order " + o.getId() + " temp " + o.getTemp() + " from overflow shelf to place " + order.getId());
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order before moving from overflow " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and default max lifeAfterMove " + o.getLifeAfterMove());
                o.setLifeAfterMove();
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and shorter life " + o.getLifeAfterMove());
                SHELF_F.placeOnShelf(o);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " is placed on frozen shelf and yielded overflow shelf position to order " + order.getId());
                SHELF_O.placeOnShelf(order);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + order.getId() + " is placed on overflow shelf after moving to frozen position on overflow by order " + o.getId());
                return;
            }
        } finally {
            frozen.unlock();
            overflow.unlock();
        }

        overflow.lock();
        Order discarded = SHELF_O.discardRandom(); //overflow shelf must be full at this point.
        if(discarded != null) {
            SHELF_O.placeOnShelf(order);
            System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + order.getId() + " is placed on overflow shelf after discarding an order on overflow");
        } else { //only happens if overflow has 0 capacity
            System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + order.getId() + " is thrown away. REMOVAL - thrown away");
        }


        overflow.unlock();
    }

    /**
     * place an order on overflow when a HOT/COLD/FROZEN shelf is full
     * @param order
     */
    private static void placeOrderOnOverflow(Order order) {
        overflow.lock();
        if (!SHELF_O.placeOnShelf(order)) {
            System.out.println(ShelfMgmtSystem.class.getSimpleName() + " not able to place on overflow " + order.getId());
            moveOrderFromOverflow(order);
        }
        overflow.unlock();
    }
}
