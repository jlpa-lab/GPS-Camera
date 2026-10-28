package com.gps.map.camera.and.time.stamp.ui.uninstall

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.ads.module.ads.wrapper.ApNativeAd
import com.facebook.shimmer.ShimmerFrameLayout
import com.gps.map.camera.and.time.stamp.MainActivity
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.ads.populateNativeAdView
import com.gps.map.camera.and.time.stamp.ui.BaseActivity
import com.gps.map.camera.and.time.stamp.bases.goneView
import com.gps.map.camera.and.time.stamp.bases.visibleView
import com.gps.map.camera.and.time.stamp.databinding.ActivityUninstallConfirmBinding

/** First shortcut screen, giving the user a chance to keep the app. */
class ConfirmUninstallActivity : BaseActivity<ActivityUninstallConfirmBinding>() {
    override fun getLayoutActivity() = R.layout.activity_uninstall_confirm
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
        applySystemInsets(findViewById(R.id.uninstall_confirm_root))
        Log.d("AppEvent", "MainActivity_uninstall_screen_01")

        findViewById<View>(R.id.back_button).setOnClickListener { openHome() }
        findViewById<View>(R.id.keep_app_button).setOnClickListener {
            Log.d("AppEvent", "Uninstall01_keep_phone_tracker")
            openHome()
        }
        val fullText = "Over 90% of Users\nLove GPS Camera"
        val spannable = SpannableString(fullText).apply {
            val startIndex = fullText.indexOf("90%")
            if (startIndex != -1) {
            setSpan(
                ForegroundColorSpan(Color.parseColor("#03B1D4")),
                startIndex,
                startIndex + "90%".length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        }
        mBinding.confirmUninstallTitle.text = spannable
        findViewById<View>(R.id.continue_uninstall_button).setOnClickListener {
            Log.d("AppEvent", "Uninstall01_continue_to_uninstall")
            startActivity(Intent(this, ReasonActivity::class.java))
            finish()
        }
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
        // Reuse home instead of adding duplicate activities to the back stack.
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    override fun initViews() {
        super.initViews()
        AdsManager.loadNativeConfirmUninstall(this, R.layout.layout_native_uninstall_confirm)
    }

    override fun observerData() {
        super.observerData()
        AdsManager.nativeConfirmUninstallAdLive.observe(this) { ad ->
            renderConfirmUninstallAd(ad)
        }
    }
    private fun renderConfirmUninstallAd(ad: ApNativeAd?) {
        val frAds = mBinding.root.findViewById<FrameLayout>(R.id.fr_ads) ?: return
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










