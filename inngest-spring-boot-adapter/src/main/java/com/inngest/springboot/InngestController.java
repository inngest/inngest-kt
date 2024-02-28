package com.inngest.springboot;

import com.inngest.CommHandler;
import com.inngest.CommResponse;
import com.inngest.signingkey.SignatureVerificationKt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

public abstract class InngestController {
    @Autowired
    CommHandler commHandler;

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        commHandler.getHeaders().forEach(headers::add);
        return headers;
    }

    @GetMapping()
    public ResponseEntity<String> index() {
        String response = commHandler.introspect();
        return ResponseEntity.ok().headers(getHeaders()).body(response);
    }

    @PutMapping()
    public ResponseEntity<String> put() {
        String response = commHandler.register();
        return ResponseEntity.ok().headers(getHeaders()).body(response);
    }

    @PostMapping()
    public ResponseEntity<String> handleRequest(
        @RequestHeader(name = "X-Inngest-Signature", required = false) String signature,
        @RequestHeader(name = "X-Inngest-Server-Kind", required = false) String serverKind,
        @RequestParam(name = "fnId") String functionId,
        @RequestBody String body
    ) {
        try {
            SignatureVerificationKt.checkHeadersAndValidateSignature(signature, body, serverKind, commHandler.getConfig());

            CommResponse response = commHandler.callFunction(functionId, body);

            HttpHeaders headers = new HttpHeaders();
            response.getHeaders().forEach(headers::add);

            return ResponseEntity.status(response.getStatusCode().getCode()).headers(headers)
                .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(e.toString());
        }
    }
}
