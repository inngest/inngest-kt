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

    fun inngestAppId(
        clientId: String,
        serveId: String? = null,
    ): String {
        if (serveId != null) return serveId
        return clientId
    }

    fun inngestEventKey(key: String? = null): String {
        if (key != null) return key
        return System.getenv(InngestSystem.EventKey.value) ?: ""
    }

    fun inngestSigningKey(
        env: InngestEnv,
        key: String? = null,
    ): String {
        if (key != null) return key

        return when (env) {
            InngestEnv.Dev -> "test"
            else -> {
                val signingKey = System.getenv(InngestSystem.SigningKey.value)
                if (signingKey == null) {
                    throw Exception("signing key is required")
                }
                signingKey
            }
        }
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

    fun inngestApiBaseUrl(
        env: InngestEnv,
        url: String? = null,
    ): String {
        if (url != null) return url

        val baseUrl = System.getenv(InngestSystem.ApiBaseUrl.value)
        if (baseUrl != null) {
            return baseUrl
        }

        return when (env) {
            InngestEnv.Dev -> "http://127.0.0.1:8288"
            else -> "https://api.inngest.com"
        }
    }

    fun inngestServeHost(host: String? = null): String? {
        if (host != null) return host
        return System.getenv(InngestSystem.ServeHost.value)
    }

    fun inngestServePath(path: String? = null): String? {
        if (path != null) return path
        return System.getenv(InngestSystem.ServePath.value)
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
        val inngestEnv = System.getenv("INNGEST_ENV")
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

    fun inngestLogLevel(level: String? = null): String {
        if (level != null) return level
        return System.getenv(InngestSystem.LogLevel.value) ?: "info"
    }
}
