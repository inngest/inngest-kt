package com.inngest.springbootdemo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Result {
    @JsonProperty("sum")
    public final int sum;

    public Result(@JsonProperty("sum") int sum) {
        this.sum = sum;
    }
}
