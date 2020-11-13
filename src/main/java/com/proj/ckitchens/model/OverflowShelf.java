package com.proj.ckitchens.model;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.svc.ShelfMgmtSystem;

import static com.proj.ckitchens.common.Temperature.*;
import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;

import java.time.LocalTime;
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

    public boolean placePackaging(Order order) {

        try {
            lock.lock();
            masterLock.lock();
            if (!availableCells.isEmpty()) {
                int freePos = availableCells.poll();
                cells[freePos] = order;
                switch(order.getTemp()) {
                    case HOT:
                        hotPositions.offer(freePos);
                        locations.put(order.getId(), freePos);
                        order.setPlacementTime();
                        System.out.println(OverflowShelf.class.getSimpleName() + " hot placed on overflow: " + order.getId() + " position " + freePos);
                        ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"INITIAL placement: order " + order.getId() + " placed on overflow shelf; temp: " + HOT);
                        break;
                    case COLD:
                        coldPositions.offer(freePos);
                        locations.put(order.getId(), freePos);
                        order.setPlacementTime();
                        System.out.println(OverflowShelf.class.getSimpleName() + " cold placed on overflow: " + order.getId()  + " position " + freePos);
                        ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"INITIAL placement: order " + order.getId() + " placed on overflow shelf; temp: " + COLD);
                        break;
                    case FROZEN:
                        frozenPositions.offer(freePos);
                        locations.put(order.getId(), freePos);
                        order.setPlacementTime();
                        System.out.println(OverflowShelf.class.getSimpleName() + " frozen placed on overflow: " + order.getId()  + " position " + freePos);
                        ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"INITIAL placement: order " + order.getId() + " placed on overflow shelf; temp: " + FROZEN);
                }
                return true;
            }
            return false;
        } finally {
            masterLock.unlock();
            lock.unlock();
        }

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
        masterLock.lock();
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
        ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"MOVED - random order " + o.getId() + " moved from overflow shelf to " + o.getTemp() + " shelf");
        try {
                return o;
            } finally {
                masterLock.unlock();
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
           masterLock.lock();
           Order o = cells[pos];
           cells[pos] = null;
           locations.remove(o.getId());
           availableCells.offer(pos);

            switch (o.getTemp()) {
                case HOT:
                    hotPositions.remove(Integer.valueOf(pos));
                    break;
                case COLD:
                    coldPositions.remove(Integer.valueOf(pos));
                    break;
                case FROZEN:
                    frozenPositions.remove(Integer.valueOf(pos));
            }
            System.out.println(OverflowShelf.class.getSimpleName() + " discarded random order " + o.getId() + " from position " + pos);
            ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - discarded: random order " + o.getId() + " discarded from overflow shelf; temp: " + o.getTemp());
            return o;
        } finally {
            masterLock.unlock();
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

    public boolean remove(Order order, boolean pastDueTime) {
        lock.lock();
        int pos = lookup(order.getId());

        try {
            masterLock.lock();
            if (pos > -1) {
                cells[pos] = null;
                availableCells.offer(pos);
                locations.remove(order.getId());

                switch (order.getTemp()) {
                    case HOT:
                        hotPositions.remove(Integer.valueOf(pos));
                        break;
                    case COLD:
                        coldPositions.remove(Integer.valueOf(pos));
                        break;
                    case FROZEN:
                        frozenPositions.remove(Integer.valueOf(pos));
                }
                if(pastDueTime) {
                    System.out.println(OverflowShelf.class.getSimpleName() + " cleaned from overflow shelf pos: " + order.getId() + " " + pos);
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + order.getId() + " from overflow shelf; temp: " + order.getTemp());
                } else {
                    System.out.println(OverflowShelf.class.getSimpleName() + " delivered from overflow shelf pos: " + order.getId() + " " + pos);
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - delivered: order " + order.getId() + " from overflow shelf; temp: " + order.getTemp());
                }
                return true;
            }
            return false;
        } finally {
            masterLock.unlock();
            lock.unlock();
        }
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
                    switch (o.getTemp()) {
                        case HOT:
                            hotPositions.remove(Integer.valueOf(entry.getValue()));
                            break;
                        case COLD:
                            coldPositions.remove(Integer.valueOf(entry.getValue()));
                            break;
                        case FROZEN:
                            frozenPositions.remove(Integer.valueOf(entry.getValue()));
                    }
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + o.getId() + " cleaned from overflow shelf; temp: " + o.getTemp());
                    masterLock.unlock();
                }
            }

        } catch (Exception e) {
            System.out.println("exception");
        } finally {
            lock.unlock();
        }

    }

    public void readContentOnShelf() {
//        lock.lock();
        masterLock.lock();
        int pos = 0;
        while (pos < capacity && cells[pos] != null) {
            Order o = cells[pos];
            System.out.println("Overflow shelf - order id: " + o.getId() + ", value: " + o.computeRemainingLifeValue(2) + ", pos: " + pos + ", temp:" + o.getTemp());
            pos++;
        }
        masterLock.unlock();
//        lock.unlock();
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
