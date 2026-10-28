package com.mobile.myapplication.app

import android.R
import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ads.module.admob.AppOpenManager
import com.mobile.myapplication.GlobalApplication

import com.mobile.myapplication.service.LocationSharingCoordinator

class AppActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        GlobalApplication.currentActivity = activity
        LocationSharingCoordinator.syncAcceptedFriends(activity.applicationContext)
        if (ResumeAdsEntryRule.shouldShowWelcomeOnResume() ||
            !ResumeAdsEntryRule.shouldEnableOpenResume()
        ) {
            AppOpenManager.getInstance().disableAppResume()
        }
        val content = activity.findViewById<View>(R.id.content)
        if (activity::class.java.simpleName !in setOf(
                "SplashActivity", "LanguageActivity", "OnBoardingActivity", "WelcomeActivity",
                "ReasonActivity", "ConfirmUninstallActivity"
            )) {
            ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
                val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                view.setPadding(view.paddingLeft, statusTop, view.paddingRight, 0)
                insets
            }
            ViewCompat.requestApplyInsets(content)
        }
        if (activity::class.java.simpleName !in setOf(
                "SplashActivity",
                "LanguageActivity",
                "OnBoardingActivity",
                "WelcomeActivity"
            )) {
            hideNavigationBar(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (GlobalApplication.currentActivity == activity) {
            GlobalApplication.currentActivity = null
        }
    }

    private fun hideNavigationBar(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.decorView.windowInsetsController?.apply {
                hide(WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }
}
