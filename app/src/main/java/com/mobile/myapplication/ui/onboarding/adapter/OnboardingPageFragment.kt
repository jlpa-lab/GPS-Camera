package com.mobile.myapplication.ui.onboarding.adapter

import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import com.ads.module.ads.wrapper.ApNativeAd
import com.bumptech.glide.Glide
import com.mobile.myapplication.ui.onboarding.model.OnboardingItem
import com.mobile.myapplication.ui.onboarding.viewmodel.OnboardingViewModel
import com.mobile.myapplication.R
import com.mobile.myapplication.ads.AdsManager
import com.mobile.myapplication.bases.ext.parcelable
import com.mobile.myapplication.ui.BaseFragment
import com.mobile.myapplication.ads.populateNativeAdView
import com.mobile.myapplication.bases.click
import com.mobile.myapplication.bases.goneView
import com.mobile.myapplication.bases.invisibleView
import com.mobile.myapplication.bases.visibleView
import com.mobile.myapplication.databinding.FragmentOnboardingPageBinding

class OnboardingPageFragment : BaseFragment<FragmentOnboardingPageBinding>() {

    override fun getLayoutFragment(): Int = R.layout.fragment_onboarding_page

    companion object {
        private const val ARG_ONBOARDING_ITEM = "arg_onboarding_item"

        fun newInstance(onboardingItem: OnboardingItem) = OnboardingPageFragment().apply {
            arguments = bundleOf(ARG_ONBOARDING_ITEM to onboardingItem)
        }
    }

    private val onboardingViewModel by activityViewModels<OnboardingViewModel>()

    private var onboardingItem: OnboardingItem = OnboardingItem(
        title = R.string.onboarding_title_1,
        description = R.string.onboarding_des_1,
        textButton = R.string.next,
        imageResId = R.drawable.onboarding_1_map,
        positionIndicator = 0
    )

    override fun initViews() {
        arguments?.parcelable<OnboardingItem>(ARG_ONBOARDING_ITEM)?.let {
            onboardingItem = it
        }
        updateLayout()
        if (onboardingItem.isHasNativeFull) {
            mBinding.layoutAdsFull.visibleView()
        } else {
            mBinding.layoutAdsFull.invisibleView()
        }
    }

    override fun observerData() {
        observeAdChannel()
    }

    override fun onClickViews() {
        mBinding.btnNext.click { onboardingViewModel.onNextClicked() }
        mBinding.imgCloseAdsFull.click { onboardingViewModel.onNextClicked() }
    }

    private fun observeAdChannel() {
        val liveData: MutableLiveData<ApNativeAd?> = when {
            onboardingItem.isHasNativeOnPage1 -> AdsManager.nativeOnboarding1AdLive
            onboardingItem.isHasNativeOnPage4 -> AdsManager.nativeOnboarding4AdLive
            onboardingItem.isHasNativeFull -> AdsManager.nativeAdOnBoardingFullLive
            else -> {
                renderNoAd()
                return
            }
        }
        liveData.observe(viewLifecycleOwner) { ad -> renderAd(ad) }
    }

    private fun renderAd(ad: ApNativeAd?) {
        if (onboardingItem.isHasNativeFull) {
            if (ad != null) {
                mBinding.layoutAdsFull.visibleView()
                mBinding.imgCloseAdsFull.visibleView()
                mBinding.layoutContent.invisibleView()
                populateNativeAdView(
                    requireActivity(),
                    ad,
                    mBinding.layoutAdsFull,
                    mBinding.shimmerAdsFull.shimmerNativeFull
                )
            } else {
                mBinding.layoutContent.visibleView()
                mBinding.layoutAdsFull.invisibleView()
                mBinding.layoutAds.invisibleView()
                mBinding.imgCloseAdsFull.invisibleView()
            }
        } else if (onboardingItem.isHasNativeOnPage1 || onboardingItem.isHasNativeOnPage4) {
            if (ad != null) {
                mBinding.layoutAds.visibleView()
                populateNativeAdView(
                    requireActivity(),
                    ad,
                    mBinding.layoutAds,
                    mBinding.shimmerAds.shimmerNativeLanguage
                )
            } else {
                mBinding.layoutAds.invisibleView()
            }
            mBinding.layoutContent.visibleView()
            mBinding.layoutAdsFull.invisibleView()
            mBinding.imgCloseAdsFull.invisibleView()
        }
    }

    private fun renderNoAd() {
        mBinding.layoutContent.visibleView()
        mBinding.layoutAds.goneView()
        mBinding.layoutAdsFull.invisibleView()
        mBinding.imgCloseAdsFull.invisibleView()
    }

    private fun updateLayout() {
        // These are local, page-specific drawable resources. Clear any previous
        // request before binding so ViewPager2/Glide cannot briefly reuse the
        // neighbouring page's image when the user moves from page 1 to page 2.
        Glide.with(this).clear(mBinding.imgOnboarding)
        mBinding.imgOnboarding.setImageResource(onboardingItem.imageResId)
        mBinding.tvTitle.text = getString(onboardingItem.title)
        mBinding.tvDes.text = getString(onboardingItem.description)
        mBinding.btnNext.text = getString(onboardingItem.textButton)
        mBinding.imgIndicator0.setImageResource(R.drawable.ic_onboarding_indicator)
        mBinding.imgIndicator1.setImageResource(R.drawable.ic_onboarding_indicator)
        mBinding.imgIndicator2.setImageResource(R.drawable.ic_onboarding_indicator)
        mBinding.imgIndicator3.setImageResource(R.drawable.ic_onboarding_indicator)
        when (onboardingItem.positionIndicator) {
            0 -> mBinding.imgIndicator0.setImageResource(R.drawable.ic_onboarding_indicator_selected)
            1 -> mBinding.imgIndicator1.setImageResource(R.drawable.ic_onboarding_indicator_selected)
            2 -> mBinding.imgIndicator2.setImageResource(R.drawable.ic_onboarding_indicator_selected)
            3 -> mBinding.imgIndicator3.setImageResource(R.drawable.ic_onboarding_indicator_selected)
        }
    }
}








