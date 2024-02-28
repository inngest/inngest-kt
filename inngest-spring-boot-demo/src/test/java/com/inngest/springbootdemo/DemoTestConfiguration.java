package com.inngest.springbootdemo;

import com.inngest.*;
import com.inngest.springboot.InngestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;

public class DemoTestConfiguration extends InngestConfiguration {
    @Override
    protected HashMap<String, InngestFunction> functions() {
        HashMap<String, InngestFunction> functions = new HashMap<>();
        addInngestFunction(functions, InngestFunctionTestHelpers.emptyStepFunction());
        addInngestFunction(functions, InngestFunctionTestHelpers.sleepStepFunction());
        addInngestFunction(functions, InngestFunctionTestHelpers.twoStepsFunction());
        addInngestFunction(functions, InngestFunctionTestHelpers.customStepResultFunction());
        addInngestFunction(functions, InngestFunctionTestHelpers.waitForEventFunction());
        addInngestFunction(functions, InngestFunctionTestHelpers.sendEventFunction());
        addInngestFunction(functions, InngestFunctionTestHelpers.nonRetriableErrorFunction());
        addInngestFunction(functions, InngestFunctionTestHelpers.retriableErrorFunction());

        return functions;
    }

    private static void addInngestFunction(
        HashMap<String, InngestFunction> functions,
        InngestFunction function) {
        functions.put(function.getConfig().getId(), function);
    }

    @Override
    protected Inngest inngestClient() {
        return new Inngest("spring_test_demo");
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
