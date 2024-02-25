package com.inngest

enum class InngestHeaderKey(val value: String) {
    ContentType("content-type"),
    UserAgent("user-agent"),
    Sdk("x-inngest-sdk"),
    Framework("x-inngest-framework"),
    Environment("x-inngest-env"),
    Platform("x-inngest-platform"),
    NoRetry("x-inngest-no-retry"),
    RequestVersion("x-inngest-req-version"),
    RetryAfter("retry-after"),
    ServerKind("x-inngest-server-kind"),
    ExpectedServerKind("x-inngest-expected-server-kind"),
    Signature("x-inngest-signature"),
}
