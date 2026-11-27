package com.gps.map.camera.and.time.stamp.ui.stamptemplate

import android.content.Context
import android.graphics.Color
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.pref.AppSharedPreferencesApp

enum class TemplateAddressMode { AUTOMATIC, MANUAL }
enum class TemplateAddressDetail { NONE, CITY, FULL }
enum class TemplateDateFormat { SHORT_MONTH, NUMERIC, TIME_FIRST, WEEKDAY, ISO }
enum class TemplatePosition { TOP, MIDDLE, BOTTOM }
enum class TemplateAlignment { LEFT, CENTER, RIGHT }

data class StampCustomization(
    val templateKey: String,
    val addressMode: TemplateAddressMode = TemplateAddressMode.AUTOMATIC,
    val addressDetail: TemplateAddressDetail = TemplateAddressDetail.FULL,
    val manualAddress: String = "",
    val showDateTime: Boolean = true,
    val dateFormat: TemplateDateFormat = TemplateDateFormat.SHORT_MONTH,
    val showMapIcon: Boolean = false,
    val showWeather: Boolean = false,
    val showCoordinates: Boolean = true,
    val backgroundColor: Int = 0xCC17283C.toInt(),
    val customColor: Int? = null,
    val recentColors: List<Int> = emptyList(),
    val position: TemplatePosition = TemplatePosition.BOTTOM,
    val alignment: TemplateAlignment = TemplateAlignment.LEFT
)

object StampTemplateCatalog {
    data class Item(val key: String, val nameRes: Int, val group: String)

    val items = listOf(
        Item("location_spotlight", R.string.template_name_location_spotlight, "Reference color"),
        Item("time_first", R.string.template_name_time_first, "Reference color"),
        Item("centered_destination", R.string.template_name_centered_destination, "Reference color"),
        Item("side_map", R.string.template_name_side_map, "Reference color"),
        Item("travel_footer", R.string.template_name_travel_footer, "Reference color"),
        Item("ocean_postcard", R.string.template_name_ocean_postcard, "Decorative"),
        Item("coral_travel_ticket", R.string.template_name_coral_travel_ticket, "Decorative"),
        Item("mint_location_label", R.string.template_name_mint_location_label, "Decorative"),
        Item("lavender_photo_journal", R.string.template_name_lavender_photo_journal, "Decorative"),
        Item("amber_field_note", R.string.template_name_amber_field_note, "Decorative"),
        Item("teal_destination_tag", R.string.template_name_teal_destination_tag, "Decorative"),
        Item("stamp_1", R.string.template_name_stamp_1, "Stamps"),
        Item("stamp_2", R.string.template_name_stamp_2, "Stamps"),
        Item("stamp_3", R.string.template_name_stamp_3, "Stamps"),
        Item("stamp_4", R.string.template_name_stamp_4, "Stamps"),
        Item("stamp_5", R.string.template_name_stamp_5, "Stamps"),
        Item("stamp_6", R.string.template_name_stamp_6, "Stamps"),
        Item("stamp_7", R.string.template_name_stamp_7, "Stamps"),
        Item("stamp_8", R.string.template_name_stamp_8, "Stamps")
    )

    /**
     * Older builds stored the three legacy Settings choices. Keep those values
     * readable so an upgrade immediately uses the equivalent rich template
     * instead of silently falling back to the first catalog item.
     */
    fun canonicalKey(key: String): String = when (key) {
        "classic" -> "location_spotlight"
        "minimal" -> "time_first"
        "detailed" -> "travel_footer"
        else -> key
    }

    fun item(key: String): Item {
        val canonical = canonicalKey(key)
        return items.firstOrNull { it.key == canonical } ?: items.first()
    }
}

object StampCustomizationStore {
    private const val SELECTED = "default_stamp"
    private const val PREFIX = "stamp_template_"

    val defaultRecentColors = listOf(
        0xCC17283C.toInt(),
        0xCC2878F0.toInt(),
        0xCC146B70.toInt(),
        0xCC594B8A.toInt(),
        0xCC4E6077.toInt()
    )

    fun selectedTemplate(context: Context): String {
        val preferences = AppSharedPreferencesApp(context.applicationContext)
        val saved = preferences.sharedPref.getString(SELECTED, preferences.defaultStamp).orEmpty()
        return StampTemplateCatalog.item(saved).key
    }

    fun selectTemplate(context: Context, key: String) {
        AppSharedPreferencesApp(context.applicationContext).defaultStamp = StampTemplateCatalog.item(key).key
    }

