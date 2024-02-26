package com.inngest.springbootdemo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
class RunIdsResponse {
    RunIdsResponseDataEntry[] data;

    RunIdsResponseDataEntry first() {
        return data[0];
    }
}

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
class RunIdsResponseDataEntry {
    String output;
    String status;
}
