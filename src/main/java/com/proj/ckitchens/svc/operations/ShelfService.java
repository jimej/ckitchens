package com.proj.ckitchens.svc.operations;

import com.proj.ckitchens.common.DoublyLinkedNode;
import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.model.Shelf;
import com.proj.ckitchens.svc.ShelfMgmtSystem;
import com.proj.ckitchens.utils.DataIntegrityViolation;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;

public class ShelfService {
    private final Shelf shelf;
    public ShelfService(Shelf shelf) {
        this.shelf = shelf;
    }
    public boolean placeOnShelf(Order order) {
        try {
            shelf.getLock().lock();
            masterLock.lock();
            validateStateMaintained();
            if (!shelf.getAvailableCells().isEmpty()) {
                int freePos = shelf.getAvailableCells().poll();
                shelf.getCells()[freePos] = order;
                DoublyLinkedNode curr = new DoublyLinkedNode(freePos);
                putOrderOnOverflowHelper(order, curr);
//                DoublyLinkedNode[] temp;
//                switch(order.getTemp()) {
//                    case HOT:
//                        temp = putOrderOnOverflowHelper(order, hotHead, hotTail, curr);
//                        hotHead = temp[0];
//                        hotTail = temp[1];
//                        break;
//                    case COLD:
//                        temp = putOrderOnOverflowHelper(order, coldHead, coldTail, curr);
//                        coldHead = temp[0];
//                        coldTail = temp[1];
//                        break;
//                    case FROZEN:
//                        temp = putOrderOnOverflowHelper(order, frozenHead, frozenTail, curr);
//                        frozenHead = temp[0];
//                        frozenTail = temp[1];
//                }
                validateStateMaintained();
                ShelfMgmtSystem.readContents("INITIAL placement: order " + order.getId() + " placed on " + shelf.getName() + " shelf at pos: "+ freePos + ", temp: " + order.getTemp(), shelf.getClass().getSimpleName());
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
    public boolean hasOnShelf(Temperature temp) {
        shelf.getLock().lock();
        try {
            return (temp == Temperature.HOT && shelf.getHotTail() != null)
                    || (temp == Temperature.COLD && shelf.getColdTail() != null)
                    || (temp == Temperature.FROZEN && shelf.getFrozenTail() != null);
        } finally {
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


    /**
     * remove an order of certain temperature on overflow to make space for new order placement
     * It's called only after verifying the order exists and the move to HOT/COLD/FROZEN shelf is allowed
     * @param temp
     * @return
     */
    public Order removeBasedOnTemperature(Temperature temp) {
        shelf.getLock().lock();
        int pos = -1;
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
        masterLock.lock();
        validateStateMaintained();
        removeOrderHelper(id);
        validateStateMaintained();
        ShelfMgmtSystem.readContents("MOVED - random order " + o.getId() + " moved from " + shelf.getName() + " shelf to " + o.getTemp() + " shelf", shelf.getClass().getSimpleName());
        try {
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
     * @return
     */
    public Order discardRandom() {
        shelf.getLock().lock();
        try {
            masterLock.lock();
            if (shelf.getCapacity() == 0) return null;
            int pos = new Random().nextInt(shelf.getCapacity());
            Order o = shelf.getCells()[pos];
            validateStateMaintained();
            removeOrderHelper(o.getId());
            validateStateMaintained();
            ShelfMgmtSystem.readContents("REMOVAL - discarded: random order from position " + pos + " - " + o.getId() + " discarded from " + shelf.getName() + " shelf; temp: " + o.getTemp(), shelf.getClass().getSimpleName());
            return o;
        } finally {
            masterLock.unlock();
            shelf.getLock().unlock();
        }
    }

    /**
     * remove an order from shelf for delivery
     * @param order
     * @return
     */
    public boolean removeForDelivery(Order order) {
        shelf.getLock().lock();

        // node is null when the order is not on this shelf
        // not arrived, discarded, cleaned, moved
        DoublyLinkedNode node = shelf.getLocations().get(order.getId());
//        if (node == null) throw new DataIntegrityViolation("not correct");
        System.out.println(" node not found on " + shelf.getName() + " shelf");
        try {
            masterLock.lock();
            if (node != null) {
                validateStateMaintained();
                removeOrderHelper(order.getId());
                validateStateMaintained();
//                if(pastDueTime) {
//                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + order.getId() + " from " + shelf.getName() + " shelf; temp: " + order.getTemp(), shelf.getClass().getSimpleName());
//                } else {
                ShelfMgmtSystem.readContents("REMOVAL - delivered: order " + order.getId() + " from " + shelf.getName() + " shelf; temp: " + order.getTemp(), shelf.getClass().getSimpleName());
//                }
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
    public void cleanup() {
        shelf.getLock().lock();
        try {
            for (int i = 0; i < shelf.getCapacity(); i++) {
                Order order = shelf.getCells()[i];
                if (order == null) continue;
                double lifeValue = order.computeRemainingLifeValue(2);
                if (lifeValue <=0) {
                    validateStateMaintained();
                    masterLock.lock();
                    removeOrderHelper(order.getId());
                    ShelfMgmtSystem.readContents("REMOVAL - cleaned: order " + order.getId() + " cleaned from " + shelf.getName() + " shelf; temp: " + order.getTemp(), shelf.getClass().getSimpleName());
                    validateStateMaintained();
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
    public void readContentOnShelf() {
        masterLock.lock();
        for (int pos = 0; pos < shelf.getCapacity(); pos++) {
            Order o = shelf.getCells()[pos];
            if (o != null) {
                System.out.println(shelf.getName() + " shelf - order id: " + o.getId() + ", value: " + o.computeRemainingLifeValue(2) + ", pos: " + pos + ", temp:" + o.getTemp());
            }
        }
        masterLock.unlock();
    }

    /**
     * extract a DoublyLinkedNode, update previous/next and head/tail
     * @param node
     * @param h
     * @param t
     * @return
     */
    private DoublyLinkedNode[] extractNode(DoublyLinkedNode node, DoublyLinkedNode h, DoublyLinkedNode t) {
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
    private void removeOrderHelper(UUID id) {
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
                temp = extractNode(node, shelf.getHotHead(), shelf.getHotTail());
//                shelf.getHotHead() = temp[0];
//                hotTail = temp[1];
                shelf.setHotHeadTail(temp[0], temp[1]);
                break;
            case COLD:
                temp = extractNode(node, shelf.getColdHead(), shelf.getColdTail());
//                coldHead = temp[0];
//                coldTail = temp[1];
                shelf.setColdHeadTail(temp[0], temp[1]);
                break;
            case FROZEN:
                temp = extractNode(node, shelf.getFrozenHead(), shelf.getFrozenTail());
//                frozenHead = temp[0];
//                frozenTail = temp[1];
                shelf.setFrozenHeadTail(temp[0], temp[1]);
        }
        shelf.getLock().unlock();
    }

    private /*DoublyLinkedNode[]*/ void putOrderOnOverflowHelper(Order o, DoublyLinkedNode curr) {
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

//        DoublyLinkedNode[] temp = new DoublyLinkedNode[] {h, t};
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
//                    hotHead = h;
//                    hotTail = t;
                    shelf.setHotHeadTail(h, t);
                    break;
                case COLD:
//                    coldHead = h;
//                    coldTail = t;
                    shelf.setColdHeadTail(h, t);
                    break;
                case FROZEN:
//                    frozenHead = h;
//                    frozenTail = t;
                    shelf.setFrozenHeadTail(h, t);
            }
            shelf.getLocations().put(o.getId(), curr);
            o.setPlacementTime();
//            return temp;
        } finally {
            shelf.getLock().unlock();
        }

    }

    private void validateStateMaintained() {
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
                node = node.next();  //also verify the middle node: next previous not null //maybe do a previous loop
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
                node = node.previous();  //also verify the middle node: next previous not null //maybe do a previous loop
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
