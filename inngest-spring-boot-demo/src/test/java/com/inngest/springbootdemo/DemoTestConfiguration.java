package com.inngest.springbootdemo;

import com.inngest.Inngest;
import com.inngest.InngestFunction;
import com.inngest.springboot.InngestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;

public class DemoTestConfiguration extends InngestConfiguration {
    @Override
    protected HashMap<String, InngestFunction> functions() {
        return new HashMap<>();
    }

    @Override
    protected Inngest inngestClient() {
        return new Inngest("spring_test_demo");
    }
}
