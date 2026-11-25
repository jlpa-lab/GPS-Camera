package com.gps.map.camera.and.time.stamp.ads

import android.os.Bundle
import com.google.android.gms.ads.AdValue
import com.gps.map.camera.and.time.stamp.utils.ITGTrackingHelper

/** Centralized, stable Firebase Analytics contract for monetization events. */
object AdAnalytics {
    private const val EVENT_AD_REQUESTED = "ad_requested"
    private const val EVENT_AD_LOADED = "ad_loaded"
    private const val EVENT_AD_FAILED_TO_LOAD = "ad_failed_to_load"
    private const val EVENT_AD_SHOWN = "ad_shown"
    private const val EVENT_AD_IMPRESSION = "ad_impression"
    private const val EVENT_AD_CLICKED = "ad_clicked"
    private const val EVENT_AD_FAILED_TO_SHOW = "ad_failed_to_show"
    private const val EVENT_AD_CLOSED = "ad_closed"

    fun requested(placement: String, format: String, adUnitId: String? = null) =
        log(EVENT_AD_REQUESTED, placement, format, adUnitId = adUnitId)

    fun loaded(placement: String, format: String, adUnitId: String? = null) =
        log(EVENT_AD_LOADED, placement, format, adUnitId = adUnitId)

    fun failedToLoad(
        placement: String,
        format: String,
        error: String? = null,
        adUnitId: String? = null,
    ) = log(EVENT_AD_FAILED_TO_LOAD, placement, format, error, adUnitId)

    fun shown(placement: String, format: String, adUnitId: String? = null) =
        log(EVENT_AD_SHOWN, placement, format, adUnitId = adUnitId)

    fun impression(placement: String, format: String, adUnitId: String? = null) =
        log(EVENT_AD_IMPRESSION, placement, format, adUnitId = adUnitId)

    fun clicked(placement: String, format: String, adUnitId: String? = null) =
        log(EVENT_AD_CLICKED, placement, format, adUnitId = adUnitId)

    fun failedToShow(
        placement: String,
        format: String,
        error: String? = null,
        adUnitId: String? = null,
    ) = log(EVENT_AD_FAILED_TO_SHOW, placement, format, error, adUnitId)

    fun closed(placement: String, format: String, adUnitId: String? = null) =
        log(EVENT_AD_CLOSED, placement, format, adUnitId = adUnitId)

    fun revenuePaid(
        placement: String,
        format: String,
        value: AdValue,
        adUnitId: String? = null,
    ) {
        val params = Bundle().apply {
            putString("ad_placement", placement)
            putString("ad_format", format)
            putString("ad_platform", "admob")
            if (!adUnitId.isNullOrBlank()) putString("ad_unit_id", adUnitId)
            putLong("ad_value_micros", value.valueMicros)
            putDouble("value", value.valueMicros / 1_000_000.0)
            putString("ad_currency", value.currencyCode)
            putString("currency", value.currencyCode)
        }
        ITGTrackingHelper.logEvent("ad_revenue_paid", params)
    }

    private fun log(
        event: String,
        placement: String,
        format: String,
        error: String? = null,
        adUnitId: String? = null,
    ) {
        val params = Bundle().apply {
            putString("ad_placement", placement)
            putString("ad_format", format)
            putString("ad_platform", "admob")
            if (!adUnitId.isNullOrBlank()) putString("ad_unit_id", adUnitId)
            if (!error.isNullOrBlank()) putString("ad_error", error.take(100))
        }
        ITGTrackingHelper.logEvent(event, params)
    }
}
