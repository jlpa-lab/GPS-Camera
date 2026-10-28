package com.mobile.myapplication.bases.ext

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.StringRes

internal const val CHECK_TIME_MULTI_CLICK = 500
private var mLastClickTime: Long = 0

fun Context.canTouch(): Boolean {
    if (SystemClock.elapsedRealtime() - mLastClickTime < CHECK_TIME_MULTI_CLICK) {
        return false
    }
    mLastClickTime = SystemClock.elapsedRealtime()
    return true
}

fun Context.showToastByString(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.showToastById(id: Int) {
    Toast.makeText(this, resources.getString(id), Toast.LENGTH_SHORT).show()
}

fun Context.getStringById(id: Int): String {
    return resources.getString(id)
}

fun Context.getCurrentSdkVersion(): Int {
    return Build.VERSION.SDK_INT
}

fun Context.isNetwork(): Boolean {
    val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        val hasTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return hasTransport && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    @Suppress("DEPRECATION")
    return cm.activeNetworkInfo?.isConnected == true
}

fun Context.getSystemLocaleString(@StringRes resId: Int): String {
    val systemConfig = Resources.getSystem().configuration
    val systemLocale = systemConfig.locales[0]

    val config = Configuration(resources.configuration)
    config.setLocale(systemLocale)

    val systemContext = createConfigurationContext(config)
    return systemContext.getString(resId)
}







