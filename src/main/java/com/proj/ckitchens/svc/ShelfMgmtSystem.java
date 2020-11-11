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
    private static final Shelf SHELF_H = new Shelf(hot, 10, Temperature.HOT);
    private static final Shelf SHELF_C = new Shelf(cold, 10, Temperature.COLD);
    private static final Shelf SHELF_F = new Shelf(frozen, 10, Temperature.FROZEN);
    private static final OverflowShelf SHELF_O = new OverflowShelf(overflow, 30);

    public static void placePackaging(Order order) {
        switch(order.getTemp()) {
            case HOT:
                if(!SHELF_H.placePackaging(order)) {
                    placePackagingOnOverflow(order);
                }
                break;
            case COLD:
                if(!SHELF_C.placePackaging(order)) {
                    placePackagingOnOverflow(order);
//                    overflow.unlock();
                }
                break;
            case FROZEN:
                if(!SHELF_F.placePackaging(order)) {
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
                    System.out.println("order picked up from hot shelf: in shelf management");
                    break;
                case COLD:
                    SHELF_C.remove(order.getId());
                    System.out.println("order picked up from cold shelf: in shelf management");
                    break;
                case FROZEN:
                    SHELF_F.remove(order.getId());
                    System.out.println("order picked up from frozen shelf: in shelf management");
            }
        }
    }

    private static void movePackagingFromOverflow(Order order) {
        overflow.lock();
        hot.lock();
        if (SHELF_O.hasOnShelf(Temperature.HOT) && SHELF_H.isCellAvailable()) {
            try {
                Order o = SHELF_O.removePos(Temperature.HOT);
                SHELF_H.placePackaging(o);
                o.setLifeAfterMove();
                SHELF_O.placePackaging(order);
                return;

            } finally {
                hot.unlock();
                overflow.unlock();
            }
        }

        cold.lock();
        if (SHELF_O.hasOnShelf(Temperature.COLD) && SHELF_C.isCellAvailable()) {
            try {

                Order o = SHELF_O.removePos(Temperature.COLD);
                SHELF_C.placePackaging(o);
                o.setLifeAfterMove();
                SHELF_O.placePackaging(order);
                return;

            } finally{
                cold.unlock();
                overflow.unlock();
            }
        }
        frozen.lock();
        if (SHELF_O.hasOnShelf(Temperature.FROZEN) && SHELF_F.isCellAvailable()) {
            try {

                Order o = SHELF_O.removePos(Temperature.FROZEN);
                SHELF_F.placePackaging(o);
                o.setLifeAfterMove();
                SHELF_O.placePackaging(order);

                return;
            } finally {
                frozen.unlock();
                overflow.unlock();
            }
        }

        SHELF_O.discardRandom(); //overflow shelf must be full.
        SHELF_O.placePackaging(order);

        overflow.unlock();
    }

    private static void placePackagingOnOverflow(Order order) {
        overflow.lock();
        if(!SHELF_O.placePackaging(order)) {
            movePackagingFromOverflow(order);
        }
        overflow.unlock();
    }
}
