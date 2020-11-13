package com.proj.ckitchens.model;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.svc.ShelfMgmtSystem;
import com.proj.ckitchens.utils.DoublyLinkedNode;

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
                        if(hotTail == null) {
                            hotTail = curr;
                            hotHead = curr;
                        } else {
                            hotTail.setNext(curr);
                            curr.setPrevious(hotTail);
                            hotTail = curr;
                        }
                        locations.put(order.getId(), curr);
                        order.setPlacementTime();
                        System.out.println(OverflowShelf.class.getSimpleName() + " hot placed on overflow: " + order.getId() + " position " + freePos);
                        ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"INITIAL placement: order " + order.getId() + " placed on overflow shelf; temp: " + HOT, OverflowShelf.class.getSimpleName());
                        break;
                    case COLD:
                        if(coldTail == null) {
                            coldTail = curr;
                            coldHead = curr;
                        } else {
                            coldTail.setNext(curr);
                            curr.setPrevious(coldTail);
                            coldTail = curr;
                        }
                        locations.put(order.getId(), curr);
                        order.setPlacementTime();
                        System.out.println(OverflowShelf.class.getSimpleName() + " cold placed on overflow: " + order.getId()  + " position " + freePos);
                        ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"INITIAL placement: order " + order.getId() + " placed on overflow shelf; temp: " + COLD, OverflowShelf.class.getSimpleName());
                        break;
                    case FROZEN:
                        if(frozenTail == null) {
                            frozenTail = curr;
                            frozenHead = curr;
                        } else {
                            frozenTail.setNext(curr);
                            curr.setPrevious(frozenTail);
                            frozenTail = curr;
                        }
                        locations.put(order.getId(), curr);
                        order.setPlacementTime();
                        System.out.println(OverflowShelf.class.getSimpleName() + " frozen placed on overflow: " + order.getId()  + " position " + freePos);
                        ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"INITIAL placement: order " + order.getId() + " placed on overflow shelf; temp: " + FROZEN, OverflowShelf.class.getSimpleName());
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
            return (temp == Temperature.HOT && hotTail != null)
                    || (temp == Temperature.COLD && coldTail != null)
                    || (temp == Temperature.FROZEN && frozenTail != null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * the position exists before the call
     * @param temp
     * @return
     */
    public Order removePos(Temperature temp) {
        lock.lock();
        Order o = null;
        int pos = -1;
        masterLock.lock();
        switch (temp) {
            case HOT:
                pos = hotHead.value();
                if(hotHead.next() != null) {
                    hotHead.next().setPrevious(null);
                    hotHead = hotHead.next();
                } else {
                    hotHead = null;
                    hotTail = null;
                }
                o = cells[pos];
                cells[pos] = null;
                availableCells.offer(pos);
                locations.remove(o.getId());
                System.out.println(OverflowShelf.class.getSimpleName() + " order " + o.getId() + " yielded hot position on overflow shelf " + o.getTemp() );
                break;
            case COLD:
                pos = coldHead.value();
                if(coldHead.next() != null) {
                    coldHead.next().setPrevious(null);
                    coldHead = coldHead.next();
                } else {
                    coldHead = null;
                    coldTail = null;
                }
                o = cells[pos];
                cells[pos] = null;
                availableCells.offer(pos);
                locations.remove(o.getId());
                System.out.println(OverflowShelf.class.getSimpleName() + " order " + o.getId() + " yielded cold position on overflow shelf " + o.getTemp() );

                break;
            case FROZEN:
                pos = frozenHead.value();
                if(frozenHead.next() != null) {
                    frozenHead.next().setPrevious(null);
                    frozenHead = frozenHead.next();
                } else {
                    frozenHead = null;
                    frozenTail = null;
                }
                o = cells[pos];
                cells[pos] = null;
                availableCells.offer(pos);
                locations.remove(o.getId());
                System.out.println(OverflowShelf.class.getSimpleName() + " order " + o.getId() + " yielded frozen position on overflow shelf " + o.getTemp() );

        }
        ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"MOVED - random order " + o.getId() + " moved from overflow shelf to " + o.getTemp() + " shelf", OverflowShelf.class.getSimpleName());
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
            masterLock.lock();
            if (capacity == 0) return null;
            int pos = new Random().nextInt(capacity);
            Order o = cells[pos];
            cells[pos] = null;
            DoublyLinkedNode node = locations.remove(o.getId());
            availableCells.offer(pos);
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
            System.out.println(OverflowShelf.class.getSimpleName() + " discarded random order " + o.getId() + " from position " + pos);
            ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - discarded: random order " + o.getId() + " discarded from overflow shelf; temp: " + o.getTemp(), OverflowShelf.class.getSimpleName());
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
            return locations.get(id).value();
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
                DoublyLinkedNode node = locations.remove(order.getId());
                switch (order.getTemp()) {
                    case HOT:
                        extractNode(node, hotHead, hotTail);
                        break;
                    case COLD:
                        extractNode(node, coldHead, coldTail);
                        break;
                    case FROZEN:
                        extractNode(node, frozenHead, frozenTail);
                }
                if(pastDueTime) {
                    System.out.println(OverflowShelf.class.getSimpleName() + " cleaned from overflow shelf pos: " + order.getId() + " " + pos);
                    ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"REMOVAL - cleaned: order " + order.getId() + " from overflow shelf; temp: " + order.getTemp(), OverflowShelf.class.getSimpleName());
                } else {
                    System.out.println(OverflowShelf.class.getSimpleName() + " delivered from overflow shelf pos: " + order.getId() + " " + pos);
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

}
