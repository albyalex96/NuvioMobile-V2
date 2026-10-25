package com.nuvio.app.core.network.dns

import com.nuvio.app.core.mediaproxy.isValidIpv4
import kotlinx.serialization.Serializable

/**
 * DNS over HTTPS resolvers offered in Settings → Advanced → Network. [System] keeps the device
 * resolver; [Custom] uses the address typed by the user. Brand names are not translated.
 */
enum class DnsOverHttpsProvider(
    val id: String,
    val displayName: String,
    val url: String?,
    val bootstrapAddresses: List<String>,
) {
    System("system", "", null, emptyList()),
    Cloudflare(
        "cloudflare",
        "Cloudflare",
        "https://cloudflare-dns.com/dns-query",
        listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"),
    ),
    Google(
        "google",
        "Google",
        "https://dns.google/dns-query",
        listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844"),
    ),
    Quad9(
        "quad9",
        "Quad9",
        "https://dns.quad9.net/dns-query",
        listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9"),
    ),
    AdGuard(
        "adguard",
        "AdGuard",
        "https://dns.adguard-dns.com/dns-query",
        listOf("94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff"),
    ),
    Mullvad(
        "mullvad",
        "Mullvad",
        "https://dns.mullvad.net/dns-query",
        listOf("194.242.2.2", "2a07:e340::2"),
    ),
    OpenDns(
        "opendns",
        "OpenDNS",
        "https://doh.opendns.com/dns-query",
        listOf("208.67.222.222", "208.67.220.220", "2620:119:35::35", "2620:119:53::53"),
    ),
    Custom("custom", "", null, emptyList());

    companion object {
        fun fromId(id: String?): DnsOverHttpsProvider =
            entries.firstOrNull { it.id == id?.trim()?.lowercase() } ?: System
    }
}

/**
 * Device DNS options. The provider is stored by id so values written by newer builds fall back to
 * [DnsOverHttpsProvider.System] instead of breaking the decoding.
 */
@Serializable
data class DnsOverHttpsSettings(
    val provider: String = DnsOverHttpsProvider.System.id,
    /** Host name, IP address or full https:// URL of the custom resolver. */
    val customAddress: String = "",
    /** Optional IP addresses of the custom resolver host, separated by commas or spaces. */
    val customBootstrap: String = "",
    /** Uses the device resolver when the DoH server does not answer. */
    val fallbackToSystem: Boolean = true,
) {
    val providerType: DnsOverHttpsProvider
        get() = DnsOverHttpsProvider.fromId(provider)

    val isEnabled: Boolean
        get() = resolveDnsOverHttpsEndpoint(this) != null

    fun normalized(): DnsOverHttpsSettings = copy(
        provider = providerType.id,
        customAddress = customAddress.trim(),
        customBootstrap = customBootstrap.trim(),
    )
}

/** Resolver actually used for the lookups: DoH URL plus the IPs of its host, when known. */
data class DnsOverHttpsEndpoint(
    val url: String,
    val bootstrapAddresses: List<String>,
)

/** Null when DoH is off or the custom address is not valid (the device resolver is used). */
fun resolveDnsOverHttpsEndpoint(settings: DnsOverHttpsSettings): DnsOverHttpsEndpoint? {
    val provider = settings.providerType
    return when (provider) {
        DnsOverHttpsProvider.System -> null
        DnsOverHttpsProvider.Custom -> {
            val address = parseDnsOverHttpsAddress(settings.customAddress) as? DohAddressParseResult.Valid
                ?: return null
            val bootstrap = when {
                address.hostIsIp -> listOf(address.host)
                else -> (parseDnsOverHttpsBootstrap(settings.customBootstrap) as? DohBootstrapParseResult.Valid)
                    ?.addresses
                    .orEmpty()
            }
            DnsOverHttpsEndpoint(address.url, bootstrap)
        }
        else -> DnsOverHttpsEndpoint(provider.url ?: return null, provider.bootstrapAddresses)
    }
}

sealed interface DohAddressParseResult {
    data class Valid(val url: String, val host: String, val hostIsIp: Boolean) : DohAddressParseResult
    enum class Error : DohAddressParseResult { Empty, NotHttps, InvalidHost }
}

sealed interface DohBootstrapParseResult {
    data class Valid(val addresses: List<String>) : DohBootstrapParseResult
    data class Invalid(val value: String) : DohBootstrapParseResult
}

private const val DEFAULT_DOH_PATH = "/dns-query"

/**
 * Accepts a host name (`dns.adguard-dns.com`), an IPv4 (`1.1.1.1`) or IPv6 (`2606:4700::1111`,
 * also in brackets) address, optionally with a port and a path, or a full `https://` URL. A
 * missing path becomes `/dns-query`, the standard DoH endpoint.
 */
