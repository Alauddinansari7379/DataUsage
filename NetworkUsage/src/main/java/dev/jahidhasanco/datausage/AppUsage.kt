package dev.jahidhasanco.datausage

/**
 * What a [AppUsage] row actually represents.
 *
 * NetworkStats attributes some traffic to synthetic uids, and package visibility
 * can hide the rest. The library reports the category rather than an English
 * label so the presentation layer can localize it.
 */
enum class AppKind {
    /** A real, resolvable application. [AppUsage.label] holds its name. */
    NORMAL,

    /** Traffic routed through tethering / hotspot clients. */
    TETHERING,

    /** Traffic belonging to apps that have since been removed. */
    UNINSTALLED,

    /** The platform itself. */
    SYSTEM,

    /** A uid the package manager would not resolve for us. */
    UNKNOWN
}

/** Data attributed to a single application (identified by its Linux [uid]). */
data class AppUsage(
    val uid: Int,
    var packageName: String = "",
    var label: String = "",
    var downloads: Long = 0L,
    var uploads: Long = 0L,
    var isSystem: Boolean = false,
    var kind: AppKind = AppKind.NORMAL
) {
    val total: Long get() = downloads + uploads
}
