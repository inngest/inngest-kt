package com.inngest.springbootdemo;

import com.inngest.InngestSystem;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.ServerSocket;

public class IntegrationTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final int DEV_SERVER_PORT = freePort();
    private static final String DEV_SERVER_BASE_URL = "http://127.0.0.1:" + DEV_SERVER_PORT;

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        System.setProperty(InngestSystem.ApiBaseUrl.getValue(), DEV_SERVER_BASE_URL);
        System.setProperty(InngestSystem.EventApiBaseUrl.getValue(), DEV_SERVER_BASE_URL);
    }

    static String devServerBaseUrl() {
        return DEV_SERVER_BASE_URL;
    }

    static int devServerPort() {
        return DEV_SERVER_PORT;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate a free TCP port for integration tests", e);
        }
    }
}
