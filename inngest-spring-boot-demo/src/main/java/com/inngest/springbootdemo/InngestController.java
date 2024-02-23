package com.inngest.springbootdemo;

import com.inngest.CommResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class InngestController {

    private static final HttpHeaders commonHeaders = new HttpHeaders();

    static {
        String inngestSdk = "inngest-kt:v0.0.1";
        commonHeaders.add("x-inngest-sdk", inngestSdk);
    }

    @GetMapping("/inngest")
    public ResponseEntity<String> index() {
        String response = InngestSingleton.getInstance().introspect();

        return ResponseEntity.ok().headers(commonHeaders).body(response);
    }

    @PutMapping("/inngest")
    public ResponseEntity<String> put() {
        String response = InngestSingleton.getInstance().register();
        return ResponseEntity.ok().headers(commonHeaders).body(response);
    }

    @PostMapping(value = "/inngest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleRequest(
        @RequestParam(name = "fnId") String functionId,
        @RequestBody String body
    ) {
        try {
            CommResponse response = InngestSingleton.getInstance().callFunction(functionId, body);

            return ResponseEntity.status(response.getStatusCode().getCode()).headers(commonHeaders)
                .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(e.toString());
        }
    }
}
