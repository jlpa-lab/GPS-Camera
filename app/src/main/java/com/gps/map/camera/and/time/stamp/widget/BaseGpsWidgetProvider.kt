package com.gps.map.camera.and.time.stamp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.Location
import android.widget.RemoteViews
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthViewActivity
import com.gps.map.camera.and.time.stamp.ui.mapview.MapViewActivity
import com.gps.map.camera.and.time.stamp.utils.location.GpsLocationProvider

/** Shared RemoteViews implementation for the launcher widgets shown by the Widget tab. */
abstract class BaseGpsWidgetProvider : AppWidgetProvider() {

    protected abstract val widgetLayout: Int
    protected abstract val widgetTitle: Int
    protected abstract val widgetImage: Int
    protected abstract val actionTarget: Class<*>

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { widgetId ->
            updateWidget(context, manager, widgetId)
        }

        if (this is LiveGpsWidgetProvider) {
            refreshLiveLocation(context, manager, ids)
        }
    }

    protected fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        locationText: String? = null
    ) {
        val views = RemoteViews(context.packageName, widgetLayout).apply {
            setTextViewText(R.id.widget_title, context.getString(widgetTitle))
            setImageViewResource(R.id.widget_image, widgetImage)
            locationText?.let { setTextViewText(R.id.widget_location, it) }
            val action = Intent(context, actionTarget)
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                action,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            setOnClickPendingIntent(R.id.widget_action, pendingIntent)
        }
        manager.updateAppWidget(widgetId, views)
    }

    private fun refreshLiveLocation(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        if (ids.isEmpty()) return
        val provider = GpsLocationProvider(context)
        if (!provider.hasPermission()) return

        provider.findCurrentLocation(
            onSuccess = { location ->
                provider.reverseGeocode(location) { locality ->
                    val label = locality ?: formatCoordinates(location)
                    ids.forEach { widgetId ->
                        updateWidget(context, manager, widgetId, label)
                    }
                }
            },
            onFailure = { }
        )
    }

    private fun formatCoordinates(location: Location): String =
        "%.4f°, %.4f°".format(location.latitude, location.longitude)

    protected fun currentWidgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, javaClass)
        )
}

class LiveGpsWidgetProvider : BaseGpsWidgetProvider() {
    override val widgetLayout = R.layout.appwidget_live_gps
    override val widgetTitle = R.string.widget_live_gps
    override val widgetImage = R.drawable.home_map_view
    override val actionTarget = MapViewActivity::class.java
}

class WorldViewWidgetProvider : BaseGpsWidgetProvider() {
    override val widgetLayout = R.layout.appwidget_world_view
    override val widgetTitle = R.string.widget_world_view
    override val widgetImage = R.drawable.home_earth_view
    override val actionTarget = EarthViewActivity::class.java
}

class TrendingViewWidgetProvider : BaseGpsWidgetProvider() {
    override val widgetLayout = R.layout.appwidget_trending_view
    override val widgetTitle = R.string.widget_trending_view
    override val widgetImage = R.drawable.home_live_video
    override val actionTarget = EarthViewActivity::class.java
}
