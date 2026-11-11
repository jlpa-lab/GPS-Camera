package com.gps.map.camera.and.time.stamp.utils.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** Shared one-shot GPS lookup used by map-based tools. */
class GpsLocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val locationClient = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun findCurrentLocation(onSuccess: (Location) -> Unit, onFailure: (LocationProblem) -> Unit) {
        if (!hasPermission()) {
            onFailure(LocationProblem.PERMISSION_DENIED)
            return
        }
        if (!isLocationEnabled()) {
            onFailure(LocationProblem.LOCATION_DISABLED)
            return
        }

        locationClient.lastLocation
            .addOnSuccessListener { cached ->
                if (cached != null && isUsable(cached)) {
                    onSuccess(cached)
                } else {
                    requestFreshLocation(onSuccess, onFailure)
                }
            }
            .addOnFailureListener {
                requestFreshLocation(onSuccess, onFailure)
            }
    }

    private fun requestFreshLocation(
        onSuccess: (Location) -> Unit,
        onFailure: (LocationProblem) -> Unit
    ) {
        try {
            locationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { fresh ->
                if (fresh != null && isUsable(fresh)) onSuccess(fresh)
                else onFailure(LocationProblem.FIX_UNAVAILABLE)
            }.addOnFailureListener {
                onFailure(LocationProblem.FIX_UNAVAILABLE)
            }
        } catch (_: SecurityException) {
            onFailure(LocationProblem.PERMISSION_DENIED)
        } catch (_: IllegalStateException) {
            onFailure(LocationProblem.FIX_UNAVAILABLE)
        }
    }

    private fun isUsable(location: Location): Boolean {
        val ageMillis = System.currentTimeMillis() - location.time
        return location.latitude in -90.0..90.0 &&
            location.longitude in -180.0..180.0 &&
            location.accuracy > 0f &&
            location.accuracy <= MAX_ACCEPTED_ACCURACY_METERS &&
            ageMillis <= MAX_LOCATION_AGE_MILLIS
    }

    enum class LocationProblem {
        PERMISSION_DENIED,
        LOCATION_DISABLED,
        FIX_UNAVAILABLE
    }

    private companion object {
        const val MAX_ACCEPTED_ACCURACY_METERS = 100f
        const val MAX_LOCATION_AGE_MILLIS = 5 * 60 * 1000L
    }
}
