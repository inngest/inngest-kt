package com.inngest.springboot;

import com.inngest.CommHandler;
import com.inngest.CommResponse;
import com.inngest.InngestEnv;
import com.inngest.signingkey.SignatureVerificationKt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

public abstract class InngestController {
    @Autowired
    CommHandler commHandler;

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        commHandler.getHeaders().forEach(headers::add);
        return headers;
    }

    // ex. https://api.mysite.com
    @Value("${inngest.serveOrigin:}")
    private String serveOrigin;

    @GetMapping
    public ResponseEntity<String> index(
        @RequestHeader(HttpHeaders.HOST) String hostHeader,
        @RequestHeader(name = "X-Inngest-Signature", required = false) String signature,
        @RequestHeader(name = "X-Inngest-Server-Kind", required = false) String serverKind
    ) {
        String requestBody = "";
        String response = commHandler.introspect(signature, requestBody, serverKind);
        return ResponseEntity.ok().headers(getHeaders()).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PutMapping()
    public ResponseEntity<String> put(
        @RequestHeader(HttpHeaders.HOST) String hostHeader,
        HttpServletRequest request
    ) {
        String origin = String.format("%s://%s", request.getScheme(), hostHeader);
        if (this.serveOrigin != null && !this.serveOrigin.isEmpty()) {
            origin = this.serveOrigin;
        }
        String response = commHandler.register(origin);
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
