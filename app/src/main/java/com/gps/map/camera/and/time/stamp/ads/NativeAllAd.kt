package com.gps.map.camera.and.time.stamp.ads

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.gps.map.camera.and.time.stamp.R

fun bindNativeAllAd(activity: AppCompatActivity, root: View?) {
    bindNativeAdSlot(
        activity = activity,
        root = root,
        adLive = AdsManager.nativeAllAdLive,
        loadingLive = AdsManager.nativeAllLoadingLive,
        adContainerId = R.id.native_all_ad,
    )
}
