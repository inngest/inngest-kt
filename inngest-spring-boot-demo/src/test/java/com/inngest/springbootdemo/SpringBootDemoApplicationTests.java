package com.inngest.springbootdemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@IntegrationTest
@Import(DemoTestConfiguration.class)
@Execution(ExecutionMode.CONCURRENT)
class SpringBootDemoApplicationIntegrationTest {
    @Value("${TEST_URL:http://localhost:8080}")
    private String testUrl;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext.getBean(DevServerComponent.class));
    }

}
