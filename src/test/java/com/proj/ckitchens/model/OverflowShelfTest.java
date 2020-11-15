package com.proj.ckitchens.model;

import com.proj.ckitchens.common.DoublyLinkedNode;
import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.utils.DataIntegrityViolation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;
import static org.hamcrest.MatcherAssert.assertThat;

public class OverflowShelfTest {

//    @Mock
    private final static ReentrantLock lock = mock(ReentrantLock.class);
    private OverflowShelf overflow;
    private Order[] cells;
    private int capacity;
    private Map<UUID, DoublyLinkedNode> locations;
    private Queue<Integer> availableCells;
    private DoublyLinkedNode hotHead, hotTail, coldHead, coldTail, frozenHead, frozenTail;


    @BeforeAll
    public static void setup() {
        doNothing().when(lock).lock();
        doNothing().when(lock).unlock();
//        Mockito.doNothing().when(masterLock).lock();
//        Mockito.doNothing().when(Mockito.spy(masterLock)).unlock();
//        masterLock = Mockito.mock(ReentrantLock.class);
        masterLock = mock(ReentrantLock.class);
        doNothing().when(masterLock).lock();
        doNothing().when(masterLock).unlock();
    }

    @BeforeEach
    public void init() {
        overflow = new OverflowShelf(lock, 5);

    }

    @Test
    public void testPlaceOneOnShelf() {
        assertDataIntegrity();
        Order o = new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", 200, 0.25);
        overflow.placeOnShelf(o);
        assertTrue(overflow.getAvailableCells().size() + 1 == overflow.getCapacity() && overflow.getLocations().size() == 1);
        assertTrue(overflow.getCells()[overflow.getLocations().get(o.getId()).value()].getId() == o.getId());
        int countNonNulls = 0;
        for(int i = 0; i < overflow.getCapacity(); i++) {
            if(overflow.getCells()[i] != null) countNonNulls++;
        }
        assertEquals(1, countNonNulls);
        DoublyLinkedNode[] headTails =  overflow.getHeadsTails();
        assertTrue(headTails[0] == headTails[1] && headTails[0].value() == overflow.getLocations().get(o.getId()).value());
        assertTrue(headTails[2] == null && headTails[3] == null && headTails[4] == null && headTails[5] == null);
        assertDataIntegrity();
    }

    /**
     *
     */
    private void assertDataIntegrity() {
        try {
            cells = overflow.getCells();
            locations = overflow.getLocations();
            availableCells = overflow.getAvailableCells();
            capacity = overflow.getCapacity();
            hotHead = overflow.getHeadsTails()[0];
            hotTail = overflow.getHeadsTails()[1];
            coldHead = overflow.getHeadsTails()[2];
            coldTail = overflow.getHeadsTails()[3];
            frozenHead = overflow.getHeadsTails()[4];
            frozenTail = overflow.getHeadsTails()[5];

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

}
