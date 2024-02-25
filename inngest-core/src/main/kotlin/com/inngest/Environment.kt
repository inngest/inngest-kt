package com.inngest

object Environment {
    fun inngestHeaders(framework: SupportedFrameworkName? = null): RequestHeaders {
        val sdk = "inngest-kt:${Version.getVersion()}"
        return mapOf(
            InngestHeaderKey.ContentType.value to "application/json",
            InngestHeaderKey.Sdk.value to sdk,
            InngestHeaderKey.UserAgent.value to sdk,
            InngestHeaderKey.Framework.value to (framework?.value),
        ).filterValues { (it is String) }.entries.associate { (k, v) -> k to v!! }
    }

    fun eventKey(key: String? = null): String {
        if (key != null) return key
        return System.getenv(InngestSystem.EventKey.value)
    }

    fun baseUrl(url: String? = null): String {
        if (url != null) return url
        return "https://inn.gs"
    }

    fun env(env: String? = null): String {
        return "prod"
    }
}
