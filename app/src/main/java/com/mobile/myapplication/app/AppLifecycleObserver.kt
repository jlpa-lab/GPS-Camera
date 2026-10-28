package com.mobile.myapplication.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ads.module.admob.AppOpenManager
import com.ads.module.ads.ERainAd
import com.ads.module.billing.AppPurchase
import com.mobile.myapplication.ui.language.LanguageActivity
import com.mobile.myapplication.ui.uninstall.ReasonActivity
import com.mobile.myapplication.ui.welcome.WelcomeActivity
import com.mobile.myapplication.ads.AdRemoteConfig
import com.mobile.myapplication.ads.inter_welcome_back
import com.mobile.myapplication.ui.onboarding.OnBoardingActivity
import com.mobile.myapplication.service.LocationSharingCoordinator
import com.mobile.myapplication.GlobalApplication
import com.mobile.myapplication.ui.splash.SplashActivity
import com.mobile.myapplication.utils.Routes
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
