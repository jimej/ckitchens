package com.proj.ckitchens.utils;

import java.util.Random;

public class RandomInt {
    public static int randomDelay(int low, int up) {
        if (low > up || low <= 0) return 5;
        return new Random().nextInt((up + low)/2) + low;
    }
}
