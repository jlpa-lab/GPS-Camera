package com.gps.map.camera.and.time.stamp.utils

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.gps.map.camera.and.time.stamp.MainActivity
import com.gps.map.camera.and.time.stamp.app.AppConstants
import com.gps.map.camera.and.time.stamp.ui.language.LanguageActivity
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthViewActivity
import com.gps.map.camera.and.time.stamp.ui.streetview.StreetViewActivity
import com.gps.map.camera.and.time.stamp.ui.mapview.MapViewActivity
import com.gps.map.camera.and.time.stamp.ui.onboarding.OnBoardingActivity
import com.gps.map.camera.and.time.stamp.ui.splash.SplashActivity
import com.gps.map.camera.and.time.stamp.ui.uninstall.ConfirmUninstallActivity
import com.gps.map.camera.and.time.stamp.ui.welcome.WelcomeActivity
import kotlin.jvm.java

object Routes {
    private fun Activity.launch(target: Class<*>, bundle: Bundle? = null) { startActivity(Intent(this, target).apply { putExtra(
        AppConstants.KEY_TRACKING_SCREEN_FROM, this@launch::class.java.simpleName); bundle?.let(::putExtras) }) }
    fun startMainActivity(fromActivity: Activity) {
        fromActivity.launch(MainActivity::class.java)
    }
    fun startOnBoardingActivity(fromActivity: Activity) = fromActivity.launch(OnBoardingActivity::class.java)
    fun startLanguageActivity(fromActivity: Activity, bundle: Bundle?) = fromActivity.launch(
        LanguageActivity::class.java, bundle)
    fun startSplashActivity(fromActivity: Activity) = fromActivity.launch(SplashActivity::class.java)
    fun startConfirmUninstallActivity(fromActivity: Activity) = fromActivity.launch(
        ConfirmUninstallActivity::class.java)
    fun startWelcomeActivity(fromActivity: Activity) = fromActivity.launch(WelcomeActivity::class.java)
    fun startEarthViewActivity(fromActivity: Activity) = fromActivity.launch(EarthViewActivity::class.java)
    fun startStreetViewActivity(fromActivity: Activity) = fromActivity.launch(StreetViewActivity::class.java)
    fun startMapViewActivity(fromActivity: Activity) = fromActivity.launch(MapViewActivity::class.java)
}
