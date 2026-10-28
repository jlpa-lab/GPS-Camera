package com.gps.map.camera.and.time.stamp.data.model

import androidx.annotation.Keep

@Keep
data class ForceUpdateConfig(
    val icon: String = "",
    val title: String = "",
    val description: String = "",
    val storeLink: String = "",
    val minVersionCode: Int = 0,
    val force: Boolean = false
)









