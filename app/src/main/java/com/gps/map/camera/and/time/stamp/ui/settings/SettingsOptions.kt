package com.gps.map.camera.and.time.stamp.ui.settings

enum class AppearanceOption(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String): AppearanceOption =
            entries.firstOrNull { it.key == key } ?: DARK
    }
}

enum class MeasurementUnit(val key: String) {
    METRIC("metric"),
    IMPERIAL("imperial");

    companion object {
        fun fromKey(key: String): MeasurementUnit =
            entries.firstOrNull { it.key == key } ?: METRIC
    }
}

enum class StampTemplate(val key: String) {
    CLASSIC("classic"),
    MINIMAL("minimal"),
    DETAILED("detailed");

    companion object {
        fun fromKey(key: String): StampTemplate =
            entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

data class SettingsState(
    val appearance: AppearanceOption,
    val languageCode: String,
    val units: MeasurementUnit,
    val stamp: StampTemplate
)
