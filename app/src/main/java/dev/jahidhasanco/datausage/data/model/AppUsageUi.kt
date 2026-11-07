package dev.jahidhasanco.datausage.data.model

import android.graphics.drawable.Drawable

/** One row of the top-apps list, with its icon already resolved. */
data class AppUsageUi(
    val uid: Int,
    val label: String,
    val packageName: String,
    val downloads: Long,
    val uploads: Long,
    val total: Long,
    val sharePercent: Int,
    val icon: Drawable?
)
