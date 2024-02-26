package com.inngest.springboot;

import com.inngest.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;

public abstract class InngestConfiguration {
    private final SupportedFrameworkName frameworkName = SupportedFrameworkName.SpringBoot;

    protected abstract HashMap<String, InngestFunction> functions();

    @Bean
    protected abstract Inngest inngestClient();

    @Bean
    protected CommHandler commHandler(@Autowired Inngest inngestClient) {
        ServeConfig serveConfig = new ServeConfig(inngestClient);
        return new CommHandler(functions(), inngestClient, serveConfig, frameworkName);
    }
}
