package com.proj.ckitchens.svc;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;
import com.proj.ckitchens.model.Shelf;
import com.proj.ckitchens.svc.operations.ShelfService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;
import static com.proj.ckitchens.svc.ShelfMgmtSystem.shelfMgmtSystem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class ShelfMgmtSystemTest {
    private final static ReentrantLock lock = mock(ReentrantLock.class);
    private final static ReentrantLock overflowLock = mock(ReentrantLock.class);
    private static Shelf hotShelf;
    private static Shelf overflowShelf;
    private static ShelfService shelfService;

    @BeforeAll
    public static void setup() {
        masterLock = mock(ReentrantLock.class);
        doNothing().when(masterLock).lock();
        doNothing().when(masterLock).unlock();
        doNothing().when(lock).lock();
        doNothing().when(lock).unlock();
        doNothing().when(overflowLock).lock();
        doNothing().when(overflowLock).unlock();
    }

    @BeforeEach
    public void init() {
        hotShelf = new Shelf(lock, 1, Temperature.HOT.name());
        overflowShelf = new Shelf(overflowLock, 1, "Overflow");
        shelfService = spy(new ShelfService(
                hotShelf,
                mock(Shelf.class),
                mock(Shelf.class),
                overflowShelf
        ));
        shelfMgmtSystem = spy(new ShelfMgmtSystem(shelfService));
        doNothing().when(shelfMgmtSystem).readContents(anyString(), anyString());
    }
    @Test
    public void testPlaceOrderOnShelf() {
        Order order1 = generateOneOrder();
        shelfMgmtSystem.placeOrderOnShelf(order1);
        verify(shelfService, times(1)).placeOnShelf(order1, hotShelf);
        verify(shelfService, times(0)).placeOnShelf(order1, overflowShelf);
        assertTrue(shelfService.getHotShelf().getLocations().size() == 1);
        assertTrue(shelfService.getOverflowShelf().getLocations().size() == 0);

        //order will be placed on overflow
        Order order2 = generateOneOrder();
        shelfMgmtSystem.placeOrderOnShelf(order2);
        verify(shelfService, times(1)).placeOnShelf(order2, hotShelf);
        verify(shelfService, times(1)).placeOnShelf(order2, overflowShelf);
        assertTrue(shelfService.getHotShelf().getLocations().size() == 1);
        assertTrue(shelfService.getOverflowShelf().getLocations().size() == 1);

        //order on overflow will be discarded
        Order order3 = generateOneOrder();
        shelfMgmtSystem.placeOrderOnShelf(order3);
        verify(shelfService, times(1)).placeOnShelf(order3, hotShelf);
        verify(shelfService, times(2)).placeOnShelf(order3, overflowShelf);
        verify(shelfService, times(1)).discardRandom(overflowShelf);
        assertTrue(shelfService.getHotShelf().getLocations().size() == 1);
        assertTrue(shelfService.getOverflowShelf().getLocations().size() == 1);

        //verify data
        assertEquals(order1.getId(), shelfService.getHotShelf().getCells()[0].getId());
        assertEquals(order3.getId(), shelfService.getOverflowShelf().getCells()[0].getId());
        assertTrue(shelfService.getHotShelf().getLocations().get(order2.getId()) == null);
        assertTrue(shelfService.getOverflowShelf().getLocations().get(order2.getId()) == null);
    }

    @Test
    public void testDeliverOrder() {
        //deliver from hotShelf
        Order order1 = generateOneOrder();
        Order order2 = generateOneOrder();
        shelfMgmtSystem.placeOrderOnShelf(order1);
        shelfMgmtSystem.placeOrderOnShelf(order2);

        shelfMgmtSystem.deliverOrder(order1);
        verify(shelfService, times(1)).removeForDelivery(order1, overflowShelf);
        verify(shelfService, times(1)).removeForDelivery(order1, hotShelf);
        assertTrue(shelfService.getHotShelf().getLocations().get(order1.getId()) == null);
        assertEquals(order2.getId(), shelfService.getOverflowShelf().getCells()[0].getId());

        //deliver from overflow, no interaction with hotShelf
        shelfMgmtSystem.deliverOrder(order2);
        verify(shelfService, times(1)).removeForDelivery(order2, overflowShelf);
        verify(shelfService, times(0)).removeForDelivery(order2, hotShelf);
        assertTrue(shelfService.getOverflowShelf().getLocations().get(order2.getId()) == null);
    }

    @Test
    public void testMoverOrderFromOverflowToTemperatureShelf() {
        Order order1 = generateOneOrder();
        Order order2 = generateOneOrder();
        shelfMgmtSystem.placeOrderOnShelf(order1);//on hotShelf
        shelfMgmtSystem.placeOrderOnShelf(order2);//on overflow

        doReturn(true).when(shelfService).isCellAvailable(hotShelf);
        Order order3 = generateOneOrder();
        shelfMgmtSystem.placeOrderOnShelf(order3);
        verify(shelfMgmtSystem, times(1)).moveOrderFromOverflow(order3);
        verify(shelfService, times(1)).removeBasedOnTemperature(Temperature.HOT, overflowShelf);
        verify(shelfService, times(2)).placeOnShelf(order2, hotShelf); //inital placement + move
        verify(shelfService, times(2)).placeOnShelf(order3, overflowShelf);//first try on overflow + after move
    }

    @Test
    public void testCleanupOrdersEndOfLife() {
        doNothing().when(shelfService).cleanup(any());
        shelfMgmtSystem.cleanupOrdersEndOfLife();

        verify(shelfService, times(1)).cleanup(overflowShelf);
        verify(shelfService, times(1)).cleanup(hotShelf);
        verify(shelfService, times(1)).cleanup(shelfService.getColdShelf());
        verify(shelfService, times(1)).cleanup(shelfService.getFrozenShelf());
    }

    private Order generateOneOrder() {
        return new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", new Random().nextInt(300), Math.random());
    }
}

