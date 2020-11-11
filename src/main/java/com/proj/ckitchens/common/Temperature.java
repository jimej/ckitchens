package com.proj.ckitchens.common;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Temperature {
    @JsonProperty("hot")
    HOT,
    @JsonProperty("cold")
    COLD,
    @JsonProperty("frozen")
    FROZEN
}
