package com.mobile.myapplication.ui.uninstall

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.ads.module.ads.wrapper.ApNativeAd
import com.facebook.shimmer.ShimmerFrameLayout
import com.mobile.myapplication.MainActivity
import com.mobile.myapplication.R
import com.mobile.myapplication.ads.AdsManager
import com.mobile.myapplication.ads.populateNativeAdView
import com.mobile.myapplication.ui.BaseActivity
import com.mobile.myapplication.bases.goneView
import com.mobile.myapplication.bases.visibleView
import com.mobile.myapplication.databinding.ActivityUninstallReasonBinding

/** Collects a reason before handing uninstalling to Android's system screen. */
class ReasonActivity : BaseActivity<ActivityUninstallReasonBinding>() {

    override fun getLayoutActivity() = R.layout.activity_uninstall_reason
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContentView(mBinding.root)
        mBinding.root.findViewById<View>(R.id.fr_ads)?.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = 0 }
        applySystemInsets(findViewById(R.id.uninstall_reason_root))
        Log.d("AppEvent", "MainActivity_uninstall_screen_02")

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.back_button).setOnClickListener { openHome() }
        findViewById<View>(R.id.cancel_button).setOnClickListener {
            Log.d("AppEvent", "Uninstall02_cancel")
            openHome()
        }
        findViewById<View>(R.id.uninstall_button).setOnClickListener {
            // Android owns the final confirmation and package removal.
            try {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${packageName}".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (_: Exception) {
            }
            finish()

//            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
        }
    }
    override fun initViews() {
        super.initViews()
        // Keep the Reason screen ad identical in size and composition to Confirm.
        AdsManager.loadNativeSurvey(this, R.layout.layout_native_uninstall_reason)
    }

    override fun observerData() {
        super.observerData()
        AdsManager.nativeSurveyAdLive.observe(this) { ad -> renderSurveyAd(ad) }
    }
    private fun applySystemInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun openHome() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    private fun renderSurveyAd(ad: ApNativeAd?) {
        val frAds = mBinding.root.findViewById<FrameLayout>(R.id.fr_ads)
            ?: return
        if (ad == null) {
            frAds.goneView()
            return
        }
        frAds.visibleView()
        val shimmer = mBinding.root.findViewById<ShimmerFrameLayout>(R.id.shimmer_ads)
        if (shimmer != null) {
            populateNativeAdView(this, ad, frAds, shimmer)
        }
    }

      override fun onActivityBackPressed() {
          openHome()
       }

}










