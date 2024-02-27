package com.inngest.springbootdemo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
class SendEventResponse {
    String status;
    String[] ids;

    String first() {
        return ids[0];
    }
}
