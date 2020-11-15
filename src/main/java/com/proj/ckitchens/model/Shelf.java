package com.proj.ckitchens.model;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

public class Shelf {
    private final Lock lock;
    private final int capacity;
    private final Order[] cells;
    private final String name;
    private final Queue<Integer> availableCells = new LinkedList<>();
    public Shelf(Lock lock, int capacity, String name) {
        this.lock = lock;
        this.capacity = capacity;
        this.cells = new Order[capacity];
        this.name = name;
        for (int i = 0; i < capacity; i++) {
            availableCells.offer(i);
        }
    }

    public Lock getLock() {
        return this.lock;
    }
    public Order[] getCells() {
        return cells;
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
