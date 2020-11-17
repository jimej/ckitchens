package com.proj.ckitchens.svc.operations;

import com.proj.ckitchens.common.DoublyLinkedNode;
import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.model.Shelf;
import com.proj.ckitchens.utils.DataIntegrityViolation;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;
import static com.proj.ckitchens.svc.ShelfMgmtSystem.shelfMgmtSystem;

/**
 * service on a single shelf
 */
public class ShelfService {
    private final Shelf hotShelf;
    private final Shelf coldShelf;
    private final Shelf frozenShelf;
    private final Shelf overflowShelf;
    private static final Logger logger = LogManager.getLogger(ShelfService.class);
    public ShelfService(Shelf hotShelf, Shelf coldShelf, Shelf frozenShelf, Shelf overflowShelf) {
        this.hotShelf = hotShelf;
        this.coldShelf = coldShelf;
        this.frozenShelf = frozenShelf;
        this.overflowShelf = overflowShelf;;
    }

    /**
     * place an order on shelf
     * also check if it's initial placement or an order is moved from overflow
     * set placement time or move time for life value calculation
     * @param order
     * @param shelf
     * @return true if order is placed on shelf, false otherwise
     */
    public boolean placeOnShelf(Order order, Shelf shelf) {
        try {
            shelf.getLock().lock();
            masterLock.lock();
            validateStateMaintained(shelf);
            if (!shelf.getAvailableCells().isEmpty()) {
                int freePos = shelf.getAvailableCells().poll();
                shelf.getCells()[freePos] = order;
                DoublyLinkedNode curr = new DoublyLinkedNode(freePos);
                putOrderOnShelfHelper(order, curr, shelf);
                validateStateMaintained(shelf);

                if(!order.isMoved()) {
                    order.setPlacementTime();
                    shelfMgmtSystem.readContents("INITIAL placement: order " + order.getId() + " placed at " + freePos + " on " + shelf.getName() + " shelf", shelf.getClass().getSimpleName());
                } else {
                    shelfMgmtSystem.readContents("MOVE placement: order " + order.getId() + " moved to " + freePos + " on " + shelf.getName() + " shelf", shelf.getClass().getSimpleName());
                }
                return true;
            }
            return false;
        } finally {
            masterLock.unlock();
            shelf.getLock().unlock();
        }
    }

    /**
     * check if the overflow shelf has an order of certain temperature.
     * This order may be vacated from overflow to make space for new order placement.
     * @param temp
     * @return
     */
    public boolean hasOnShelf(Temperature temp, Shelf shelf) {
        if (!isOverflowShelf(shelf)) {
            throw new IllegalArgumentException("This method can only be called on Overflow shelf");
        }
        shelf.getLock().lock();
        try {
            return (temp == Temperature.HOT && shelf.getHotTail() != null)
                    || (temp == Temperature.COLD && shelf.getColdTail() != null)
                    || (temp == Temperature.FROZEN && shelf.getFrozenTail() != null);
        } finally {
            shelf.getLock().unlock();
        }
    }

    /**
     * checks if the shelf has space
     * @param shelf
     * @return true if the shelf has space
     */
    public boolean isCellAvailable(Shelf shelf) {
        shelf.getLock().lock();
        try {
            return !shelf.getAvailableCells().isEmpty();
        } finally {
            shelf.getLock().unlock();
        }
    }


    /**
     * remove an order of certain temperature on overflow to make space for new order placement
     * It's called only after verifying the order of the temperature exists and
     * HOT/COLD/FROZEN shelf has more space
     * @param temp
     * @return removed order from overflow shelf
     */
    public Order removeBasedOnTemperature(Temperature temp, Shelf shelf) {
        if (!isOverflowShelf(shelf)) {
            throw new IllegalArgumentException("This method can only be called on Overflow shelf");
        }
        shelf.getLock().lock();
        int pos = -1;
        try {
            masterLock.lock();
            switch (temp) {
                case HOT:
                    pos = shelf.getHotHead().value();
                    break;
                case COLD:
                    pos = shelf.getColdHead().value();
                    break;
                case FROZEN:
                    pos = shelf.getFrozenHead().value();
            }
            Order o = shelf.getCells()[pos];
            UUID id = o.getId();

            validateStateMaintained(shelf);
            removeOrderHelper(id, shelf);
            validateStateMaintained(shelf);
            shelfMgmtSystem.readContents("MOVED - random order " + o.getId() + " moved from " + shelf.getName() + " shelf at position " + pos+ " to " + o.getTemp() + " shelf", shelf.getClass().getSimpleName());

            return o;
        } finally {
            masterLock.unlock();
            shelf.getLock().unlock();
        }

    }

