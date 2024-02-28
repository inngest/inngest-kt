package com.inngest.springbootdemo;

import com.inngest.*;

import java.util.HashMap;
import java.util.Objects;

public class InngestFunctionTestHelpers {

    static SendEventResponse sendEvent(Inngest inngest, String eventName) {
        InngestEvent event = new InngestEvent(eventName, new HashMap<String, String>());
        SendEventResponse response = inngest.send(event, SendEventResponse.class);

        assert Objects.requireNonNull(response).ids.length > 0;
        return response;
    }
}
