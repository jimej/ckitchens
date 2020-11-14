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

    /**
     * Order placement on a regular shelf
     * @param order
     * @return
     */
    public boolean placeOnShelf(Order order) {

        try {
            lock.lock();
            masterLock.lock();
            validateStateMaintained();
            if (!availableCells.isEmpty()) {
                int freePos = availableCells.poll();
                cells[freePos] = order;
                locations.put(order.getId(), freePos);
                validateStateMaintained();
                if(!order.isMoved()) {
                    order.setPlacementTime();
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"INITIAL placement: order " + order.getId() + " placed at " + freePos + " on " + temperature + " shelf", Shelf.class.getSimpleName());
                } else {
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"MOVE placement: order " + order.getId() + " moved to " + freePos + " on " + temperature + " shelf", Shelf.class.getSimpleName());
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

    public void removeForDelivery(UUID id) {
        lock.lock();

        Integer pos = locations.get(id);
        validateStateMaintained();
        masterLock.lock();
        if (pos != null) {
            cells[pos] = null;
            availableCells.offer(pos);
            locations.remove(id);
//            if(pastDueTime) {
//                ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + id + " at " + pos + " cleaned from " + temperature + " shelf", Shelf.class.getSimpleName());
//            } else {
                ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - delivered: order " + id + " picked up at " + pos + " from " + temperature + " shelf", Shelf.class.getSimpleName());
//            }
            validateStateMaintained();
        } else {
            System.out.println(Shelf.class.getSimpleName() + " can not find on shelf: " + id);
        }
        masterLock.unlock();
        lock.unlock();
    }

    public void cleanup() {
        lock.lock();
        validateStateMaintained();
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
                    validateStateMaintained();
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + o.getId() + " cleaned from " + temperature + " shelf", Shelf.class.getSimpleName());
                    masterLock.unlock();
                }
            }
        } catch (Exception e ) {
            e.printStackTrace();
        }finally {
            lock.unlock();
        }

    }

    public void readContentOnShelf() {
        masterLock.lock();
        int pos = 0;
        while (pos < capacity && cells[pos]!= null) {
            Order o = cells[pos];
            System.out.println(this.temperature + " shelf - order id: " + o.getId() + ", value: " + o.computeRemainingLifeValue(1) + ", pos: " + pos + ", temp:" + o.getTemp());
            pos++;
        }
        masterLock.unlock();
    }

    private void validateStateMaintained() {
        lock.lock();
        try {
            Iterator<Map.Entry<UUID, Integer>> it = locations.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> a = it.next();
                UUID id = a.getKey();
                int pos = a.getValue();
                assert cells[pos].getId() == id && cells[pos].getTemp() == this.temperature;
            }
            Iterator<Integer> listIt = availableCells.iterator();
            while (listIt.hasNext()) {
                int temp = listIt.next();
                assert cells[temp] == null;
            }
            assert locations.size() + availableCells.size() == capacity;
        } catch (Exception e ) {
            System.out.println(e);
        }
         finally {
            lock.unlock();
        }
    }
}