    /**
     * discard a random order from overflow shelf when overflow is full and moving an order from overflow
     * to regular shelf is not possible.
     *
     * @return the discarded order from overflow
     */
    public Order discardRandom(Shelf shelf) {
        if (!isOverflowShelf(shelf)) {
            throw new IllegalArgumentException("This method can only be called on Overflow shelf");
        }
        shelf.getLock().lock();
        try {
            masterLock.lock();
            if (shelf.getCapacity() == 0 || isCellAvailable(shelf)) return null; //overflow shelf must be full
            int pos = new Random().nextInt(shelf.getCapacity());
            Order o = shelf.getCells()[pos];
            validateStateMaintained(shelf);
            removeOrderHelper(o.getId(), shelf);
            validateStateMaintained(shelf);
            shelfMgmtSystem.readContents("REMOVAL - discarded: random order from position " + pos + " - " + o.getId() + " discarded from " + shelf.getName() + " shelf; temp: " + o.getTemp(), shelf.getClass().getSimpleName());
            return o;
        } finally {
            masterLock.unlock();
            shelf.getLock().unlock();
        }
    }

    /**
     * remove an order from shelf for delivery
     * boolean return value decides if need to check other shelves after checking on overflow
     * @param order
     * @return false if the order is not found on shelf; true when the order is found even it's not delivered
     * because life reaches 0 (since delivery starts at overflow, if it's found on overflow, no need to check other shelves)
     */
    public boolean removeForDelivery(Order order, Shelf shelf) {
        shelf.getLock().lock();

        // node is null when the order is not on this shelf
        // not arrived, discarded, cleaned, moved
        DoublyLinkedNode node = shelf.getLocations().get(order.getId());
        if (node == null) {
            logger.log(Level.DEBUG, "NOT FOUND - {} is not found on shelf {}", order.getId(), shelf.getName());
        }

        try {
            masterLock.lock();
            if (node != null) {
                int pos = shelf.getLocations().get(order.getId()).value();
                validateStateMaintained(shelf);
                double lifeValue = computeLifeValue(order, shelf);
                removeOrderHelper(order.getId(), shelf);
                validateStateMaintained(shelf);
                //if lifeValue reached 0 at delivery time
                if(lifeValue <= 0) {
                    shelfMgmtSystem.readContents("REMOVAL - past due: order " + order.getId() + " from " + shelf.getName() + " shelf at position " + pos + "; temp: " + order.getTemp(), shelf.getClass().getSimpleName());
                    return true;
                }
                shelfMgmtSystem.readContents("REMOVAL - delivered: order " + order.getId() + " from " + shelf.getName() + " shelf at position " + pos + "; temp: " + order.getTemp(), shelf.getClass().getSimpleName());
                return true;
            }
            return false;
        } finally {
            masterLock.unlock();
            shelf.getLock().unlock();
        }
    }

