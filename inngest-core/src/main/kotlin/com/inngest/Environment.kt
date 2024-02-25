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
}
