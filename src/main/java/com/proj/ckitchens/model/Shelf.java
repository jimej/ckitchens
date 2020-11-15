package com.proj.ckitchens.model;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.svc.ShelfMgmtSystem;
import com.proj.ckitchens.utils.DataIntegrityViolation;

import java.util.*;
import java.util.concurrent.locks.Lock;

import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;

public class Shelf {
    private final Lock lock;
    private final int capacity;
    private final Order[] cells;
    private final String name;
    private final Queue<Integer> availableCells = new LinkedList<>();
    private final Map<UUID, Integer> locations;
    public Shelf(Lock lock, int capacity, String name) {
        this.lock = lock;
        this.capacity = capacity;
        this.cells = new Order[capacity];
        this.name = name;
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
                    ShelfMgmtSystem.readContents("INITIAL placement: order " + order.getId() + " placed at " + freePos + " on " + name + " shelf", Shelf.class.getSimpleName());
                } else {
                    ShelfMgmtSystem.readContents("MOVE placement: order " + order.getId() + " moved to " + freePos + " on " + name + " shelf", Shelf.class.getSimpleName());
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
            removeOrderHelper(id);
//            cells[pos] = null;
//            availableCells.offer(pos);
//            locations.remove(id);
//            if(pastDueTime) {
//                ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + id + " at " + pos + " cleaned from " + temperature + " shelf", Shelf.class.getSimpleName());
//            } else {
                ShelfMgmtSystem.readContents("REMOVAL - delivered: order " + id + " picked up at " + pos + " from " + name + " shelf", Shelf.class.getSimpleName());
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
            for (int i = 0; i < capacity; i++) {
                Order order = cells[i];
                if (order == null) continue;
                double lifeValue = order.computeRemainingLifeValue(1);
                if (lifeValue <=0) {
                    validateStateMaintained();
                    masterLock.lock();
                    removeOrderHelper(order.getId());
                    ShelfMgmtSystem.readContents("REMOVAL - cleaned: order " + order.getId() + " cleaned from " + name + " shelf; temp: " + order.getTemp(), Shelf.class.getSimpleName());
                    validateStateMaintained();
                    masterLock.unlock();
                }
            }

//            Iterator<Map.Entry<UUID, Integer>> it = locations.entrySet().iterator();
//            while(it.hasNext()) {
//               Map.Entry<UUID, Integer> entry = it.next();
//               Order o = cells[entry.getValue()];
//                double lifeValue = o.computeRemainingLifeValue(1);
//                if (lifeValue <= 0) {
//                    masterLock.lock();
//                    it.remove();
//                    cells[entry.getValue()] = null;
//                    availableCells.offer(entry.getValue());
//                    validateStateMaintained();
//                    ShelfMgmtSystem.readContents("REMOVAL - cleaned: order " + o.getId() + " cleaned from " + temperature + " shelf", Shelf.class.getSimpleName());
//                    masterLock.unlock();
//                }
//            }
        } catch (Exception e ) {
            e.printStackTrace();
        }finally {
            lock.unlock();
        }

    }

    public void readContentOnShelf() {
        masterLock.lock();
        for (int pos = 0; pos < capacity; pos++) {
            Order o = cells[pos];
            if (o != null) {
                System.out.println(this.name + " shelf - order id: " + o.getId() + ", value: " + o.computeRemainingLifeValue(1) + ", pos: " + pos + ", temp:" + o.getTemp());
            }
        }
        masterLock.unlock();
    }

    private void removeOrderHelper(UUID id) {
        Integer pos =  locations.remove(id);
        if(pos == null) throw new DataIntegrityViolation("Error: this method ");
        cells[pos] = null;
        availableCells.offer(pos);
//        locations.remove(id);
    }

    private void validateStateMaintained() {
        lock.lock();
        try {
            Iterator<Map.Entry<UUID, Integer>> it = locations.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> a = it.next();
                UUID id = a.getKey();
                int pos = a.getValue();
                assert cells[pos].getId() == id && cells[pos].getTemp().name() == this.name;
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

    public Lock getLock() {
        return this.lock;
    }
    public Order[] getCells() {
        return cells;
    }
    public Map<UUID, Integer> getLocations() {
        return locations;
    }
    public Queue<Integer> getAvailableCells() {
        return availableCells;
    }
    public int getCapacity() {
        return capacity;
    }

    public String getName() {
        return this.name;
    }
}
