package com.proj.ckitchens.svc.operations;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.model.TemperatureShelf;
import com.proj.ckitchens.utils.DataIntegrityViolation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

public class TemperatureShelfServiceTest {
    private final static ReentrantLock lock = mock(ReentrantLock.class);
    private TemperatureShelf hshelf;
    private TemperatureShelfService service;

    @BeforeAll
    public static void setup() {
        masterLock = mock(ReentrantLock.class);
        doNothing().when(masterLock).lock();
        doNothing().when(masterLock).unlock();
        doNothing().when(lock).lock();
        doNothing().when(lock).unlock();
    }

    @BeforeEach
    public void init() {
        hshelf = new TemperatureShelf(lock, 3, Temperature.HOT.name());
        service = new TemperatureShelfService(hshelf);
    }

    @Test
    public void testPlaceOnShelf() {
        assertStateMaintained();
        Order o = generateOneOrder();
        boolean placed = service.placeOnShelf(o);
        assertTrue(placed);
        assertEquals(o.getId(), hshelf.getCells()[0].getId());
        assertEquals(1, hshelf.getLocations().size());
        assertStateMaintained();

        o = generateOneOrder();
        assertTrue(service.placeOnShelf(o));
        assertEquals(2, hshelf.getLocations().size());
        assertStateMaintained();

        o = generateOneOrder();
        assertTrue(service.placeOnShelf(o));
        assertEquals(3, hshelf.getLocations().size());
        assertStateMaintained();

        o = generateOneOrder();
        assertFalse(service.placeOnShelf(o));
        assertEquals(3, hshelf.getLocations().size());
        assertStateMaintained();
    }

    @Test //not added to ShelfService yet
    public void testPlaceWrongTemperature() {
        Order o = new Order(UUID.randomUUID(), Temperature.COLD, "Pizza", 200, 0.25);
        assertFalse(service.placeOnShelf(o));;
        assertEquals(0, hshelf.getLocations().size());
        assertStateMaintained();
    }

    @Test
    public void testIsCellAvailable() {
        assertTrue(service.isCellAvailable());
        service.placeOnShelf(generateOneOrder());
        assertTrue(service.isCellAvailable());
        service.placeOnShelf(generateOneOrder());
        assertTrue(service.isCellAvailable());
        service.placeOnShelf(generateOneOrder());
        assertFalse(service.isCellAvailable());
    }

    /**
     * in the process, also test {@link TemperatureShelfService#isCellAvailable()}, {@link TemperatureShelfService#placeOnShelf(Order)}
     */
    @Test
    public void testRemoveForDelivery() {
        Order o1 = generateOneOrder();
        Order o2 = generateOneOrder();
        Order o3 = generateOneOrder();
        service.placeOnShelf(o1);
        service.placeOnShelf(o2);
        service.placeOnShelf(o3);

        //remove first one
        service.removeForDelivery(o1.getId());
        assertTrue(hshelf.getCells()[0] == null);
        assertTrue(service.isCellAvailable());
        assertStateMaintained();

        o1 = generateOneOrder();
        service.placeOnShelf(o1);
        assertFalse(service.isCellAvailable());
        assertStateMaintained();

        //remove last one
        service.removeForDelivery(o3.getId());
        assertTrue(hshelf.getCells()[2] == null);
        assertTrue(service.isCellAvailable());
        assertStateMaintained();

        o3 = generateOneOrder();
        service.placeOnShelf(o3);
        assertFalse(service.isCellAvailable());
        assertStateMaintained();

        //remove middle one
        service.removeForDelivery(o2.getId());
        assertTrue(hshelf.getCells()[1] == null);
        assertTrue(service.isCellAvailable());
        assertEquals(2, hshelf.getLocations().size());
        assertStateMaintained();

        //remove until empty
        service.removeForDelivery(o1.getId());
        assertEquals(1, hshelf.getLocations().size());
        assertStateMaintained();

        service.removeForDelivery(o3.getId());
        assertEquals(0, hshelf.getLocations().size());
        assertStateMaintained();
    }

    @Test
    public void testCleanup() {
        Order o1 = new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", 2, Math.random());
        Order o2 = new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", 10, Math.random());
        Order o3 = new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", 10, 0.01);
        service.placeOnShelf(o1);
        service.placeOnShelf(o2);
        service.placeOnShelf(o3);
        service.removeForDelivery(o2.getId());
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {}
        service.cleanup();
        assertTrue(hshelf.getCells()[0] == null);
        assertEquals(1, hshelf.getLocations().size());
        assertStateMaintained();
    }

    private void assertStateMaintained() {
        try {
            Iterator<Map.Entry<UUID, Integer>> it = hshelf.getLocations().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> a = it.next();
                UUID id = a.getKey();
                int pos = a.getValue();
                assertTrue(hshelf.getCells()[pos].getId() == id && hshelf.getCells()[pos].getTemp().name() == hshelf.getName());
            }
            Iterator<Integer> listIt = hshelf.getAvailableCells().iterator();
            while (listIt.hasNext()) {
                int temp = listIt.next();
                assertTrue(hshelf.getCells()[temp] == null);
            }
            assertTrue(hshelf.getLocations().size() + hshelf.getAvailableCells().size() == hshelf.getCapacity());
        } catch (Exception e ) {
            throw new DataIntegrityViolation("Error: data integrity violation");
        }
    }

    private Order generateOneOrder() {
        return new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", new Random().nextInt(300), Math.random());
    }

}
