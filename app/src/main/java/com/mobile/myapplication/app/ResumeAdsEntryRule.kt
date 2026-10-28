package com.mobile.myapplication.app

import com.mobile.myapplication.ads.AdRemoteConfig
import com.mobile.myapplication.ads.inter_welcome_back
import com.mobile.myapplication.ads.native_welcome
import com.mobile.myapplication.ads.open_resume

enum class ResumeAdsEntryMode {
    OPEN_RESUME,
    WELCOME,
    NONE,
}

object ResumeAdsEntryRule {
    @Volatile
    private var suppressNextResume = false

    /**
     * Suppresses the next process-resume ad/screen. This is used for external
     * system flows such as App Settings where returning must reveal the
     * feature screen that opened the flow.
     */
    fun suppressNextResume() {
        suppressNextResume = true
    }

    fun consumeResumeSuppression(): Boolean {
        if (!suppressNextResume) return false
        suppressNextResume = false
        return true
    }

    fun currentMode(): ResumeAdsEntryMode {
        if (!AdRemoteConfig.isInitialized()) return ResumeAdsEntryMode.NONE

        val canUseOpenResume = AdRemoteConfig.open_resume.isEnable
        if (canUseOpenResume) return ResumeAdsEntryMode.OPEN_RESUME

        val canUseWelcome = AdRemoteConfig.native_welcome.isEnable && AdRemoteConfig.inter_welcome_back.isEnable
        return if (canUseWelcome) ResumeAdsEntryMode.WELCOME else ResumeAdsEntryMode.NONE
    }

    fun shouldEnableOpenResume(): Boolean = currentMode() == ResumeAdsEntryMode.OPEN_RESUME

    fun shouldShowWelcomeOnResume(): Boolean =
        currentMode() == ResumeAdsEntryMode.WELCOME && !AdRemoteConfig.open_resume.isEnable
}