//package com.proj.ckitchens.svc;
//
//import com.proj.ckitchens.common.Temperature;
//import com.proj.ckitchens.model.Order;
//import com.proj.ckitchens.model.Shelf;
//import com.proj.ckitchens.svc.operations.ShelfService;
//import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//
//import java.util.Random;
//import java.util.UUID;
//import java.util.concurrent.locks.Lock;
//import java.util.concurrent.locks.ReentrantLock;
//
//import static com.proj.ckitchens.svc.ShelfMgmtSystem.masterLock;
//import static com.proj.ckitchens.svc.ShelfMgmtSystem.shelfMgmtSystem;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.Mockito.*;
//
//public class ShelfMgmtSystemTest {
//    private final static ReentrantLock lock = mock(ReentrantLock.class);
//    private final static ReentrantLock overflowLock = mock(ReentrantLock.class);
//    private static Shelf hotShelf;// = new Shelf(lock, 1, Temperature.HOT.name());
//    private static Shelf overflowShelf;// = new Shelf(overflowLock, 1, "Overflow");
//    private static ShelfService shelfService;// = spy(new ShelfService(
////            hotShelf, //new Shelf(lock, 1, Temperature.HOT.name()),
////            mock(Shelf.class),
////            mock(Shelf.class),
////            overflowShelf
////    ));
////    private static final ShelfMgmtSystem shelfMgmtSystem;
//
//    @BeforeAll
//    public static void setup() {
//
////        shelfMgmtSystem = spy(new ShelfMgmtSystem(shelfService));
//        masterLock = mock(ReentrantLock.class);
//        doNothing().when(masterLock).lock();
//        doNothing().when(masterLock).unlock();
//        doNothing().when(lock).lock();
//        doNothing().when(lock).unlock();
//        doNothing().when(overflowLock).unlock();
//        doNothing().when(overflowLock).unlock();
//    }
//
//    @BeforeEach
//    public void init() {
//        hotShelf = new Shelf(lock, 1, Temperature.HOT.name());
//        overflowShelf = new Shelf(overflowLock, 1, "Overflow");
//        shelfService = spy(new ShelfService(
//                hotShelf, //new Shelf(lock, 1, Temperature.HOT.name()),
//                mock(Shelf.class),
//                mock(Shelf.class),
//                overflowShelf
//        ));
//        shelfMgmtSystem = spy(new ShelfMgmtSystem(shelfService));
//    }
//    @Test
//    public void testPlaceOrderOnShelf() {
//        Order order1 = generateOneOrder();
//        shelfMgmtSystem.placeOrderOnShelf(order1);
//        verify(shelfService, times(1)).placeOnShelf(order1, hotShelf);
//        verify(shelfService, times(0)).placeOnShelf(order1, overflowShelf);
//        assertTrue(shelfService.getHotShelf().getLocations().size() == 1);
//        assertTrue(shelfService.getOverflowShelf().getLocations().size() == 0);
//
//        //order will be placed on overflow
//        Order order2 = generateOneOrder();
//        shelfMgmtSystem.placeOrderOnShelf(order2);
//        verify(shelfService, times(1)).placeOnShelf(order2, hotShelf);
//        verify(shelfService, times(1)).placeOnShelf(order2, overflowShelf);
//        assertTrue(shelfService.getHotShelf().getLocations().size() == 1);
//        assertTrue(shelfService.getOverflowShelf().getLocations().size() == 1);
//
//        //order on overflow will be discarded
//        Order order3 = generateOneOrder();
//        shelfMgmtSystem.placeOrderOnShelf(order3);
//        verify(shelfService, times(1)).placeOnShelf(order3, hotShelf);
//        verify(shelfService, times(2)).placeOnShelf(order3, overflowShelf);
//        verify(shelfService, times(1)).discardRandom(overflowShelf);
//        assertTrue(shelfService.getHotShelf().getLocations().size() == 1);
//        assertTrue(shelfService.getOverflowShelf().getLocations().size() == 1);
//
//        //verify data
//        assertEquals(order1.getId(), shelfService.getHotShelf().getCells()[0].getId());
//        assertEquals(order3.getId(), shelfService.getOverflowShelf().getCells()[0].getId());
//        assertTrue(shelfService.getHotShelf().getLocations().get(order2.getId()) == null);
//        assertTrue(shelfService.getOverflowShelf().getLocations().get(order2.getId()) == null);
//    }
//
//    @Test
//    public void testDeliverOrder() {
//        //deliver from hotShelf
//        Order order1 = generateOneOrder();
//        Order order2 = generateOneOrder();
//        shelfMgmtSystem.placeOrderOnShelf(order1);
//        shelfMgmtSystem.placeOrderOnShelf(order2);
//
//        shelfMgmtSystem.deliverOrder(order1);
//        verify(shelfService, times(1)).removeForDelivery(order1, overflowShelf);
//        verify(shelfService, times(1)).removeForDelivery(order1, hotShelf);
//        assertTrue(shelfService.getHotShelf().getLocations().get(order1.getId()) == null);
//        assertEquals(order2.getId(), shelfService.getOverflowShelf().getCells()[0].getId());
//
//        //deliver from overflow, no interaction with hotShelf
//        shelfMgmtSystem.deliverOrder(order2);
//        verify(shelfService, times(1)).removeForDelivery(order2, overflowShelf);
//        verify(shelfService, times(0)).removeForDelivery(order2, hotShelf);
//        assertTrue(shelfService.getOverflowShelf().getLocations().get(order2.getId()) == null);
//    }
//
//    @Test
//    public void testMoverOrderFromOverflowToTemperatureShelf() {
//        Order order1 = generateOneOrder();
//        Order order2 = generateOneOrder();
//        shelfMgmtSystem.placeOrderOnShelf(order1);//on hotShelf
//        shelfMgmtSystem.placeOrderOnShelf(order2);//on overflow
//
//        doReturn(true).when(shelfService).isCellAvailable(hotShelf);
//        Order order3 = generateOneOrder();
//        shelfMgmtSystem.placeOrderOnShelf(order3);
//        verify(shelfMgmtSystem, times(1)).moveOrderFromOverflow(order3);
//        verify(shelfService, times(1)).removeBasedOnTemperature(Temperature.HOT, overflowShelf);
//        verify(shelfService, times(2)).placeOnShelf(order2, hotShelf); //inital placement + move
//        verify(shelfService, times(2)).placeOnShelf(order3, overflowShelf);//first try on overflow + after move
//    }
//
//    @Test
//    public void testCleanupOrdersEndOfLife() {
//        doNothing().when(shelfService).cleanup(any());
////        doNothing().when(shelfService).cleanup(shelfService.getFrozenShelf());
//        shelfMgmtSystem.cleanupOrdersEndOfLife();
//
////        Lock fakeLock = mock(ReentrantLock.class);
////        doNothing().when(fakeLock).lock();
////        doNothing().when(fakeLock).unlock();
////        doReturn(fakeLock).when(shelfService.getColdShelf()).getLock();
//////        doNothing().when(shelfService.getColdShelf()).getLock();
//////        doNothing().when(shelfService.getFrozenShelf()).getLock();
////        doReturn(fakeLock).when(shelfService.getFrozenShelf()).getLock();
//
//        verify(shelfService, times(1)).cleanup(overflowShelf);
//        verify(shelfService, times(1)).cleanup(hotShelf);
//        verify(shelfService, times(1)).cleanup(shelfService.getColdShelf());
//        verify(shelfService, times(1)).cleanup(shelfService.getFrozenShelf());
//    }
//
//
//
//    private Order generateOneOrder() {
//        return new Order(UUID.randomUUID(), Temperature.HOT, "Pizza", new Random().nextInt(300), Math.random());
//    }
//}