    /**
     * clean up all past due orders on the overflow shelf
     */
    public void cleanup(Shelf shelf) {
        shelf.getLock().lock();
        try {
            for (int i = 0; i < shelf.getCapacity(); i++) {
                Order order = shelf.getCells()[i];
                if (order == null) continue;
                double lifeValue = computeLifeValue(order, shelf);
                if (lifeValue <=0) {
                    validateStateMaintained(shelf);
                    masterLock.lock();
                    removeOrderHelper(order.getId(), shelf);
                    shelfMgmtSystem.readContents("REMOVAL - cleaned: order " + order.getId() + " cleaned from " + shelf.getName() + " shelf; temp: " + order.getTemp(), shelf.getClass().getSimpleName());
                    validateStateMaintained(shelf);
                    masterLock.unlock();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            shelf.getLock().unlock();
        }

    }

    /**
     * read content on shelf
     */
    public void readContentOnShelf(Shelf shelf) {
        masterLock.lock();
        for (int pos = 0; pos < shelf.getCapacity(); pos++) {
            Order o = shelf.getCells()[pos];
            if (o != null) {
                System.out.println(shelf.getName() + " shelf - order id: " + o.getId() + ", value: " + computeLifeValue(o, shelf)/*o.computeRemainingLifeValue(2)*/ + ", pos: " + pos + ", temp:" + o.getTemp());
            }
        }
        masterLock.unlock();
    }

    /**
     * compute remaining life value
     * @param order
     * @param shelf
     * @return
     */
    private double computeLifeValue(Order order, Shelf shelf) {
        double lifeValue;
        if (isOverflowShelf(shelf)) {
            lifeValue = order.computeRemainingLifeValue(2);
        } else {
            lifeValue = order.computeRemainingLifeValue(1);
        }
        return lifeValue;
    }

    /**
     * check if an overflow shelf
     * @param shelf
     * @return
     */
    private boolean isOverflowShelf(Shelf shelf) {
        return !(Temperature.HOT.name().equals(shelf.getName())
                || Temperature.COLD.name().equals(shelf.getName())
                || Temperature.FROZEN.name().equals(shelf.getName()));
    }

    /**
     * extract a DoublyLinkedNode, update previous/next and head/tail
     * @param node
     * @param h
     * @param t
     * @return
     */
    private DoublyLinkedNode[] extractNode(DoublyLinkedNode node, DoublyLinkedNode h, DoublyLinkedNode t, Shelf shelf) {
        shelf.getLock().lock();
        DoublyLinkedNode[] temp = new DoublyLinkedNode[] {h, t};
        try {
            if (node.previous() == null && node.next() == null) {
                temp[0] = null;
                temp[1] = null;
            } else if (node.next() == null) {
                node.previous().setNext(null);
                temp[1] = node.previous();
            } else if (node.previous() == null) {
                node.next().setPrevious(null);
                temp[0] = node.next();
            } else {
                node.next().setPrevious(node.previous());
                node.previous().setNext(node.next());
            }
            return temp;
        } finally {
            shelf.getLock().unlock();
        }
    }

    /**
     * precondition: the order must be on the shelf
     * @param id
     */
    private void removeOrderHelper(UUID id, Shelf shelf) {
        if(shelf.getLocations().get(id) == null) return;
        shelf.getLock().lock();
        DoublyLinkedNode node = shelf.getLocations().remove(id);
        if (node == null) throw new DataIntegrityViolation("Error: can not remove order not on shelf - order " + id);
        Order o = shelf.getCells()[node.value()];
        if (o == null) throw new DataIntegrityViolation("Error: order in locations map but not in cells");

        shelf.getCells()[node.value()] = null;

        shelf.getAvailableCells().offer(node.value());
        DoublyLinkedNode[] temp;
        switch (o.getTemp()) {
            case HOT:
                temp = extractNode(node, shelf.getHotHead(), shelf.getHotTail(), shelf);
                shelf.setHotHeadTail(temp[0], temp[1]);
                break;
            case COLD:
                temp = extractNode(node, shelf.getColdHead(), shelf.getColdTail(), shelf);
                shelf.setColdHeadTail(temp[0], temp[1]);
                break;
            case FROZEN:
                temp = extractNode(node, shelf.getFrozenHead(), shelf.getFrozenTail(), shelf);
                shelf.setFrozenHeadTail(temp[0], temp[1]);
        }
        shelf.getLock().unlock();
    }

    /**
     * put order o on shelf with position at curr.value(), update head and tail to maintain state
     * @param o
     * @param curr
     * @param shelf
     */
    private void putOrderOnShelfHelper(Order o, DoublyLinkedNode curr, Shelf shelf) {
        shelf.getLock().lock();
        DoublyLinkedNode h = null;
        DoublyLinkedNode t = null;
        switch (o.getTemp()) {
            case HOT:
                h = shelf.getHotHead();
                t = shelf.getHotTail();
                break;
            case COLD:
                h = shelf.getColdHead();
                t = shelf.getColdTail();
                break;
            case FROZEN:
                h = shelf.getFrozenHead();
                t = shelf.getFrozenTail();
        }

        try {
            if (t == null) {
                h = curr;
                t = curr;
            } else {
                t.setNext(curr);
                curr.setPrevious(t);
                t = curr;
            }
            switch (o.getTemp()) {
                case HOT:
                    shelf.setHotHeadTail(h, t);
                    break;
                case COLD:
                    shelf.setColdHeadTail(h, t);
                    break;
                case FROZEN:
                    shelf.setFrozenHeadTail(h, t);
            }
            shelf.getLocations().put(o.getId(), curr);
            o.setPlacementTime();
        } finally {
            shelf.getLock().unlock();
        }

    }

    public Shelf getHotShelf() {
        return hotShelf;
    }

    public Shelf getColdShelf() {
        return coldShelf;
    }

    public Shelf getFrozenShelf() {
        return frozenShelf;
    }

    public Shelf getOverflowShelf() {
        return overflowShelf;
    }

    /**
     * this verifies data is consistent on a shelf before and after an operation on the shelf
     * @param shelf
     */
    private void validateStateMaintained(Shelf shelf) {
        shelf.getLock().lock();
        try {
            Iterator<Map.Entry<UUID, DoublyLinkedNode>> it = shelf.getLocations().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, DoublyLinkedNode> a = it.next();
                UUID id = a.getKey();
                int pos = a.getValue().value();
                assert shelf.getCells()[pos].getId() == id;
            }
            Iterator<Integer> listIt = shelf.getAvailableCells().iterator();
            while (listIt.hasNext()) {
                int temp = listIt.next();
                assert shelf.getCells()[temp] == null;
            }
            assert shelf.getLocations().size() + shelf.getAvailableCells().size() == shelf.getCapacity();
            assert (shelf.getHotHead() == null && shelf.getHotTail() == null) || (shelf.getHotHead() != null && shelf.getHotTail() != null);
            assert (shelf.getColdHead() == null && shelf.getColdTail() == null) || (shelf.getColdHead() != null && shelf.getColdTail() != null);
            assert (shelf.getFrozenHead() == null && shelf.getFrozenTail() == null) || (shelf.getFrozenHead() != null && shelf.getFrozenTail() != null);
            assert (shelf.getHotHead() != shelf.getHotTail()) || (shelf.getHotHead() == null && shelf.getHotTail() == null) || (shelf.getHotHead().previous() == null && shelf.getHotTail().next() == null);
            assert (shelf.getColdHead() != shelf.getColdTail()) || (shelf.getColdHead() == null && shelf.getColdTail() == null) || (shelf.getColdHead().previous() == null && shelf.getColdTail().next() == null);
            assert (shelf.getFrozenHead() != shelf.getFrozenTail()) ||(shelf.getFrozenHead()==null && shelf.getFrozenTail() ==null)|| (shelf.getFrozenHead().previous() == null && shelf.getFrozenTail().next() == null);
            DoublyLinkedNode node = shelf.getHotHead();
            int sum = 0;
            while (node != null) {
                Order o = shelf.getCells()[node.value()];
                assert o != null && o.getTemp() == Temperature.HOT;
                sum++;
                node = node.next();
            }
            node = shelf.getColdHead();
            while (node != null) {
                Order o = shelf.getCells()[node.value()];
                assert o != null && o.getTemp() == Temperature.COLD;
                sum++;
                node = node.next();
            }
            node = shelf.getFrozenHead();
            while (node != null) {
                Order o = shelf.getCells()[node.value()];
                assert o != null && o.getTemp() == Temperature.FROZEN;
                sum++;
                node = node.next();
            }
            assert sum == shelf.getLocations().size();
            sum = 0;
            node = shelf.getHotTail();
            while (node != null) {
                Order o = shelf.getCells()[node.value()];
                assert o != null && o.getTemp() == Temperature.HOT;
                sum++;
                node = node.previous();
            }
            node = shelf.getColdTail();
            while (node != null) {
                Order o = shelf.getCells()[node.value()];
                assert o != null && o.getTemp() == Temperature.COLD;
                sum++;
                node = node.previous();
            }
            node = shelf.getFrozenTail();
            while (node != null) {
                Order o = shelf.getCells()[node.value()];
                assert o != null && o.getTemp() == Temperature.FROZEN;
                sum++;
                node = node.previous();
            }
            assert sum == shelf.getLocations().size();
        }catch (Exception e) {
            System.out.println(e);
        } finally {
            shelf.getLock().unlock();
        }
    }
}
