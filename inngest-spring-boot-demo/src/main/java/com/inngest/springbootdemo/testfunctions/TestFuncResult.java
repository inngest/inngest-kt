package com.inngest.springbootdemo.testfunctions;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor

public class TestFuncResult {
    int sum;

    TestFuncResult(int sum) {
        this.sum = sum;
    }
}
