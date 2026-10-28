package com.gps.map.camera.and.time.stamp

import com.gps.map.camera.and.time.stamp.localization.LocaleManager
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import com.ads.module.application.AdsMultiDexApplication
import com.ads.module.admob.Admob
import com.ads.module.admob.AppOpenManager
import com.ads.module.ads.ERainAd
import com.ads.module.billing.AppPurchase
import com.ads.module.config.AdjustConfig
import com.ads.module.config.ERainAdConfig
import com.itg.devconfig.DevConfig
import com.gps.map.camera.and.time.stamp.ads.AdRemoteConfig
import com.gps.map.camera.and.time.stamp.app.AppActivityLifecycleCallbacks
import com.gps.map.camera.and.time.stamp.app.AppLifecycleObserver
import com.gps.map.camera.and.time.stamp.bases.ext.isNetwork
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.ui.language.LanguageActivity
import com.gps.map.camera.and.time.stamp.ui.onboarding.OnBoardingActivity
import com.gps.map.camera.and.time.stamp.ui.splash.SplashActivity
import com.gps.map.camera.and.time.stamp.ui.welcome.WelcomeActivity

/** Tracks the visible activity so application-level resume navigation stays safe. */
class GlobalApplication : AdsMultiDexApplication() {

    lateinit var appLifecycleObserver: AppLifecycleObserver
        private set
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var instance: GlobalApplication

        @SuppressLint("StaticFieldLeak")
        var currentActivity: Activity? = null
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        LocaleManager.initialize(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
        registerActivityLifecycleCallbacks(AppActivityLifecycleCallbacks())
        //FirebaseApp.initializeApp(this)
        MobileAds.initialize(this) {}
        DevConfig.init(
            context = this,
            nkhStudioVersion = BuildConfig.ERAIN_STUDIO_VERSION,
            playServicesAdsVersion = BuildConfig.PLAY_SERVICES_ADS_VERSION,
            gdprModuleVersion = BuildConfig.GDPR_MODULE_VERSION
        )
        AdRemoteConfig.initializeFromAssets(this)
        // 4. Initialize Ads Config & SDK
        initAds()

        // 5. Initialize Billing IAP
        initBilling()
        registerNetworkMonitor()

    }

    private fun registerNetworkMonitor() {
        val connectivity = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val mainHandler = Handler(Looper.getMainLooper())
        val refreshAds = {
            if (!applicationContext.isNetwork()) {
                AdsManager.clearAll()
            }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                mainHandler.post(refreshAds)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                mainHandler.post(refreshAds)
            }
        }
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
    }

    private fun initAds() {
        val environment =
            if (BuildConfig.DEBUG) ERainAdConfig.ENVIRONMENT_DEVELOP else ERainAdConfig.ENVIRONMENT_PRODUCTION
        mERainAdConfig = ERainAdConfig(this, environment)

        val adjustConfig = AdjustConfig(true, resources.getString(R.string.adjust_token))
        mERainAdConfig.adjustConfig = adjustConfig
        mERainAdConfig.facebookClientToken = resources.getString(R.string.facebook_client_token)
        mERainAdConfig.adjustTokenTiktok = resources.getString(R.string.event_token)
        mERainAdConfig.intervalInterstitialAd = 35

        mERainAdConfig.idAdResume = ""


        ERainAd.getInstance().init(this, mERainAdConfig)

        Admob.getInstance().setDisableAdResumeWhenClickAds(true)
        Admob.getInstance().setOpenActivityAfterShowInterAds(false)
        AppOpenManager.getInstance().disableAppResumeWithActivity(SplashActivity::class.java)
        AppOpenManager.getInstance().disableAppResumeWithActivity(LanguageActivity::class.java)
        AppOpenManager.getInstance().disableAppResumeWithActivity(OnBoardingActivity::class.java)
        AppOpenManager.getInstance().disableAppResumeWithActivity(WelcomeActivity::class.java)

    }
    private fun initBilling() {
        val listIAP: MutableList<String?> = ArrayList<String?>()
        listIAP.add("android.test.purchased")
        val listSub: MutableList<String?> = ArrayList<String?>()
        AppPurchase.getInstance().initBilling(this, listIAP, listSub)
    }
    fun Context.getSystemLocaleString(@StringRes resId: Int): String {
        val systemConfig = Resources.getSystem().configuration
        val systemLocale = systemConfig.locales[0]

        val config = Configuration(resources.configuration)
        config.setLocale(systemLocale)

        val systemContext = createConfigurationContext(config)
        return systemContext.getString(resId)
    }
}









