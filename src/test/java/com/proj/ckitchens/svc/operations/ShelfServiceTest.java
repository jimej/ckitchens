package com.proj.ckitchens.svc.operations;

import com.proj.ckitchens.common.DoublyLinkedNode;
import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.model.Shelf;
import com.proj.ckitchens.svc.ShelfMgmtSystem;
import com.proj.ckitchens.utils.DataIntegrityViolation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;
import static com.proj.ckitchens.svc.ShelfMgmtSystem.shelfMgmtSystem;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.proj.ckitchens.svc.TestFixture.*;

public class ShelfServiceTest {
    private final static ReentrantLock lock = mock(ReentrantLock.class);
    private final static ReentrantLock overflowLock = mock(ReentrantLock.class);
    private Shelf hshelf;
    private Shelf cshelf;
    private Shelf fshelf;
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
        doNothing().when(lock).lock();
        doNothing().when(lock).unlock();
        doNothing().when(overflowLock).unlock();
        shelfMgmtSystem = mock(ShelfMgmtSystem.class);
        doNothing().when(shelfMgmtSystem).readContents(anyString(), anyString());
    }

    @BeforeEach
    public void init() {
        hshelf = new Shelf(lock, 3, Temperature.HOT.name());
        cshelf = mock(Shelf.class);
        fshelf = mock(Shelf.class);
        overflowShelf = new Shelf(lock, 3, "Overflow");
        service = new ShelfService(hshelf, cshelf, fshelf, overflowShelf);
    }

    //The following tests are for all types of shelves, even though only tested for hot shelf - since other
    //shelves use the same code
    @Test
    public void testPlaceOnShelf() {
        assertStateMaintained(hshelf);
        Order o = generateOneHotOrder();
        boolean placed = service.placeOnShelf(o, hshelf);
        assertTrue(placed);
        assertEquals(o.getId(), hshelf.getCells()[0].getId());
        assertEquals(1, hshelf.getLocations().size());
        assertStateMaintained(hshelf);

        o = generateOneHotOrder();
        assertTrue(service.placeOnShelf(o, hshelf));
        assertEquals(2, hshelf.getLocations().size());
        assertStateMaintained(hshelf);

        o = generateOneHotOrder();
        assertTrue(service.placeOnShelf(o, hshelf));
        assertEquals(3, hshelf.getLocations().size());
        assertStateMaintained(hshelf);

        o = generateOneHotOrder();
        assertFalse(service.placeOnShelf(o, hshelf));
        assertEquals(3, hshelf.getLocations().size());
        assertStateMaintained(hshelf);
    }

    @Test
    public void testIsCellAvailable() {
        assertTrue(service.isCellAvailable(hshelf));
        service.placeOnShelf(generateOneHotOrder(), hshelf);
        assertTrue(service.isCellAvailable(hshelf));
        service.placeOnShelf(generateOneHotOrder(), hshelf);
        assertTrue(service.isCellAvailable(hshelf));
        service.placeOnShelf(generateOneHotOrder(), hshelf);
        assertFalse(service.isCellAvailable(hshelf));
    }

    /**
     * in the process, also test {@link ShelfService#isCellAvailable(Shelf)}, {@link ShelfService#placeOnShelf(Order, Shelf)}
     */
    @Test
    public void testRemoveForDelivery() {
        Order o1 = generateOneHotOrder();
        Order o2 = generateOneHotOrder();
        Order o3 = generateOneHotOrder();
        service.placeOnShelf(o1, hshelf);
        service.placeOnShelf(o2, hshelf);
        service.placeOnShelf(o3, hshelf);

        //remove first one
        service.removeForDelivery(o1, hshelf);
        assertTrue(hshelf.getCells()[0] == null);
        assertTrue(service.isCellAvailable(hshelf));
        assertStateMaintained(hshelf);

        o1 = generateOneHotOrder();
        service.placeOnShelf(o1, hshelf);
        assertFalse(service.isCellAvailable(hshelf));
        assertStateMaintained(hshelf);

        //remove last one
        service.removeForDelivery(o3, hshelf);
        assertTrue(hshelf.getCells()[2] == null);
        assertTrue(service.isCellAvailable(hshelf));
        assertStateMaintained(hshelf);

        o3 = generateOneHotOrder();
        service.placeOnShelf(o3, hshelf);
        assertFalse(service.isCellAvailable(hshelf));
        assertStateMaintained(hshelf);

        //remove middle one
        service.removeForDelivery(o2, hshelf);
        assertTrue(hshelf.getCells()[1] == null);
        assertTrue(service.isCellAvailable(hshelf));
        assertEquals(2, hshelf.getLocations().size());
        assertStateMaintained(hshelf);

        //remove until empty
        service.removeForDelivery(o1, hshelf);
        assertEquals(1, hshelf.getLocations().size());
        assertStateMaintained(hshelf);

        service.removeForDelivery(o3, hshelf);
        assertEquals(0, hshelf.getLocations().size());
        assertStateMaintained(hshelf);
    }

    @Test
    public void testCleanup() {
        Order o1 = new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", 2, Math.random());
        Order o2 = new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", 10, Math.random());
        Order o3 = new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", 10, 0.01);
        service.placeOnShelf(o1, hshelf);
        service.placeOnShelf(o2, hshelf);
        service.placeOnShelf(o3, hshelf);
        service.removeForDelivery(o2, hshelf);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {}
        service.cleanup(hshelf);
        assertTrue(hshelf.getCells()[0] == null);
        assertEquals(1, hshelf.getLocations().size());
        assertStateMaintained(hshelf);
    }

    //the following tests are for methods called only on overflow shelf

    @Test
    public void testDiscardRandom() {
        //call on temperature shelf
        assertThrows(IllegalArgumentException.class, () -> service.discardRandom(hshelf));

        //call on overflow shelf
        service.placeOnShelf(generateOneOrder(Temperature.HOT), overflowShelf);
        Order o = service.discardRandom(overflowShelf);
        assertTrue(o == null);
        assertStateMaintained(overflowShelf);


        service.placeOnShelf(generateOneOrder(Temperature.FROZEN), overflowShelf);
        o = service.discardRandom(overflowShelf);
        assertTrue(o == null);
        assertStateMaintained(overflowShelf);

        service.placeOnShelf(generateOneOrder(Temperature.HOT), overflowShelf);
        o = service.discardRandom(overflowShelf);
        assertTrue(o != null);
        assertTrue(overflowShelf.getLocations().size() == 2);
        assertStateMaintained(overflowShelf);

    }

    @Test
    public void testRemoveBasedOnTemperature() {
        //call on temperature shelf
        assertThrows(IllegalArgumentException.class, () -> service.removeBasedOnTemperature(Temperature.HOT, hshelf));

        //call on overflow shelf
        service.placeOnShelf(generateOneOrder(Temperature.COLD), overflowShelf);
        service.placeOnShelf(generateOneOrder(Temperature.FROZEN), overflowShelf);
        Order o = service.removeBasedOnTemperature(Temperature.COLD, overflowShelf);
        assertTrue(o.getTemp() == Temperature.COLD);
        assertTrue(overflowShelf.getLocations().size() == 1);
        assertStateMaintained(overflowShelf);

        o = service.removeBasedOnTemperature(Temperature.FROZEN, overflowShelf);
        assertTrue(o.getTemp() == Temperature.FROZEN);
        assertTrue(overflowShelf.getLocations().size() == 0);
        assertStateMaintained(overflowShelf);
    }

    @Test()
    public void testRemoveNonExistingTemperature() {
        //call on temperature shelf
        assertThrows(IllegalArgumentException.class, () -> service.discardRandom(hshelf));

        //call on overflow shelf
        service.placeOnShelf(generateOneOrder(Temperature.COLD), overflowShelf);
        service.placeOnShelf(generateOneOrder(Temperature.FROZEN), overflowShelf);
        assertThrows(NullPointerException.class, () -> service.removeBasedOnTemperature(Temperature.HOT, overflowShelf));
        assertStateMaintained(overflowShelf);
    }

    @Test
    public void testHasOnShelf() {
        //call on temperature shelf
        assertThrows(IllegalArgumentException.class, () -> service.hasOnShelf(Temperature.HOT, hshelf));

        //call on overflow
        Order o = generateOneOrder(Temperature.HOT);
        service.placeOnShelf(o, overflowShelf);
        assertTrue(service.hasOnShelf(Temperature.HOT, overflowShelf));
        assertFalse(service.hasOnShelf(Temperature.COLD, overflowShelf));
        assertFalse(service.hasOnShelf(Temperature.FROZEN, overflowShelf));
        assertStateMaintained(overflowShelf);

        o = generateOneOrder(Temperature.COLD);
        service.placeOnShelf(o, overflowShelf);
        assertTrue(service.hasOnShelf(Temperature.HOT, overflowShelf));
        assertTrue(service.hasOnShelf(Temperature.COLD, overflowShelf));
        assertFalse(service.hasOnShelf(Temperature.FROZEN, overflowShelf));
        assertStateMaintained(overflowShelf);

        o = generateOneOrder(Temperature.FROZEN);
        service.placeOnShelf(o, overflowShelf);
        assertTrue(service.hasOnShelf(Temperature.HOT, overflowShelf));
        assertTrue(service.hasOnShelf(Temperature.COLD, overflowShelf));
        assertTrue(service.hasOnShelf(Temperature.FROZEN, overflowShelf));
        assertStateMaintained(overflowShelf);

        service.removeBasedOnTemperature(Temperature.COLD, overflowShelf);
        assertTrue(service.hasOnShelf(Temperature.HOT, overflowShelf));
        assertFalse(service.hasOnShelf(Temperature.COLD, overflowShelf));
        assertTrue(service.hasOnShelf(Temperature.FROZEN, overflowShelf));
        assertStateMaintained(overflowShelf);

        service.removeForDelivery(o, overflowShelf);
        assertTrue(service.hasOnShelf(Temperature.HOT, overflowShelf));
        assertFalse(service.hasOnShelf(Temperature.COLD, overflowShelf));
        assertFalse(service.hasOnShelf(Temperature.FROZEN, overflowShelf));
        assertStateMaintained(overflowShelf);
    }


    private void assertStateMaintained(Shelf shelf) {
        try {
            cells = shelf.getCells();
            locations = shelf.getLocations();
            availableCells = shelf.getAvailableCells();
            capacity = shelf.getCapacity();

            hotHead = shelf.getHotHead();
            hotTail = shelf.getHotTail();
            coldHead = shelf.getColdHead();
            coldTail = shelf.getColdTail();
            frozenHead = shelf.getFrozenHead();
            frozenTail = shelf.getFrozenTail();

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

//    private Order generateOneHotOrder() {
//        return generateOneOrder(Temperature.HOT);
//    }
//
//    private Order generateOneOrder(Temperature t) {
//        return new Order(UUID.randomUUID(), t, "Pizza", new Random().nextInt(300), Math.random());
//    }

}
