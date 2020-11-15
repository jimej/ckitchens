package com.proj.ckitchens.model;

import java.util.*;
import java.util.concurrent.locks.Lock;

public class TemperatureShelf extends ShelfParent {
//    private final Lock lock;
//    private final int capacity;
//    private final Order[] cells;
//    private final String name;
//    private final Queue<Integer> availableCells = new LinkedList<>();
    private final Map<UUID, Integer> locations;
    public TemperatureShelf(Lock lock, int capacity, String name) {
        super(lock, capacity, name);
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
    public Map<UUID, Integer> getLocations() {
        return locations;
    }
//    public Queue<Integer> getAvailableCells() {
//        return availableCells;
//    }
//    public int getCapacity() {
//        return capacity;
//    }
//
//    public String getName() {
//        return this.name;
//    }
}
