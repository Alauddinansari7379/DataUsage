package dev.jahidhasanco.datausage.data.model

/** One row of the daily breakdown table. */
data class UsagesData(
    var mobile: String = "",
    var wifi: String = "",
    var date: String = "",
    var totalBytes: Long = 0L,
    var sharePercent: Int = 0
)
