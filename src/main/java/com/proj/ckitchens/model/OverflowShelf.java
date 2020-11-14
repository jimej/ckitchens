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
    public boolean placeOnShelf(Order order) {
        try {
            lock.lock();
            masterLock.lock();
            validateStateMaintained();
            if (!availableCells.isEmpty()) {
                int freePos = availableCells.poll();
                cells[freePos] = order;
                DoublyLinkedNode curr = new DoublyLinkedNode(freePos);
                DoublyLinkedNode[] temp;
                switch(order.getTemp()) {
                    case HOT:
                        temp = putOrderOnOverflowHelper(order, hotHead, hotTail, curr);
                        hotHead = temp[0];
                        hotTail = temp[1];
                        break;
                    case COLD:
                        temp = putOrderOnOverflowHelper(order, coldHead, coldTail, curr);
                        coldHead = temp[0];
                        coldTail = temp[1];
                        break;
                    case FROZEN:
                        temp = putOrderOnOverflowHelper(order, frozenHead, frozenTail, curr);
                        frozenHead = temp[0];
                        frozenTail = temp[1];
                }
                validateStateMaintained();
                ShelfMgmtSystem.readContents("INITIAL placement: order " + order.getId() + " placed on overflow shelf at pos: "+ freePos + ", temp: " + order.getTemp(), OverflowShelf.class.getSimpleName());
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
        validateStateMaintained();
        removeOrderHelper(temp, pos, id);
        validateStateMaintained();
        ShelfMgmtSystem.readContents("MOVED - random order " + o.getId() + " moved from overflow shelf to " + o.getTemp() + " shelf", OverflowShelf.class.getSimpleName());
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
            validateStateMaintained();
            removeOrderHelper(o.getTemp(), pos, o.getId());
            validateStateMaintained();
            ShelfMgmtSystem.readContents("REMOVAL - discarded: random order from position " + pos + " - " + o.getId() + " discarded from overflow shelf; temp: " + o.getTemp(), OverflowShelf.class.getSimpleName());
            return o;
        } finally {
            masterLock.unlock();
            lock.unlock();
        }
    }

    /**
     * remove an order from shelf for delivery
     * @param order
     * @return
     */
    public boolean removeForDelivery(Order order) {
        lock.lock();
        DoublyLinkedNode node = locations.get(order.getId());
        Integer pos = node != null? node.value() : null;

        try {
            masterLock.lock();
            if (pos != null) {
                validateStateMaintained();
                removeOrderHelper(order.getTemp(), pos, order.getId());
                validateStateMaintained();
//                if(pastDueTime) {
//                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + order.getId() + " from overflow shelf; temp: " + order.getTemp(), OverflowShelf.class.getSimpleName());
//                } else {
                    ShelfMgmtSystem.readContents("REMOVAL - delivered: order " + order.getId() + " from overflow shelf; temp: " + order.getTemp(), OverflowShelf.class.getSimpleName());
//                }
                return true;
            }
            return false;
        } finally {
            masterLock.unlock();
            lock.unlock();
        }
    }

    /**
     * clean up all past due orders on the overflow shelf
     */
    public void cleanup() {
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
                    validateStateMaintained();
                    it.remove();
                    cells[node.value()] = null;
                    availableCells.offer(node.value());
                    DoublyLinkedNode[] temp;
                    switch (o.getTemp()) {
                        case HOT:
                            temp = extractNode(node, hotHead, hotTail);
                            hotHead = temp[0];
                            hotTail = temp[1];
                            break;
                        case COLD:
                            temp = extractNode(node, coldHead, coldTail);
                            coldHead = temp[0];
                            coldTail = temp[1];
                            break;
                        case FROZEN:
                            temp = extractNode(node, frozenHead, frozenTail);
                            frozenHead = temp[0];
                            frozenTail = temp[1];
                    }
                    validateStateMaintained();
                    ShelfMgmtSystem.readContents("REMOVAL - cleaned: order " + o.getId() + " cleaned from overflow shelf; temp: " + o.getTemp(), OverflowShelf.class.getSimpleName());
                    masterLock.unlock();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

    }

    /**
     * read content on shelf
     */
    public void readContentOnShelf() {
        masterLock.lock();
        for (int pos = 0; pos < capacity; pos++) {
            Order o = cells[pos];
            if (o != null) {
                System.out.println("Overflow shelf - order id: " + o.getId() + ", value: " + o.computeRemainingLifeValue(2) + ", pos: " + pos + ", temp:" + o.getTemp());
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
        lock.lock();
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
            lock.unlock();
        }
    }

    private void removeOrderHelper(Temperature temperature, int pos, UUID id) {
        lock.lock();
        cells[pos] = null;
        DoublyLinkedNode node = locations.remove(id);
        availableCells.offer(pos);
        DoublyLinkedNode[] temp;
        switch (temperature) {
            case HOT:
                temp = extractNode(node, hotHead, hotTail);
                hotHead = temp[0];
                hotTail = temp[1];
                break;
            case COLD:
                temp = extractNode(node, coldHead, coldTail);
                coldHead = temp[0];
                coldTail = temp[1];
                break;
            case FROZEN:
                temp = extractNode(node, frozenHead, frozenTail);
                frozenHead = temp[0];
                frozenTail = temp[1];
        }
        lock.unlock();
    }

    private DoublyLinkedNode[] putOrderOnOverflowHelper(Order o, DoublyLinkedNode h, DoublyLinkedNode t, DoublyLinkedNode curr) {
        lock.lock();
        DoublyLinkedNode[] temp = new DoublyLinkedNode[] {h, t};
        try {
            if (t == null) {
                temp[0] = curr;
                temp[1] = curr;
            } else {
                t.setNext(curr);
                curr.setPrevious(t);
                temp[1] = curr;
            }
            locations.put(o.getId(), curr);
            o.setPlacementTime();
            return temp;
        } finally {
            lock.unlock();
        }

    }

    private void validateStateMaintained() {
        lock.lock();
        try {
            Iterator<Map.Entry<UUID, DoublyLinkedNode>> it = locations.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, DoublyLinkedNode> a = it.next();
                UUID id = a.getKey();
                int pos = a.getValue().value();
                assert cells[pos].getId() == id;
            }
            Iterator<Integer> listIt = availableCells.iterator();
            while (listIt.hasNext()) {
                int temp = listIt.next();
                assert cells[temp] == null;
            }
            assert locations.size() + availableCells.size() == capacity;
            assert (hotHead == null && hotTail == null) || (hotHead != null && hotTail != null);
            assert (coldHead == null && coldTail == null) || (coldHead != null && coldTail != null);
            assert (frozenHead == null && frozenTail == null) || (frozenHead != null && frozenTail != null);
            assert (hotHead != hotTail) || (hotHead == null && hotTail == null) || (hotHead.previous() == null && hotTail.next() == null);
            assert (coldHead != coldTail) || (coldHead == null && coldTail == null) || (coldHead.previous() == null && coldTail.next() == null);
            assert (frozenHead != frozenTail) ||(frozenHead==null && frozenTail ==null)|| (frozenHead.previous() == null && frozenTail.next() == null);
            DoublyLinkedNode node = hotHead;
            int sum = 0;
            while (node != null) {
                Order o = cells[node.value()];
                assert o != null && o.getTemp() == Temperature.HOT;
                sum++;
                node = node.next();  //also verify the middle node: next previous not null //maybe do a previous loop
            }
            node = coldHead;
            while (node != null) {
                Order o = cells[node.value()];
                assert o != null && o.getTemp() == Temperature.COLD;
                sum++;
                node = node.next();
            }
            node = frozenHead;
            while (node != null) {
                Order o = cells[node.value()];
                assert o != null && o.getTemp() == Temperature.FROZEN;
                sum++;
                node = node.next();
            }
            assert sum == locations.size();
            sum = 0;
            node = hotTail;
            while (node != null) {
                Order o = cells[node.value()];
                assert o != null && o.getTemp() == Temperature.HOT;
                sum++;
                node = node.previous();  //also verify the middle node: next previous not null //maybe do a previous loop
            }
            node = coldTail;
            while (node != null) {
                Order o = cells[node.value()];
                assert o != null && o.getTemp() == Temperature.COLD;
                sum++;
                node = node.previous();
            }
            node = frozenTail;
            while (node != null) {
                Order o = cells[node.value()];
                assert o != null && o.getTemp() == Temperature.FROZEN;
                sum++;
                node = node.previous();
            }
            assert sum == locations.size();
        }catch (Exception e) {
            System.out.println(e);
        } finally {
            lock.unlock();
        }
    }
}
