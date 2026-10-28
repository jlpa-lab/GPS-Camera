package com.gps.map.camera.and.time.stamp.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ads.module.admob.AppOpenManager
import com.ads.module.ads.ERainAd
import com.ads.module.billing.AppPurchase
import com.gps.map.camera.and.time.stamp.ui.language.LanguageActivity
import com.gps.map.camera.and.time.stamp.ui.uninstall.ReasonActivity
import com.gps.map.camera.and.time.stamp.ui.welcome.WelcomeActivity
import com.gps.map.camera.and.time.stamp.ads.AdRemoteConfig
import com.gps.map.camera.and.time.stamp.ads.inter_welcome_back
import com.gps.map.camera.and.time.stamp.ui.onboarding.OnBoardingActivity
import com.gps.map.camera.and.time.stamp.service.LocationSharingCoordinator
import com.gps.map.camera.and.time.stamp.GlobalApplication
import com.gps.map.camera.and.time.stamp.ui.splash.SplashActivity
import com.gps.map.camera.and.time.stamp.utils.Routes
import kotlin.jvm.java


class AppLifecycleObserver : DefaultLifecycleObserver {

    private val listActivityDisableResume = arrayListOf(
        SplashActivity::class.java,
        LanguageActivity::class.java,
        OnBoardingActivity::class.java,
        WelcomeActivity::class.java,
        ReasonActivity::class.java,
       // PhoneLocatorActivity::class.java,
    )

    override fun onStart(owner: LifecycleOwner) {
        val currentActivity = GlobalApplication.currentActivity
        if (currentActivity != null) {
            LocationSharingCoordinator.syncAcceptedFriends(currentActivity.applicationContext)
            if (ResumeAdsEntryRule.consumeResumeSuppression()) {
                // Returning from Android Settings must restore the feature
                // that opened it, without inserting Welcome Back or an app
                // open ad into that navigation path.
                AppOpenManager.getInstance().disableAppResume()
                return
            }
            val isDisable = listActivityDisableResume.any { clazz ->
                clazz.isInstance(currentActivity)
            }
            if (!isDisable && ResumeAdsEntryRule.shouldShowWelcomeOnResume()
                && !AppOpenManager.getInstance().isInterstitialShowing
                && !AppPurchase.getInstance().isPurchased(currentActivity.applicationContext)
                && ERainAd.getInstance().getShouldDisplayInterWelcomeBack(AdRemoteConfig.inter_welcome_back.enableUaCheck)
            ) {
                AppOpenManager.getInstance().disableAppResume()
                if (AppOpenManager.getInstance().isDisableAdResumeByClickAction)
                    AppOpenManager.getInstance().isDisableAdResumeByClickAction = false
                else
                    Routes.startWelcomeActivity(currentActivity)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {}
}
