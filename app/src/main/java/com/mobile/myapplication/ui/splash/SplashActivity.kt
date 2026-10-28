package com.mobile.myapplication.ui.splash

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings
import android.view.Window
import com.ads.module.admob.AppOpenManager
import com.ads.module.ads.ERainAd
import com.ads.module.funtion.AdCallback
import com.mobile.myapplication.R
import com.mobile.myapplication.ads.AdRemoteConfig
import com.mobile.myapplication.ads.AdsManager
import com.mobile.myapplication.ads.AdsManager.loadNativeLanguage
import com.mobile.myapplication.ads.RemoteConfigUtils
import com.mobile.myapplication.ads.banner_splash
import com.mobile.myapplication.ads.banner_splash_uninstall
import com.mobile.myapplication.ads.inter_splash
import com.mobile.myapplication.ads.inter_splash_uninstall
import com.mobile.myapplication.ads.open_resume
import com.mobile.myapplication.app.AppConstants
import com.mobile.myapplication.app.ResumeAdsEntryRule
import com.mobile.myapplication.localization.LocaleManager
import com.mobile.myapplication.pref.AppSharedPref
import com.mobile.myapplication.pref.AppSharedPreferencesApp
import com.mobile.myapplication.service.LocationSharingCoordinator
import com.mobile.myapplication.bases.ConsentHandler
import com.mobile.myapplication.databinding.ActivitySplashBinding
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mobile.myapplication.utils.Routes
import com.onesignal.OneSignal


/** Displays the branded launch artwork while startup services initialize. */
class SplashActivity : AppCompatActivity() , RemoteConfigUtils.Listener {
    private val handler = Handler(Looper.getMainLooper())

    private var getConfigSuccess = false
    private var loaderAnimator: ObjectAnimator? = null
    private var showLanguageNextTime = true
    private var hasMovedFromSplash = false
    private var isCheckingNetworkForStartup = true
    private var hasStartedStartupFlow = false
    private var startupServicesStarted = false
    private var remoteConfigTimer: CountDownTimer? = null
    private lateinit var binding: ActivitySplashBinding
    private lateinit var consentHandler: ConsentHandler
    private var noInternetDialog: Dialog? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val appSharedPref: AppSharedPref by lazy {
        AppSharedPreferencesApp(applicationContext)
    }
    private fun shouldShowLanguageNextTime() = showLanguageNextTime

    private val isFromUninstallShortcut: Boolean
        get() = intent.getStringExtra(AppConstants.FROM_SHORTCUT) == AppConstants.ACTION_OPEN_UNINSTALL
                || intent.action == "android.intent.action.SHORTCUT_UNINSTALL_APP"

//    private val openLanguageScreen = Runnable {
//        startActivity(Intent(this, LanguageActivity::class.java))
//        finish()
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the Android 12+ splash handoff before Activity.onCreate so
        // the system splash exits as soon as this activity draws its layout.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // SplashActivity is not a BaseActivity, so apply the saved in-app
        // language before inflating the first screen after a cold start.
        LocaleManager.applyPersisted(this)
        // A new splash marks a new app-launch session. Track Friend may ask
        // for background location again during this session if it is still
        // missing, while the current session remains usable with foreground
        // "While using the app" access.
        LocationSharingCoordinator.resetPermissionPromptSession()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        binding = ActivitySplashBinding.inflate(layoutInflater)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        // Display the same layout instance used by binding so the splash banner is visible.
        setContentView(binding.root)
        Log.d("AppEvent", "SplashActivity")
        OneSignal.initWithContext(applicationContext, getString(R.string.onesignal_app_id))
    //    RemoteConfigUtils.init(this, this)

        val track = findViewById<View>(R.id.splash_loader_track)
        val segment = findViewById<View>(R.id.splash_loader_segment)

        // Move a fixed segment continuously across the loading track.
        track.post {
            loaderAnimator = ObjectAnimator.ofFloat(
                segment,
                View.TRANSLATION_X,
                -segment.width.toFloat(),
                track.width.toFloat()
            ).apply {
                duration = 700L
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }

//        waitForRemoteConfig()
        registerNetworkCallback()
        consentHandler = ConsentHandler(
            activity = this,
            appSharedPref = appSharedPref,
            trackingSuffix = 1,
            onConsentFlowCompleted = { checkRemoteConfigResult() }
        )
        // NEW: Initial network check to start app flow or show no internet dialog
        checkAndStartAppFlow()
    }


    private fun startAppFlow() {
        if (startupServicesStarted) return
        startupServicesStarted = true
        RemoteConfigUtils.init(this, this)
        waitForRemoteConfig()
        AdsManager.loadNativeLanguage(
            this,
            true,
            R.layout.layout_native_language_click
        )
    }

