package com.proj.ckitchens.model;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.svc.ShelfMgmtSystem;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.locks.Lock;

import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;

public class Shelf {
    private final Lock lock;
    private final int capacity;
    private final Order[] cells;
    private final Temperature temperature;
    private final Queue<Integer> availableCells = new LinkedList<>();
    private final Map<UUID, Integer> locations;
    public Shelf(Lock lock, int capacity, Temperature temperature) {
        this.lock = lock;
        this.capacity = capacity;
        this.cells = new Order[capacity];
        this.temperature = temperature;
        for(int i = 0; i < capacity; i++) {
            availableCells.offer(i);
        }
        locations = new HashMap<>();
    }

    public boolean placePackaging(Order order) {

        try {
            lock.lock();
            masterLock.lock();
            if (!availableCells.isEmpty()) {
                int freePos = availableCells.poll();
                cells[freePos] = order;
                locations.put(order.getId(), freePos);
                //only for logging and testing
                switch(order.getTemp()) {
                    case HOT:
                        System.out.println(Shelf.class.getSimpleName() +  " order " + order.getId() + " placed on hot shelf position: " + freePos + " isMoved:" + order.isMoved());
                        break;
                    case COLD:
                        System.out.println(Shelf.class.getSimpleName() + " order " + order.getId() + " placed on cold shelf position: " + freePos + " isMoved:" + order.isMoved());
                        break;
                    case FROZEN:
                        System.out.println(Shelf.class.getSimpleName() + " order " + order.getId() + " placed on frozen shelf position: " + freePos + " isMoved:" + order.isMoved());
                }
                if(!order.isMoved()) {
                    order.setPlacementTime();
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"INITIAL placement: order " + order.getId() + " placed on " + temperature + " shelf");
                } else {
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"MOVE placement: order " + order.getId() + " moved to " + temperature + " shelf");
                }
                return true;
            }
            return false;
        } finally {
            masterLock.unlock();
            lock.unlock();
        }

    }

    public boolean isCellAvailable() {
        lock.lock();
        try {
            return !availableCells.isEmpty();
        } finally {
            lock.unlock();
        }
    }

//    public boolean hasAvailableCells() {
//        lock.lock();
//        try {
//            return !availableCells.isEmpty();
//        } finally {
//            lock.unlock();
//        }
//    }

    public int lookup(UUID id) {
        lock.lock();
        try {
            if(locations.get(id) == null) {
                System.out.println(Shelf.class.getSimpleName() + " order " + id + " not found on " + temperature + " shelf");
                return -1;
            }
            System.out.println(Shelf.class.getSimpleName() + " order " + id + " found on " + temperature + " shelf at position " + locations.get(id));
            return locations.get(id);
        } finally {
            lock.unlock();
        }
    }

    public void remove(UUID id, boolean pastDueTime) {
        lock.lock();

        int pos = lookup(id);
        masterLock.lock();
        if (pos > -1) {
            cells[pos] = null;
            availableCells.offer(pos);
            locations.remove(id);
            if(pastDueTime) {
                System.out.println(Shelf.class.getSimpleName() + " cleaned from " + temperature + " shelf: " + id);
                ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + id + " cleaned from " + temperature + " shelf");
            } else {
                System.out.println(Shelf.class.getSimpleName() + " delivered from " + temperature + " shelf: " + id);
                ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - delivered: order " + id + " picked up from " + temperature + " shelf");
            }
        } else {
            System.out.println(Shelf.class.getSimpleName() + " can not find on shelf: " + id);
        }
        masterLock.unlock();
        lock.unlock();
    }

    public void discardPastDue() {
        lock.lock();
        try {
            Iterator<Map.Entry<UUID, Integer>> it = locations.entrySet().iterator();
            while(it.hasNext()) {
               Map.Entry<UUID, Integer> entry = it.next();
               Order o = cells[entry.getValue()];
                double lifeValue = o.computeRemainingLifeValue(1);
                if (lifeValue <= 0) {
                    masterLock.lock();
                    it.remove();
                    cells[entry.getValue()] = null;
                    availableCells.offer(entry.getValue());
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + o.getId() + " cleaned from " + temperature + " shelf");
                    masterLock.unlock();
                }
            }
        } catch (Exception e ) {
            System.out.println("exception");
        }finally {
            lock.unlock();
        }

    }

    public void readContentOnShelf() {
//        lock.lock();
        masterLock.lock();
        int pos = 0;
        while (pos < capacity && cells[pos]/*cells.get(pos) */!= null) {
            Order o = cells[pos];
//            Order o = cells.get(pos);
            System.out.println(this.temperature + " shelf - order id: " + o.getId() + ", value: " + o.computeRemainingLifeValue(1) + ", pos: " + pos + ", temp:" + o.getTemp());
            pos++;
        }
        masterLock.unlock();
//        lock.unlock();
    }

    private void validateStateMaintained() {

    }
}
