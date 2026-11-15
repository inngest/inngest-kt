package com.inngest.springboot;

import com.inngest.CommHandler;
import com.inngest.CommResponse;
import com.inngest.FunctionContext;
import com.inngest.Inngest;
import com.inngest.InngestFunction;
import com.inngest.InngestFunctionConfigBuilder;
import com.inngest.ResultStatusCode;
import com.inngest.ServeConfig;
import com.inngest.Step;
import com.inngest.SupportedFrameworkName;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class EventDataJavaInteropTest {
    private static final String FN_ID = "capture-fn";

    @Test
    void nestedEventDataIsPlainJavaMap() {
        CaptureFunction function = new CaptureFunction();
        HashMap<String, InngestFunction> functions = new HashMap<>();
        functions.put(FN_ID, function);

        CommResponse response = getResponse(functions);
        assertEquals(ResultStatusCode.FunctionComplete, response.getStatusCode(), response.getBody());

        FunctionContext ctx = function.captured;
        Map<String, Object> data = ctx.getEvent().getData();

        Object reference = data.get("reference");
        assertInstanceOf(Map.class, reference);
        assertEquals("https://inngest.com", ((Map<?, ?>) reference).get("url"));

        Object items = data.get("items");
        assertInstanceOf(List.class, items);
        assertInstanceOf(Map.class, ((List<?>) items).get(0));
    }

    @NotNull
    private static CommResponse getResponse(HashMap<String, InngestFunction> functions) {
        Inngest client = new Inngest("test-app", null, "evt-key", null, true);
        CommHandler commHandler = new CommHandler(
            functions,
            client,
            new ServeConfig(client),
            SupportedFrameworkName.SpringBoot
        );

        String requestBody = "{"
            + "\"ctx\": {\"attempt\": 0, \"fn_id\": \"" + FN_ID + "\", \"run_id\": \"run-test\", \"env\": \"test\"},"
            + "\"event\": {\"name\": \"test/run\", \"data\": {\"reference\": {\"url\": \"https://inngest.com\"}, \"items\": [{\"id\": 1}]}},"
            + "\"events\": [{\"name\": \"test/run\", \"data\": {\"reference\": {\"url\": \"https://inngest.com\"}, \"items\": [{\"id\": 1}]}}],"
            + "\"steps\": {}"
            + "}";

        return commHandler.callFunction(FN_ID, requestBody);
    }

    private static class CaptureFunction extends InngestFunction {
        private FunctionContext captured;

        @Override
        public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
            return builder.id(FN_ID);
        }

        @Override
        public Object execute(FunctionContext ctx, Step step) {
            captured = ctx;
            return "captured";
        }
    }
}
