package com.gps.map.camera.and.time.stamp.ui.language

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import com.ads.module.ads.wrapper.ApNativeAd
import com.itg.devconfig.utils.setOnAdminAdToggleListener
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.ads.AdsManager.loadNativeLanguageClick
import com.gps.map.camera.and.time.stamp.ads.RemoteConfigUtils
import com.gps.map.camera.and.time.stamp.ads.populateNativeAdView
import com.gps.map.camera.and.time.stamp.app.AppConstants
import com.gps.map.camera.and.time.stamp.bases.ext.isNetwork
import com.gps.map.camera.and.time.stamp.bases.goneView
import com.gps.map.camera.and.time.stamp.bases.visibleView
import com.gps.map.camera.and.time.stamp.databinding.ActivityLanguageBinding
import com.gps.map.camera.and.time.stamp.localization.LocaleManager
import com.gps.map.camera.and.time.stamp.ui.BaseActivity
import com.gps.map.camera.and.time.stamp.ui.onboarding.OnBoardingActivity
import com.gps.map.camera.and.time.stamp.utils.Routes

/** Handles language selection and the initial notification permission request. */
class LanguageActivity : BaseActivity<ActivityLanguageBinding>() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var confirmButton: ImageView
    private var selectedRow: View? = null
    private var selectedRadio: RadioButton? = null
    private var pendingLocale: String? = null
    private val fromSetting
        get() = intent.getBooleanExtra(AppConstants.KEY_SETTING, false)
    private val revealConfirmation = Runnable { confirmButton.visibility = View.VISIBLE }

    override fun getLayoutActivity(): Int = R.layout.activity_language

    override fun requestWindow() {
        // Keep the window preview the same color as the language screen while
        // AppCompat recreates the activity for the committed locale change.
        window.setBackgroundDrawableResource(R.color.screen_background)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // CHANGE: BaseActivity already inflated and attached this layout as mBinding.
        // Reusing it prevents the ad from being rendered into a detached copy.
        Log.d("AppEvent", "LanguageActivity")
        // Firebase Remote Config is initialized once from SplashActivity.
        confirmButton = mBinding.confirmLanguage
        populateLanguages()

        confirmButton.setOnClickListener {
          //  FirebaseAnalytics.getInstance(this).logEvent("LanguageActivity_confirm", null)
            val localeToApply = pendingLocale ?: appSharedPref.languageCode
            // Match the reference flow: persist the choice first. The next
            // BaseActivity applies it before inflating its layout.
            appSharedPref.languageCode = localeToApply
            LocaleManager.apply(this, localeToApply)
            if (fromSetting) {
                Routes.startMainActivity(this)
            } else {
                startActivity(Intent(this, OnBoardingActivity::class.java))
            }
            finish()
        }
        mBinding.tvLanguage.setOnAdminAdToggleListener(){
            Routes.startSplashActivity(this@LanguageActivity)
            finish()
        }
        mBinding.root.postDelayed({
            if (fromSetting) {
                // Settings opens this screen as a utility page; it must remain ad-free.
                mBinding.flAds.goneView()
            } else {
                // Observe and load language ads only for the onboarding entry path.
                listenLanguageAd()
                loadNativeLanguageClick(this, appSharedPref.firstLanguage, R.layout.layout_native_language_click)
                initAds()
            }
        }, 100L)
    }

    private fun populateLanguages() {
        // Inflate one shared row layout for every supported language.
        val languageList = findViewById<LinearLayout>(R.id.language_list)
        val inflater = LayoutInflater.from(this)

        languages.forEach { language ->
            val row = inflater.inflate(R.layout.item_language_option, languageList, false)
            val labels = getString(language.labelRes).split("\n", limit = 2)
            row.findViewById<TextView>(R.id.language_native_name).text = labels.first()
            row.findViewById<TextView>(R.id.language_english_name).text = labels.getOrElse(1) { labels.first() }
            val radio = row.findViewById<RadioButton>(R.id.language_radio)

            row.setOnClickListener {
                // Switch to the click-stage ad only during onboarding, never from Settings.
                if (!fromSetting) listenLanguageClickAd()
                selectedRow?.isSelected = false
                selectedRadio?.isChecked = false
                row.isSelected = true
                radio.isChecked = true
                selectedRow = row
                selectedRadio = radio

                handler.removeCallbacks(revealConfirmation)
                confirmButton.visibility = View.INVISIBLE
                // The confirmation tick follows the remotely configurable delay.
                // Do not apply the locale here. Applying it immediately causes
                // AppCompat to recreate this activity and produces a visible
                // flash before the user confirms with the Done button.
                pendingLocale = language.locale
//                FirebaseAnalytics.getInstance(this)
//                    .logEvent("LanguageActivity_select_${language.tag}", null)
                val delay = if (RemoteConfigUtils.shouldDelayLanguageDoneButton()) {
                    RemoteConfigUtils.getTimeDelayButtonDoneLanguage()
                } else {
                    0L
                }
                handler.postDelayed(revealConfirmation, delay)
            }
            languageList.addView(row)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(revealConfirmation)
        super.onDestroy()
    }
    private fun initAds() {
        if (fromSetting) {
            mBinding.flAds.goneView()
        } else {
            AdsManager.loadNativeOnboarding1(
                this,
                appSharedPref.firstOnBoarding,
                R.layout.layout_native_onboarding
            )
        }
    }

    private fun listenLanguageAd() {
        AdsManager.nativeLanguageAdLive.removeObservers(this)
        AdsManager.nativeLanguageAdLive.observe(this) { ad ->
            if (ad != null) showNativeLanguage(ad) else mBinding.flAds.goneView()
        }
    }

    private fun listenLanguageClickAd() {
        AdsManager.nativeLanguageClickAdLive.removeObservers(this)
        AdsManager.nativeLanguageClickAdLive.observe(this) { ad ->
            if (ad != null) showNativeLanguage(ad) else mBinding.flAds.visibility = View.GONE
        }
    }

    private fun showNativeLanguage(ad: ApNativeAd) {
        if (!isNetwork()) {
            mBinding.flAds.visibility = View.GONE
            return
        }
        mBinding.flAds.visibleView()
        populateNativeAdView(
            this,
            ad,
            mBinding.flAds,
            mBinding.shimmerAds.shimmerNativeLanguage
        )
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001

        private val languages = listOf(
            LanguageOption("english", "en", R.string.language_english),
            LanguageOption("vietnamese", "vi", R.string.language_vietnamese),
            LanguageOption("japanese", "ja", R.string.language_japanese),
            LanguageOption("korean", "ko", R.string.language_korean),
            LanguageOption("french", "fr", R.string.language_french),
            LanguageOption("spanish", "es", R.string.language_spanish),
            LanguageOption("german", "de", R.string.language_german),
            LanguageOption("portuguese", "pt", R.string.language_portuguese),
            LanguageOption("hindi", "hi", R.string.language_hindi),
            LanguageOption("chinese", "zh", R.string.language_chinese),
            LanguageOption("arabic", "ar", R.string.language_arabic),
            LanguageOption("russian", "ru", R.string.language_russian)
        )
    }

        private data class LanguageOption(val tag: String, val locale: String, val labelRes: Int)
}











