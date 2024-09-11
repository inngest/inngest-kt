package com.inngest.springbootdemo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
class EventsResponse {
    EventEntry[] data;
}

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
class EventEntry {
    String id;
    String name;

    String internal_id;

    EventEntryData data;
}

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
class EventEntryData{
    EventData event;
}

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
class EventData {
    String name;
}

