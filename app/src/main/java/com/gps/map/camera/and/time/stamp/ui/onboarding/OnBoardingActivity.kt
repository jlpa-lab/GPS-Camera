package com.gps.map.camera.and.time.stamp.ui.onboarding

import android.os.Build
import com.ads.module.ads.ERainAd
import androidx.activity.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.AdRemoteConfig
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.ads.RemoteConfigUtils
import com.gps.map.camera.and.time.stamp.ads.native_onboarding_fullscreen_1_3
import com.gps.map.camera.and.time.stamp.bases.ext.isNetwork
import com.gps.map.camera.and.time.stamp.databinding.ActivityOnboardingBinding
import com.gps.map.camera.and.time.stamp.ui.BaseActivity
import com.gps.map.camera.and.time.stamp.ui.onboarding.adapter.OnboardingAdapter
import com.gps.map.camera.and.time.stamp.ui.onboarding.model.OnboardingItem
import com.gps.map.camera.and.time.stamp.ui.onboarding.viewmodel.OnboardingViewModel
import com.gps.map.camera.and.time.stamp.ui.uninstall.ShortcutManager
import com.gps.map.camera.and.time.stamp.utils.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs

@AndroidEntryPoint
class OnBoardingActivity : BaseActivity<ActivityOnboardingBinding>() {

    override val shouldShowNavigationBars = RemoteConfigUtils.getOnShowNavigationButton()

    override fun getLayoutActivity(): Int = R.layout.activity_onboarding

    private val onboardingViewModel by viewModels<OnboardingViewModel>()
    private lateinit var onboardingAdapter: OnboardingAdapter
    private val onboardingItems = mutableListOf<OnboardingItem>()



    override fun initViews() {
        initPage()
        initOnboardingItems()

        mBinding.root.postDelayed({
            AdsManager.loadNativeOnboarding4(
                this,
                appSharedPref.firstOnBoarding,
                R.layout.layout_native_onboarding
            )
            AdsManager.loadNativeOnboardingFull(
                this,
                appSharedPref.firstOnBoarding,
                R.layout.layout_native_onboarding_full
            )

            AdsManager.loadInterOnboarding(this)

        }, 100L)
    }

    private fun registerShortcutsAfterOnboarding() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        // Keep shortcut availability behind the same UA/Remote Config gate as
        // the reference ad flow.
        if (ERainAd.getInstance().getShouldDisplayWidgetUninstall(
                RemoteConfigUtils.getOnEnableUninstallWidget()
            )
        ) {
            ShortcutManager.initShortCut(this@OnBoardingActivity)
        }
    }

    override fun observerData() {
        super.observerData()
        onboardingViewModel.isNeedNextPage.observe(this) {
            val currentPosition = mBinding.viewPager.currentItem
            if (currentPosition < onboardingAdapter.itemCount - 1) {
                mBinding.viewPager.currentItem = currentPosition + 1
            } else startNextActivity()
        }
    }

    private fun initPage() {
        onboardingAdapter = OnboardingAdapter(supportFragmentManager, lifecycle)
        mBinding.viewPager.adapter = onboardingAdapter
        mBinding.viewPager.clipToPadding = false
        mBinding.viewPager.clipChildren = false
        mBinding.viewPager.offscreenPageLimit = 4
        mBinding.viewPager.getChildAt(0).overScrollMode = RecyclerView.OVER_SCROLL_ALWAYS
        val compositePageTransformer = CompositePageTransformer()
        compositePageTransformer.addTransformer(MarginPageTransformer(100))
        compositePageTransformer.addTransformer { view, position ->
            val r = 1 - abs(position)
            view.scaleY = 0.8f + r * 0.2f
            val absPosition = abs(position)
            view.alpha = 1.0f - (1.0f - 0.3f) * absPosition
        }
        mBinding.viewPager.setPageTransformer(compositePageTransformer)

        mBinding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in onboardingItems.indices && onboardingItems[position].isHasNativeOnPage4) {
                    AdsManager.loadNativeHome(this@OnBoardingActivity, R.layout.layout_native_home)
                }
//                ITGTrackingHelper.logEvent(when(position){
//                    0 -> { EventTracking.ONBOARDING_FIRST}
//                    1 -> { EventTracking.ONBOARDING_SECOND}
//                    2 -> { EventTracking.ONBOARDING_FULL1}
//                    3 -> { EventTracking.ONBOARDING_THIRD}
//                    4 -> { EventTracking.ONBOARDING_FULL2}
//                    5 -> { EventTracking.ONBOARDING_4th}
//                    else -> EventTracking.ONBOARDING_FIRST
//                }, null)
            }
        })
    }

    private fun initOnboardingItems() {
        onboardingItems.clear()
        onboardingItems.add(
            OnboardingItem(
                title = R.string.onboarding_title_1,
                // CHANGE: use the dedicated onboarding description instead of repeating the title.
                description = R.string.onboarding_des_1,
                textButton = R.string.next,
                imageResId = R.drawable.onboarding_1_map,
                positionIndicator = 0,
                isHasNativeOnPage1 = true
            )
        )
        onboardingItems.add(
            OnboardingItem(
                title = R.string.onboarding_title_2,
                // CHANGE: use the dedicated onboarding description instead of repeating the title.
                description = R.string.onboarding_des_2,
                textButton = R.string.next,
                imageResId = R.drawable.onboarding_2_map,
                positionIndicator = 1
            )
        )
        if (isNetwork(this@OnBoardingActivity) && ERainAd.getInstance().getShouldDisplayNativeOnboardingFull1(
                AdRemoteConfig.native_onboarding_fullscreen_1_3.enableUaCheck))
            onboardingItems.add(OnboardingItem(isHasNativeFull = true))

        onboardingItems.add(
            OnboardingItem(
                title = R.string.onboarding_title_3,
                // CHANGE: use the dedicated onboarding description instead of repeating the title.
                description = R.string.onboarding_des_3,
                textButton = R.string.next,
                imageResId = R.drawable.onboarding_3_map,
                positionIndicator = 2,
            )
        )

        onboardingItems.add(
            OnboardingItem(
                title = R.string.onboarding_title_4,
                // CHANGE: use the dedicated onboarding description instead of repeating the title.
                description = R.string.onboarding_des_4,
                textButton = R.string.get_started,
                imageResId = R.drawable.onboarding_4_map,
                positionIndicator = 3,
                isHasNativeOnPage4 = true
            )
        )
        onboardingAdapter.submitData(onboardingItems)
    }

    private fun startNextActivity() {
        registerShortcutsAfterOnboarding()
        appSharedPref.firstOnBoarding = false
        AdsManager.showInterOnboarding(this) {
            Routes.startMainActivity(this)
            finish()
        }
    }

    override fun onActivityBackPressed() {
        // block back press on onboarding
    }
}








