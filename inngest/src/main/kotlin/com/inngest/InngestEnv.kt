package com.inngest

enum class InngestSystem(
    val value: String,
) {
    // Critical variables
    EventKey("INNGEST_EVENT_KEY"),
    SigningKey("INNGEST_SIGNING_KEY"),
    Env("INNGEST_ENV"),

    // Optional variables
    EventApiBaseUrl("INNGEST_BASE_URL"),
    ApiBaseUrl("INNGEST_API_BASE_URL"),
    LogLevel("INNGEST_LOG_LEVEL"),

    // TODO - Rename this env variable to match other SDKS
//    ApiOrigin("INNGEST_API_ORIGIN"),
    ServeOrigin("INNGEST_SERVE_ORIGIN"),
    ServePath("INNGEST_SERVE_PATH"),
    Dev("INNGEST_DEV"),
}

enum class InngestEnv(
    var value: String,
) {
    Dev("dev"),
    Prod("prod"),
    Other("other"),
}
