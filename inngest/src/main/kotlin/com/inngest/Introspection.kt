package com.inngest

import com.beust.klaxon.Json

abstract class Introspection(
    @Json("authentication_succeeded") open val authenticationSucceeded: Boolean?,
    open val functionCount: Int,
    open val hasEventKey: Boolean,
    open val hasSigningKey: Boolean,
    open val mode: String,
    @Json("schema_version") val schemaVersion: String = "2024-05-24",
)

internal data class InsecureIntrospection(
    @Json("authentication_succeeded") override var authenticationSucceeded: Boolean? = null,
    @Json("function_count") override val functionCount: Int,
    @Json("has_event_key") override val hasEventKey: Boolean,
    @Json("has_signing_key") override val hasSigningKey: Boolean,
    override val mode: String,
) : Introspection(authenticationSucceeded, functionCount, hasEventKey, hasSigningKey, mode)

internal data class SecureIntrospection(
    @Json("api_origin") val apiOrigin: String,
    @Json("app_id") val appId: String,
    @Json("authentication_succeeded") override val authenticationSucceeded: Boolean?,
    // TODO: Add capabilities when adding the trust probe
    // @Json("capabilities") val capabilities: Capabilities,
    @Json("event_api_origin") val eventApiOrigin: String,
    @Json("event_key_hash") val eventKeyHash: String?,
    val env: String?,
    val framework: String,
    @Json("function_count") override val functionCount: Int,
    @Json("has_event_key") override val hasEventKey: Boolean,
    @Json("has_signing_key") override val hasSigningKey: Boolean,
    @Json("has_signing_key_fallback") val hasSigningKeyFallback: Boolean = false,
    override val mode: String,
    @Json("sdk_language") val sdkLanguage: String,
    @Json("sdk_version") val sdkVersion: String,
    @Json("serve_origin") val serveOrigin: String?,
    @Json("serve_path") val servePath: String?,
    @Json("signing_key_fallback_hash") val signingKeyFallbackHash: String? = null,
    @Json("signing_key_hash") val signingKeyHash: String?,
) : Introspection(authenticationSucceeded, functionCount, hasEventKey, hasSigningKey, mode)
