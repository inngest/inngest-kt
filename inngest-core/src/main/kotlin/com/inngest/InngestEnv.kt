package com.inngest

enum class InngestSystem(val value: String) {
    // Critical variables
    EventKey("INNGEST_EVENT_KEY"),
    SigningKey("INNGEST_SIGNING_KEY"),
    Env("INNGEST_ENV"),

    // Optional variables
    LogLevel("INNGEST_LOG_LEVEL"),
    ApiOrigin("INNGEST_API_ORIGIN"),
    EventApiOrigin("INNGEST_EVENT_API_ORIGIN"),
    ServeOrigin("INNGEST_SERVE_ORIGIN"),
    ServePath("INNGEST_SERVE_PATH"),
}

enum class InngestEnv(val value: String) {
    Dev("dev"),
    Prod("prod"),
}
