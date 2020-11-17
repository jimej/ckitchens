package com.proj.ckitchens.model;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;

public class TemperatureShelf extends ShelfParent {
    private final Map<UUID, Integer> locations;
    public TemperatureShelf(Lock lock, int capacity, String name) {
        super(lock, capacity, name);
        locations = new HashMap<>();
    }
    public Map<UUID, Integer> getLocations() {
        return locations;
    }
}
