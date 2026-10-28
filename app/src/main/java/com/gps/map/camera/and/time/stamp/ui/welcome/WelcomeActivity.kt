package com.gps.map.camera.and.time.stamp.ui.welcome

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.ads.module.admob.AppOpenManager
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.app.AppConstants
import com.gps.map.camera.and.time.stamp.localization.LocaleManager

/** No-ad welcome screen displayed after returning from the background. */
class WelcomeActivity : AppCompatActivity() {
    private var navigationInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleManager.applyPersisted(this)
        AppOpenManager.getInstance().disableAppResume()
        when (intent.getStringExtra(AppConstants.FROM_SHORTCUT)) {
//            AppConstants.ACTION_OPEN_PHONE_LOCATOR -> {
//                startActivity(Intent(this, PhoneLocatorActivity::class.java))
//                finish()
//                return
//            }
//            AppConstants.ACTION_OPEN_TRACK_FRIEND -> {
//                startActivity(Intent(this, TrackFriendActivity::class.java))
//                finish()
//                return
//            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // CHANGE: keep the Welcome Back screen immersive by hiding the device navigation bar.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.statusBarColor = Color.rgb(255, 254, 250)
        window.navigationBarColor = Color.rgb(255, 254, 250)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContentView(R.layout.activity_welcome)
        applySystemInsets(findViewById(R.id.welcome_root))
        Log.d("AppEvent", "WelcomeActivity")

        // Android's swipe-back gesture uses OnBackInvokedDispatcher on newer
        // devices. Register through AndroidX so it follows the same ad flow as
        // the hardware back button.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                continueFromWelcome()
            }
        })

        // Preload in the background. Start remains usable even when the ad
        // request is slow, blocked by DNS, offline, or disabled remotely.
        AdsManager.loadInterWelcome(this)

        findViewById<View>(R.id.welcome_start_button).setOnClickListener {
            Log.d("AppEvent", "WelcomeActivity_start")
            continueFromWelcome()
        }
    }

    override fun onResume() {
        super.onResume()
        AppOpenManager.getInstance().disableAppResume()
    }

    override fun onDestroy() {
        AppOpenManager.getInstance().disableAppResume()
        super.onDestroy()
    }

    private fun applySystemInsets(root: View) {
        // Keep content clear of status and navigation bars on every device.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun continueFromWelcome() {
        if (navigationInProgress || isFinishing || isDestroyed) return
        navigationInProgress = true
        AdsManager.showInterWelcome(this) { finish() }
    }
}










