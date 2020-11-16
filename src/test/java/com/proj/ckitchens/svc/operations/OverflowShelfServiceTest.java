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

public class OverflowShelfServiceTest {
//    private final static ReentrantLock lock = mock(ReentrantLock.class);
    private final static ReentrantLock overflowLock = mock(ReentrantLock.class);
//    private Shelf hshelf;
    private Shelf overflowShelf;
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
//        doNothing().when(lock).lock();
//        doNothing().when(lock).unlock();
        doNothing().when(overflowLock).unlock();
        doNothing().when(overflowLock).unlock();
    }

    @BeforeEach
    public void init() {
//        hshelf = new Shelf(lock, 3, Temperature.HOT.name());
        overflowShelf = new Shelf(overflowLock, 3, "Overflow");
        service = new ShelfService(overflowShelf);
    }

    @Test
    public void testDiscardRandom() {
        service.placeOnShelf(generateOneOrder(Temperature.HOT));
        Order o = service.discardRandom();
        assertTrue(o == null);
        assertStateMaintained();


        service.placeOnShelf(generateOneOrder(Temperature.FROZEN));
        o = service.discardRandom();
        assertTrue(o == null);
        assertStateMaintained();

        service.placeOnShelf(generateOneOrder(Temperature.HOT));
        o = service.discardRandom();
        assertTrue(o != null);
        assertTrue(overflowShelf.getLocations().size() == 2);
        assertStateMaintained();

    }

    @Test
    public void testRemoveBasedOnTemperature() {
        service.placeOnShelf(generateOneOrder(Temperature.COLD));
        service.placeOnShelf(generateOneOrder(Temperature.FROZEN));
        Order o = service.removeBasedOnTemperature(Temperature.COLD);
        assertTrue(o.getTemp() == Temperature.COLD);
        assertTrue(overflowShelf.getLocations().size() == 1);
        assertStateMaintained();

        o = service.removeBasedOnTemperature(Temperature.FROZEN);
        assertTrue(o.getTemp() == Temperature.FROZEN);
        assertTrue(overflowShelf.getLocations().size() == 0);
        assertStateMaintained();
    }

    @Test()
    public void testRemoveNonExistingTemperature() {
        service.placeOnShelf(generateOneOrder(Temperature.COLD));
        service.placeOnShelf(generateOneOrder(Temperature.FROZEN));
        assertThrows(NullPointerException.class, () -> service.removeBasedOnTemperature(Temperature.HOT));
        assertStateMaintained();
    }

    @Test
    public void testHashOnShelf() {
        Order o = generateOneOrder(Temperature.HOT);
        service.placeOnShelf(o);
        assertTrue(service.hasOnShelf(Temperature.HOT));
        assertFalse(service.hasOnShelf(Temperature.COLD));
        assertFalse(service.hasOnShelf(Temperature.FROZEN));
        assertStateMaintained();

        o = generateOneOrder(Temperature.COLD);
        service.placeOnShelf(o);
        assertTrue(service.hasOnShelf(Temperature.HOT));
        assertTrue(service.hasOnShelf(Temperature.COLD));
        assertFalse(service.hasOnShelf(Temperature.FROZEN));
        assertStateMaintained();

        o = generateOneOrder(Temperature.FROZEN);
        service.placeOnShelf(o);
        assertTrue(service.hasOnShelf(Temperature.HOT));
        assertTrue(service.hasOnShelf(Temperature.COLD));
        assertTrue(service.hasOnShelf(Temperature.FROZEN));
        assertStateMaintained();

        service.removeBasedOnTemperature(Temperature.COLD);
        assertTrue(service.hasOnShelf(Temperature.HOT));
        assertFalse(service.hasOnShelf(Temperature.COLD));
        assertTrue(service.hasOnShelf(Temperature.FROZEN));
        assertStateMaintained();

        service.removeForDelivery(o);
        assertTrue(service.hasOnShelf(Temperature.HOT));
        assertFalse(service.hasOnShelf(Temperature.COLD));
        assertFalse(service.hasOnShelf(Temperature.FROZEN));
        assertStateMaintained();
    }

    private void assertStateMaintained(/*Shelf hshelf*/) {
        try {
            cells = overflowShelf.getCells();
            locations = overflowShelf.getLocations();
            availableCells = overflowShelf.getAvailableCells();
            capacity = overflowShelf.getCapacity();
//            hotHead = hshelf.getHeadsTails()[0];
//            hotTail = hshelf.getHeadsTails()[1];
//            coldHead = hshelf.getHeadsTails()[2];
//            coldTail = hshelf.getHeadsTails()[3];
//            frozenHead = hshelf.getHeadsTails()[4];
//            frozenTail = hshelf.getHeadsTails()[5];

            hotHead = overflowShelf.getHotHead();
            hotTail = overflowShelf.getHotTail();
            coldHead = overflowShelf.getColdHead();
            coldTail = overflowShelf.getColdTail();
            frozenHead = overflowShelf.getFrozenHead();
            frozenTail = overflowShelf.getFrozenTail();

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

    private Order generateOneOrder(Temperature t) {
        return new Order(UUID.randomUUID(), t, "Pizza", new Random().nextInt(300), Math.random());
    }

}
