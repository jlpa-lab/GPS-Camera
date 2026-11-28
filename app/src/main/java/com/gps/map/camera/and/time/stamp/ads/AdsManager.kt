package com.gps.map.camera.and.time.stamp.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.ads.module.R
import com.ads.module.ads.ERainAd
import com.ads.module.ads.wrapper.ApInterstitialAd
import com.ads.module.ads.wrapper.ApNativeAd
import com.ads.module.billing.AppPurchase
import com.ads.module.funtion.AdCallback
import com.ads.module.funtion.RewardCallback
import com.ads.module.util.AppConstant
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import timber.log.Timber

@SuppressLint("StaticFieldLeak")
object AdsManager {

    private const val WELCOME_AD_SHOW_FALLBACK_TIMEOUT_MS = 2_500L

    val nativeLanguageAdLive = MutableLiveData<ApNativeAd?>()
    val nativeLanguageClickAdLive = MutableLiveData<ApNativeAd?>()
    val nativeOnboarding1AdLive = MutableLiveData<ApNativeAd?>()
    val nativeOnboarding4AdLive = MutableLiveData<ApNativeAd?>()
    val nativeAdOnBoardingFullLive = MutableLiveData<ApNativeAd?>()
    val nativeAdOnBoardingFull2Live = MutableLiveData<ApNativeAd?>()
    val nativeSurveyAdLive = MutableLiveData<ApNativeAd?>()
    val nativeConfirmUninstallAdLive = MutableLiveData<ApNativeAd?>()
    val nativeWelcomeAdLive = MutableLiveData<ApNativeAd?>()
    val nativePermissionAdLive = MutableLiveData<ApNativeAd?>()
    val nativeHomeAdLive = MutableLiveData<ApNativeAd?>()
    val nativeAllAdLive = MutableLiveData<ApNativeAd?>()
    val nativeHomeLoadingLive = MutableLiveData(false)
    val nativeAllLoadingLive = MutableLiveData(false)
    val nativeWelcomeLoadingLive = MutableLiveData(false)
    var interWelcomeAdLive = MutableLiveData<ApInterstitialAd?>()

    var interHomeBackAdLive = MutableLiveData<ApInterstitialAd?>()
    var interHomeAdLive = MutableLiveData<ApInterstitialAd?>()

    var interAllAdLive = MutableLiveData<ApInterstitialAd?>()

    private val adConfigMap = mutableMapOf<ApNativeAd, AdUnitConfig>()
    fun getAdConfig(ad: ApNativeAd): AdUnitConfig? = adConfigMap[ad]

    private var interSplashAd: ApInterstitialAd? = null
    private var interOnboarding: ApInterstitialAd? = null

    private var rewardExample :RewardedAd? = null

    private fun trackedInterstitialCallback(
        placement: String,
        adUnitId: String? = null,
        onLoaded: (ApInterstitialAd?) -> Unit = {},
        onLoadFailed: (LoadAdError?) -> Unit = {},
        onNext: () -> Unit = {},
        onClosed: () -> Unit = {},
        onShow: () -> Unit = {},
        onImpression: () -> Unit = {},
        onClick: () -> Unit = {},
        onFailedShow: (AdError?) -> Unit = {},
    ) = object : AdCallback() {
        override fun onApInterstitialLoad(ad: ApInterstitialAd?) {
            super.onApInterstitialLoad(ad)
            AdAnalytics.loaded(placement, "interstitial", adUnitId)
            ad?.interstitialAd?.setOnPaidEventListener { value ->
                AdAnalytics.revenuePaid(placement, "interstitial", value, adUnitId)
            }
            onLoaded(ad)
        }

        override fun onAdFailedToLoad(error: LoadAdError?) {
            super.onAdFailedToLoad(error)
            AdAnalytics.failedToLoad(placement, "interstitial", error?.message, adUnitId)
            onLoadFailed(error)
        }

        override fun onInterstitialShow() {
            super.onInterstitialShow()
            AdAnalytics.shown(placement, "interstitial", adUnitId)
            onShow()
        }

        override fun onAdImpression() {
            super.onAdImpression()
            AdAnalytics.impression(placement, "interstitial", adUnitId)
            onImpression()
        }

        override fun onAdClicked() {
            super.onAdClicked()
            AdAnalytics.clicked(placement, "interstitial", adUnitId)
            onClick()
        }

        override fun onAdFailedToShow(error: AdError?) {
            super.onAdFailedToShow(error)
            AdAnalytics.failedToShow(placement, "interstitial", error?.message, adUnitId)
            onFailedShow(error)
        }

        override fun onAdFailedToShowHigh(error: AdError?) = onAdFailedToShow(error)
        override fun onAdFailedToShowMedium(error: AdError?) = onAdFailedToShow(error)
        override fun onAdFailedToShowAll(error: AdError?) = onAdFailedToShow(error)

        override fun onNextAction() {
            super.onNextAction()
            onNext()
        }

        override fun onAdClosed() {
            super.onAdClosed()
            AdAnalytics.closed(placement, "interstitial", adUnitId)
            onClosed()
        }
    }

