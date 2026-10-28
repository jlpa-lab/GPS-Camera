package com.gps.map.camera.and.time.stamp.ui.onboarding.model

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gps.map.camera.and.time.stamp.R
import kotlinx.android.parcel.Parcelize


@Parcelize
data class OnboardingItem(
    @StringRes val title: Int = R.string.onboarding_title_1,
    @StringRes val description: Int = R.string.onboarding_title_1,
    @StringRes val textButton: Int =  R.string.next,
    @DrawableRes val imageResId: Int = R.drawable.onboarding_1_map,
    val positionIndicator: Int = -1,
    val isHasNativeOnPage1: Boolean = false,
    val isHasNativeOnPage4: Boolean = false,
    val isHasNativeFull: Boolean = false,
): Parcelable







