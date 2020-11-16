package com.proj.ckitchens.model;

import com.proj.ckitchens.common.DoublyLinkedNode;

import java.util.*;
import java.util.concurrent.locks.Lock;

public class Shelf extends ShelfParent /*extends Shelf*/ {
//    private final Lock lock;
//    private final int capacity;
//    private final Order[] cells;
//    private final String name;
//    private final Queue<Integer> availableCells = new LinkedList<>();
    private DoublyLinkedNode hotHead, coldHead, frozenHead;
    private DoublyLinkedNode hotTail, coldTail, frozenTail;

    private final Map<UUID, DoublyLinkedNode> locations;
    public Shelf(Lock lock, int capacity, String name) {
        super(lock, capacity, name);
//        super(lock, capacity, null);
//        this.lock = lock;
//        this.capacity = capacity;
//        this.cells = new Order[capacity];
//        this.name = name;
//        for(int i = 0; i < capacity; i++) {
//            availableCells.offer(i);
//        }
        locations = new HashMap<>();
    }

//    public Lock getLock() {
//        return this.lock;
//    }
//    public Order[] getCells() {
//        return cells;
//    }
    public Map<UUID, DoublyLinkedNode> getLocations() {
        return locations;
    }
//    public Queue<Integer> getAvailableCells() {
//        return availableCells;
//    }
//    public int getCapacity() {
//        return capacity;
//    }

    public DoublyLinkedNode getHotHead(){
        return hotHead;
    }
    public DoublyLinkedNode getHotTail() {
        return hotTail;
    }

    public void setHotHeadTail(DoublyLinkedNode h, DoublyLinkedNode t) {
        hotHead = h;
        hotTail = t;
    }
    public DoublyLinkedNode getColdHead() {
        return coldHead;
    }
    public DoublyLinkedNode getColdTail(){
        return coldTail;
    }

    public void setColdHeadTail(DoublyLinkedNode h, DoublyLinkedNode t) {
        coldHead = h;
        coldTail = t;
    }

    public DoublyLinkedNode getFrozenHead() {
        return frozenHead;
    }
    public DoublyLinkedNode getFrozenTail() {
        return frozenTail;
    }
    public void setFrozenHeadTail(DoublyLinkedNode h, DoublyLinkedNode t) {
        frozenHead = h;
        frozenTail = t;
    }
//    public DoublyLinkedNode[] getHeadsTails() {
//        return new DoublyLinkedNode[] {hotHead, hotTail, coldHead, coldTail, frozenHead, frozenTail};
//    }
//    public String getName() {
//        return this.name;
//    }
}
