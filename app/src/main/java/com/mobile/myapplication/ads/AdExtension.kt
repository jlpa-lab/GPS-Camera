package com.mobile.myapplication.ads

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import com.ads.module.admob.Admob
import com.ads.module.ads.ERainAd
import com.ads.module.ads.wrapper.ApNativeAd
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.nativead.NativeAdView
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.mobile.myapplication.R
import com.mobile.myapplication.bases.ext.dpToPx

/**
 * Top-level helper – CTA height/color resolved automatically from [AdsManager.getAdConfig].
 */
fun populateNativeAdView(
    activity: Activity,
    apNativeAd: ApNativeAd,
    adPlaceHolder: FrameLayout,
    containerShimmerLoading: ShimmerFrameLayout,
) {
    if (apNativeAd.admobNativeAd == null && apNativeAd.nativeView == null) {
        containerShimmerLoading.visibility = View.GONE
        return
    }

    val config = AdsManager.getAdConfig(apNativeAd)

    val adView = LayoutInflater.from(activity)
        .inflate(apNativeAd.layoutCustomNative, null) as NativeAdView

    if (config?.key == "native_all") {
        adView.findViewById<View>(R.id.ad_container)
            ?.setBackgroundResource(R.drawable.bg_native_ads_compact)

        adView.findViewById<TextView>(R.id.ad_icon)?.apply {
            setBackgroundResource(R.drawable.bg_home_ad_badge)
            minWidth = activity.resources.getDimensionPixelSize(R.dimen.native_ad_badge_min_width)
            minHeight = activity.resources.getDimensionPixelSize(R.dimen.native_ad_badge_min_height)
            setPadding(
                activity.resources.getDimensionPixelSize(R.dimen.native_ad_badge_padding_horizontal),
                activity.resources.getDimensionPixelSize(R.dimen.native_ad_badge_padding_vertical),
                activity.resources.getDimensionPixelSize(R.dimen.native_ad_badge_padding_horizontal),
                activity.resources.getDimensionPixelSize(R.dimen.native_ad_badge_padding_vertical)
            )
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    containerShimmerLoading.stopShimmer()
    containerShimmerLoading.visibility = View.GONE
    adPlaceHolder.visibility = View.VISIBLE

    adView.findViewById<View>(R.id.ad_call_to_action)?.let { ctaButton ->
        ctaButton.updateLayoutParams {
            val rawHeightDp = config?.heightCTA ?: 45
            val clampedHeightDp = rawHeightDp.coerceIn(36, 52)
            height = clampedHeightDp.dpToPx(activity).toInt()
            if (config?.key == "native_all" && this is ViewGroup.MarginLayoutParams) {
                bottomMargin = 10.dpToPx(activity).toInt()
            }

        }
        applyCtaColor(ctaButton, config?.colorCTA)
    }

    // Dynamic component reordering and visibility control
    if (config != null) {
        val validKeys = setOf("icon_headline", "body", "media", "cta")
        var enabledComponents = config.components
        if (enabledComponents.isEmpty() || enabledComponents.none { it in validKeys }) {
            enabledComponents = listOf("icon_headline", "body", "media", "cta")
        }
        val blockMap = mutableMapOf<String, View>()

        adView.findViewById<View>(R.id.block_icon_headline)?.let { blockMap["icon_headline"] = it }
        adView.findViewById<View>(R.id.ad_body)?.let { blockMap["body"] = it }
        adView.findViewById<View>(R.id.ad_media)?.let { blockMap["media"] = it }
        adView.findViewById<View>(R.id.ad_call_to_action)?.let { blockMap["cta"] = it }

        val adContainer = adView.findViewById<View>(R.id.ad_container) as? LinearLayout
        if (adContainer != null) {
            // Option 2: Dynamic reordering in LinearLayout container
            blockMap.values.forEach { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                view.visibility = View.GONE
            }
            enabledComponents.forEach { componentName ->
                blockMap[componentName]?.let { view ->
                    adContainer.addView(view)
                    view.visibility = View.VISIBLE
                }
            }
        } else {
            // Option 1 fallback: Toggle visibility on existing flat structures
            val showIconHeadline = enabledComponents.contains("icon_headline")
            val blockIconHeadline = adView.findViewById<View>(R.id.block_icon_headline)
            if (blockIconHeadline != null) {
                blockIconHeadline.visibility = if (showIconHeadline) View.VISIBLE else View.GONE
            } else {
                adView.findViewById<View>(R.id.ad_app_icon)?.visibility = if (showIconHeadline) View.VISIBLE else View.GONE
                adView.findViewById<View>(R.id.ad_icon)?.visibility = if (showIconHeadline) View.VISIBLE else View.GONE
                adView.findViewById<View>(R.id.ad_headline)?.visibility = if (showIconHeadline) View.VISIBLE else View.GONE
                adView.findViewById<View>(R.id.ad_advertiser)?.visibility = if (showIconHeadline) View.VISIBLE else View.GONE
            }
            adView.findViewById<View>(R.id.ad_body)?.visibility = if (enabledComponents.contains("body")) View.VISIBLE else View.GONE
            adView.findViewById<View>(R.id.ad_media)?.visibility = if (enabledComponents.contains("media")) View.VISIBLE else View.GONE
            adView.findViewById<View>(R.id.ad_call_to_action)?.visibility = if (enabledComponents.contains("cta")) View.VISIBLE else View.GONE
        }
    }

    Admob.getInstance().populateUnifiedNativeAdView(apNativeAd.admobNativeAd, adView)
    adPlaceHolder.removeAllViews()
    adPlaceHolder.addView(adView)
    // A loading update can be delivered after the ad update because the two
    // LiveData streams are independent. Re-assert the final loaded state after
    // the view is attached so the shimmer cannot remain over the ad.
    adPlaceHolder.post {
        containerShimmerLoading.stopShimmer()
        containerShimmerLoading.visibility = View.GONE
        adPlaceHolder.visibility = View.VISIBLE
    }
}

private fun applyCtaColor(ctaButton: View, colorCTA: String?) {
    val radiusPx = 20.dpToPx(ctaButton.context).toFloat()

    if (colorCTA.isNullOrBlank() || colorCTA == "default") {
        // Apply Default Gradient (#2878F0 -> #46B8F4)
        ctaButton.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.parseColor("#2878F0"),
                Color.parseColor("#46B8F4")
            )
        ).apply {
            cornerRadius = radiusPx
        }
    } else {
        // Apply Custom solid color from remote config
        try {
            val color = Color.parseColor(colorCTA)
            ctaButton.background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = radiusPx
            }
        } catch (_: IllegalArgumentException) { }
    }
}

/**
 * ERainAd extension – explicit ctaHeightInDp, kept for backward-compat.
 */
fun ERainAd.populateNativeAdView(
    activity: Activity,
    apNativeAd: ApNativeAd,
    adPlaceHolder: FrameLayout,
    containerShimmerLoading: ShimmerFrameLayout,
    ctaHeightInDp: Int = 40,
) {
    if (apNativeAd.admobNativeAd == null && apNativeAd.nativeView == null) {
        containerShimmerLoading.visibility = View.GONE
        return
    }

    val adView: NativeAdView =
        LayoutInflater.from(activity).inflate(apNativeAd.layoutCustomNative, null) as NativeAdView

    containerShimmerLoading.stopShimmer()
    containerShimmerLoading.visibility = View.GONE
    adPlaceHolder.visibility = View.VISIBLE

    adView.findViewById<View>(R.id.ad_call_to_action)?.updateLayoutParams {
        height = ctaHeightInDp.dpToPx(adPlaceHolder.context).toInt()
    }

    Admob.getInstance().populateUnifiedNativeAdView(apNativeAd.admobNativeAd, adView)
    adPlaceHolder.removeAllViews()
    adPlaceHolder.addView(adView)
}








