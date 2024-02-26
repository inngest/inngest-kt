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
        functions.put("no-step-fn", InngestFunctionTestHelpers.emptyStepFunction());
        functions.put("sleep-fn", InngestFunctionTestHelpers.sleepStepFunction());
        functions.put("two-steps-fn", InngestFunctionTestHelpers.twoStepsFunction());

        return functions;
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
