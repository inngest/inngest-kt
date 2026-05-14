package com.inngest.springboot;

import com.inngest.CommHandler;
import com.inngest.CommResponse;
import com.inngest.InngestEnv;
import com.inngest.InngestQueryParamKey;
import com.inngest.SyncResponse;
import com.inngest.signingkey.SignatureVerificationKt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.NativeWebRequest;

import java.lang.reflect.InvocationTargetException;

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
        @RequestHeader(name = "X-Inngest-Server-Kind", required = false) String serverKind,
        NativeWebRequest request
    ) {
        String origin = String.format("%s://%s", getRequestScheme(request), hostHeader);
        if (this.serveOrigin != null && !this.serveOrigin.isEmpty()) {
            origin = this.serveOrigin;
        }
        SyncResponse response = commHandler.register(
            origin,
            request.getParameter(InngestQueryParamKey.SyncId.getValue()),
            serverKind
        );

        HttpHeaders headers = new HttpHeaders();
        response.getHeaders().forEach(headers::add);

        return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody());
    }

    private String getRequestScheme(NativeWebRequest request) {
        Object nativeRequest = request.getNativeRequest();
        if (nativeRequest == null) {
            return "http";
        }

        try {
            Object scheme = nativeRequest.getClass().getMethod("getScheme").invoke(nativeRequest);
            if (scheme instanceof String && !((String) scheme).isEmpty()) {
                return (String) scheme;
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            return "http";
        }

        return "http";
    }

    @PostMapping()
    public ResponseEntity<String> handleRequest(
        @RequestHeader(name = "X-Inngest-Signature", required = false) String signature,
        @RequestHeader(name = "X-Inngest-Server-Kind", required = false) String serverKind,
        @RequestParam(name = "fnId", required = false) String functionId,
        @RequestBody String body
    ) {
        try {
            if (functionId == null) {
                return commResponse(commHandler.protocolErrorResponse(new IllegalArgumentException("Missing fnId parameter")));
            }

            SignatureVerificationKt.checkHeadersAndValidateSignature(signature, body, serverKind, commHandler.getConfig());

            CommResponse response = commHandler.callFunction(functionId, body);

            return commResponse(response);
        } catch (Exception e) {
            return commResponse(commHandler.protocolErrorResponse(e));
        }
    }

    private ResponseEntity<String> commResponse(CommResponse response) {
        HttpHeaders headers = new HttpHeaders();
        response.getHeaders().forEach(headers::add);

        return ResponseEntity.status(response.getStatusCode().getCode()).headers(headers)
            .body(response.getBody());
    }
}
