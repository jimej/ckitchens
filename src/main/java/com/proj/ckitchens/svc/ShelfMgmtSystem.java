package com.proj.ckitchens.svc;

import com.proj.ckitchens.common.OrderQueue;
import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.model.OverflowShelf;
import com.proj.ckitchens.model.Shelf;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * manage movement of orders on shelf
 */
public class ShelfMgmtSystem {
    private static final Lock hot = new ReentrantLock();
    private static final Lock cold = new ReentrantLock();
    private static final Lock frozen = new ReentrantLock();
    private static final Lock overflow = new ReentrantLock();
    private static final Shelf SHELF_H = new Shelf(hot, 20, Temperature.HOT);
    private static final Shelf SHELF_C = new Shelf(cold, 20, Temperature.COLD);
    private static final Shelf SHELF_F = new Shelf(frozen, 20, Temperature.FROZEN);
    private static final OverflowShelf SHELF_O = new OverflowShelf(overflow, 40);

    public static void placePackaging(Order order) {
        switch (order.getTemp()) {
            case HOT:
                if (!SHELF_H.placePackaging(order)) {
                    placePackagingOnOverflow(order);
                }
                break;
            case COLD:
                if (!SHELF_C.placePackaging(order)) {
                    placePackagingOnOverflow(order);
//                    overflow.unlock();
                }
                break;
            case FROZEN:
                if (!SHELF_F.placePackaging(order)) {
                    placePackagingOnOverflow(order);
//                    overflow.unlock();
                }
        }
    }

    public static void readContents() {

    }

    public static void discardPackagingEndOfLife(Temperature temp) {
        SHELF_O.discardPastDue();
        SHELF_H.discardPastDue();
        SHELF_C.discardPastDue();
        SHELF_F.discardPastDue();
    }

    public static void deliverOrder(Order order) {
//        !SHELF_O.remove(order) &&
        if (!SHELF_O.remove(order)) {
            switch (order.getTemp()) {
                case HOT:
                    SHELF_H.remove(order.getId()); //should lock both? if the order is just to be placed; can't be placed on HOT shelf anyway
                    break;
                case COLD:
                    SHELF_C.remove(order.getId());
                    break;
                case FROZEN:
                    SHELF_F.remove(order.getId());
            }
        }
    }

    private static void movePackagingFromOverflow(Order order) {
        try {
            overflow.lock();
            hot.lock();
            if (SHELF_O.hasOnShelf(Temperature.HOT) && SHELF_H.isCellAvailable()) {
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " about to move an order from overflow to hot shelf to place " + order.getId() + " on overflow shelf");
                Order o = SHELF_O.removePos(Temperature.HOT);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " removed an order " + o.getId() + " temp " + o.getTemp() + " from overflow shelf to place " + order.getId());
                SHELF_H.placePackaging(o);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " is placed on hot shelf and yielded overflow shelf position to order " + order.getId());
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order before moving from overflow " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and default max lifeAfterMove " + o.getLifeAfterMove());
                o.setLifeAfterMove();
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and shorter life " + o.getLifeAfterMove());
                SHELF_O.placePackaging(order);
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
                Order o = SHELF_O.removePos(Temperature.COLD);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " removed an order " + o.getId() + " temp " + o.getTemp() + " from overflow shelf to place " + order.getId());
                SHELF_C.placePackaging(o);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " is placed on cold shelf and yielded overflow shelf position to order " + order.getId());
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order before moving from overflow " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and default max lifeAfterMove " + o.getLifeAfterMove());
                o.setLifeAfterMove();
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and shorter life " + o.getLifeAfterMove());
                SHELF_O.placePackaging(order);
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
                Order o = SHELF_O.removePos(Temperature.FROZEN);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " removed an order " + o.getId() + " temp " + o.getTemp() + " from overflow shelf to place " + order.getId());
                SHELF_F.placePackaging(o);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " is placed on frozen shelf and yielded overflow shelf position to order " + order.getId());
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order before moving from overflow " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and default max lifeAfterMove " + o.getLifeAfterMove());
                o.setLifeAfterMove();
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + o.getId() + " " + o.isMoved() + " with original life " + o.getShelfLife() + " and shorter life " + o.getLifeAfterMove());
                SHELF_O.placePackaging(order);
                System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + order.getId() + " is placed on overflow shelf after moving to frozen position on overflow by order " + o.getId());
                return;
            }
        } finally {
            frozen.unlock();
            overflow.unlock();
        }

        overflow.lock();
        SHELF_O.discardRandom(); //overflow shelf must be full.
        SHELF_O.placePackaging(order);
        System.out.println(ShelfMgmtSystem.class.getSimpleName() + " order " + order.getId() + " is placed on overflow shelf after discarding an order on overflow");


        overflow.unlock();
    }

    private static void placePackagingOnOverflow(Order order) {
        overflow.lock();
        if (!SHELF_O.placePackaging(order)) {
            System.out.println(ShelfMgmtSystem.class.getSimpleName() + " not able to place on overflow " + order.getId());
            movePackagingFromOverflow(order);
        }
        overflow.unlock();
    }
}
