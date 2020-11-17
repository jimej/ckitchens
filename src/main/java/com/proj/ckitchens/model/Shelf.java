package com.proj.ckitchens.model;

import com.proj.ckitchens.common.DoublyLinkedNode;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;

public class Shelf extends ShelfParent {
    private DoublyLinkedNode hotHead, coldHead, frozenHead;
    private DoublyLinkedNode hotTail, coldTail, frozenTail;

    private final Map<UUID, DoublyLinkedNode> locations;

    public Shelf(Lock lock, int capacity, String name) {
        super(lock, capacity, name);
        locations = new HashMap<>();
    }

    public Map<UUID, DoublyLinkedNode> getLocations() {
        return locations;
    }

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
}
