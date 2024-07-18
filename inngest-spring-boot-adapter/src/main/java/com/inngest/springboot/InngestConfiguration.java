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
    protected abstract ServeConfig serve(@Autowired Inngest inngestClient);

    @Bean
    protected CommHandler commHandler(@Autowired Inngest inngestClient, @Autowired ServeConfig serve) {
        return new CommHandler(functions(), inngestClient, serve, frameworkName);
    }
}