    private fun loadNativeInternal(
        activity: Activity,
        config: AdUnitConfig,
        layoutRes: Int,
        liveData: MutableLiveData<ApNativeAd?>,
        shouldDisplay: Boolean = true,
        loadingLive: MutableLiveData<Boolean>? = null,
    ) {
        if (!config.isEnable
            || !RemoteConfigUtils.getShowAds()
            || AppPurchase.getInstance().isPurchased(activity)
            || !shouldDisplay
        ) {
            loadingLive?.postValue(false)
            liveData.postValue(null)
            return
        }
        // Keep one request in flight and reuse the preloaded ad when a screen
        // is recreated. Cancelling a slow request here causes the low-memory
        // devices that need preloading most to lose the ad entirely.
        if (liveData.value != null || loadingLive?.value == true) return
        loadingLive?.postValue(true)
        if (!activity.isNetworkAvailable()) {
            AdAnalytics.failedToLoad(config.key, "native", "network_unavailable", config.id)
            liveData.postValue(null)
            loadingLive?.postValue(false)
            return
        }

        AdAnalytics.requested(config.key, "native", config.id)
        ERainAd.getInstance()
            .loadNativeAdResultCallback(activity, config.id, layoutRes, object : AdCallback() {
                override fun onNativeAdLoaded(nativeAd: ApNativeAd) {
                    super.onNativeAdLoaded(nativeAd)
                    adConfigMap[nativeAd] = config
                    nativeAd.admobNativeAd?.setOnPaidEventListener { value ->
                        AdAnalytics.revenuePaid(config.key, "native", value, config.id)
                    }
                    AdAnalytics.loaded(config.key, "native", config.id)
                    liveData.postValue(nativeAd)
                    loadingLive?.postValue(false)
                }

                override fun onAdFailedToLoad(adError: LoadAdError?) {
                    super.onAdFailedToLoad(adError)
                    AdAnalytics.failedToLoad(config.key, "native", adError?.message, config.id)
                    liveData.postValue(null)
                    loadingLive?.postValue(false)
                }

                override fun onAdImpression() {
                    super.onAdImpression()
                    AdAnalytics.impression(config.key, "native", config.id)
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    AdAnalytics.clicked(config.key, "native", config.id)
                }
            })
    }

    fun loadNativeLanguage(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_language_1 else AdRemoteConfig.native_language_2
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeLanguageAdLive
        )
    }

    fun loadNativeLanguageClick(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_language_1_click else AdRemoteConfig.native_language_2_click
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeLanguageClickAdLive
        )
    }

    fun loadNativeOnboarding1(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_onboarding_1_1 else AdRemoteConfig.native_onboarding_2_1
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeOnboarding1AdLive
        )
    }

    fun loadNativeOnboarding4(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_onboarding_1_4 else AdRemoteConfig.native_onboarding_2_4
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeOnboarding4AdLive,
            ERainAd.getInstance().getShouldDisplayNativeOnboardingNormal2(config.enableUaCheck)
        )
    }

    fun loadNativeOnboardingFull(activity: Activity, isFirst: Boolean, layoutRes: Int) {
        val config =
            if (isFirst) AdRemoteConfig.native_onboarding_fullscreen_1_2 else AdRemoteConfig.native_onboarding_fullscreen_2_2
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeAdOnBoardingFullLive,
            ERainAd.getInstance().getShouldDisplayNativeOnboardingFull1(config.enableUaCheck)
        )
    }

