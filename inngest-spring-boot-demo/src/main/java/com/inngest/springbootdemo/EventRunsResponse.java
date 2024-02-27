package com.inngest.springbootdemo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
class EventRunsResponse<T> {
    RunEntry<T>[] data;

    RunEntry<T> first() {
        return data[0];
    }
}

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
class RunResponse<T> {
    RunEntry<T> data;
}

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
class RunEntry<T> {
    String run_id;
    String event_id;

    String run_started_at;
    String ended_at;

    String function_id;
    String function_version;

    T output;
    String status;
}

