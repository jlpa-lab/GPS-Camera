package com.mobile.myapplication.ads

import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.facebook.shimmer.ShimmerFrameLayout
import com.mobile.myapplication.R

fun bindNativeAllAd(activity: AppCompatActivity, root: View?) {
    root ?: return
    val adContainer = root.findViewById<FrameLayout>(R.id.native_all_ad) ?: return
    val shimmer = root.findViewById<ShimmerFrameLayout>(R.id.shimmer_native_small) ?: return
    var pendingRender: Runnable? = null

    fun hideAdContainer() {
        pendingRender?.let(root::removeCallbacks)
        pendingRender = null
        shimmer.stopShimmer()
        shimmer.visibility = View.GONE
        adContainer.visibility = View.GONE
        root.visibility = View.GONE
    }

    fun showShimmer() {
        pendingRender?.let(root::removeCallbacks)
        pendingRender = null
        root.visibility = View.VISIBLE
        adContainer.visibility = View.GONE
        shimmer.visibility = View.VISIBLE
        shimmer.startShimmer()
    }
    if (AdsManager.nativeAllAdLive.value == null && AdsManager.nativeAllLoadingLive.value != true) {
        hideAdContainer()
    } else if (AdsManager.nativeAllAdLive.value == null && AdsManager.nativeAllLoadingLive.value == true) {
        showShimmer()
    }

    AdsManager.nativeAllLoadingLive.observe(activity) { loading ->
        if (loading && AdsManager.nativeAllAdLive.value == null) {
            showShimmer()
        } else if (!loading && AdsManager.nativeAllAdLive.value == null) {
            hideAdContainer()
        }
    }

    AdsManager.nativeAllAdLive.observe(activity) { ad ->
        if (ad != null) {
            showShimmer()
            pendingRender = Runnable {
                if (!activity.isFinishing && !activity.isDestroyed && root.isAttachedToWindow) {
                    adContainer.visibility = View.VISIBLE
                    populateNativeAdView(activity, ad, adContainer, shimmer)
                }
            }.also { root.postDelayed(it, SHIMMER_HANDOFF_DELAY_MS) }
        } else {
            if (AdsManager.nativeAllLoadingLive.value != true) {
                hideAdContainer()
            }
        }
    }
}

private const val SHIMMER_HANDOFF_DELAY_MS = 220L
