package dev.jahidhasanco.datausage

/**
 * A snapshot of the currently active network link.
 *
 * Deliberately carries facts rather than display text; the presentation layer
 * turns [connected] / [transport] / [carrierName] into a localized label.
 */
data class ConnectionInfo(
    val connected: Boolean = false,
    val transport: NetworkType? = null,
    val carrierName: String = "",
    val metered: Boolean = false,
    val roaming: Boolean = false,
    val vpn: Boolean = false,
    val downstreamKbps: Int = 0,
    val upstreamKbps: Int = 0
)