    private fun checkAndStartAppFlow() {
        if (isNetwork()) {
            noInternetDialog?.dismiss()
            binding.frBanner.visibility = View.VISIBLE
            if (isCheckingNetworkForStartup) {
                isCheckingNetworkForStartup = false
                startAppFlow()
            } else if (!hasStartedStartupFlow && !startupServicesStarted) {
                // Remote Config may have timed out while the device was
                // offline. Restart the startup services after validation.
                startAppFlow()
            }
        } else {
            showNoInternetDialog()
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        networkCallback = null
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Connection restored: Resume startup flow on main thread
                handler.post {
                    checkAndStartAppFlow()
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                // onAvailable can arrive before DNS validation completes.
                // Re-check when Android reports the validated capability too.
                handler.post {
                    checkAndStartAppFlow()
                }
            }

            override fun onLost(network: Network) {
                // Connection lost: Show dialog on main thread
                handler.post {
                    if (!isNetwork()) {
                        showNoInternetDialog()
                    }
                }
            }
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
        } catch (_: Exception) {}
    }
    private fun showNoInternetDialog() {
        binding.frBanner.visibility = View.GONE
        if (noInternetDialog == null) {
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_no_internet)
            dialog.setCancelable(false) // Prevent user from dismissing dialog without internet
            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
            }
            // Direct user to Wi-Fi settings when clicking "Connect" button
            dialog.findViewById<View>(R.id.btnConnectInternet)?.setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: Exception) {}
                }
            }
            noInternetDialog = dialog
        }
        noInternetDialog?.takeIf { !it.isShowing }?.show()
    }
    private fun waitForRemoteConfig() {
        remoteConfigTimer = object : CountDownTimer(AppConstants.DEFAULT_TIME_SPLASH, 100) {
            override fun onTick(millisUntilFinished: Long) {
                if (getConfigSuccess && millisUntilFinished < AppConstants.DEFAULT_LIMIT_TIME_SPLASH) {
                    startConsentOrContinue()
                    cancel()
                }
            }

            override fun onFinish() {
                startConsentOrContinue()
            }
        }.also { it.start() }
    }

    private fun startConsentOrContinue() {
        if (hasStartedStartupFlow || isFinishing || isDestroyed) return
        if (!isNetwork()) {
            startupServicesStarted = false
            showNoInternetDialog()
            return
        }
        hasStartedStartupFlow = true

        if (
            RemoteConfigUtils.getOnShowDialogConsent() &&
            appSharedPref.isConfirmConsent.not() &&
            appSharedPref.isUserGlobal.not() &&
            isNetwork()
        ) {
            consentHandler.requestConsent()
        } else {
            checkRemoteConfigResult()
        }
    }

    override fun loadSuccess() {
        getConfigSuccess = true
    }

    override fun onResume() {
        super.onResume()
        // Re-check network state when returning to app (e.g. after changing settings in Wi-Fi screen)
        if (!isCheckingNetworkForStartup && isNetwork()) {
            noInternetDialog?.dismiss()
            ERainAd.getInstance().onCheckShowSplashWhenFail(this@SplashActivity, object : AdCallback() {
                override fun onNextAction() {
                    super.onNextAction()
                    moveActivity()
                }
            }, 1000)
        } else if (!isNetwork()) {
            showNoInternetDialog()
        }
    }

    private fun checkRemoteConfigResult() {
        AdRemoteConfig.initialize(this, RemoteConfigUtils.getAdRemoteConfig())
        loadSplashBanner()
        if (!isFromUninstallShortcut) {
            loadNativeLanguage(
                this@SplashActivity,
                appSharedPref.firstLanguage,
                R.layout.layout_native_language
            )
        }
        val splashInterConfig = if (isFromUninstallShortcut) {
            AdRemoteConfig.inter_splash_uninstall
        } else {
            AdRemoteConfig.inter_splash
        }
        if (splashInterConfig.isEnable && isNetwork()) {
            ERainAd.getInstance().loadSplashInterstitialAds(
                this, splashInterConfig.id, 30000, 5000, object : AdCallback() {
                    override fun onNextAction() {
                        super.onNextAction()
                        moveActivity()
                    }

                    override fun onAdLoaded() {
                        super.onAdLoaded()
                    }
                })
        } else {
            moveActivity()
        }
        if (ResumeAdsEntryRule.shouldEnableOpenResume()) {
            AppOpenManager.getInstance().setAppResumeAdId(AdRemoteConfig.open_resume.id)
            AppOpenManager.getInstance().enableAppResume()
        } else {
            AppOpenManager.getInstance().disableAppResume()
        }
    }


    private fun moveActivity() {
        if (hasMovedFromSplash || isFinishing || isDestroyed) return
        hasMovedFromSplash = true
        loaderAnimator?.cancel()
        when {
            isFromUninstallShortcut -> Routes.startConfirmUninstallActivity(this)
            shouldShowLanguageNextTime() || appSharedPref.firstOnBoarding -> Routes.startLanguageActivity(
                this,
                null
            )

            else -> Routes.startMainActivity(this)
        }
       finish()
    }
    private fun loadSplashBanner() {
        val bannerConfig = if (isFromUninstallShortcut) {
            AdRemoteConfig.banner_splash_uninstall
        } else {
            AdRemoteConfig.banner_splash
        }
        AdsManager.loadBanner(
            this,
            bannerConfig,
            binding.frBanner,
            isCollapse = false
        )
    }

    override fun onDestroy() {
        // Clean up callbacks and dialog references
        unregisterNetworkCallback()
        noInternetDialog?.dismiss()
        noInternetDialog = null
        remoteConfigTimer?.cancel()
        if (::consentHandler.isInitialized) {
            consentHandler.clear()
        }
        loaderAnimator?.cancel()
        super.onDestroy()
    }

    private fun isNetwork(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        val hasTransport = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || // 0
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||     // 1
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)   // 3
        return hasTransport && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }


    companion object {
        // Use the project-defined Splash duration instead of the old 1-second shortcut.
        private const val SPLASH_DURATION_MS = AppConstants.DEFAULT_TIME_SPLASH
    }
}










