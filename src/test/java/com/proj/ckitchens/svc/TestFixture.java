package com.proj.ckitchens.svc;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Order;

import java.util.Random;
import java.util.UUID;

public class TestFixture {
    public static Order generateOneHotOrder() {
        return generateOneOrder(Temperature.HOT);
    }

    public static Order generateOneOrder(Temperature t) {
        return new Order(UUID.randomUUID(), t, "Pizza", new Random().nextInt(300), Math.random());
    }
}
