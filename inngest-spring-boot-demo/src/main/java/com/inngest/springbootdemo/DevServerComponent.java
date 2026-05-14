package com.inngest.springbootdemo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inngest.CommHandler;
import com.inngest.InngestSystem;
import okhttp3.Request;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class DevServerComponent implements DisposableBean {
    @FunctionalInterface
    interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    private final CommHandler commHandler;
    private final OkHttpClient httpClient = new OkHttpClient();

    private String baseUrl;
    private Process devServerProcess;
    private volatile boolean started = false;

    DevServerComponent(CommHandler commHandler) {
        this.commHandler = commHandler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start(ApplicationReadyEvent event) throws Exception {
        if (started) {
            return;
        }

        int appPort = getWebServerPort(event);
        String appOrigin = "http://127.0.0.1:" + appPort;
        this.baseUrl = configuredDevServerBaseUrl();
        int devServerPort = URI.create(baseUrl).getPort();

        devServerProcess = new ProcessBuilder(devServerCommand(appOrigin, devServerPort))
            .redirectErrorStream(true)
            .start();

        waitForStartup(appOrigin);
        started = true;
    }

    private int getWebServerPort(ApplicationReadyEvent event) {
        Object applicationContext = event.getApplicationContext();

        try {
            Object webServer = applicationContext.getClass().getMethod("getWebServer").invoke(applicationContext);
            Object port = webServer.getClass().getMethod("getPort").invoke(webServer);
            if (port instanceof Number) {
                return ((Number) port).intValue();
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Application context does not expose an embedded web server port", e);
        }

        throw new IllegalStateException("Application context returned a non-numeric embedded web server port");
    }

    private List<String> devServerCommand(
        String appOrigin,
        int devServerPort
    ) {
        String explicitCliPath = System.getenv("INNGEST_CLI_PATH");
        if (hasText(explicitCliPath)) {
            return devServerCommand(explicitCliPath, appOrigin, devServerPort);
        }

        if (!isCommandOnPath("inngest")) {
            throw new IllegalStateException(
                "The `inngest` CLI must be available on PATH. Run tests via `nix develop` or set INNGEST_CLI_PATH."
            );
        }

        return devServerCommand("inngest", appOrigin, devServerPort);
    }

    private List<String> devServerCommand(
        String cliPath,
        String appOrigin,
        int devServerPort
    ) {
        return Arrays.asList(
            cliPath,
            "dev",
            "-u",
            appOrigin + "/api/inngest",
            "--port",
            String.valueOf(devServerPort),
            "--no-discovery",
            "--no-poll",
            "--retry-interval",
            "1"
        );
    }

    private boolean isCommandOnPath(String command) {
        String path = System.getenv("PATH");
        if (!hasText(path)) {
            return false;
        }

        for (String pathEntry : path.split(System.getProperty("path.separator"))) {
            Path candidate = Paths.get(pathEntry, command);
            if (Files.isExecutable(candidate)) {
                return true;
            }
        }

        return false;
    }

    private void waitForStartup(String appOrigin) throws Exception {
        while (true) {
            try {
                Request request = new Request.Builder()
                    .url(String.format("%s/health", baseUrl))
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.code() == 200) {
                        Thread.sleep(6000);
                        commHandler.register(appOrigin, null);
                        return;
                    }
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                Thread.sleep(1000);
            }
        }
    }

    private String configuredDevServerBaseUrl() {
        String propertyKey = InngestSystem.ApiBaseUrl.getValue();
        String configuredBaseUrl = System.getProperty(propertyKey);
        if (hasText(configuredBaseUrl)) {
            return configuredBaseUrl;
        }

        String envBaseUrl = System.getenv(propertyKey);
        if (hasText(envBaseUrl)) {
            return envBaseUrl;
        }

        return "http://127.0.0.1:8288";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // TODO: Figure out how to make this generic.
    // The issue is that deserialization will fail on the `output` generic
    // type and return either `LinkedHashMap` or `ArrayList`.
    EventRunsResponse<Object> runsByEvent(String eventId) throws Exception {
        Request request = new Request.Builder()
            .url(String.format("%s/v1/events/%s/runs", baseUrl, eventId))
            .build();
        return makeRequest(request, new TypeReference<EventRunsResponse<Object>>() {
        });
    }

    EventsResponse listEvents() throws Exception {
        Request request = new Request.Builder()
            .url(String.format("%s/v1/events", baseUrl))
            .build();
        return makeRequest(request, new TypeReference<EventsResponse>() {
        });
    }

    EventRunsResponse<Object> waitForEventRuns(
        String eventId,
        Duration timeout
    ) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            EventRunsResponse<Object> runs = runsByEvent(eventId);
            if (runs != null && runs.getData() != null && runs.getData().length > 0) {
                return runs;
            }

            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for runs for event " + eventId);
            }

            Thread.sleep(100);
        }
    }

    RunEntry<Object> waitForRunStatus(
        String runId,
        String expectedStatus,
        Duration timeout
    ) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        String lastStatus = null;
        while (true) {
            RunResponse<Object> run = runById(runId, Object.class);
            if (run != null && run.getData() != null) {
                if (expectedStatus.equals(run.getData().getStatus())) {
                    return run.getData();
                }
                lastStatus = run.getData().getStatus();
            }

            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError(
                    String.format(
                        "Timed out waiting for run %s to reach status %s; last status was %s",
                        runId,
                        expectedStatus,
                        lastStatus
                    )
                );
            }

            Thread.sleep(100);
        }
    }

    EventEntry waitForEvent(
        Predicate<EventEntry> matcher,
        Duration timeout
    ) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            EventsResponse events = listEvents();
            if (events != null && events.getData() != null) {
                for (EventEntry event : events.getData()) {
                    if (matcher.test(event)) {
                        return event;
                    }
                }
            }

            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for matching event");
            }

            Thread.sleep(100);
        }
    }

    void waitForCondition(
        String description,
        Duration timeout,
        CheckedBooleanSupplier condition
    ) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            if (condition.getAsBoolean()) {
                return;
            }

            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for " + description);
            }

            Thread.sleep(100);
        }
    }

    <T> RunResponse<T> runById(String eventId, Class<T> outputType) throws Exception {
        Request request = new Request.Builder()
            .url(String.format("%s/v1/runs/%S", baseUrl, eventId))
            .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 200) {
                assert response.body() != null;

                String strResponse = response.body().string();
                ObjectMapper mapper = new ObjectMapper();

                JsonNode node = mapper.readTree(strResponse);
                JsonNode dataResult = node.path("data").path("output");

                T output = mapper.treeToValue(dataResult, outputType);
                RunResponse<T> result = mapper.readValue(strResponse, new TypeReference<RunResponse<T>>() {
                });
                result.getData().setOutput(output);
                return result;
            }
        }
        return null;
    }

    private <T> T makeRequest(Request request, TypeReference<T> typeReference) throws Exception {
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 200) {
                assert response.body() != null;

                String strResponse = response.body().string();
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(strResponse, typeReference);
            }
        }
        return null;
    }


    @Override
    public void destroy() {
        if (devServerProcess != null && devServerProcess.isAlive()) {
            devServerProcess.destroy();
        }
    }
}
