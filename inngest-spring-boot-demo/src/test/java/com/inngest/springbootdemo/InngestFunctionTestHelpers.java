package com.inngest.springbootdemo;

import com.inngest.*;

import java.util.HashMap;
import java.util.Objects;

public class InngestFunctionTestHelpers {

    static SendEventsResponse sendEvent(Inngest inngest, String eventName) {
        InngestEvent event = new InngestEvent(eventName, new HashMap<String, String>());
        SendEventsResponse response = inngest.send(event);

        assert Objects.requireNonNull(response).getIds().length > 0;
        return response;
    }
}