    fun read(context: Context, key: String = selectedTemplate(context)): StampCustomization {
        val prefs = AppSharedPreferencesApp(context.applicationContext).sharedPref
        val templateKey = StampTemplateCatalog.item(key).key
        val p = PREFIX + templateKey + "_"
        val defaults = defaultsFor(templateKey)
        val storedColors = prefs.getString(p + "recent_colors", null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { it !in 0x00000000..0x00FFFFFF }
            ?.take(5)
            .orEmpty()
        val recentColors = (storedColors + defaultRecentColors).distinct().take(5)
        return StampCustomization(
            templateKey = templateKey,
            addressMode = enumValue(prefs.getString(p + "address_mode", null), TemplateAddressMode.AUTOMATIC),
            addressDetail = enumValue(prefs.getString(p + "address_detail", null), TemplateAddressDetail.FULL),
            manualAddress = prefs.getString(p + "manual_address", "").orEmpty(),
            showDateTime = prefs.getBoolean(p + "show_date_time", true),
            dateFormat = enumValue(prefs.getString(p + "date_format", null), TemplateDateFormat.SHORT_MONTH),
            showMapIcon = prefs.getBoolean(p + "show_map_icon", defaults.mapIcon),
            showWeather = prefs.getBoolean(p + "show_weather", defaults.weather),
            showCoordinates = prefs.getBoolean(p + "show_coordinates", true),
            backgroundColor = prefs.getInt(p + "background_color", defaults.background),
            customColor = if (prefs.contains(p + "custom_color")) prefs.getInt(p + "custom_color", Color.TRANSPARENT) else null,
            recentColors = recentColors,
            position = enumValue(prefs.getString(p + "position", null), TemplatePosition.BOTTOM),
            alignment = enumValue(prefs.getString(p + "alignment", null), TemplateAlignment.LEFT)
        )
    }

    fun save(context: Context, value: StampCustomization) {
        val preferences = AppSharedPreferencesApp(context.applicationContext)
        val templateKey = StampTemplateCatalog.item(value.templateKey).key
        val p = PREFIX + templateKey + "_"
        preferences.editor
            .putString(SELECTED, templateKey)
            .putString(p + "address_mode", value.addressMode.name)
            .putString(p + "address_detail", value.addressDetail.name)
            .putString(p + "manual_address", value.manualAddress)
            .putBoolean(p + "show_date_time", value.showDateTime)
            .putString(p + "date_format", value.dateFormat.name)
            .putBoolean(p + "show_map_icon", value.showMapIcon)
            .putBoolean(p + "show_weather", value.showWeather)
            .putBoolean(p + "show_coordinates", value.showCoordinates)
            .putInt(p + "background_color", value.backgroundColor)
            .putString(p + "recent_colors", value.recentColors.joinToString(","))
            .putString(p + "position", value.position.name)
            .putString(p + "alignment", value.alignment.name)
            .apply()
        if (value.customColor == null) preferences.editor.remove(p + "custom_color").apply()
        else preferences.editor.putInt(p + "custom_color", value.customColor).apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private data class TemplateDefaults(val background: Int, val mapIcon: Boolean, val weather: Boolean)

    private fun defaultsFor(key: String): TemplateDefaults = when (key) {
        "location_spotlight", "time_first", "centered_destination", "side_map", "travel_footer" ->
            TemplateDefaults(0xCC17283C.toInt(), mapIcon = false, weather = false)
        "ocean_postcard" -> TemplateDefaults(0xB31C4770.toInt(), mapIcon = false, weather = false)
        "coral_travel_ticket" -> TemplateDefaults(0xB34B2638.toInt(), mapIcon = true, weather = false)
        "mint_location_label" -> TemplateDefaults(0xB3235B4E.toInt(), mapIcon = true, weather = false)
        "lavender_photo_journal" -> TemplateDefaults(0xB34B3D70.toInt(), mapIcon = false, weather = false)
        "amber_field_note" -> TemplateDefaults(0xB34A432F.toInt(), mapIcon = true, weather = false)
        "teal_destination_tag" -> TemplateDefaults(0xB3206264.toInt(), mapIcon = true, weather = false)
        "stamp_1", "stamp_4", "stamp_5", "stamp_6", "stamp_8" ->
            TemplateDefaults(0xB32C4861.toInt(), mapIcon = key == "stamp_4" || key == "stamp_6" || key == "stamp_8", weather = true)
        "stamp_2", "stamp_3", "stamp_7" ->
            TemplateDefaults(0xB32C4861.toInt(), mapIcon = true, weather = false)
        else -> TemplateDefaults(0xCC17283C.toInt(), mapIcon = false, weather = false)
    }
}
