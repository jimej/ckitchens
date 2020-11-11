package com.proj.ckitchens.model;

import com.proj.ckitchens.common.OrderQueue;
import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.svc.ShelfMgmtSystem;

import static com.proj.ckitchens.common.Temperature.*;

import java.util.*;
import java.util.concurrent.locks.Lock;

public class OverflowShelf extends Shelf {
    private final Lock lock;
    private final int capacity;
    private final Order[] cells;
    private final Queue<Integer> availableCells = new LinkedList<>();
    private final Queue<Integer> hotPositions = new LinkedList<>();
    private final Queue<Integer> coldPositions = new LinkedList<>();
    private final Queue<Integer> frozenPositions = new LinkedList<>();
    private final Map<UUID, Integer> locations;
    public OverflowShelf(Lock lock, int capacity) {
        super(lock, capacity, null);
        this.lock = lock;
        this.capacity = capacity;
        this.cells = new Order[capacity];
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
                switch(order.getTemp()) {
                    case HOT:
                        hotPositions.offer(freePos);
                        locations.put(order.getId(), freePos);
                        order.setPlacementTime();
                        System.out.println(OverflowShelf.class.getSimpleName() + " hot placed on overflow: " + order.getId() + " position " + freePos);
                        break;
                    case COLD:
                        coldPositions.offer(freePos);
                        locations.put(order.getId(), freePos);
                        order.setPlacementTime();
                        System.out.println(OverflowShelf.class.getSimpleName() + " cold placed on overflow: " + order.getId()  + " position " + freePos);
                        break;
                    case FROZEN:
                        frozenPositions.offer(freePos);
                        locations.put(order.getId(), freePos);
                        order.setPlacementTime();
                        System.out.println(OverflowShelf.class.getSimpleName() + " frozen placed on overflow: " + order.getId()  + " position " + freePos);
                }
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }

//        return false;
    }

    public boolean hasOnShelf(Temperature temp) {
        lock.lock();
        try {
            return (temp == Temperature.HOT && !hotPositions.isEmpty())
                || (temp == Temperature.COLD && !coldPositions.isEmpty())
                || (temp == Temperature.FROZEN && !frozenPositions.isEmpty());
//            switch (temp) {
//                case HOT:
//                    if(!hotPositions.isEmpty())
//                    break;
//                case COLD:
//                    break;
//                case FROZEN:
//            }
        } finally {
            lock.unlock();
        }
    }

    public Order removePos(Temperature temp) {
        lock.lock();
        Order o = null;
        int pos = -1;
        switch (temp) {
                case HOT:
                    pos = hotPositions.poll();
                    o = cells[pos];
                    cells[pos] = null;
                    availableCells.offer(pos);
                    locations.remove(o.getId());
                    System.out.println(OverflowShelf.class.getSimpleName() + " order " + o.getId() + " yielded hot position on overflow shelf " + o.getTemp() );
                    break;
                case COLD:
                    pos = coldPositions.poll();
                    o = cells[pos];
                    cells[pos] = null;
                    availableCells.offer(pos);
                    locations.remove(o.getId());
                    System.out.println(OverflowShelf.class.getSimpleName() + " order " + o.getId() + " yielded cold position on overflow shelf " + o.getTemp() );

                    break;
                case FROZEN:
                    pos = frozenPositions.poll();
                    o = cells[pos];
                    cells[pos] = null;
                    availableCells.offer(pos);
                    locations.remove(o.getId());
                    System.out.println(OverflowShelf.class.getSimpleName() + " order " + o.getId() + " yielded frozen position on overflow shelf " + o.getTemp() );

        }
            try {
                return o;
            } finally {
                lock.unlock();
            }

    }

    /**
     * only called when overflow shelf is full
     * @return
     */
    public Order discardRandom() {
        lock.lock();

        try {
           int pos = new Random().nextInt(capacity);
           Order o = cells[pos];
           cells[pos] = null;
           locations.remove(o.getId());
           availableCells.offer(pos);
           System.out.println(OverflowShelf.class.getSimpleName() + " discarded random order " + o.getId() + " from position " + pos);

            switch (o.getTemp()) {
                case HOT:
                    hotPositions.remove(pos);
                    break;
                case COLD:
                    coldPositions.remove(pos);
                    break;
                case FROZEN:
                    frozenPositions.remove(pos);
            }
            return o;
        } finally {
            lock.unlock();
        }
    }

    public int lookup(UUID id) {
        lock.lock();
        try {
            if(locations.get(id) == null) {
                System.out.println(OverflowShelf.class.getSimpleName() + " order " + id + " not found on overflow shelf");
                return -1;
            } else {
                System.out.println(OverflowShelf.class.getSimpleName() + " order " + id + " found on overflow shelf at position " + locations.get(id));
            }
            return locations.get(id);
        } finally {
            lock.unlock();
        }
    }

    public boolean remove(Order order) {
        lock.lock();
        int pos = lookup(order.getId());

        try {
            if (pos > -1) {
                System.out.println(OverflowShelf.class.getSimpleName() + " delivered from overflow shelf pos: " + order.getId() + " " + pos);
                cells[pos] = null;
                availableCells.offer(pos);
                locations.remove(order.getId());

                switch (order.getTemp()) {
                    case HOT:
                        hotPositions.remove(pos);
                        break;
                    case COLD:
                        coldPositions.remove(pos);
                        break;
                    case FROZEN:
                        frozenPositions.remove(pos);
                }
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void discardPastDue() {
        lock.lock();
        for(UUID id : locations.keySet()) {
            Order o = cells[locations.get(id)];
            double lifeValue =  o.computeRemainingLifeValue(2);
            if (lifeValue <=0) {
                remove(o);
            }

        }
        lock.unlock();
    }


//    public boolean hasAvailableCells() {
//        lock.lock();
//        try {
//            return !availableCells.isEmpty();
//        } finally {
//            lock.unlock();
//        }
//    }

}
