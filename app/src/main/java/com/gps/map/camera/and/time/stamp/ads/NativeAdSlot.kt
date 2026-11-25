package com.gps.map.camera.and.time.stamp.ads

import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import com.ads.module.ads.wrapper.ApNativeAd
import com.facebook.shimmer.ShimmerFrameLayout
import com.gps.map.camera.and.time.stamp.R

/** Binds a preloaded native ad without tying the screen to a particular ad source. */
fun bindNativeAdSlot(
    activity: AppCompatActivity,
    root: View?,
    adLive: LiveData<ApNativeAd?>,
    loadingLive: LiveData<Boolean>,
    adContainerId: Int,
    shimmerId: Int = R.id.shimmer_native_small,
) {
    root ?: return
    val adContainer = root.findViewById<FrameLayout>(adContainerId) ?: return
    val shimmer = root.findViewById<ShimmerFrameLayout>(shimmerId) ?: return
    var pendingRender: Runnable? = null

    fun hide() {
        pendingRender?.let(root::removeCallbacks)
        pendingRender = null
        shimmer.stopShimmer()
        shimmer.visibility = View.GONE
        adContainer.visibility = View.GONE
        root.visibility = View.GONE
    }

    fun showLoading() {
        pendingRender?.let(root::removeCallbacks)
        pendingRender = null
        root.visibility = View.VISIBLE
        adContainer.visibility = View.GONE
        shimmer.visibility = View.VISIBLE
        shimmer.startShimmer()
    }

    fun render(ad: ApNativeAd) {
        showLoading()
        pendingRender = Runnable {
            if (!activity.isFinishing && !activity.isDestroyed && root.isAttachedToWindow) {
                adContainer.visibility = View.VISIBLE
                populateNativeAdView(activity, ad, adContainer, shimmer)
            }
        }.also { root.postDelayed(it, 220L) }
    }

    when {
        adLive.value != null -> render(adLive.value!!)
        loadingLive.value == true -> showLoading()
        else -> hide()
    }

    loadingLive.observe(activity) { loading ->
        if (loading && adLive.value == null) showLoading()
        else if (!loading && adLive.value == null) hide()
    }
    adLive.observe(activity) { ad ->
        if (ad != null) render(ad)
        else if (loadingLive.value != true) hide()
    }
}
