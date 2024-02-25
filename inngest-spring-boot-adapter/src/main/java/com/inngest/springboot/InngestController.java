package com.inngest.springboot;

import com.inngest.CommHandler;
import com.inngest.CommResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

public abstract class InngestController {
    @Autowired
    CommHandler commHandler;

    private static final HttpHeaders commonHeaders = new HttpHeaders();

    static {
        String inngestSdk = "inngest-kt:v0.0.1";
        commonHeaders.add("x-inngest-sdk", inngestSdk);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> index() {
        String response = commHandler.introspect();
        return ResponseEntity.ok().headers(commonHeaders).body(response);
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> put() {
        String response = commHandler.register();
        return ResponseEntity.ok().headers(commonHeaders).body(response);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleRequest(
        @RequestParam(name = "fnId") String functionId,
        @RequestBody String body
    ) {
        try {
            CommResponse response = commHandler.callFunction(functionId, body);

            return ResponseEntity.status(response.getStatusCode().getCode()).headers(commonHeaders)
                .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(e.toString());
        }
    }
}
