package com.gps.map.camera.and.time.stamp.ads

import androidx.annotation.Keep
@Keep
data class AdUnitConfig(
    val id: String,
    val isEnable: Boolean,
    val key: String = "",
    val enableUaCheck: Boolean = false,
    val reloadIntervalSeconds: Int? = null,
    val colorCTA: String = "default",
    val heightCTA: Int = 45,
    val positionCTA: String = "BOTTOM",
    val components: List<String> = listOf("icon_headline", "body", "media", "cta")
)








