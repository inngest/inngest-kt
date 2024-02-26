package com.inngest.springbootdemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Request;

import javax.annotation.PreDestroy;

import okhttp3.OkHttpClient;
import okhttp3.Response;

public class DevServerComponent {
    static String baseUrl = "http://127.0.0.1:8288";
    OkHttpClient httpClient = new OkHttpClient();

    DevServerComponent() throws Exception {
        Runtime rt = Runtime.getRuntime();
        rt.exec("pkill inngest-cli");
        rt.exec("npx -y inngest-cli dev -u http://127.0.0.1:8080/api/inngest");

        waitForStartup();
    }

    private void waitForStartup() throws Exception {

        while (true) {
            try {
                Request request = new Request.Builder()
                    .url(baseUrl)
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.code() == 200) {
                        return;
                    }
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                Thread.sleep(1000);
            }
        }
    }

    RunIdsResponse runIds(String runId) throws Exception {
        Request request = new Request.Builder()
            .url(String.format("%s/v1/events/%s/runs", baseUrl, runId))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 200) {
                assert response.body() != null;

                String strResponse = response.body().string();
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(strResponse, RunIdsResponse.class);
            }
        }
        return null;
    }

    @PreDestroy
    public void stop() throws Exception {
        Runtime rt = Runtime.getRuntime();
        rt.exec("pkill inngest-cli");
    }
}
