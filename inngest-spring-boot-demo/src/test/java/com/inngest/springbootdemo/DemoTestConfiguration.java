package com.inngest.springbootdemo;

import com.inngest.*;
import com.inngest.springboot.InngestConfiguration;
import com.inngest.springbootdemo.testfunctions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;

public class DemoTestConfiguration extends InngestConfiguration {
    @Override
    protected HashMap<String, InngestFunction> functions() {
        HashMap<String, InngestFunction> functions = new HashMap<>();
        addInngestFunction(functions, new EmptyStepFunction());
        addInngestFunction(functions, new SleepStepFunction());
        addInngestFunction(functions, new TwoStepsFunction());
        addInngestFunction(functions, new CustomStepFunction());
        addInngestFunction(functions, new WaitForEventFunction());
        addInngestFunction(functions, new SendEventFunction());
        addInngestFunction(functions, new NonRetriableErrorFunction());
        addInngestFunction(functions, new RetriableErrorFunction());

        return functions;
    }

    private static void addInngestFunction(
        HashMap<String, InngestFunction> functions,
        InngestFunction function) {
        functions.put(function.id(), function);
    }

    @Override
    protected Inngest inngestClient() {
        return new Inngest("spring_test_demo");
    }

    @Override
    protected ServeConfig serve(Inngest client) {
        return new ServeConfig(client);
    }

    @Bean
    protected CommHandler commHandler(@Autowired Inngest inngestClient) {
        ServeConfig serveConfig = new ServeConfig(inngestClient);
        return new CommHandler(functions(), inngestClient, serveConfig, SupportedFrameworkName.SpringBoot);
    }

    @Bean
    protected DevServerComponent devServerComponent(@Autowired Inngest inngestClient) throws Exception {
        return new DevServerComponent();
    }
}
