package com.proj.ckitchens.model;

import com.proj.ckitchens.common.OrderQueue;
import com.proj.ckitchens.common.Temperature;

import java.util.*;
import java.util.concurrent.locks.Lock;

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
//        emptyCells = Arrays.asList(0);
        for(int i = 0; i < capacity; i++) {
            availableCells.offer(i);
        }
        locations = new HashMap<>();
    }

    public void readContents() {
        lock.lock();

        lock.unlock();
    }

    public boolean placePackaging(Order order) {
        lock.lock();
        try {
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
                }
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }

//        return false;
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

    public void remove(UUID id) {
        lock.lock();

        int pos = lookup(id);
        if (pos > -1) {
            System.out.println(Shelf.class.getSimpleName() + " delivered from " + temperature + " shelf: " + id);
            cells[pos] = null;
            availableCells.offer(pos);
            locations.remove(id);
        } else {
            System.out.println(Shelf.class.getSimpleName() + " can not find on shelf: " + id);
        }
        lock.unlock();
    }

    public void discardPastDue() {
        lock.lock();
        for(UUID id : locations.keySet()) {
            Order o = cells[locations.get(id)];
            double lifeValue =  o.computeRemainingLifeValue(1);
            if (lifeValue <=0) {
                remove(o.getId());
            }

        }
        lock.unlock();
    }

    private void maintainState() {

    }
}
