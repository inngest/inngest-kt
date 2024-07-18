package com.inngest.springbootdemo;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.inngest.springboot.InngestController;

@RestController
@RequestMapping(value = "/api/inngest")
public class DemoController extends InngestController {

}
