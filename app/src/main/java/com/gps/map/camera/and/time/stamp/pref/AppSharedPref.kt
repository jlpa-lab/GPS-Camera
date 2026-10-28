package com.gps.map.camera.and.time.stamp.pref

import android.content.SharedPreferences

interface AppSharedPref {

    val sharedPref: SharedPreferences

    val editor: SharedPreferences.Editor

    var languageCode: String

    var firstLanguage: Boolean

    var firstOnBoarding: Boolean

    var isConfirmConsent: Boolean

    var isUserGlobal: Boolean

    var isRate: Boolean
}







