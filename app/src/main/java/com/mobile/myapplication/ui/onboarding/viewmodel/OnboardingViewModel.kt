package com.mobile.myapplication.ui.onboarding.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mobile.myapplication.ui.BaseViewModel
import kotlinx.coroutines.launch

class OnboardingViewModel : BaseViewModel() {

    private val _isNeedNextPage = MutableLiveData<Boolean>()
    val isNeedNextPage: LiveData<Boolean> = _isNeedNextPage

    private val _nativeAdFullLoaded = MutableLiveData<Boolean>()
    val nativeAdFullLoaded: LiveData<Boolean> = _nativeAdFullLoaded

    fun onNextClicked() {
        viewModelScope.launch {
            _isNeedNextPage.value = true
        }
    }

    fun onNextPageHandled() {
        _isNeedNextPage.value = false
    }

    fun notifyNativeAdFullLoaded() {
        viewModelScope.launch {
            _nativeAdFullLoaded.value = true
        }
    }
}








