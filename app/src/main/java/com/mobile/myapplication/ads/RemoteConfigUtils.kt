package com.mobile.myapplication.ads

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.mobile.myapplication.BuildConfig
import com.mobile.myapplication.GlobalApplication
import com.mobile.myapplication.app.AppConstants
import com.mobile.myapplication.app.AppConstants.DEFAULT_TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON
import com.mobile.myapplication.data.model.ForceUpdateConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object RemoteConfigUtils {
    var completed = false
    private const val ON_SHOW_NAVIGATION_BUTTON = "on_show_navigation_button"
    private const val ON_SHOW_DIALOG_CONSENT = "on_show_dialog_consent"
    private const val DELAY_SHOW_LANGUAGE_DONE_BUTTON = "delay_button_done_language"
    private const val TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON = "time_delay_show_language_done_button"
    private const val AD_REMOTE_CONFIG = "ad_remote_config"
    private const val FORCE_UPDATE_CONFIG = "force_update_config"
    private const val BACKEND_BASE_URL = "backend_base_url"
    private const val TERMS_AND_CONDITIONS_URL = "terms_and_conditions_url"
    private const val PRIVACY_POLICY_URL = "privacy_policy_url"
    private const val MAPS_API_KEY = "maps_api_key"
    private const val GOOGLE_PLACES_API_KEY = "google_places_api_key"

    private const val ON_ENABLE_UNINSTALL_WIDGET = "on_enable_uninstall_widget"

    private val mapConditionForAd: HashMap<String, Any> = hashMapOf(
        ON_SHOW_DIALOG_CONSENT to true,
        ON_SHOW_NAVIGATION_BUTTON to false,
        DELAY_SHOW_LANGUAGE_DONE_BUTTON to true,
        ON_ENABLE_UNINSTALL_WIDGET to false,
        TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON to DEFAULT_TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON,
    )
    
    //Default layout
    private const val AD_LANGUAGE_LAYOUT_FILE = "ad_language_layout.json"
    private const val AD_REMOTE_CONFIG_FILE_DEBUG = "ad_config_debug.json"
    private const val AD_REMOTE_CONFIG_FILE_RELEASE = "ad_config.json"
    private const val FORCE_UPDATE_CONFIG_FILE = "force_update_config.json"

    fun getOnShowNavigationButton(): Boolean = getBoolean(ON_SHOW_NAVIGATION_BUTTON)
    fun getOnShowDialogConsent(): Boolean = getBoolean(ON_SHOW_DIALOG_CONSENT)
    fun getOnEnableUninstallWidget(): Boolean = getBoolean(ON_ENABLE_UNINSTALL_WIDGET, false)
    const val TRACKER_BACKEND_URL: String = "https://phone-tracker-backend-ol00.onrender.com"

    fun getBackendBaseUrl(): String = getString(BACKEND_BASE_URL, TRACKER_BACKEND_URL)
//    fun getTermsAndConditionsUrl(): String =
//        getString(TERMS_AND_CONDITIONS_URL, AppConstants.LINK_TERMS_AND_CONDITIONS)
//    fun getPrivacyPolicyUrl(): String =
//        getString(PRIVACY_POLICY_URL, AppConstants.LINK_PRIVACY_POLICY)
//    fun getMapsApiKey(): String = getString(MAPS_API_KEY, BuildConfig.MAPS_API_KEY)
//    fun getGooglePlacesApiKey(): String =
//        getString(GOOGLE_PLACES_API_KEY, BuildConfig.GOOGLE_PLACES_API_KEY)

    interface Listener {
        fun loadSuccess()
    }

    lateinit var listener: Listener
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adUnitConfigAdapter = moshi.adapter(AdUnitConfig::class.java)
    private val forceUpdateAdapter = moshi.adapter(ForceUpdateConfig::class.java)

    fun init(context: Context, mListener: Listener) {
        listener = mListener
        completed = false
        remoteConfig = getFirebaseRemoteConfig(context)
    }

    private fun getDefaultsFromAdConfig(context: Context): Map<String, Any> {
        val defaults = mutableMapOf<String, Any>()
        try {
            if (!AdRemoteConfig.isInitialized()) {
                AdRemoteConfig.initializeFromAssets(context)
            }
            val adConfig = AdRemoteConfig.getInstance()
            adConfig.ads.forEach { (key, adUnitConfig) ->
                val jsonString = adUnitConfigAdapter.toJson(adUnitConfig)
                defaults[key] = jsonString
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return defaults
    }

    private fun getFirebaseRemoteConfig(context: Context): FirebaseRemoteConfig {
        remoteConfig = Firebase.remoteConfig

        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) {
                0
            } else {
                60 * 60
            }
        }
        val defaults = getDefaultsFromAdConfig(context).toMutableMap()
        // Firebase Remote Config stores the complete placement map as one JSON string.
        // Keep the bundled build-specific file as the SDK default as well, so the app
        // remains usable when fetchAndActivate cannot reach Firebase.
        defaults[AD_REMOTE_CONFIG] = loadDefaultAdRemoteConfig()
       // defaults[BACKEND_BASE_URL] = BuildConfig.TRACKER_BACKEND_URL
        defaults[TERMS_AND_CONDITIONS_URL] = AppConstants.LINK_TERMS_AND_CONDITIONS
        defaults[PRIVACY_POLICY_URL] = AppConstants.LINK_PRIVACY_POLICY
        //defaults[MAPS_API_KEY] = BuildConfig.MAPS_API_KEY
       // defaults[GOOGLE_PLACES_API_KEY] = BuildConfig.GOOGLE_PLACES_API_KEY
        remoteConfig.apply {
            setConfigSettingsAsync(configSettings)
            mapConditionForAd.forEach { (key, value) ->
                defaults[key] = value
            }
            setDefaultsAsync(defaults)
            fetchAndActivate().addOnCompleteListener {
                completed = true
                listener.loadSuccess()
            }
        }
        return remoteConfig
    }

    private fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return try {
            if (!completed) defaultValue
            else remoteConfig.getBoolean(key)
        } catch (ex: Exception) {
            ex.printStackTrace()
            defaultValue
        }
    }


    private fun getLong(key: String, defaultValue: Long = 0): Long {
        return try {
            if (!completed) defaultValue
            else remoteConfig.getLong(key)
        } catch (ex: Exception) {
            ex.printStackTrace()
            defaultValue
        }
    }

    private fun getString(key: String, defaultValue: String): String {
        return try {
            if (!completed) defaultValue
            else remoteConfig.getString(key).trim().ifBlank { defaultValue }
        } catch (ex: Exception) {
            ex.printStackTrace()
            defaultValue
        }
    }


    fun getAdRemoteConfig(): String {
        val defaultValue = loadDefaultAdRemoteConfig()
        if (!completed) {
            return defaultValue
        } else {
            val configValue = remoteConfig.getString(AD_REMOTE_CONFIG)
            return configValue.ifBlank { defaultValue }
        }
    }


    fun getForceUpdateConfig(): ForceUpdateConfig? {
        val defaultJson = loadDefaultForceUpdateConfig()
        val json = if (!completed) {
            defaultJson
        } else {
            val configValue = remoteConfig.getString(FORCE_UPDATE_CONFIG)
            configValue.ifBlank { defaultJson }
        }

        return try {
            forceUpdateAdapter.fromJson(json)
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    private fun loadDefaultAdLanguageLayout(): String {
        return try {
            GlobalApplication.instance.assets.open(AD_LANGUAGE_LAYOUT_FILE).bufferedReader().use { reader ->
                reader.readText()
            }
        } catch (ex: Exception) {
            "{}"
        }
    }

    private fun loadDefaultForceUpdateConfig(): String {
        return try {
            GlobalApplication.instance.assets.open(FORCE_UPDATE_CONFIG_FILE).bufferedReader().use { reader ->
                reader.readText()
            }
        } catch (ex: Exception) {
            "{}"
        }
    }

    private fun loadDefaultAdRemoteConfig(): String {
        val fileName = if (BuildConfig.DEBUG) {
            AD_REMOTE_CONFIG_FILE_DEBUG
        } else {
            AD_REMOTE_CONFIG_FILE_RELEASE
        }
        return try {
            GlobalApplication.instance.assets.open(fileName).bufferedReader().use { reader ->
                reader.readText()
            }
        } catch (ex: Exception) {
            "{}"
        }
    }


    fun shouldDelayLanguageDoneButton(): Boolean = getBoolean(DELAY_SHOW_LANGUAGE_DONE_BUTTON, true)
    fun getTimeDelayButtonDoneLanguage(): Long = getLong(
        TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON,
        DEFAULT_TIME_DELAY_SHOW_LANGUAGE_DONE_BUTTON
    )
}







