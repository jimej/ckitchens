package com.proj.ckitchens.model;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.svc.ShelfMgmtSystem;
import com.proj.ckitchens.common.DoublyLinkedNode;

import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.locks.Lock;

public class OverflowShelf extends Shelf {
    private final Lock lock;
    private final int capacity;
    private final Order[] cells;
    private final Queue<Integer> availableCells = new LinkedList<>();
    private static DoublyLinkedNode hotHead, coldHead, frozenHead;
    private static DoublyLinkedNode hotTail, coldTail, frozenTail;

    private final Map<UUID, DoublyLinkedNode> locations;
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

    /**
     * Order placement on Overflow shelf
     * @param order
     * @return
     */
    public boolean placePackaging(Order order) {
        try {
            lock.lock();
            masterLock.lock();
            if (!availableCells.isEmpty()) {
                int freePos = availableCells.poll();
                cells[freePos] = order;
                DoublyLinkedNode curr = new DoublyLinkedNode(freePos);
                switch(order.getTemp()) {
                    case HOT:
                        putOrderOnOverflowHelper(order, hotHead, hotTail, curr);
                        break;
                    case COLD:
                        putOrderOnOverflowHelper(order, coldHead, coldTail, curr);
                        break;
                    case FROZEN:
                        putOrderOnOverflowHelper(order, frozenHead, frozenTail, curr);
                }
                ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"INITIAL placement: order " + order.getId() + " placed on overflow shelf at pos: "+ freePos + ", temp: " + order.getTemp(), OverflowShelf.class.getSimpleName());
                return true;
            }
            return false;
        } finally {
            masterLock.unlock();
            lock.unlock();
        }
    }

    /**
     * check if the overflow shelf has an order of certain temperature.
     * This order may be vacated from overflow to make space for new order placement.
     * @param temp
     * @return
     */
    public boolean hasOnShelf(Temperature temp) {
        lock.lock();
        try {
            return (temp == Temperature.HOT && hotTail != null)
                    || (temp == Temperature.COLD && coldTail != null)
                    || (temp == Temperature.FROZEN && frozenTail != null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * remove an order of certain temperature on overflow to make space for new order placement
     * It's called only after verifying the order exists and the move to HOT/COLD/FROZEN shelf is allowed
     * @param temp
     * @return
     */
    public Order removeBasedOnTemperature(Temperature temp) {
        lock.lock();
        int pos = -1;
        switch (temp) {
            case HOT:
                pos = hotHead.value();
                break;
            case COLD:
                pos = coldHead.value();
                break;
            case FROZEN:
                pos = frozenHead.value();
        }
        Order o = cells[pos];
        UUID id = o.getId();
        masterLock.lock();
        removeOrderHelper(temp, pos, id);
        ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"MOVED - random order " + o.getId() + " moved from overflow shelf to " + o.getTemp() + " shelf", OverflowShelf.class.getSimpleName());
        try {
            return o;
        } finally {
            masterLock.unlock();
            lock.unlock();
        }

    }

    /**
     * discard a random order from overflow shelf when overflow is full and moving an order from overflow
     * to regular shelf is not possible.
     *
     * @return
     */
    public Order discardRandom() {
        lock.lock();
        try {
            masterLock.lock();
            if (capacity == 0) return null;
            int pos = new Random().nextInt(capacity);
            Order o = cells[pos];
            removeOrderHelper(o.getTemp(), pos, o.getId());
            ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - discarded: random order from position " + pos + " - " + o.getId() + " discarded from overflow shelf; temp: " + o.getTemp(), OverflowShelf.class.getSimpleName());
            return o;
        } finally {
            masterLock.unlock();
            lock.unlock();
        }
    }

    /**
     * look up the location on the shelf given order id
     * @param id
     * @return
     */
    public int lookup(UUID id) {
        lock.lock();
        try {
            if(locations.get(id) == null) {
                System.out.println(OverflowShelf.class.getSimpleName() + " order " + id + " not found on overflow shelf");
                return -1;
            } else {
                System.out.println(OverflowShelf.class.getSimpleName() + " order " + id + " found on overflow shelf at position " + locations.get(id).value());
            }
            return locations.get(id).value();
        } finally {
            lock.unlock();
        }
    }

    /**
     * remove an order from shelf for delivery
     * @param order
     * @param pastDueTime
     * @return
     */
    public boolean removeForDelivery(Order order, boolean pastDueTime) {
        lock.lock();
        DoublyLinkedNode node = locations.get(order.getId());
        Integer pos = node != null? node.value() : null;

        try {
            masterLock.lock();
            if (pos != null) {
                removeOrderHelper(order.getTemp(), pos, order.getId());
                if(pastDueTime) {
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + order.getId() + " from overflow shelf; temp: " + order.getTemp(), OverflowShelf.class.getSimpleName());
                } else {
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - delivered: order " + order.getId() + " from overflow shelf; temp: " + order.getTemp(), OverflowShelf.class.getSimpleName());
                }
                return true;
            }
            return false;
        } finally {
            masterLock.unlock();
            lock.unlock();
        }
    }

    /**
     * discard all past due orders on the overflow shelf
     */
    public void discardPastDue() {
        lock.lock();
        try {
            Iterator<Map.Entry<UUID, DoublyLinkedNode>> it = locations.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<UUID, DoublyLinkedNode> entry = it.next();
                DoublyLinkedNode node = entry.getValue();
                Order o = cells[node.value()];

                double lifeValue = o.computeRemainingLifeValue(2);
                if (lifeValue <= 0) {
                    masterLock.lock();
                    it.remove();
                    cells[node.value()] = null;
                    availableCells.offer(node.value());
                    switch (o.getTemp()) {
                        case HOT:
                            extractNode(node, hotHead, hotTail);
                            break;
                        case COLD:
                            extractNode(node, coldHead, coldTail);
                            break;
                        case FROZEN:
                            extractNode(node, frozenHead, frozenTail);
                    }
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + o.getId() + " cleaned from overflow shelf; temp: " + o.getTemp(), OverflowShelf.class.getSimpleName());
                    masterLock.unlock();
                }
            }

        } catch (Exception e) {
            System.out.println("exception");
        } finally {
            lock.unlock();
        }

    }

    /**
     * read content on shelf
     */
    public void readContentOnShelf() {
        masterLock.lock();
        int pos = 0;
        while (pos < capacity && cells[pos] != null) {
            Order o = cells[pos];
            System.out.println("Overflow shelf - order id: " + o.getId() + ", value: " + o.computeRemainingLifeValue(2) + ", pos: " + pos + ", temp:" + o.getTemp());
            pos++;
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
    private void extractNode(DoublyLinkedNode node, DoublyLinkedNode h, DoublyLinkedNode t) {
        lock.lock();
        if(node.previous() == null && node.next() == null) {
            h = null;
            t = null;
        } else if (node.next() == null) {
            node.previous().setNext(null);
            t = node.previous();
        } else if (node.previous() == null) {
            node.next().setPrevious(null);
            h = node.next();
        } else {
            node.next().setPrevious(node.previous());
            node.previous().setNext(node.next());
        }
        lock.unlock();
    }

    private void removeOrderHelper(Temperature temp, int pos, UUID id) {
        lock.lock();
        cells[pos] = null;
        DoublyLinkedNode node = locations.remove(id);
        availableCells.offer(pos);
        switch (temp) {
            case HOT:
                extractNode(node, hotHead, hotTail);
                break;
            case COLD:
                extractNode(node, coldHead, coldTail);
                break;
            case FROZEN:
                extractNode(node, frozenHead, frozenTail);
        }
        lock.unlock();
    }

    private void putOrderOnOverflowHelper(Order o, DoublyLinkedNode h, DoublyLinkedNode t, DoublyLinkedNode curr) {
        if(t == null) {
            t = curr;
            h = curr;
        } else {
            t.setNext(curr);
            curr.setPrevious(t);
            t = curr;
        }
        locations.put(o.getId(), curr);
        o.setPlacementTime();
    }
}
