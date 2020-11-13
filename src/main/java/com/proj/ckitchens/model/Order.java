package com.proj.ckitchens.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.proj.ckitchens.common.Temperature;

import java.util.UUID;

public class Order {
    private final UUID id;
    private final Temperature temp;
    private final String name;
    private final long shelfLife;
    private final double decayRate;

    private long placementTime;
    private long moveTime;
    private long lifeAfterMove = Long.MAX_VALUE;
    private boolean isMoved = false;

    @JsonCreator
    public Order(
        @JsonProperty("id")
        UUID id,
        @JsonProperty("temp")
        Temperature temp,
        @JsonProperty("name")
        String name,
        @JsonProperty("shelfLife")
        long shelfLife,
        @JsonProperty("decayRate")
        double decayRate
    ) {
        this.id = id;
        this.name = name;
        this.temp = temp;
        this.shelfLife = shelfLife;
        this.decayRate = decayRate;
    }

    public UUID getId() {
        return id;
    }

    public long getShelfLife() {
        return shelfLife;
    }

    public Temperature getTemp() {
        return temp;
    }

    public void setPlacementTime() {
        this.placementTime = System.currentTimeMillis();
    }

    public void setLifeAfterMove() {
        long timeOnOverflow = (System.currentTimeMillis() - placementTime)/1000;
        lifeAfterMove = shelfLife - timeOnOverflow - (long)decayRate * timeOnOverflow * 2;
        moveTime = System.currentTimeMillis();
        isMoved = true;
    }

    public double computeRemainingLifeValue(int modifier) {
        if(!isMoved) {
            long orderAge = (System.currentTimeMillis() - placementTime)/1000;
            return (shelfLife - orderAge - decayRate * orderAge * modifier)/shelfLife;
        }
        if(isMoved) {
            long ageOnNewShelf = (System.currentTimeMillis() - moveTime)/1000;
            return (lifeAfterMove - ageOnNewShelf - decayRate * ageOnNewShelf * modifier)/lifeAfterMove;
        }
        return Long.MAX_VALUE;
    }

    public long getLifeAfterMove() {
        return this.lifeAfterMove;
    }

    public boolean isMoved() {
        return isMoved;
    }
}
