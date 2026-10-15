package com.nuvio.app.core.logging

/**
 * Masks secrets before a log line is stored, so copied or shared logs never carry credentials.
 *
 * Covers: `Bearer` tokens, `Authorization` headers, secret-looking query/form parameters and JSON
 * fields (tokens, API keys, passwords) and long URL path segments (addon configurations such as
 * Stremio manifests embed debrid keys in the path). 40-character info hashes stay readable.
 */
internal object AppLogRedactor {
    const val MASK = "***"

    private const val SECRET_NAMES =
        "access_token|refresh_token|id_token|token|api_key|apikey|api-key|key|secret|client_secret|" +
            "password|passwd|pwd|auth|authorization|session|session_id|sessionid|code|device_code|pin"

    private val bearer = Regex("""(?i)\b(bearer|basic)\s+[A-Za-z0-9._~+/=\-]+""")
    private val authorizationHeader = Regex("""(?i)\b(authorization|x-api-key|api-key|cookie)\s*[:=]\s*[^\s,;"']+""")
    private val queryParameter = Regex("""(?i)([?&;\s]|^)($SECRET_NAMES)=([^&\s"',;]+)""")
    private val jsonField = Regex("""(?i)"($SECRET_NAMES)"\s*:\s*"[^"]*"""")
    private val longPathSegment = Regex("""(?<=/)[A-Za-z0-9%._~+=\-]{48,}(?=[/?#\s"']|$)""")

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var result = text
        result = bearer.replace(result) { "${it.groupValues[1]} $MASK" }
        result = authorizationHeader.replace(result) { "${it.groupValues[1]}: $MASK" }
        result = queryParameter.replace(result) { "${it.groupValues[1]}${it.groupValues[2]}=$MASK" }
        result = jsonField.replace(result) { "\"${it.groupValues[1]}\":\"$MASK\"" }
        result = longPathSegment.replace(result, MASK)
        return result
    }
}
