package com.inngest.springbootdemo;


import com.inngest.*;
import com.inngest.springboot.InngestConfiguration;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
public class DemoConfiguration extends InngestConfiguration {

    @Override
    public HashMap<String, InngestFunction> functions() {
        HashMap<String, InngestFunction> functions = new HashMap<>();

        functions.put("fn-id-slug", new UserSignupFunction());
        functions.put("fn-follow-up", new FollowupFunction());

        return functions;
    }

    @Override
    protected Inngest inngestClient() {
        return new Inngest("spring_demo");
    }

    @Override
    protected ServeConfig serve(Inngest inngestClient) {
        return new ServeConfig(inngestClient);
    }
}
