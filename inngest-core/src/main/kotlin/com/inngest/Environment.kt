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

    fun inngestEventKey(key: String? = null): String {
        if (key != null) return key
        return System.getenv(InngestSystem.EventKey.value) ?: "NO_EVENT_KEY_SET"
    }

    fun inngestEventApiBaseUrl(
        env: InngestEnv,
        url: String? = null,
    ): String {
        if (url != null) return url

        val baseUrl = System.getenv(InngestSystem.EventApiBaseUrl.value)
        if (baseUrl != null) {
            return baseUrl
        }

        return when (env) {
            InngestEnv.Dev -> "http://127.0.0.1:8288"
            InngestEnv.Prod -> "https://inn.gs"
            InngestEnv.Other -> "https://inn.gs"
        }
    }

    fun inngestEnv(
        env: String? = null,
        isDev: Boolean? = null,
    ): InngestEnv {
        if (isDev != null) {
            return when (isDev) {
                true -> InngestEnv.Dev
                false -> InngestEnv.Prod
            }
        }
        val sysDev = System.getenv(InngestSystem.Dev.value)
        if (sysDev != null) {
            return when (sysDev) {
                "0" -> InngestEnv.Prod
                "1" -> InngestEnv.Dev
                else -> {
                    var other = InngestEnv.Other
                    other.value = sysDev
                    other
                }
            }
        }

        if (env != null) {
            return when (env) {
                "dev" -> InngestEnv.Dev
                "development" -> InngestEnv.Dev
                "prod" -> InngestEnv.Prod
                "production" -> InngestEnv.Prod
                else -> {
                    var other = InngestEnv.Other
                    other.value = env
                    other
                }
            }
        }

        // Read from environment variable
        val inngestEnv = System.getenv(InngestSystem.Env.value)
        return when (inngestEnv) {
            null -> InngestEnv.Dev
            "dev" -> InngestEnv.Dev
            "development" -> InngestEnv.Dev
            "prod" -> InngestEnv.Prod
            "production" -> InngestEnv.Prod

            else -> {
                var other = InngestEnv.Other
                other.value = inngestEnv
                other
            }
        }
    }
}
