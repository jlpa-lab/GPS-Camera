package com.gps.map.camera.and.time.stamp.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.gps.map.camera.and.time.stamp.pref.AppSharedPreferencesApp
import com.gps.map.camera.and.time.stamp.ui.settings.AppearanceOption

/** Applies the saved appearance before an activity creates its views. */
object ThemeManager {
    fun apply(context: Context, appearance: AppearanceOption) {
        AppSharedPreferencesApp(context.applicationContext).appearanceMode = appearance.key
        val mode = when (appearance) {
            AppearanceOption.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppearanceOption.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppearanceOption.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun initialize(context: Context) {
        val saved = AppSharedPreferencesApp(context.applicationContext).appearanceMode
        val appearance = AppearanceOption.fromKey(saved)
        val mode = when (appearance) {
            AppearanceOption.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppearanceOption.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppearanceOption.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
