package com.inngest.springbootdemo;

import com.inngest.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class InngestFunctionTestHelpers {

    static SendEventsResponse sendEvent(Inngest inngest, String eventName) {
        return sendEvent(inngest, eventName, new HashMap());
    }

    static SendEventsResponse sendEvent(Inngest inngest, String eventName, Map<String, Object> data) {
        InngestEvent event = new InngestEvent(eventName, data);
        SendEventsResponse response = inngest.send(event);

        assert Objects.requireNonNull(response).getIds().length > 0;
        return response;
    }
}
