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
    //    private final Queue<Integer> hotPositions = new LinkedList<>();
//    private final Queue<Integer> coldPositions = new LinkedList<>();
//    private final Queue<Integer> frozenPositions = new LinkedList<>();
//    private final LinkedList<DoublyLinkedNode> hotPositions = new LinkedList<>();
//    private final LinkedList<DoublyLinkedNode> coldPositions = new LinkedList<>();
//    private final LinkedList<DoublyLinkedNode> frozenPositions = new LinkedList<>();
    private static DoublyLinkedNode hotHead, coldHead, frozenHead =  null;
    private static DoublyLinkedNode hotTail, coldTail, frozenTail =  null;

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
//                        hotPositions.offer(freePos);
                        locations.put(order.getId(), curr);
                        order.setPlacementTime();
                        System.out.println(OverflowShelf.class.getSimpleName() + " hot placed on overflow: " + order.getId() + " position " + freePos);
                        ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"INITIAL placement: order " + order.getId() + " placed on overflow shelf; temp: " + HOT);
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
                        ShelfMgmtSystem.readContents(LocalTime.now().withNano(0),"INITIAL placement: order " + order.getId() + " placed on overflow shelf; temp: " + COLD);
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
            return (temp == Temperature.HOT && hotTail != null)
                    || (temp == Temperature.COLD && coldTail != null)
                    || (temp == Temperature.FROZEN && frozenTail != null);
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
//                    head = hotHead;
//                    head.getNext().setPrevious(null);
                pos = hotHead.value();
                if(hotHead.getNext() != null) {
                    hotHead.getNext().setPrevious(null);
                    hotHead = hotHead.getNext();
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
                if(coldHead.getNext() != null) {
                    coldHead.getNext().setPrevious(null);
                    coldHead = coldHead.getNext();
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
                if(frozenHead.getNext() != null) {
                    frozenHead.getNext().setPrevious(null);
                    frozenHead = frozenHead.getNext();
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
            DoublyLinkedNode node = locations.remove(o.getId());
            availableCells.offer(pos);
            DoublyLinkedNode[] temp = null;
            switch (o.getTemp()) {
                case HOT:
                    temp = nodeMaintenance(node, hotHead, hotTail);
                    hotHead = temp[0];
                    hotTail = temp[1];
//                    hotPositions.remove(pos);
                    break;
                case COLD:
                    temp = nodeMaintenance(node, coldHead, coldTail);
                    coldHead = temp[0];
                    coldTail = temp[1];
                    break;
                case FROZEN:
                    temp = nodeMaintenance(node, frozenHead, frozenTail);
                    frozenHead = temp[0];
                    frozenTail = temp[1];
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
                DoublyLinkedNode[] temp = null;
                switch (order.getTemp()) {
                    case HOT:
                        temp = nodeMaintenance(node, hotHead, hotTail);
                        hotHead = temp[0];
                        hotTail = temp[1];
//                    hotPositions.remove(pos);
                        break;
                    case COLD:
                        temp = nodeMaintenance(node, coldHead, coldTail);
                        coldHead = temp[0];
                        coldTail = temp[1];
                        break;
                    case FROZEN:
                        temp = nodeMaintenance(node, frozenHead, frozenTail);
                        frozenHead = temp[0];
                        frozenTail = temp[1];
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
                    DoublyLinkedNode[] temp = null;
                    switch (o.getTemp()) {
                        case HOT:
                            temp = nodeMaintenance(node, hotHead, hotTail);
                            hotHead = temp[0];
                            hotTail = temp[1];
//                    hotPositions.remove(pos);
                            break;
                        case COLD:
                            temp = nodeMaintenance(node, coldHead, coldTail);
                            coldHead = temp[0];
                            coldTail = temp[1];
                            break;
                        case FROZEN:
                            temp = nodeMaintenance(node, frozenHead, frozenTail);
                            frozenHead = temp[0];
                            frozenTail = temp[1];
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
    private DoublyLinkedNode[] nodeMaintenance(DoublyLinkedNode node, DoublyLinkedNode h, DoublyLinkedNode t) {
        lock.lock();
        if(node.getPrevious() == null && node.getNext() == null) {
//                        hotHead = hotTail = null;
            h = null;
            t = null;
        } else if (node.getNext() == null) {
            node.getPrevious().setNext(null);
            t = node.getPrevious();
        } else if (node.getPrevious() == null) {
            node.getNext().setPrevious(null);
            h = node.getNext();
        } else {
            node.getNext().setPrevious(node.getPrevious());
            node.getPrevious().setNext(node.getNext());
        }
        try {
            return new DoublyLinkedNode[]{h, t};
        } catch (Exception e ) {
            System.out.println("exception");
            return null;
        }
        finally {
            lock.unlock();
        }
    }

}
