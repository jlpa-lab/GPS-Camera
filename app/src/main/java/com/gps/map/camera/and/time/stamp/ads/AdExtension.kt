package com.gps.map.camera.and.time.stamp.ads

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
import android.widget.TextView
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.bases.ext.dpToPx

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
        .inflate(apNativeAd.layoutCustomNative, null) as? NativeAdView ?: run {
        containerShimmerLoading.stopShimmer()
        containerShimmerLoading.visibility = View.GONE
        adPlaceHolder.visibility = View.GONE
        return
    }

    applyAdBackground(adView, config?.colorBG)

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

    // Keep the reference layout's stable structure and only toggle optional blocks.
    // Re-parenting these views at runtime changes ConstraintLayout/LinearLayout
    // params and was the reason the home ad could render with the wrong size.
    if (config != null) {
        val validKeys = setOf("icon_headline", "body", "media", "cta")
        var enabledComponents = config.components
        if (enabledComponents.isEmpty() || enabledComponents.none { it in validKeys }) {
            enabledComponents = listOf("icon_headline", "body", "media", "cta")
        }
        val showIconHeadline = enabledComponents.contains("icon_headline")
        adView.findViewById<View>(R.id.block_icon_headline)?.visibility =
            if (showIconHeadline) View.VISIBLE else View.GONE
        adView.findViewById<View>(R.id.ad_app_icon)?.visibility =
            if (showIconHeadline) View.VISIBLE else View.GONE
        adView.findViewById<View>(R.id.ad_icon)?.visibility =
            if (showIconHeadline) View.VISIBLE else View.GONE
        adView.findViewById<View>(R.id.ad_headline)?.visibility =
            if (showIconHeadline) View.VISIBLE else View.GONE
        adView.findViewById<View>(R.id.ad_advertiser)?.visibility =
            if (showIconHeadline) View.VISIBLE else View.GONE
        adView.findViewById<View>(R.id.ad_body)?.visibility =
            if (enabledComponents.contains("body")) View.VISIBLE else View.GONE
        adView.findViewById<View>(R.id.ad_media)?.visibility =
            if (enabledComponents.contains("media")) View.VISIBLE else View.GONE
        adView.findViewById<View>(R.id.ad_call_to_action)?.visibility =
            if (enabledComponents.contains("cta")) View.VISIBLE else View.GONE
    }

    Admob.getInstance().populateUnifiedNativeAdView(apNativeAd.admobNativeAd, adView)
    adPlaceHolder.removeAllViews()
    adPlaceHolder.addView(adView)
    AdAnalytics.shown(config?.key ?: "unknown", "native", config?.id)
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

private fun applyAdBackground(adView: NativeAdView, colorBG: String?) {
    if (colorBG.isNullOrBlank() || colorBG.equals("default", ignoreCase = true)) return
    val color = runCatching { Color.parseColor(colorBG) }.getOrNull() ?: return
    val target = adView.findViewById<View>(R.id.ad_container) ?: adView
    target.background = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 18.dpToPx(adView.context).toFloat()
        setStroke(1.dpToPx(adView.context).toInt(), Color.argb(90, 126, 188, 255))
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
        (LayoutInflater.from(activity)
            .inflate(apNativeAd.layoutCustomNative, null) as? NativeAdView) ?: run {
            containerShimmerLoading.stopShimmer()
            containerShimmerLoading.visibility = View.GONE
            adPlaceHolder.visibility = View.GONE
            return
        }

    applyAdBackground(adView, AdsManager.getAdConfig(apNativeAd)?.colorBG)

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