//    fun loadNativeOnboardingFull2(activity: Activity, isFirst: Boolean, layoutRes: Int) {
//        val config =
//            if (isFirst) AdRemoteConfig.native_onboarding_fullscreen_1_4 else AdRemoteConfig.native_onboarding_fullscreen_2_4
//        loadNativeInternal(
//            activity,
//            config,
//            layoutRes,
//            nativeAdOnBoardingFull2Live,
//            ERainAd.getInstance().getShouldDisplayNativeOnboardingFull2(config.enableUaCheck)
//        )
//    }

    fun loadNativePermission(activity: Activity, layoutRes: Int) {
        val config = AdRemoteConfig.native_permission
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativePermissionAdLive,
            ERainAd.getInstance().getShouldDisplayNativePermission(config.enableUaCheck)
            )
    }

    fun loadNativeHome(activity: Activity, layoutRes: Int) {
        val config = AdRemoteConfig.native_home
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeHomeAdLive,
            ERainAd.getInstance().getShouldDisplayNativeHome(config.enableUaCheck),
            loadingLive = nativeHomeLoadingLive
        )
    }

    fun loadNativeAll(activity: Activity, layoutRes: Int) {
        loadNativeInternal(
            activity,
            AdRemoteConfig.native_all,
            layoutRes,
            nativeAllAdLive,
            loadingLive = nativeAllLoadingLive
        )
    }

    fun loadNativeSurvey(activity: Activity, layoutRes: Int) {
        loadNativeInternal(
            //changed from native_survey to native_uninstall
            activity, AdRemoteConfig.native_uninstall, layoutRes, nativeSurveyAdLive,
            ERainAd.getInstance()
                .getShouldDisplayWidgetUninstall(AdRemoteConfig.native_uninstall.enableUaCheck)
        )
    }

    fun loadNativeConfirmUninstall(activity: Activity, layoutRes: Int) {
        loadNativeInternal(
            activity,
            AdRemoteConfig.native_uninstall,
            layoutRes,
            nativeConfirmUninstallAdLive,
            ERainAd.getInstance()
                .getShouldDisplayWidgetUninstall(AdRemoteConfig.native_uninstall.enableUaCheck)
        )
    }

    fun loadNativeWelcome(activity: Activity, layoutRes: Int) {
        loadNativeInternal(
            activity,
            AdRemoteConfig.native_welcome_back,
            layoutRes,
            nativeWelcomeAdLive,
            ERainAd.getInstance().getShouldDisplayNativeWelcomeBack(AdRemoteConfig.native_welcome_back.enableUaCheck),
            loadingLive = nativeWelcomeLoadingLive
        )
    }

    // ── Dashboard / Test helpers (ignore shouldDisplay) ──

    /** Dedicated LiveData for customization preview – won't collide with real flows */
    val nativeDashboardPreviewLive = MutableLiveData<ApNativeAd?>()

    /**
     * Load a native ad for dashboard preview purposes.
     * Bypasses all shouldDisplay checks so it always loads.
     */
    fun loadNativeForDashboard(activity: Activity, configKey: String, layoutRes: Int) {
        val config = try {
            AdRemoteConfig.getInstance().ads[configKey]
                ?: AdUnitConfig(id = "", isEnable = false)
        } catch (_: Exception) {
            AdUnitConfig(id = "", isEnable = false)
        }
        // Force shouldDisplay = true to bypass SDK limits
        loadNativeInternal(
            activity,
            config,
            layoutRes,
            nativeDashboardPreviewLive,
            shouldDisplay = true
        )
    }

    /** Load native language ad for dashboard – ignores shouldDisplay */
    fun loadNativeLanguageForDashboard(activity: Activity, layoutRes: Int) {
        loadNativeForDashboard(activity, "native_language_1", layoutRes)
    }

    /** Load native onboarding full for dashboard – ignores shouldDisplay */
    fun loadNativeFullForDashboard(activity: Activity, layoutRes: Int) {
        loadNativeForDashboard(activity, "native_onboarding_fullscreen_1_3", layoutRes)
    }

    fun loadInterOnboarding(context: Context, ignoreLimit: Boolean = false) {
        val config = AdRemoteConfig.inter_onboarding
        if (!config.isEnable
            || !RemoteConfigUtils.getShowAds()
            || AppPurchase.getInstance().isPurchased(context)
            || (!ignoreLimit && !ERainAd.getInstance().getShouldDisplayInterOnboarding(config.enableUaCheck))
        ) {
            interOnboarding = null
            return
        }
        AdAnalytics.requested("inter_onboarding", "interstitial", config.id)
        interOnboarding = ERainAd.getInstance().getInterstitialAds(
            context,
            config.id,
            trackedInterstitialCallback("inter_onboarding", adUnitId = config.id)
        )
    }



    fun showInterOnboarding(context: Context, ignoreLimit: Boolean = false, onAction: () -> Unit) {
        val interstitial = interOnboarding
        if (interstitial != null && interstitial.isReady && !AppPurchase.getInstance()
                .isPurchased(context) && (ignoreLimit)
        ) {
            ERainAd.getInstance()
                .forceShowInterstitial(
                    context,
                    interstitial,
                    trackedInterstitialCallback(
                        "inter_onboarding",
                        adUnitId = AdRemoteConfig.inter_onboarding.id,
                        onNext = onAction
                    ),
                    true
                )
        } else {
            onAction()
        }
    }

    fun loadInterBack(context: Context, ignoreLimit: Boolean = false) {
        val cached = interHomeBackAdLive.value?.takeIf { it.isReady }
        val request = MutableLiveData<ApInterstitialAd?>()
        interHomeBackAdLive = request
        fun emit(ad: ApInterstitialAd?) {
            request.value = ad
        }
        val config = AdRemoteConfig.inter_back
        if (!config.isEnable
            || !RemoteConfigUtils.getShowAds()
            || AppPurchase.getInstance().isPurchased(context)
            || (!ignoreLimit && !ERainAd.getInstance()
                .getShouldDisplayInterWelcomeBack(config.enableUaCheck))
        ) {
            emit(null)
            return
        }
        if (cached != null) {
            emit(cached)
            return
        }
        var loaded: ApInterstitialAd? = null
        AdAnalytics.requested("inter_back", "interstitial", config.id)
        loaded = ERainAd.getInstance().getInterstitialAds(
            context,
            config.id,
            trackedInterstitialCallback(
                "inter_back",
                adUnitId = config.id,
                onLoaded = { emit(it ?: loaded) },
                onLoadFailed = { emit(null) }
            )
        )
        if (loaded?.isReady == true) emit(loaded)
    }

    fun loadInterWelcome(
        context: Context,
        ignoreLimit: Boolean = false,
        onLoaded: (ApInterstitialAd?) -> Unit = {}
    ) {
        val cached = interWelcomeAdLive.value?.takeIf { it.isReady }
        val request = MutableLiveData<ApInterstitialAd?>()
        interWelcomeAdLive = request
        var callbackInvoked = false
        fun emit(ad: ApInterstitialAd?) {
            request.value = ad
            if (!callbackInvoked) {
                callbackInvoked = true
                onLoaded(ad)
            }
        }
        // The Welcome Back flow has its own ad unit. Keep the live data name
        // stable because the screen consumes the same cache for start/back.
        val config = AdRemoteConfig.inter_welcome_back
        if (!config.isEnable
            || !RemoteConfigUtils.getShowAds()
            || AppPurchase.getInstance().isPurchased(context)
            || (!ignoreLimit && !ERainAd.getInstance()
                .getShouldDisplayInterWelcomeBack(config.enableUaCheck))
        ) {
            emit(null)
            return
        }
        if (cached != null) {
            emit(cached)
            return
        }
        var loaded: ApInterstitialAd? = null
        AdAnalytics.requested("inter_welcome_back", "interstitial", config.id)
        loaded = ERainAd.getInstance().getInterstitialAds(
            context,
            config.id,
            trackedInterstitialCallback(
                "inter_welcome_back",
                adUnitId = config.id,
                onLoaded = { emit(it ?: loaded) },
                onLoadFailed = { emit(null) }
            )
        )
        if (loaded?.isReady == true) emit(loaded)
    }

    /** Shows the back interstitial and reports whether it was consumed. */
    fun showInterHomeBack(
        context: Context,
        ignoreLimit: Boolean = false,
        onAction: (adWasShown: Boolean) -> Unit
    ) {
        if (!context.isNetworkAvailable()) {
            onAction(false)
            return
        }
        val interstitial = interHomeBackAdLive.value
        val allowShow = ignoreLimit || ERainAd.getInstance()
            .getShouldDisplayInterWelcomeBack(AdRemoteConfig.inter_back.enableUaCheck)
        if (interstitial != null && interstitial.isReady && !AppPurchase.getInstance()
                .isPurchased(context) && allowShow
        ) {
            val handler = Handler(Looper.getMainLooper())
            var adStarted = false
            var actionCompleted = false
            fun continueOnce(adWasShown: Boolean) {
                if (actionCompleted) return
                actionCompleted = true
                handler.removeCallbacksAndMessages(null)
                interHomeBackAdLive = MutableLiveData()
                loadInterBack(context, ignoreLimit = true)
                onAction(adWasShown)
            }
            handler.postDelayed(
                { if (!adStarted) continueOnce(false) },
                WELCOME_AD_SHOW_FALLBACK_TIMEOUT_MS
            )
            ERainAd.getInstance()
                .forceShowInterstitial(
                    context,
                    interstitial,
                    trackedInterstitialCallback(
                        "inter_back",
                        adUnitId = AdRemoteConfig.inter_back.id,
                        onShow = { adStarted = true },
                        onImpression = { adStarted = true },
                        onNext = { continueOnce(adStarted) },
                        onClosed = { continueOnce(adStarted) },
                        onFailedShow = { continueOnce(false) }
                    ),
                    false
                )
        } else {
            onAction(false)
        }
    }

    fun showInterWelcome(context: Context, ignoreLimit: Boolean = false, onAction: () -> Unit) {
        // Do not wait on a stale SDK ad object when the device is offline or
        // DNS validation has failed. The reference flow continues immediately
        // when an interstitial cannot be shown.
        if (!context.isNetworkAvailable()) {
            onAction()
            return
        }
        val interstitial = interWelcomeAdLive.value
        val config = AdRemoteConfig.inter_welcome_back
        val isPurchased = AppPurchase.getInstance().isPurchased(context)
        val allowShow = ignoreLimit || ERainAd.getInstance()
            .getShouldDisplayInterWelcomeBack(config.enableUaCheck)
        if (interstitial != null && interstitial.isReady && !isPurchased && allowShow
        ) {
            showLoadedWelcomeInterstitial(context, interstitial, onAction)
        } else if (config.isEnable && !isPurchased && allowShow) {
            // Never block the Welcome Back CTA on a new ad request. The ad is
            // already preloaded while the screen is visible; if it is not
            // ready (offline, DNS failure, timeout, or SDK failure), continue
            // immediately and let the next Welcome Back screen preload again.
            onAction()
        } else {
            onAction()
        }
    }

    private fun showLoadedWelcomeInterstitial(
        context: Context,
        interstitial: ApInterstitialAd,
        onAction: () -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var adStarted = false
        var actionCompleted = false
        fun continueOnce() {
            if (actionCompleted) return
            actionCompleted = true
            handler.removeCallbacksAndMessages(null)
            interWelcomeAdLive = MutableLiveData()
            // Keep the next Welcome Back cycle preloaded, matching the
            // reference ad flow without delaying the current navigation.
            loadInterWelcome(context, ignoreLimit = true)
            onAction()
        }
        val fallback = Runnable {
            // A stale ad can report itself ready but never invoke an SDK
            // show/failure callback (DNS failure, timeout, or mediation error).
            // Only bypass it when the ad never started; a real ad remains on
            // screen until its normal close callback.
            if (!adStarted) continueOnce()
        }
        handler.postDelayed(fallback, WELCOME_AD_SHOW_FALLBACK_TIMEOUT_MS)
        ERainAd.getInstance()
            .forceShowInterstitial(
                context,
                interstitial,
                trackedInterstitialCallback(
                    "inter_welcome_back",
                    adUnitId = AdRemoteConfig.inter_welcome_back.id,
                    onShow = { adStarted = true },
                    onImpression = { adStarted = true },
                    onNext = { continueOnce() },
                    onClosed = { continueOnce() },
                    onFailedShow = { continueOnce() }
                ),
                false
            )
    }


    fun loadInterHome(context: Context, ignoreLimit: Boolean = false) {
        val cached = interHomeAdLive.value?.takeIf { it.isReady }
        val request = MutableLiveData<ApInterstitialAd?>()
        interHomeAdLive = request
        fun emit(ad: ApInterstitialAd?) {
            request.value = ad
        }
        val config = AdRemoteConfig.inter_home
        if (!config.isEnable
            || !RemoteConfigUtils.getShowAds()
            || AppPurchase.getInstance().isPurchased(context)
            || (!ignoreLimit && !ERainAd.getInstance()
                .getShouldDisplayInterWelcomeBack(config.enableUaCheck))
        ) {
            emit(null)
            return
        }
        if (cached != null) {
            emit(cached)
            return
        }
        var loaded: ApInterstitialAd? = null
        AdAnalytics.requested("inter_home", "interstitial", config.id)
        loaded = ERainAd.getInstance().getInterstitialAds(
            context,
            config.id,
            trackedInterstitialCallback(
                "inter_home",
                adUnitId = config.id,
                onLoaded = { emit(it ?: loaded) },
                onLoadFailed = { emit(null) }
            )
        )
        if (loaded?.isReady == true) emit(loaded)
    }

    fun showInterHome(context: Context, ignoreLimit: Boolean = false, onAction: () -> Unit) {
        val interstitial = interHomeAdLive.value
        val allowShow = ignoreLimit || ERainAd.getInstance()
            .getShouldDisplayInterWelcomeBack(AdRemoteConfig.inter_home.enableUaCheck)
        if (interstitial != null && interstitial.isReady && !AppPurchase.getInstance()
                .isPurchased(context) && allowShow
        ) {
            var actionCompleted = false
            fun continueOnce() {
                if (actionCompleted) return
                actionCompleted = true
                interHomeAdLive = MutableLiveData()
                onAction()
            }
            ERainAd.getInstance()
                .forceShowInterstitial(
                    context,
                    interstitial,
                    trackedInterstitialCallback(
                        "inter_home",
                        adUnitId = AdRemoteConfig.inter_home.id,
                        onNext = { continueOnce() },
                        onClosed = { continueOnce() },
                        onFailedShow = { continueOnce() }
                    ),
                    false
                )
        } else {
            onAction()
        }
    }
    fun loadAndShowReward(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailed: () -> Unit
    ) {
        if (!RemoteConfigUtils.getShowAds() || AdRemoteConfig.reward_example.isEnable.not() || AppPurchase.getInstance()
                .isPurchased(activity)
        ) {
            onFailed()
            return
        }

        ERainAd.getInstance().initRewardAds(
            activity,
            AdRemoteConfig.reward_example.id,
            object : AdCallback() {
                override fun onRewardAdLoaded(rewardedAd: RewardedAd?) {
                    super.onRewardAdLoaded(rewardedAd)
                    rewardExample = rewardedAd

                    var isEarn = false
                    ERainAd.getInstance().showRewardAds(
                        activity,
                        rewardExample,
                        object : RewardCallback {
                            override fun onUserEarnedReward(var1: RewardItem?) {
                                isEarn = true
                                rewardExample = null
                            }

                            override fun onRewardedAdClosed() {
                                if (isEarn) onSuccess()
                                else onFailed()
                            }

                            override fun onRewardedAdFailedToShow(codeError: Int) {
                                rewardExample = null
                                onFailed()
                            }

                            override fun onAdClicked() {

                            }
                        }
                    )
                }

                override fun onAdFailedToLoad(i: LoadAdError?) {
                    super.onAdFailedToLoad(i)
                    onFailed()
                }
            }
        )
    }

    fun loadInterAll(context: Context, ignoreLimit: Boolean = false) {
        val cached = interAllAdLive.value?.takeIf { it.isReady }
        val request = MutableLiveData<ApInterstitialAd?>()
        interAllAdLive = request
        fun emit(ad: ApInterstitialAd?) {
            request.value = ad
        }
        val config = AdRemoteConfig.inter_all
        if (!config.isEnable
            || AppPurchase.getInstance().isPurchased(context)
            || (!ignoreLimit && !ERainAd.getInstance()
                .getShouldDisplayInterWelcomeBack(config.enableUaCheck))
        ) {
            emit(null)
            return
        }
        if (cached != null) {
            emit(cached)
            return
        }
        var loaded: ApInterstitialAd? = null
        loaded = ERainAd.getInstance().getInterstitialAds(context, config.id, object : AdCallback() {
            override fun onApInterstitialLoad(apInterstitialAd: ApInterstitialAd?) {
                super.onApInterstitialLoad(apInterstitialAd)
                emit(apInterstitialAd ?: loaded)
            }

            override fun onAdFailedToLoad(adError: LoadAdError?) {
                super.onAdFailedToLoad(adError)
                emit(null)
            }
        })
        if (loaded?.isReady == true) emit(loaded)
    }

    fun showInterAll(context: Context, ignoreLimit: Boolean = false, onAction: () -> Unit) {
        val interstitial = interAllAdLive.value
        val allowShow = (ignoreLimit || ERainAd.getInstance()
            .getShouldDisplayInterWelcomeBack(AdRemoteConfig.inter_all.enableUaCheck)
                )
        if (interstitial != null && interstitial.isReady && !AppPurchase.getInstance()
                .isPurchased(context) && allowShow
        ) {
            ERainAd.getInstance()
                .forceShowInterstitial(context, interstitial, object : AdCallback() {
                    override fun onNextAction() {
                        super.onNextAction()
                        interAllAdLive = MutableLiveData()
                        onAction()
                    }
                }, false)
        } else {
            onAction()
        }
    }

    fun loadBanner(
        context: Context,
        adUnitConfig: AdUnitConfig,
        frAds: FrameLayout,
        isCollapse: Boolean,
        onAdClicked: () -> Unit = {},
    ) {

        val activity = context as? Activity ?: run {
            frAds.visibility = View.GONE
            return
        }
        if (adUnitConfig.isEnable && RemoteConfigUtils.getShowAds()) {
            removeBannerView(activity, frAds)
            AdAnalytics.requested(adUnitConfig.key, "banner", adUnitConfig.id)
            if (isCollapse) ERainAd.getInstance().loadCollapsibleBanner(
                activity,
                adUnitConfig.id,
                AppConstant.CollapsibleGravity.BOTTOM,
                object : AdCallback() {
                    override fun onAdLoaded() {
                        super.onAdLoaded()
                        findLoadedBannerView(frAds)?.setOnPaidEventListener { value ->
                            AdAnalytics.revenuePaid(adUnitConfig.key, "banner", value, adUnitConfig.id)
                        }
                        AdAnalytics.loaded(adUnitConfig.key, "banner", adUnitConfig.id)
                        AdAnalytics.shown(adUnitConfig.key, "banner", adUnitConfig.id)
                    }

                    override fun onAdImpression() {
                        super.onAdImpression()
                        AdAnalytics.impression(adUnitConfig.key, "banner", adUnitConfig.id)
                    }

                    override fun onAdClicked() {
                        super.onAdClicked()
                        AdAnalytics.clicked(adUnitConfig.key, "banner", adUnitConfig.id)
                        onAdClicked()
                    }

                    override fun onAdFailedToLoad(i: LoadAdError?) {
                        super.onAdFailedToLoad(i)
                        AdAnalytics.failedToLoad(adUnitConfig.key, "banner", i?.message, adUnitConfig.id)
                        frAds.visibility = View.GONE
                        Timber.tag("AdsManager_Banner")
                            .d("Load banner on ${activity.javaClass.simpleName} failed by : ${i?.message}")
                    }
                })
            else ERainAd.getInstance()
                .loadBanner(activity, adUnitConfig.id, object : AdCallback() {
                    override fun onAdLoaded() {
                        super.onAdLoaded()
                        findLoadedBannerView(frAds)?.setOnPaidEventListener { value ->
                            AdAnalytics.revenuePaid(adUnitConfig.key, "banner", value, adUnitConfig.id)
                        }
                        AdAnalytics.loaded(adUnitConfig.key, "banner", adUnitConfig.id)
                        AdAnalytics.shown(adUnitConfig.key, "banner", adUnitConfig.id)
                    }

                    override fun onAdImpression() {
                        super.onAdImpression()
                        AdAnalytics.impression(adUnitConfig.key, "banner", adUnitConfig.id)
                    }

                    override fun onAdClicked() {
                        super.onAdClicked()
                        AdAnalytics.clicked(adUnitConfig.key, "banner", adUnitConfig.id)
                        onAdClicked()
                    }

                    override fun onAdFailedToLoad(i: LoadAdError?) {
                        super.onAdFailedToLoad(i)
                        AdAnalytics.failedToLoad(adUnitConfig.key, "banner", i?.message, adUnitConfig.id)
                        frAds.visibility = View.GONE
                        Timber.tag("AdsManager_Banner")
                            .d("Load banner on ${activity.javaClass.simpleName} failed by : ${i?.message}")
                    }
                })
        } else {
            frAds.removeAllViews()
            frAds.visibility = View.GONE
        }
    }

    fun destroyBanner(frAds: FrameLayout?) {
        if (frAds == null) return
        try {
            val container = frAds.findViewById<FrameLayout>(R.id.banner_container)
            if (container != null) {
                for (i in 0 until container.childCount) {
                    val view = container.getChildAt(i)
                    if (view is AdView) {
                        view.destroy()
                    }
                }
            }
            frAds.removeAllViews()
            frAds.visibility = View.GONE
        } catch (_: Exception) {
        }
    }

    @SuppressLint("InflateParams")
    private fun findLoadedBannerView(frAds: FrameLayout): AdView? {
        val container = frAds.findViewById<FrameLayout>(R.id.banner_container) ?: return null
        return (0 until container.childCount)
            .asSequence()
            .map(container::getChildAt)
            .filterIsInstance<AdView>()
            .firstOrNull()
    }

    @SuppressLint("InflateParams")
    private fun removeBannerView(activity: Activity, frAds: FrameLayout) {
        try {
            val container = frAds.findViewById<FrameLayout>(R.id.banner_container)
            if (container != null) {
                for (i in 0 until container.childCount) {
                    val view = container.getChildAt(i)
                    if (view is AdView) {
                        view.destroy()
                        container.removeView(view)
                    }
                }
            }
            val shimmerFrameLayout = LayoutInflater.from(activity)
                .inflate(R.layout.layout_banner_control, null)
            frAds.removeAllViews()
            frAds.addView(shimmerFrameLayout)
        } catch (_: Exception) {
        }
    }

    fun clearAll() {
        nativeLanguageAdLive.postValue(null)
        nativeLanguageClickAdLive.postValue(null)
        nativeOnboarding1AdLive.postValue(null)
        nativeOnboarding4AdLive.postValue(null)
        nativeAdOnBoardingFullLive.postValue(null)
        nativeSurveyAdLive.postValue(null)
        nativeConfirmUninstallAdLive.postValue(null)
        nativeWelcomeAdLive.postValue(null)
        nativeHomeAdLive.postValue(null)
        nativeAllAdLive.postValue(null)
        nativeHomeLoadingLive.postValue(false)
        nativeAllLoadingLive.postValue(false)
        nativeWelcomeLoadingLive.postValue(false)
        interWelcomeAdLive = MutableLiveData()
        interSplashAd = null
        interOnboarding = null
    }

    private fun Context.isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            val hasTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            hasTransport && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnectedOrConnecting == true
        }
    }
}








