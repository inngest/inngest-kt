package com.inngest.testing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inngest.Event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProtocolFixtures {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolFixtures() {
    }

    public static Event event(String name) {
        return event(name, linkedData("message", "hello"));
    }

    public static Event event(String name, LinkedHashMap<String, Object> data) {
        return new Event("evt-test", name, data, null, 1700000000000L, null);
    }

    public static LinkedHashMap<String, Object> linkedData(Object... keyValues) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            data.put((String) keyValues[i], keyValues[i + 1]);
        }
        return data;
    }

    public static Map<String, Object> memoizedData(Object data) {
        return Collections.<String, Object>singletonMap("data", data);
    }

    public static Map<String, Object> memoizedError(String name, String message) {
        LinkedHashMap<String, Object> error = new LinkedHashMap<>();
        error.put("name", name);
        error.put("message", message);
        error.put("stack", name + ": " + message);

        return Collections.<String, Object>singletonMap("error", error);
    }

    public static String executionRequestPayloadJson(String functionId) {
        Event event = event("test/run");
        return executionRequestPayloadJson(functionId, event, Collections.singletonList(event), Collections.emptyMap());
    }

    public static String executionRequestPayloadJson(
        String functionId,
        Event event,
        List<Event> events,
        Map<String, Object> steps
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("ctx", executionContext(functionId));
        payload.put("event", event);
        payload.put("events", events);
        payload.put("steps", steps);

        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("failed to serialize execution payload", e);
        }
    }

    public static String hashStepId(String stepId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashedBytes = digest.digest(stepId.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashedByte : hashedBytes) {
                builder.append(String.format("%02x", hashedByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    private static Map<String, Object> executionContext(String functionId) {
        LinkedHashMap<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("attempt", 0);
        ctx.put("fn_id", functionId);
        ctx.put("run_id", "run-test");
        ctx.put("env", "test");
        return ctx;
    }
}
