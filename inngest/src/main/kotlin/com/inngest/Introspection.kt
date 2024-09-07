package com.inngest

import com.beust.klaxon.Json

abstract class Introspection(
    open val functionCount: Int,
    open val hasEventKey: Boolean,
    open val hasSigningKey: Boolean,
    open val mode: String,
    @Json("authentication_succeeded") open val authenticationSucceeded: Boolean?,
    @Json("schema_version") val schemaVersion: String = "2024-05-24",
)

internal data class InsecureIntrospection(
    @Json("function_count") override val functionCount: Int,
    @Json("has_event_key") override val hasEventKey: Boolean,
    @Json("has_signing_key") override val hasSigningKey: Boolean,
    override val mode: String,
    @Json("authentication_succeeded") override var authenticationSucceeded: Boolean? = null,
) : Introspection(functionCount, hasEventKey, hasSigningKey, mode, authenticationSucceeded)

internal data class SecureIntrospection(
    @Json("authentication_succeeded") override val authenticationSucceeded: Boolean?,
    @Json("function_count") override val functionCount: Int,
    @Json("has_event_key") override val hasEventKey: Boolean,
    @Json("has_signing_key") override val hasSigningKey: Boolean,
    override val mode: String,
    @Json("api_origin") val apiOrigin: String,
    @Json("app_id") val appId: String,
    // TODO: Add capabilities when adding the trust probe
//    @Json("capabilities") val capabilities: Capabilities,
    val env: String?,
    @Json("event_api_origin") val eventApiOrigin: String,
    @Json("event_key_hash") val eventKeyHash: String?,
    val framework: String,
    @Json("sdk_language") val sdkLanguage: String,
    @Json("sdk_version") val sdkVersion: String,
    @Json("serve_origin") val serveOrigin: String?,
    @Json("serve_path") val servePath: String?,
    // TODO: Remove the default value when implementing signing key fallback
    @Json("signing_key_fallback_hash") val signingKeyFallbackHash: String? = null,
    @Json("has_signing_key_fallback") val hasSigningKeyFallback: Boolean = false,
    @Json("signing_key_hash") val signingKeyHash: String?,
) : Introspection(functionCount, hasEventKey, hasSigningKey, mode, authenticationSucceeded)