fun parseDnsOverHttpsAddress(input: String): DohAddressParseResult {
    val value = input.trim()
    if (value.isEmpty()) return DohAddressParseResult.Error.Empty
    if (value.any(Char::isWhitespace)) return DohAddressParseResult.Error.InvalidHost
    val lower = value.lowercase()
    if (lower.startsWith("http://")) return DohAddressParseResult.Error.NotHttps
    if ("://" in lower && !lower.startsWith("https://")) return DohAddressParseResult.Error.NotHttps
    val withoutScheme = if (lower.startsWith("https://")) value.substring("https://".length) else value

    // A bare IPv6 address has no path and several colons: wrap it in brackets.
    val normalized = if (withoutScheme.count { it == ':' } >= 2 && !withoutScheme.startsWith("[") && '/' !in withoutScheme) {
        "[$withoutScheme]"
    } else {
        withoutScheme
    }
    val authority = normalized.substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isEmpty() || '@' in authority) return DohAddressParseResult.Error.InvalidHost
    val rest = normalized.substring(authority.length)

    val host: String
    val port: String?
    if (authority.startsWith("[")) {
        val end = authority.indexOf(']')
        if (end < 0) return DohAddressParseResult.Error.InvalidHost
        host = authority.substring(1, end)
        val afterHost = authority.substring(end + 1)
        port = when {
            afterHost.isEmpty() -> null
            afterHost.startsWith(":") -> afterHost.substring(1)
            else -> return DohAddressParseResult.Error.InvalidHost
        }
        if (!isValidIpv6(host)) return DohAddressParseResult.Error.InvalidHost
    } else {
        host = authority.substringBefore(':')
        port = authority.substringAfter(':', missingDelimiterValue = "").takeIf { ':' in authority }
        if (!isValidIpv4(host) && !isValidDnsHostName(host)) return DohAddressParseResult.Error.InvalidHost
    }
    if (port != null && (port.isEmpty() || !port.all(Char::isDigit) || port.toIntOrNull() !in 1..65535)) {
        return DohAddressParseResult.Error.InvalidHost
    }

    val hostIsIp = isValidIpv4(host) || isValidIpv6(host)
    val urlAuthority = if (isValidIpv6(host)) "[$host]" else host.lowercase()
    val path = rest.ifEmpty { DEFAULT_DOH_PATH }.let { if (it == "/") DEFAULT_DOH_PATH else it }
    val url = "https://$urlAuthority${port?.let { ":$it" }.orEmpty()}$path"
    return DohAddressParseResult.Valid(url = url, host = if (hostIsIp) host else host.lowercase(), hostIsIp = hostIsIp)
}

/** Empty input is valid (no bootstrap): the resolver host is then found with the device DNS. */
fun parseDnsOverHttpsBootstrap(input: String): DohBootstrapParseResult {
    val parts = input.split(',', ';', ' ', '\n', '\t').map(String::trim).filter(String::isNotEmpty)
    val addresses = parts.map { part ->
        val address = part.removePrefix("[").removeSuffix("]")
        if (!isValidIpv4(address) && !isValidIpv6(address)) return DohBootstrapParseResult.Invalid(part)
        address
    }
    return DohBootstrapParseResult.Valid(addresses.distinct())
}

/** Host name with at least two labels; all-numeric names must be valid IPv4 addresses instead. */
fun isValidDnsHostName(value: String): Boolean {
    if (value.length > 253) return false
    val labels = value.removeSuffix(".").split('.')
    if (labels.size < 2) return false
    if (labels.all { label -> label.all(Char::isDigit) }) return false
    return labels.all { label ->
        label.length in 1..63 &&
            label.first().isLetterOrDigit() &&
            label.last().isLetterOrDigit() &&
            label.all { it.isAsciiLetterOrDigit() || it == '-' }
    }
}

/** IPv6 literal (hex groups, a single `::`, optional embedded IPv4); no zone ids. */
fun isValidIpv6(value: String): Boolean {
    if (value.isEmpty() || value.length > 45 || ':' !in value) return false
    if (":::" in value || value.split("::").size > 2) return false
    if (value.startsWith(":") && !value.startsWith("::")) return false
    if (value.endsWith(":") && !value.endsWith("::")) return false
    var text = value
    val lastColon = text.lastIndexOf(':')
    val tail = text.substring(lastColon + 1)
    if ('.' in tail) {
        // The embedded IPv4 counts as the last two groups.
        if (!isValidIpv4(tail)) return false
        text = text.substring(0, lastColon + 1) + "0:0"
    }
    val hasCompression = "::" in text
    val groups = text.split(':')
    val nonEmpty = groups.filter { it.isNotEmpty() }
    if (nonEmpty.any { group -> group.length > 4 || !group.all { it.isHexDigit() } }) return false
    if (!hasCompression && groups.any { it.isEmpty() }) return false
    return if (hasCompression) nonEmpty.size <= 7 else nonEmpty.size == 8
}

private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
