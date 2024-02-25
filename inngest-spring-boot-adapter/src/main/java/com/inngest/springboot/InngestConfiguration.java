package com.inngest.springboot;

import com.inngest.CommHandler;
import com.inngest.Inngest;
import com.inngest.InngestFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;

public abstract class InngestConfiguration {
    protected abstract HashMap<String, InngestFunction> functions();

    @Bean
    protected abstract Inngest inngestClient();


    @Bean
    protected CommHandler commHandler(@Autowired Inngest inngestClient) {
        return new CommHandler(functions(), inngestClient);
    }
}
