package com.proj.ckitchens.svc.operations;

import com.proj.ckitchens.common.DoublyLinkedNode;
import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.model.Shelf;
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

public class ShelfServiceTest {
    private final static ReentrantLock lock = mock(ReentrantLock.class);
//    private final static ReentrantLock overflowLock = mock(ReentrantLock.class);
    private Shelf hshelf;
//    private Shelf overflowShelf;
    private ShelfService service;

    private Order[] cells;
    private int capacity;
    private Map<UUID, DoublyLinkedNode> locations;
    private Queue<Integer> availableCells;
    private DoublyLinkedNode hotHead, hotTail, coldHead, coldTail, frozenHead, frozenTail;

    @BeforeAll
    public static void setup() {
        masterLock = mock(ReentrantLock.class);
        doNothing().when(masterLock).lock();
        doNothing().when(masterLock).unlock();
        doNothing().when(lock).lock();
        doNothing().when(lock).unlock();
//        doNothing().when(overflowLock).unlock();
//        doNothing().when(overflowLock).unlock();
    }

    @BeforeEach
    public void init() {
        hshelf = new Shelf(lock, 3, Temperature.HOT.name());
//        overflowShelf = new Shelf(lock, 3, "Overflow");
        service = new ShelfService(hshelf);
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
        service.removeForDelivery(o1);
        assertTrue(hshelf.getCells()[0] == null);
        assertTrue(service.isCellAvailable());
        assertStateMaintained();

        o1 = generateOneOrder();
        service.placeOnShelf(o1);
        assertFalse(service.isCellAvailable());
        assertStateMaintained();

        //remove last one
        service.removeForDelivery(o3);
        assertTrue(hshelf.getCells()[2] == null);
        assertTrue(service.isCellAvailable());
        assertStateMaintained();

        o3 = generateOneOrder();
        service.placeOnShelf(o3);
        assertFalse(service.isCellAvailable());
        assertStateMaintained();

        //remove middle one
        service.removeForDelivery(o2);
        assertTrue(hshelf.getCells()[1] == null);
        assertTrue(service.isCellAvailable());
        assertEquals(2, hshelf.getLocations().size());
        assertStateMaintained();

        //remove until empty
        service.removeForDelivery(o1);
        assertEquals(1, hshelf.getLocations().size());
        assertStateMaintained();

        service.removeForDelivery(o3);
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
        service.removeForDelivery(o2);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {}
        service.cleanup();
        assertTrue(hshelf.getCells()[0] == null);
        assertEquals(1, hshelf.getLocations().size());
        assertStateMaintained();
    }

    private void assertStateMaintained(/*Shelf hshelf*/) {
        try {
            cells = hshelf.getCells();
            locations = hshelf.getLocations();
            availableCells = hshelf.getAvailableCells();
            capacity = hshelf.getCapacity();
//            hotHead = hshelf.getHeadsTails()[0];
//            hotTail = hshelf.getHeadsTails()[1];
//            coldHead = hshelf.getHeadsTails()[2];
//            coldTail = hshelf.getHeadsTails()[3];
//            frozenHead = hshelf.getHeadsTails()[4];
//            frozenTail = hshelf.getHeadsTails()[5];

            hotHead = hshelf.getHotHead();
            hotTail = hshelf.getHotTail();
            coldHead = hshelf.getColdHead();
            coldTail = hshelf.getColdTail();
            frozenHead = hshelf.getFrozenHead();
            frozenTail = hshelf.getFrozenTail();

            Iterator<Map.Entry<UUID, DoublyLinkedNode>> it = locations.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, DoublyLinkedNode> a = it.next();
                UUID id = a.getKey();
                int pos = a.getValue().value();
                assertTrue(cells[pos].getId() == id);
            }
            Iterator<Integer> listIt = availableCells.iterator();
            while (listIt.hasNext()) {
                int temp = listIt.next();
                assertTrue(cells[temp] == null);
            }
            assertTrue(locations.size() + availableCells.size() == capacity);
            assertTrue((hotHead == null && hotTail == null) || (hotHead != null && hotTail != null));
            assertTrue((coldHead == null && coldTail == null) || (coldHead != null && coldTail != null));
            assertTrue((frozenHead == null && frozenTail == null) || (frozenHead != null && frozenTail != null));
            assertTrue((hotHead != hotTail) || (hotHead == null && hotTail == null) || (hotHead.previous() == null && hotTail.next() == null));
            assertTrue((coldHead != coldTail) || (coldHead == null && coldTail == null) || (coldHead.previous() == null && coldTail.next() == null));
            assertTrue((frozenHead != frozenTail) ||(frozenHead==null && frozenTail ==null)|| (frozenHead.previous() == null && frozenTail.next() == null));
            DoublyLinkedNode node = hotHead;
            int sum = 0;
            while (node != null) {
                Order o = cells[node.value()];
                assertTrue(o != null && o.getTemp() == Temperature.HOT);
                sum++;
                node = node.next();  //also verify the middle node: next previous not null //maybe do a previous loop
            }
            node = coldHead;
            while (node != null) {
                Order o = cells[node.value()];
                assertTrue(o != null && o.getTemp() == Temperature.COLD);
                sum++;
                node = node.next();
            }
            node = frozenHead;
            while (node != null) {
                Order o = cells[node.value()];
                assertTrue(o != null && o.getTemp() == Temperature.FROZEN);
                sum++;
                node = node.next();
            }
            assertEquals(locations.size(), sum);
            sum = 0;
            node = hotTail;
            while (node != null) {
                Order o = cells[node.value()];
                assertTrue(o != null && o.getTemp() == Temperature.HOT);
                sum++;
                node = node.previous();  //also verify the middle node: next previous not null //maybe do a previous loop
            }
            node = coldTail;
            while (node != null) {
                Order o = cells[node.value()];
                assertTrue(o != null && o.getTemp() == Temperature.COLD);
                sum++;
                node = node.previous();
            }
            node = frozenTail;
            while (node != null) {
                Order o = cells[node.value()];
                assertTrue(o != null && o.getTemp() == Temperature.FROZEN);
                sum++;
                node = node.previous();
            }
            assertEquals(locations.size(), sum);
        }catch (Exception e) {
            throw new DataIntegrityViolation("Error: data integrity violation");
        }
    }

    private Order generateOneOrder() {
        return new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", new Random().nextInt(300), Math.random());
    }

}
