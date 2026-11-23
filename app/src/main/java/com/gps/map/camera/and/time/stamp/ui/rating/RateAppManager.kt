package com.gps.map.camera.and.time.stamp.ui.rating

import android.content.Context
import com.gps.map.camera.and.time.stamp.pref.AppSharedPreferencesApp

/** Coordinates the one-per-session rate prompt and its automatic entry triggers. */
object RateAppManager {
    enum class EntryPoint { AUTOMATIC, SETTINGS }

    private var shownThisSession = false
    private var automaticTriggerPending = false
    private var automaticLaunchInProgress = false

    fun markConnectionAdded() {
        automaticTriggerPending = true
    }

    fun markGpsToolUsed() {
        automaticTriggerPending = true
    }

    fun markNearbyUsed() {
        automaticTriggerPending = true
    }

    fun shouldShowAutomaticRateDialog(context: Context): Boolean =
        !AppSharedPreferencesApp(context).isRate && !shownThisSession &&
            automaticTriggerPending && !automaticLaunchInProgress

    /** Claims the first automatic prompt before starting its Activity. */
    fun claimAutomaticRateDialog(context: Context): Boolean {
        if (AppSharedPreferencesApp(context).isRate || shownThisSession ||
            !automaticTriggerPending || automaticLaunchInProgress
        ) return false
        automaticLaunchInProgress = true
        return true
    }

    fun canOpenSettingsRateDialog(context: Context): Boolean =
        !AppSharedPreferencesApp(context).isRate && !shownThisSession

    fun hasShownThisSession(): Boolean = shownThisSession

    /** Atomically claims the dialog so duplicate activity resumes cannot show it twice. */
    fun beginRateDialog(context: Context, entryPoint: EntryPoint): Boolean {
        // An explicit Settings action is a fresh user request and must still
        // open the dialog even if an automatic prompt was dismissed earlier
        // in this app session.
        if (entryPoint == EntryPoint.SETTINGS) {
            shownThisSession = true
            automaticTriggerPending = false
            automaticLaunchInProgress = false
            return true
        }
        if (AppSharedPreferencesApp(context).isRate) {
            automaticLaunchInProgress = false
            return false
        }
        if (shownThisSession) {
            automaticLaunchInProgress = false
            return false
        }
        if (entryPoint == EntryPoint.AUTOMATIC &&
            !automaticTriggerPending && !automaticLaunchInProgress
        ) return false

        shownThisSession = true
        automaticTriggerPending = false
        automaticLaunchInProgress = false
        return true
    }

    fun onUserRated(context: Context) {
        AppSharedPreferencesApp(context).isRate = true
        shownThisSession = true
        automaticTriggerPending = false
        automaticLaunchInProgress = false
    }

    fun onRateDialogDismissed() {
        // Dismissal blocks the prompt for this session, but is not persisted as Rated.
        shownThisSession = true
        automaticTriggerPending = false
        automaticLaunchInProgress = false
    }

    /** Negative feedback ends this session's prompt without permanently rating the user. */
    fun onNegativeFeedbackSubmitted() {
        shownThisSession = true
        automaticTriggerPending = false
        automaticLaunchInProgress = false
    }

    fun cancelAutomaticRateDialogClaim() {
        automaticLaunchInProgress = false
    }

    /** Consume only the trigger that was followed by an interstitial. */
    fun skipAutomaticTriggerForInterstitial() {
        automaticTriggerPending = false
        automaticLaunchInProgress = false
    }
}
