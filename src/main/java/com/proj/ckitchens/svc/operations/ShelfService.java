package com.proj.ckitchens.svc.operations;

import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.model.Shelf;
import com.proj.ckitchens.svc.ShelfMgmtSystem;
import com.proj.ckitchens.utils.DataIntegrityViolation;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;

public class ShelfService {
    private final Shelf shelf;
    public ShelfService(Shelf shelf) {
        this.shelf = shelf;
    }

    /**
     * Order placement on a regular shelf
     * @param order
     * @return
     */
    public boolean placeOnShelf(Order order) {

        try {
            shelf.getLock().lock();
            if (!shelf.getName().equals(order.getTemp().name())) {
                return false;
            }
            masterLock.lock();
            validateStateMaintained();
            if (!shelf.getAvailableCells().isEmpty()) {
                int freePos = shelf.getAvailableCells().poll();
                shelf.getCells()[freePos] = order;
                shelf.getLocations().put(order.getId(), freePos);
                validateStateMaintained();
                if(!order.isMoved()) {
                    order.setPlacementTime();
                    ShelfMgmtSystem.readContents("INITIAL placement: order " + order.getId() + " placed at " + freePos + " on " + shelf.getName() + " shelf", Shelf.class.getSimpleName());
                } else {
                    ShelfMgmtSystem.readContents("MOVE placement: order " + order.getId() + " moved to " + freePos + " on " + shelf.getName() + " shelf", Shelf.class.getSimpleName());
                }
                return true;
            }
            return false;
        } finally {
            masterLock.unlock();
            shelf.getLock().unlock();
        }

    }

    public boolean isCellAvailable() {
        shelf.getLock().lock();
        try {
            return !shelf.getAvailableCells().isEmpty();
        } finally {
            shelf.getLock().unlock();
        }
    }

    public void removeForDelivery(UUID id) {
        shelf.getLock().lock();

        Integer pos = shelf.getLocations().get(id);
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
            ShelfMgmtSystem.readContents("REMOVAL - delivered: order " + id + " picked up at " + pos + " from " + shelf.getName() + " shelf", Shelf.class.getSimpleName());
//            }
            validateStateMaintained();
        } else {
            System.out.println(Shelf.class.getSimpleName() + " can not find on shelf: " + id);
        }
        masterLock.unlock();
        shelf.getLock().unlock();
    }

    public void cleanup() {
        shelf.getLock().lock();
        validateStateMaintained();
        try {
            for (int i = 0; i < shelf.getCapacity(); i++) {
                Order order = shelf.getCells()[i];
                if (order == null) continue;
                double lifeValue = order.computeRemainingLifeValue(1);
                if (lifeValue <= 0) {
                    validateStateMaintained();
                    masterLock.lock();
                    removeOrderHelper(order.getId());
                    ShelfMgmtSystem.readContents("REMOVAL - cleaned: order " + order.getId() + " cleaned from " + shelf.getName() + " shelf; temp: " + order.getTemp(), Shelf.class.getSimpleName());
                    validateStateMaintained();
                    masterLock.unlock();
                }
            }
        } catch (Exception e ) {
            e.printStackTrace();
        }finally {
            shelf.getLock().unlock();
        }

    }

    public void readContentOnShelf() {
        masterLock.lock();
        for (int pos = 0; pos < shelf.getCapacity(); pos++) {
            Order o = shelf.getCells()[pos];
            if (o != null) {
                System.out.println(shelf.getName() + " shelf - order id: " + o.getId() + ", value: " + o.computeRemainingLifeValue(1) + ", pos: " + pos + ", temp:" + o.getTemp());
            }
        }
        masterLock.unlock();
    }

    private void removeOrderHelper(UUID id) {
        Integer pos =  shelf.getLocations().remove(id);
        if(pos == null) throw new DataIntegrityViolation("Error: this method ");
        shelf.getCells()[pos] = null;
        shelf.getAvailableCells().offer(pos);
//        locations.remove(id);
    }

    private void validateStateMaintained() {
        shelf.getLock().lock();
        try {
            Iterator<Map.Entry<UUID, Integer>> it = shelf.getLocations().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> a = it.next();
                UUID id = a.getKey();
                int pos = a.getValue();
                assert shelf.getCells()[pos].getId() == id && shelf.getCells()[pos].getTemp().name() == shelf.getName();
            }
            Iterator<Integer> listIt = shelf.getAvailableCells().iterator();
            while (listIt.hasNext()) {
                int temp = listIt.next();
                assert shelf.getCells()[temp] == null;
            }
            assert shelf.getLocations().size() + shelf.getAvailableCells().size() == shelf.getCapacity();
        } catch (Exception e ) {
            System.out.println(e);
        }
        finally {
            shelf.getLock().unlock();
        }
    }

}
