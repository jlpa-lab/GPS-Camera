package com.gps.map.camera.and.time.stamp.pref

import android.content.Context
import android.content.SharedPreferences

class AppSharedPreferencesApp(context: Context) : AppSharedPref {

    companion object {

        private const val PREFERENCE_FILE_KEY = "app_shared_preferences_app"

        private const val LANGUAGE_CODE = "language_code"

        private const val FIRST_LANGUAGE = "first_language"

        private const val FIRST_ONBOARDING = "first_onboarding"

        private const val IS_CONFIRM_CONSENT = "is_confirm_consent"

        private const val IS_USER_GLOBAL = "is_user_global"

        private const val IS_RATE = "is_rate"

        private const val APPEARANCE_MODE = "appearance_mode"

        private const val MEASUREMENT_UNIT = "measurement_unit"

        private const val DEFAULT_STAMP = "default_stamp"
    }

    override val sharedPref: SharedPreferences =
        context.getSharedPreferences(PREFERENCE_FILE_KEY, Context.MODE_PRIVATE)

    override val editor: SharedPreferences.Editor
        get() = sharedPref.edit()

    override var languageCode: String
        get() = sharedPref.getString(LANGUAGE_CODE, "en") ?: "en"
        set(value) = editor.putString(LANGUAGE_CODE, value).apply()

    override var firstLanguage: Boolean
        get() = sharedPref.getBoolean(FIRST_LANGUAGE, true)
        set(value) = editor.putBoolean(FIRST_LANGUAGE, value).apply()

    override var firstOnBoarding: Boolean
        get() = sharedPref.getBoolean(FIRST_ONBOARDING, true)
        set(value) = editor.putBoolean(FIRST_ONBOARDING, value).apply()

    override var isConfirmConsent: Boolean
        get() = sharedPref.getBoolean(IS_CONFIRM_CONSENT, false)
        set(value) = editor.putBoolean(IS_CONFIRM_CONSENT, value).apply()

    override var isUserGlobal: Boolean
        get() = sharedPref.getBoolean(IS_USER_GLOBAL, false)
        set(value) = editor.putBoolean(IS_USER_GLOBAL, value).apply()

    override var isRate: Boolean
        get() = sharedPref.getBoolean(IS_RATE, false)
        set(value) = editor.putBoolean(IS_RATE, value).apply()

    override var appearanceMode: String
        get() = sharedPref.getString(APPEARANCE_MODE, "dark") ?: "dark"
        set(value) = editor.putString(APPEARANCE_MODE, value).apply()

    override var measurementUnit: String
        get() = sharedPref.getString(MEASUREMENT_UNIT, "metric") ?: "metric"
        set(value) = editor.putString(MEASUREMENT_UNIT, value).apply()

    override var defaultStamp: String
        get() = sharedPref.getString(DEFAULT_STAMP, "classic") ?: "classic"
        set(value) = editor.putString(DEFAULT_STAMP, value).apply()

}







